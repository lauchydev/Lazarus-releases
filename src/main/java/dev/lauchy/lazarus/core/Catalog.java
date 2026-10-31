package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * dragsim.co's item catalog: every item's id, display name, NPC sell price and ability
 * cooldowns. Fetched at most twice a day and kept in config/lazarus/cache, so it works
 * offline once it has been seen.
 * <p>
 * The catalog isn't the whole story: it leaves out some items, names others differently from
 * the game ("Dragon Fragments" for every fragment, "Necron's Aegis" for Necron Aegis) and has
 * no NPC price for some (Golden Eye). So the Lazarus server's item index of what real items say
 * (name, rarity, NPC price; {@code GET /items}, started from dragsim.co by tools/item-index and
 * added to by every Lazarus user) is laid over it, then whatever this computer has seen on items
 * in game, in menus, on the market and in profiles that the index doesn't know yet. What is seen
 * is sent to the server for everyone. The index is kept in config/lazarus/cache/items.json and
 * what was seen beyond it in items-seen.json, so both work offline.
 */
public final class Catalog {
	private static final Path FILE = Config.dir().resolve("cache").resolve("catalog.json");
	private static final Duration MAX_AGE = Duration.ofHours(12);
	/** The server's item index as last fetched. */
	private static final Path INDEX = Config.dir().resolve("cache").resolve("items.json");
	/** What this computer has seen beyond the index. */
	private static final Path SEEN = Config.dir().resolve("cache").resolve("items-seen.json");
	/** How often the index is fetched afresh. */
	private static final Duration INDEX_EVERY = Duration.ofHours(3);
	/** How many items one upload carries; the server takes up to 500. */
	private static final int UPLOAD = 400;
	/** What real items have said about themselves, by id: the server's index, then what this computer has seen beyond it. */
	private static final Map<String, Seen> seen = new ConcurrentHashMap<>();
	/** The server's index alone, so only what was learned beyond it is saved and sent. */
	private static final Map<String, Seen> index = new ConcurrentHashMap<>();
	/** Ids learned since they were last sent to the server. */
	private static final java.util.Set<String> unsent = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean saving = new AtomicBoolean();
	/** An id the server's index takes: the game's own, or "~" and one made from the name of an item that has none. */
	private static final java.util.regex.Pattern ITEM_ID = java.util.regex.Pattern.compile("~?[a-z0-9_]{1,64}");
	private static final java.util.regex.Pattern PET_ITEM = java.util.regex.Pattern.compile(".+_pet_(common|uncommon|rare|epic|legendary|mythic)");
	/** A reforge stone's description: "Applies the Renowned reforge". */
	private static final java.util.regex.Pattern APPLIES = java.util.regex.Pattern.compile("Applies the (\\w[\\w ]*?) reforge");
	/** The last catalog read, so what is seen later can be laid over it again. */
	private static volatile JsonElement base = new JsonObject();

	/** What a real item says about itself: its name in game, rarity and NPC price; "" or -1 where it says nothing. */
	private record Seen(String name, String rarity, long npc) {
		/** This, with what {@code over} says laid on top; where it says nothing, this stays (the server merges the same way). */
		Seen with(Seen over) {
			return new Seen(over.name.isEmpty() ? name : over.name, over.rarity.isEmpty() ? rarity : over.rarity, over.npc > 0 ? over.npc : npc);
		}
	}

	/**
	 * One catalog item. {@code material} is the vanilla item it looks like, {@code color} a
	 * leather dye ("#rrggbb" or "r,g,b") and {@code skin} a head's base64 texture; for skins
	 * and dyes sold as cosmetics these are the look they apply. Any may be empty.
	 * {@code description} and {@code setBonus} are lore lines with § colour codes.
	 * {@code abilities} is every ability the item can have; {@code defaultAbilities} the keys of
	 * those it has unless the item itself says otherwise (a Hyperion's scrolls change them).
	 */
	public record Item(String id, String name, long npcSell, List<Ability> abilities, String material, String color, String skin, String tier,
			List<String> description, SetBonus setBonus, List<String> defaultAbilities) {
	}

	/**
	 * An ability as its lore shows it. {@code hidden} ones work in game but aren't listed; a
	 * {@code fullSet} one is a full set bonus over {@code maxCount} pieces.
	 */
	public record Ability(String name, String trigger, double cooldown, List<String> lines, double cost, String costType, boolean hidden,
			String key, boolean fullSet, int maxCount) {
	}

	/** A stat a pet gives: {@code perLevel} times its level, or just that once when {@code flat}. */
	public record Perk(String stat, double perLevel, boolean flat) {
	}

	/** A pet ability: the lowest rarity that has it, and its lore lines (name first) at each level. */
	public record PetAbility(String minRarity, Map<Integer, List<String>> levels) {
	}

	/** A full set bonus: its name, lore lines and how many pieces make the set (0 if unknown). */
	public record SetBonus(String name, List<String> lines, int pieces) {
	}

	/**
	 * A pet type: its display name, head texture, whether it levels (most of Dragsim's pets sit
	 * at their max level), its category ("combat"), the stats it gives and its abilities' keys.
	 */
	public record Pet(String name, String skin, boolean levels, int maxLevel, String category, List<Perk> perks, List<String> abilities) {
	}

	private record Data(Map<String, Item> byId, Map<String, String> idByName, Map<String, List<String>> idsByName, Map<String, double[]> progression,
			Map<String, Pet> pets, Map<String, PetAbility> petAbilities, Map<String, List<String>> reforges, List<String> accessories) {
	}

	private static volatile Data data = new Data(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());

	private Catalog() {
	}

	/** Loads the cached copy now and refreshes it in the background when stale. */
	public static void init() {
		CompletableFuture.runAsync(() -> {
			readSeen(Config.readJson(INDEX), index);
			seen.putAll(index);
			readSeen(Config.readJson(SEEN), seen);
			// What was seen before it could be sent goes with the next upload.
			seen.forEach((id, s) -> {
				if (!s.equals(index.get(id))) unsent.add(id);
			});
			JsonElement cached = Config.readJson(FILE);
			use(cached != null ? cached : new JsonObject());
			if (cached == null || stale()) refresh();
			Shared.every(INDEX_EVERY, Catalog::syncIndex);
		});
	}

	/**
	 * Sends what was seen beyond the server's index, then fetches the index afresh: what this
	 * computer saw is kept only while the index still lacks it.
	 */
	private static void syncIndex() {
		upload();
		if (!(Shared.getJson("/items") instanceof JsonObject fresh)) return;
		Map<String, Seen> got = new HashMap<>();
		readSeen(fresh, got);
		if (got.isEmpty()) return;
		Config.writeJson(INDEX, fresh);
		Map<String, Seen> beyond = new HashMap<>();
		seen.forEach((id, s) -> {
			Seen known = got.get(id);
			if (s.equals(index.get(id)) || known != null && known.with(s).equals(known)) return;
			beyond.put(id, known == null ? s : known.with(s));
		});
		synchronized (Catalog.class) {
			index.clear();
			index.putAll(got);
			seen.clear();
			seen.putAll(got);
			seen.putAll(beyond);
			unsent.retainAll(beyond.keySet());
			use(base);
		}
		saveSeen();
	}

	/** Sends what was learned since the last upload to the server's index, in batches. */
	private static void upload() {
		if (!LazarusServer.configured()) return;
		List<String> ids = new java.util.ArrayList<>(unsent);
		for (int from = 0; from < ids.size(); from += UPLOAD) {
			JsonObject items = new JsonObject();
			for (String id : ids.subList(from, Math.min(ids.size(), from + UPLOAD))) {
				Seen s = seen.get(id);
				if (s != null) items.add(id, entry(s));
			}
			JsonObject body = new JsonObject();
			body.add("items", items);
			try {
				if (LazarusServer.post("/items", body).statusCode() != 200) return;
				items.keySet().forEach(unsent::remove);
			} catch (Exception e) {
				Lazarus.LOG.debug("Could not send learned items: {}", e.getMessage());
				return;
			}
		}
	}

	public static boolean loaded() {
		return !data.byId.isEmpty();
	}

	public static Item item(String id) {
		if (id == null) return null;
		String key = id.toLowerCase(Locale.ROOT);
		Item item = data.byId.get(key);
		// An item the site's catalogue lacks is learned by its name as "~" and that name, but the game
		// still calls it by the bare id (an applied "ultimate_hyperion_skin"), so that finds it too.
		return item != null || key.startsWith("~") ? item : data.byId.get("~" + key);
	}

	/**
	 * The catalog id for a display name. Reforges and stars sit around the base name, so
	 * if the whole name is unknown, leading words are dropped one at a time.
	 */
	public static String id(String displayName) {
		String n = Text.lower(displayName).replaceAll("[✪➊➋➌➍➎]+", "").trim();
		Map<String, String> byName = data.idByName;
		while (!n.isEmpty()) {
			String id = byName.get(n);
			if (id == null && n.endsWith("s")) id = byName.get(n.substring(0, n.length() - 1)); // "16 Gelatinous Membranes"
			if (id != null) return id;
			int space = n.indexOf(' ');
			if (space < 0) break;
			n = n.substring(space + 1);
		}
		return null;
	}

	/**
	 * The catalog id for a display name, told apart by its lore when several skins share the
	 * name: each Baby Skin is named the same and only its lore says which helmet it goes on.
	 */
	public static String id(String displayName, List<String> lore) {
		String id = id(displayName);
		Item item = item(id);
		List<String> twins = item == null ? null : data.idsByName.get(item.name.toLowerCase(Locale.ROOT));
		if (twins == null || lore.isEmpty()) return id;
		Map<String, List<String>> descriptions = new java.util.LinkedHashMap<>();
		for (String twin : twins) {
			Item i = item(twin);
			if (i != null) descriptions.put(twin, i.description);
		}
		String byLore = SameName.byLore(descriptions, lore);
		return byLore != null ? byLore : id;
	}

	/**
	 * The total XP needed for each level of a skill (index 0 is level 1), from the catalog's
	 * "progression" curves; skills without their own curve use "default". Empty if unknown.
	 */
	public static double[] curve(String skill) {
		Map<String, double[]> p = data.progression;
		double[] c = p.get(skill.toLowerCase(Locale.ROOT));
		return c != null ? c : p.getOrDefault("default", new double[0]);
	}

	/** A pet type ("ENDERDRAGON"), or null when unknown. */
	public static Pet pet(String type) {
		return type == null ? null : data.pets.get(type.toLowerCase(Locale.ROOT));
	}

	/** A pet ability by key ("pet_end_strike"), or null when unknown. */
	public static PetAbility petAbility(String key) {
		return key == null ? null : data.petAbilities.get(key.toLowerCase(Locale.ROOT));
	}

	/** The lore a reforge adds to an item ("sparkling": its bonus), empty when it adds none. */
	public static List<String> reforge(String name) {
		return name == null ? List.of() : data.reforges.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
	}

	/**
	 * The stones that apply each reforge, by the reforge's name in lower case ("renowned"), read
	 * from "Applies the Renowned reforge" in the stones' descriptions.
	 */
	public static Map<String, List<String>> reforgeStones() {
		Map<String, List<String>> out = new HashMap<>();
		for (Item item : data.byId.values()) {
			java.util.regex.Matcher m = APPLIES.matcher(Text.plain(String.join(" ", item.description())));
			if (m.find()) out.computeIfAbsent(m.group(1).toLowerCase(Locale.ROOT), k -> new java.util.ArrayList<>()).add(item.id());
		}
		return out;
	}

	/** A pet type's head texture (base64), or "" when unknown. */
	public static String petSkin(String type) {
		Pet p = pet(type);
		return p == null ? "" : p.skin;
	}

	/** Every accessory the catalogue lists (talismans, rings, artifacts, relics and the rest), by id. */
	public static List<String> accessories() {
		return data.accessories;
	}

	/** NPC sell value of one of {@code id}, or -1 when unknown. */
	public static long npcSell(String id) {
		Item i = item(id);
		return i == null ? -1 : i.npcSell;
	}

	/**
	 * Notes what a real item of {@code id} says about itself (from its {@code item_data}): its
	 * name, rarity and NPC price, each "" or -1 when it doesn't say. Anything new is used and
	 * saved a few seconds later, together with whatever else turns up by then, so a full menu of
	 * new items costs one rebuild.
	 */
	public static void learn(String id, String name, String rarity, long npc) {
		if (id == null || !ITEM_ID.matcher(id.toLowerCase(Locale.ROOT)).matches()) return;
		String key = id.toLowerCase(Locale.ROOT);
		String n = name == null ? "" : itemName(name);
		String r = rarity == null ? "" : rarity.toUpperCase(Locale.ROOT);
		Seen old = seen.get(key);
		Seen told = new Seen(n.equalsIgnoreCase(key) ? "" : n, r, npc > 0 ? npc : -1);
		Seen now = old == null ? told : old.with(told);
		if (now.equals(old)) return;
		seen.put(key, now);
		unsent.add(key);
		if (saving.getAndSet(true)) return;
		Shared.later(5, () -> {
			saving.set(false);
			use(base);
			saveSeen();
			upload();
		});
	}

	/** Keeps what was seen beyond the server's index, for the next start and the next upload. */
	private static void saveSeen() {
		JsonObject out = new JsonObject();
		seen.forEach((k, v) -> {
			if (!v.equals(index.get(k))) out.add(k, entry(v));
		});
		Config.writeJson(SEEN, out);
	}

	/** One item as the index holds it: [name, rarity, npc]. */
	private static com.google.gson.JsonArray entry(Seen s) {
		com.google.gson.JsonArray a = new com.google.gson.JsonArray();
		a.add(s.name);
		a.add(s.rarity);
		a.add(s.npc);
		return a;
	}

	/** A name as items carry it, without colour codes or a pet's "[Lvl 100]". */
	public static String itemName(String name) {
		return Text.plain(name).replaceFirst("^\\[Lvl \\d+\\]\\s*", "").trim();
	}

	/** Reads an index of {id: [name, rarity, npc]} into {@code into}. */
	private static void readSeen(JsonElement json, Map<String, Seen> into) {
		if (!(json instanceof JsonObject all)) return;
		for (var e : all.entrySet()) {
			if (!(e.getValue() instanceof com.google.gson.JsonArray a) || a.size() < 3) continue;
			try {
				long npc = (long) Math.ceil(a.get(2).getAsDouble()); // a few sell for under a coin
				into.put(e.getKey().toLowerCase(Locale.ROOT), new Seen(a.get(0).getAsString(), a.get(1).getAsString(), npc > 0 ? npc : -1));
			} catch (RuntimeException ignored) {
				// one bad entry shouldn't lose the rest of the index
			}
		}
	}

	private static boolean stale() {
		try {
			return Files.getLastModifiedTime(FILE).toInstant().plus(MAX_AGE).isBefore(java.time.Instant.now());
		} catch (IOException e) {
			return true;
		}
	}

	private static void refresh() {
		Http.json("/data/catalog.json").thenAccept(json -> {
			use(json);
			Config.writeJson(FILE, json);
		}).exceptionally(e -> {
			Lazarus.LOG.warn("Could not fetch the dragsim.co catalog: {}", e.getMessage());
			return null;
		});
	}

	private static synchronized void use(JsonElement json) {
		base = json;
		try {
			JsonObject root = json.getAsJsonObject();
			JsonObject items = root.get("items") instanceof JsonObject o ? o : new JsonObject();
			JsonObject lore = root.has("itemLore") ? root.getAsJsonObject("itemLore") : new JsonObject();
			Map<String, Item> byId = new HashMap<>();
			Map<String, String> idByName = new HashMap<>();
			List<String> accessories = new java.util.ArrayList<>();
			java.util.Set<String> cosmetics = new java.util.HashSet<>();
			for (var e : items.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				String id = e.getKey().toLowerCase(Locale.ROOT);
				if (string(o, "category").equalsIgnoreCase("ACCESSORY")) accessories.add(id);
				String name = o.has("name") ? Text.plain(o.get("name").getAsString()) : id;
				long npc = o.has("npc_sell_price") && !o.get("npc_sell_price").isJsonNull() ? o.get("npc_sell_price").getAsLong() : -1;
				JsonObject cosmetic = o.has("cosmetic") && o.get("cosmetic").isJsonObject() ? o.getAsJsonObject("cosmetic") : new JsonObject();
				if (!cosmetic.isEmpty()) cosmetics.add(id);
				String skin = string(cosmetic, "skin_value");
				if (skin.isEmpty()) skin = texture(o.get("skin"));
				String color = cosmetic.has("colors") && cosmetic.get("colors").isJsonArray() && !cosmetic.getAsJsonArray("colors").isEmpty()
						&& cosmetic.getAsJsonArray("colors").get(0).isJsonPrimitive() ? cosmetic.getAsJsonArray("colors").get(0).getAsString() : string(o, "color");
				JsonElement entryLore = lore.get(e.getKey());
				JsonObject loreObj = entryLore != null && entryLore.isJsonObject() ? entryLore.getAsJsonObject() : new JsonObject();
				byId.put(id, new Item(id, name, npc, abilities(entryLore), string(o, "material"), color, skin, string(o, "tier"),
						lines(o.has("description") ? o.get("description") : loreObj.get("description")), setBonus(o, loreObj), lines(o.get("default_abilities"))));
				idByName.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
			}
			// What real items say wins: their name in game finds them, and fills a rarity or NPC price the catalog lacks.
			seen.forEach((id, s) -> {
				Item i = byId.get(id);
				String name = s.name.isEmpty() ? i == null ? id : i.name : s.name;
				String tier = s.rarity.isEmpty() ? i == null ? "" : i.tier : s.rarity;
				long npc = i != null && i.npcSell > 0 ? i.npcSell : s.npc;
				byId.put(id, i == null ? new Item(id, name, npc, List.of(), "", "", "", tier, List.of(), null, List.of())
						: new Item(id, name, npc, i.abilities, i.material, i.color, i.skin, tier, i.description, i.setBonus, i.defaultAbilities));
				// A pet item ("goop_pet_legendary", named "Goop") never takes a name from the item it's named after,
				// nor does one known only by its name in a menu ("~summoning_eye") from the real item.
				if (s.name.isEmpty()) return;
				if (id.startsWith("~") || PET_ITEM.matcher(id).matches()) idByName.putIfAbsent(s.name.toLowerCase(Locale.ROOT), id);
				else idByName.put(s.name.toLowerCase(Locale.ROOT), id);
			});
			Map<String, double[]> curves = new HashMap<>();
			if (root.has("progression") && root.get("progression").isJsonObject()) {
				for (var e : root.getAsJsonObject("progression").entrySet()) {
					if (!e.getValue().isJsonArray()) continue;
					curves.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonArray().asList().stream()
							.filter(JsonElement::isJsonPrimitive).mapToDouble(JsonElement::getAsDouble).toArray());
				}
			}
			Map<String, Pet> pets = new HashMap<>();
			if (root.has("pets") && root.get("pets").isJsonObject()) {
				for (var e : root.getAsJsonObject("pets").entrySet()) {
					if (!e.getValue().isJsonObject()) continue;
					JsonObject o = e.getValue().getAsJsonObject();
					String name = string(o, "display");
					boolean levels = o.has("levels") && o.get("levels").isJsonPrimitive() && o.get("levels").getAsBoolean();
					List<Perk> perks = o.has("perks") && o.get("perks").isJsonArray() ? o.getAsJsonArray("perks").asList().stream()
							.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
							.map(k -> new Perk(string(k, "stat"), number(k, "per_level"), bool(k, "flat")))
							.toList() : List.of();
					pets.put(e.getKey().toLowerCase(Locale.ROOT), new Pet(name, texture(o.get("skin")), levels, (int) number(o, "max_level"),
							string(o, "category"), perks, lines(o.get("abilities"))));
				}
			}
			Map<String, PetAbility> petAbilities = new HashMap<>();
			if (root.get("petAbilities") instanceof JsonObject all) {
				for (var e : all.entrySet()) {
					if (!(e.getValue() instanceof JsonObject a)) continue;
					Map<Integer, List<String>> levels = new HashMap<>();
					if (a.get("levels") instanceof JsonObject byLevel) {
						for (var l : byLevel.entrySet()) {
							try {
								levels.put(Integer.parseInt(l.getKey()), lines(l.getValue()));
							} catch (NumberFormatException ignored) {
								// not a level
							}
						}
					}
					petAbilities.put(e.getKey().toLowerCase(Locale.ROOT), new PetAbility(string(a, "minRarity"), Map.copyOf(levels)));
				}
			}
			Map<String, List<String>> reforges = new HashMap<>();
			if (root.get("reforges") instanceof JsonObject all) {
				for (var e : all.entrySet()) reforges.put(e.getKey().toLowerCase(Locale.ROOT), lines(e.getValue()));
			}
			// Skins that share a name ("Baby Skin", "Shimmer Skin" for each dragon armour set) are told apart by
			// their lore. Only skins: a dragon helmet's two ids or a pet item's rarities are priced as they always were.
			Map<String, List<String>> idsByName = new HashMap<>();
			byId.forEach((id, item) -> {
				if (cosmetics.contains(id)) idsByName.computeIfAbsent(item.name.toLowerCase(Locale.ROOT), n -> new java.util.ArrayList<>()).add(id);
			});
			idsByName.values().removeIf(ids -> ids.size() < 2);
			idsByName.replaceAll((n, ids) -> ids.stream().sorted().toList());
			data = new Data(Map.copyOf(byId), Map.copyOf(idByName), Map.copyOf(idsByName), Map.copyOf(curves), Map.copyOf(pets), Map.copyOf(petAbilities), Map.copyOf(reforges), List.copyOf(accessories));
		} catch (RuntimeException e) {
			Lazarus.LOG.warn("The dragsim.co catalog was not in the expected shape", e);
		}
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** A skin given either as the base64 value itself or as {value: ...}. */
	static String texture(JsonElement e) {
		if (e == null || e.isJsonNull()) return "";
		if (e.isJsonPrimitive()) return e.getAsString();
		return e.isJsonObject() ? string(e.getAsJsonObject(), "value") : "";
	}

	private static List<Ability> abilities(JsonElement entry) {
		if (entry == null || !entry.isJsonObject() || !entry.getAsJsonObject().has("abilities")) return List.of();
		return entry.getAsJsonObject().getAsJsonArray("abilities").asList().stream()
				.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
				.map(a -> new Ability(string(a, "name"), string(a, "trigger"), number(a, "cooldown"),
						lines(a.has("lines") ? a.get("lines") : a.get("description")), number(a, "cost"),
						string(a, "costType").isEmpty() ? string(a, "cost_type") : string(a, "costType"),
						bool(a, "hidden"), string(a, "key"), bool(a, "fullSet"), (int) number(a, "maxCount")))
				.toList();
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
	}

	private static double number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** Lore text given as an array of lines or one string with line breaks. */
	private static List<String> lines(JsonElement e) {
		if (e == null || e.isJsonNull()) return List.of();
		if (e.isJsonPrimitive()) return List.of(e.getAsString().split("\\R", -1));
		if (!e.isJsonArray()) return List.of();
		return e.getAsJsonArray().asList().stream().filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).toList();
	}

	/**
	 * The item's full set bonus, wherever the catalog keeps it: on the item or in its lore,
	 * as an object with a name and lines, or as plain lines.
	 */
	private static SetBonus setBonus(JsonObject item, JsonObject lore) {
		for (JsonObject o : List.of(item, lore)) {
			for (String key : List.of("fullSetBonus", "full_set_bonus", "setBonus", "set_bonus")) {
				JsonElement e = o.get(key);
				if (e == null || e.isJsonNull()) continue;
				if (!e.isJsonObject()) return new SetBonus("", lines(e), 0);
				JsonObject b = e.getAsJsonObject();
				int pieces = b.has("pieces") && b.get("pieces").isJsonArray() ? b.getAsJsonArray("pieces").size() : (int) number(b, "pieces");
				return new SetBonus(string(b, "name"), lines(b.has("lines") ? b.get("lines") : b.get("description")), pieces);
			}
		}
		return null;
	}
}
