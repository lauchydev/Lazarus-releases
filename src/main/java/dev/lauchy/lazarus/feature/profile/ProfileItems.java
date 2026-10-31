package dev.lauchy.lazarus.feature.profile;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Recolour;
import dev.lauchy.lazarus.core.Text;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns the site's profile JSON into item stacks: containers as slot maps, pets as heads,
 * and each item with its real look (vanilla material, leather dye, head skin) and lore.
 */
public final class ProfileItems {
	static final int PAGE = 54;

	/** A tab's items, with a title per page when the pages are separate containers (storage). */
	record Container(Map<Integer, ItemStack> items, List<String> pageTitles) {
	}

	private ProfileItems() {
	}

	/**
	 * Storage pages, one per backpack: {"0": page, "1": ...} or a plain list of pages,
	 * possibly under "pages" or "backpacks". Each page with anything in it becomes a page of
	 * the tab, keeping its own number.
	 */
	static Container storage(JsonElement e) {
		Map<Integer, ItemStack> items = new TreeMap<>();
		List<String> titles = new ArrayList<>();
		for (String wrapper : new String[]{"pages", "backpacks", "storage"}) {
			if (e != null && e.isJsonObject() && e.getAsJsonObject().has(wrapper)) e = e.getAsJsonObject().get(wrapper);
		}
		Map<Integer, JsonElement> pages = new TreeMap<>();
		if (e != null && e.isJsonArray()) {
			int i = 0;
			for (JsonElement page : e.getAsJsonArray()) pages.put(i++, page);
		} else if (e != null && e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				try {
					pages.put(Integer.parseInt(en.getKey()), en.getValue());
				} catch (NumberFormatException ignored) {
					// not a page
				}
			}
		}
		for (var en : pages.entrySet()) {
			Map<Integer, ItemStack> page = slots(en.getValue());
			if (page.isEmpty()) continue;
			int base = titles.size() * PAGE;
			page.forEach((slot, stack) -> {
				if (slot < PAGE) items.put(base + slot, stack);
			});
			String name = en.getValue().isJsonObject() ? Text.plain(first(en.getValue().getAsJsonObject(), "name", "title")) : "";
			titles.add(name.isEmpty() ? "Storage " + (en.getKey() + 1) : name);
		}
		return new Container(items, titles);
	}

	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

	/**
	 * The wardrobe as in game: one column per set, helmet to boots top to bottom, nine sets
	 * to a band of four rows. The site sends {equipped: n, slots: {"<set>": {helmet,
	 * chestplate, leggings, boots}}}; the equipped set is left empty there because its armour
	 * is what you wear, inventory slots 41-44, so it is read from there and marked.
	 */
	static Map<Integer, ItemStack> wardrobe(JsonElement e, JsonElement inventory) {
		if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has("slots")) return slots(e);
		JsonObject w = e.getAsJsonObject();
		JsonObject sets = obj(w, "slots");
		Map<Integer, ItemStack> worn = slots(inventory);
		int equipped = -1;
		try {
			if (w.has("equipped") && w.get("equipped").isJsonPrimitive()) equipped = w.get("equipped").getAsInt();
		} catch (RuntimeException ignored) {
			// no equipped set
		}
		java.util.TreeSet<Integer> keys = new java.util.TreeSet<>();
		for (String k : sets.keySet()) {
			try {
				keys.add(Integer.parseInt(k));
			} catch (NumberFormatException ignored) {
				// not a set
			}
		}
		if (equipped >= 0) keys.add(equipped);
		Map<Integer, ItemStack> out = new TreeMap<>();
		for (int set : keys) {
			JsonObject pieces = obj(sets, Integer.toString(set));
			for (int i = 0; i < PIECES.length; i++) {
				JsonElement piece = pieces.get(PIECES[i]);
				ItemStack s = piece != null && piece.isJsonObject() ? stack(piece.getAsJsonObject()) : ItemStack.EMPTY;
				if (s.isEmpty() && set == equipped) s = worn.getOrDefault(41 + i, ItemStack.EMPTY).copy();
				if (s.isEmpty()) continue;
				if (set == equipped) {
					List<Component> lore = new ArrayList<>(s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
					lore.add(line(""));
					lore.add(line("§a✔ Equipped set"));
					s.set(DataComponents.LORE, new ItemLore(lore));
				}
				out.put(set / 9 * 36 + i * 9 + set % 9, s);
			}
		}
		return out;
	}

	/** A sparse {"slot": item} map, a list, or a wardrobe-style {slots: ...}, flattened to slot order. */
	static Map<Integer, ItemStack> slots(JsonElement e) {
		Map<Integer, ItemStack> out = new TreeMap<>();
		if (e == null || e.isJsonNull()) return out;
		if (e.isJsonObject()) { // a page wrapped with its name or size: {items: {...}, size: 45}
			for (String inner : new String[]{"slots", "items", "contents"}) {
				if (e.getAsJsonObject().has(inner)) return slots(e.getAsJsonObject().get(inner));
			}
		}
		if (e.isJsonObject()) {
			for (var s : e.getAsJsonObject().entrySet()) {
				try {
					int slot = Integer.parseInt(s.getKey());
					addAll(out, slot, s.getValue());
				} catch (NumberFormatException ignored) {
					// not a slot key
				}
			}
		} else if (e.isJsonArray()) {
			int i = 0;
			for (JsonElement item : e.getAsJsonArray()) {
				if (item.isJsonArray()) { // a wardrobe set: its pieces in a row
					for (JsonElement piece : item.getAsJsonArray()) addAll(out, i++, piece);
					i = (i + 8) / 9 * 9;
				} else addAll(out, i++, item);
			}
		}
		return out;
	}

	private static void addAll(Map<Integer, ItemStack> out, int slot, JsonElement e) {
		if (e != null && e.isJsonObject()) {
			ItemStack s = stack(e.getAsJsonObject());
			if (!s.isEmpty()) out.put(slot, s);
		}
	}

	static Map<Integer, ItemStack> pets(JsonElement e) {
		Map<Integer, ItemStack> out = new TreeMap<>();
		if (e == null || !e.isJsonArray()) return out;
		int i = 0;
		for (JsonElement p : e.getAsJsonArray()) {
			if (!p.isJsonObject()) continue;
			JsonObject o = p.getAsJsonObject();
			String type = first(o, "name", "type", "pet", "id");
			String rarity = first(o, "tier", "rarity");
			Catalog.Pet def = Catalog.pet(first(o, "type", "name", "pet", "id"));
			String level = first(o, "level", "lvl");
			if (level.isEmpty() && o.has("totalXp")) {
				level = String.valueOf(PetLevel.of(first(o, "type", "name", "pet", "id"), rarity, num(o, "totalXp"), (int) num(o, "xpCurve"),
						def != null && def.levels(), def == null ? 0 : def.maxLevel()));
			}
			Catalog.Item override = Catalog.item(first(o, "skinOverride"));
			String skin = override != null && !override.skin().isEmpty() ? override.skin() : Catalog.petSkin(first(o, "type", "name", "pet", "id"));
			ItemStack s = new ItemStack(skin.isEmpty() ? Items.BONE : Items.PLAYER_HEAD);
			if (!skin.isEmpty()) skin(s, skin);
			// As the game names it: "[Lvl 100]" in grey, then the catalogue's name ("Ender Dragon") in the rarity's colour.
			String name = (level.isEmpty() ? "" : "§7[Lvl " + level + "] ") + colour(rarity) + (def != null && !def.name().isEmpty() ? def.name() : pretty(type));
			s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
			s.set(DataComponents.LORE, new ItemLore(petLore(o, rarity, def, level)));
			if (Boolean.parseBoolean(first(o, "active", "equipped"))) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			// A pet wearing a skin looks like that skin, not like the pet of this type remembered from a menu.
			boolean skinned = override != null && !override.skin().isEmpty();
			ItemStack shown = skinned ? s : ItemLooks.pet(s, first(o, "type", "name", "pet", "id"), rarity, level);
			// Its own id, skin and held item, as the game stamps them on a pet, so its tooltip values this pet
			// and not whichever one of its kind lent it a look.
			shown.set(DataComponents.CUSTOM_DATA, CustomData.of(petTag(o, def, rarity)));
			out.put(i++, shown);
		}
		return out;
	}

	/** A pet's data the way the game stamps it: its market id as "entry", and its skin and held item under extra.pet_data. */
	static CompoundTag petTag(JsonObject o, Catalog.Pet def, String rarity) {
		CompoundTag tag = new CompoundTag();
		String r = "_pet_" + rarity.toLowerCase(Locale.ROOT);
		for (String name : new String[]{first(o, "type", "name", "pet", "id"), def == null ? "" : Text.plain(def.name())}) {
			String id = Text.lower(name).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "") + r;
			if (!name.isEmpty() && Catalog.item(id) != null) {
				tag.putString("entry", id);
				break;
			}
		}
		CompoundTag pet = new CompoundTag();
		String skin = first(o, "skinOverride", "skin_override");
		if (!skin.isEmpty()) pet.putString("skinOverride", skin);
		String held = first(o, "equippedItem", "equipped_item", "heldItem", "held_item");
		if (!held.isEmpty() && !held.equals("null")) pet.putString("equippedItem", held);
		CompoundTag extra = new CompoundTag();
		extra.put("pet_data", pet);
		tag.put("extra", extra);
		return tag;
	}

	/** Rarities from lowest, for pet abilities that need a minimum rarity. */
	private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

	/** Fields a pet's tooltip already shows in its own way; anything else is listed plainly below. */
	private static final java.util.Set<String> PET_SHOWN = java.util.Set.of("name", "type", "pet", "id", "tier", "rarity", "level", "lvl",
			"totalxp", "total_xp", "exp", "xp", "xpcurve", "xp_curve", "candiesused", "candies_used", "active", "equipped",
			"equippeditem", "equipped_item", "helditem", "held_item", "skinoverride", "skin_override");

	/**
	 * A pet's tooltip, laid out like dragsim.co shows a pet: its category, the stats it gives at
	 * its level, its abilities at that level and rarity (the catalogue's own lore lines), then
	 * the held item by its real name and colour, XP and candies, whether it's active, and the
	 * rarity line.
	 */
	private static List<Component> petLore(JsonObject o, String rarity, Catalog.Pet def, String level) {
		List<Component> lore = new ArrayList<>();
		String category = def == null || def.category().isEmpty() ? "" : pretty(def.category());
		lore.add(line("§8" + (category.isEmpty() ? "" : category + " ") + "Pet"));
		int lvl = level.matches("\\d+") ? Integer.parseInt(level) : 0;
		if (def != null && lvl > 0) {
			List<Component> perks = new ArrayList<>();
			for (Catalog.Perk k : def.perks()) perks.add(statLine(k.stat(), k.flat() ? k.perLevel() : k.perLevel() * lvl));
			section(lore, perks);
			int rank = RARITIES.indexOf(rarity.toUpperCase(Locale.ROOT));
			for (String key : def.abilities()) {
				Catalog.PetAbility a = Catalog.petAbility(key);
				if (a == null || rank < RARITIES.indexOf(a.minRarity().isEmpty() ? "COMMON" : a.minRarity().toUpperCase(Locale.ROOT))) continue;
				List<String> lines = a.levels().getOrDefault(lvl, List.of());
				section(lore, lines.isEmpty() ? List.of(line("§6" + pretty(key.replaceFirst("^pet_", "")))) : lines.stream().map(ProfileItems::loreLine).toList());
			}
		}
		lore.add(line(""));
		String held = first(o, "equippedItem", "equipped_item", "heldItem", "held_item");
		lore.add(line("§7Held item: " + (held.isEmpty() || held.equals("null") ? "§8None" : heldName(held))));
		String xp = first(o, "totalXp", "total_xp", "exp", "xp");
		if (!xp.isEmpty()) {
			try {
				lore.add(line("§7Total XP: §b" + Text.compact(Double.parseDouble(xp))));
			} catch (NumberFormatException e) {
				lore.add(line("§7Total XP: §b" + xp));
			}
		}
		String candies = first(o, "candiesUsed", "candies_used");
		if (!candies.isEmpty() && !candies.equals("0")) lore.add(line("§7Pet candy used: §d" + candies));
		boolean extra = false;
		for (var f : o.entrySet()) {
			if (!f.getValue().isJsonPrimitive() || PET_SHOWN.contains(f.getKey().toLowerCase(Locale.ROOT))) continue;
			if (!extra) lore.add(line(""));
			extra = true;
			lore.add(line("§8" + label(f.getKey()) + ": " + f.getValue().getAsString()));
		}
		lore.add(line(""));
		if (Boolean.parseBoolean(first(o, "active", "equipped"))) lore.add(line("§a✔ Active pet"));
		if (!rarity.isEmpty()) lore.add(line(colour(rarity) + "§l" + rarity.toUpperCase(Locale.ROOT).replace('_', ' ') + " PET"));
		return lore;
	}

	/**
	 * A pet item's display name in its rarity colour: the catalog's name and tier when it
	 * knows the id, else read from the id itself ("pet_item_iron_claws_rare" is a rare Iron Claws).
	 */
	private static String heldName(String id) {
		Catalog.Item def = Catalog.item(id);
		if (def != null && !def.name().isEmpty()) return colour(def.tier() == null ? "" : def.tier()) + Text.plain(def.name());
		String bare = id.toLowerCase(Locale.ROOT).replaceFirst("^pet_item_", "");
		String tier = "";
		for (String t : new String[]{"very_special", "special", "divine", "mythic", "legendary", "epic", "rare", "uncommon", "common"}) {
			if (bare.endsWith("_" + t)) {
				tier = t;
				bare = bare.substring(0, bare.length() - t.length() - 1);
				break;
			}
		}
		return colour(tier) + pretty(bare);
	}

	/** A plain, unupgraded item of a catalogue id as the game shows it: its look, name, lore and rarity. */
	public static ItemStack plain(String id) {
		Catalog.Item def = Catalog.item(id);
		JsonObject o = new JsonObject();
		o.addProperty("entry", id);
		JsonObject data = new JsonObject();
		if (def != null) {
			data.addProperty("item_name", def.name());
			data.addProperty("rarity", def.tier());
		}
		o.add("item_data", data);
		return stack(o);
	}

	/**
	 * An item from the site's shape: {entry, count, material, item_data{item_name, rarity,
	 * reforge, color}, item_modifiers, extra{item_skin_applied, item_dye}, ...}. Its look
	 * (vanilla item, leather dye, head skin) comes from the item itself, then the catalog.
	 */
	static ItemStack stack(JsonObject o) {
		String entry0 = o.has("entry") ? o.get("entry").getAsString() : "";
		Catalog.Item def = Catalog.item(entry0);
		JsonObject extra = obj(o, "extra");
		String skin = appliedSkin(o, extra, def);
		Item item = material(o.has("material") && o.get("material").isJsonPrimitive() ? o.get("material").getAsString() : def != null ? def.material() : "");
		if (!skin.isEmpty() && item != Items.PLAYER_HEAD && isHelmetSkin(o, extra)) item = Items.PLAYER_HEAD; // a helmet skin turns it into a head
		int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
		ItemStack s = new ItemStack(item, Math.min(count, 99));
		JsonObject data = obj(o, "item_data");
		Catalog.learn(entry0, data.has("item_name") ? data.get("item_name").getAsString() : "", data.has("rarity") ? data.get("rarity").getAsString() : "",
				data.get("npc_sell_price") instanceof JsonPrimitive npc && npc.isNumber() ? npc.getAsLong() : -1);
		String entry = entry0;
		if (!skin.isEmpty()) skin(s, skin);
		int rgb = rgb(dye(data, extra, def));
		if (rgb >= 0) s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		String name = data.has("item_name") ? data.get("item_name").getAsString() : "";
		if (name.isEmpty() || name.equalsIgnoreCase(entry)) name = Catalog.item(entry) != null ? Catalog.item(entry).name() : pretty(entry); // some items carry their id as their name
		String rarity = data.has("rarity") ? data.get("rarity").getAsString() : "";
		String reforge = data.has("reforge") && !data.get("reforge").isJsonNull() ? data.get("reforge").getAsString() : "";
		JsonObject mods = obj(o, "item_modifiers");
		int stars = mods.has("star_level") ? mods.get("star_level").getAsInt() : 0;
		// A name the server wrote with its own colours ("§7[Lvl 1] §6Goop") keeps them, as the game shows it.
		String shown = (reforge.isEmpty() ? "" : pretty(reforge) + " ") + (name.contains("§") ? name : Text.plain(name)) + (stars > 0 ? " §6" + "✪".repeat(Math.min(stars, 5)) : "");
		s.set(DataComponents.CUSTOM_NAME, Component.literal(colour(rarity) + shown).withStyle(st -> st.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		boolean recomb = bool(mods, "recombobulated");
		String type = itemType(data, item);
		boolean dungeon = stars > 0 || extra.has("dungeon_quality") || extra.has("dungeon_upgrade");
		lore.addAll(statLines(obj(obj(o, "item_stats"), "base"), obj(obj(data, "reforge_stats"), "base"), mods, stars, type, dungeon));
		section(lore, modifierLines(mods, reforge));
		if (def != null) {
			section(lore, def.description().stream().map(ProfileItems::loreLine).toList());
			Catalog.SetBonus set = def.setBonus();
			if (set != null) {
				List<Component> b = new ArrayList<>();
				if (!set.name().isEmpty()) b.add(line("§6Full Set Bonus: " + set.name()));
				set.lines().forEach(l -> b.add(loreLine(l)));
				section(lore, b);
			}
			// The item's own abilities when it lists them (a Hyperion's scrolls), else the catalogue's defaults, as dragsim.co shows them.
			for (String key : abilityKeys(o, def)) {
				Catalog.Ability a = def.abilities().stream().filter(x -> x.key().equalsIgnoreCase(key)).findFirst().orElse(null);
				if (a == null) section(lore, List.of(line("§6Ability: " + pretty(key))));
				else if (!a.hidden()) section(lore, abilityLines(a));
			}
		}
		section(lore, Catalog.reforge(reforge).stream().map(ProfileItems::loreLine).toList());
		section(lore, appliedLines(mods, extra));
		if (!rarity.isEmpty()) {
			if (!lore.isEmpty()) lore.add(line(""));
			String c = colour(rarity);
			String footer = rarity.toUpperCase(Locale.ROOT).replace('_', ' ') + (dungeon ? " DUNGEON" : "") + (type.isEmpty() ? "" : " " + type);
			// A recombobulated item's rarity line is wrapped in scrambled letters, as in game.
			lore.add(line(recomb ? c + "§l§kK§r " + c + "§l" + footer + " " + c + "§l§kK" : c + "§l" + footer));
		}
		s.set(DataComponents.LORE, new ItemLore(lore));
		if (recomb) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		// Its catalog id and item UUID, so the in-game look of this very item can be found again,
		// and its data and upgrades as the server stamps them in game, so its tooltip can value it.
		CompoundTag tag = new CompoundTag();
		if (!entry.isEmpty()) tag.putString("entry", entry);
		for (String key : new String[]{"item_data", "item_modifiers", "extra"}) {
			if (o.get(key) instanceof JsonObject part && !part.isEmpty()) tag.put(key, JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, part));
		}
		String uuid = first(o, "uuid", "item_uuid");
		if (uuid.isEmpty()) uuid = first(data, "uuid", "item_uuid");
		if (!uuid.isEmpty()) tag.putString("uuid", uuid);
		if (!tag.isEmpty()) s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	/** A stat's icon and colour as SkyBlock shows them, and whether it reads as a percentage. */
	private record StatStyle(String icon, String colour, boolean percent) {
	}

	private static final Map<String, StatStyle> STATS = Map.ofEntries(
			Map.entry("health", new StatStyle("❤", "§c", false)),
			Map.entry("defense", new StatStyle("❈", "§a", false)),
			Map.entry("true_defense", new StatStyle("❂", "§f", false)),
			Map.entry("strength", new StatStyle("❁", "§c", false)),
			Map.entry("damage", new StatStyle("❁", "§c", false)),
			Map.entry("intelligence", new StatStyle("✎", "§b", false)),
			Map.entry("speed", new StatStyle("✦", "§f", false)),
			Map.entry("crit_chance", new StatStyle("☣", "§9", true)),
			Map.entry("crit_damage", new StatStyle("☠", "§9", true)),
			Map.entry("attack_speed", new StatStyle("⚔", "§e", true)),
			Map.entry("bonus_attack_speed", new StatStyle("⚔", "§e", true)),
			Map.entry("ability_damage", new StatStyle("๑", "§c", true)),
			Map.entry("ferocity", new StatStyle("⫽", "§c", false)),
			Map.entry("magic_find", new StatStyle("✯", "§b", false)),
			Map.entry("pet_luck", new StatStyle("♣", "§d", false)),
			Map.entry("sea_creature_chance", new StatStyle("α", "§3", true)),
			Map.entry("health_regen", new StatStyle("❣", "§c", false)),
			Map.entry("vitality", new StatStyle("♨", "§4", false)),
			Map.entry("mending", new StatStyle("☄", "§a", false)));
	private static final java.util.Set<String> ARMOUR = java.util.Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
	/** Stats a dungeon item boosts inside dungeons; the rest show their plain total there. */
	private static final java.util.Set<String> DUNGEON_BOOSTED = java.util.Set.of("health", "defense", "true_defense", "intelligence",
			"strength", "damage", "crit_chance", "crit_damage", "ferocity", "health_regen", "vitality", "mending");
	/** In-game tooltips show dungeon stats at 1.6x (262.5 Health reads +420). */
	private static final double DUNGEON_BOOST = 1.6;

	/**
	 * Stat lines the way the game writes them: the total, then the reforge's share in blue,
	 * hot potato books in yellow and stars in gold. The site's base stats already include
	 * the reforge but not books or stars. Each star adds 2.5% of the stat before the reforge
	 * (worked out from in-game tooltips: 5 stars on 180 Health is +22.5). A book gives armour
	 * +4 Health and +2 Defense, and weapons +2 Damage and +2 Strength. Dungeon items end
	 * with the stat's value in dungeons, in dark grey.
	 */
	private static List<Component> statLines(JsonObject base, JsonObject reforge, JsonObject mods, int stars, String type, boolean dungeon) {
		Map<String, Double> values = new java.util.LinkedHashMap<>();
		for (var e : base.entrySet()) {
			if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) values.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsDouble());
		}
		int books = (int) (num(mods, "hot_potato_books") + num(mods, "golden_hot_potato_books"));
		Map<String, Double> bookBonus = new java.util.HashMap<>();
		if (books > 0 && ARMOUR.contains(type)) {
			bookBonus.put("health", 4.0 * books);
			bookBonus.put("defense", 2.0 * books);
		} else if (books > 0 && !type.isEmpty()) {
			bookBonus.put("damage", 2.0 * books);
			bookBonus.put("strength", 2.0 * books);
		}
		bookBonus.keySet().forEach(k -> values.putIfAbsent(k, 0.0));
		List<Component> out = new ArrayList<>();
		for (var e : values.entrySet()) {
			String key = e.getKey();
			double fromReforge = num(reforge, key);
			double hpb = bookBonus.getOrDefault(key, 0.0);
			double fromStars = (e.getValue() - fromReforge) * 0.025 * Math.min(stars, 5);
			double total = e.getValue() + hpb + fromStars;
			if (Math.abs(total) < 1e-9) continue;
			StatStyle st = STATS.getOrDefault(key, new StatStyle("", "§a", false));
			String pct = st.percent() ? "%" : "";
			StringBuilder b = new StringBuilder();
			if (!st.icon().isEmpty()) b.append(st.colour()).append(st.icon()).append(' ');
			b.append("§7").append(label(key)).append(": ").append(st.colour()).append(signed(total)).append(pct);
			if (fromReforge != 0) b.append(" §9(").append(signed(fromReforge)).append(pct).append(')');
			if (hpb != 0) b.append(" §e(").append(signed(hpb)).append(pct).append(')');
			if (Math.abs(fromStars) > 1e-9) b.append(" §6(").append(signed(fromStars)).append(pct).append(')');
			if (dungeon) b.append(" §8(").append(signed(DUNGEON_BOOSTED.contains(key) ? total * DUNGEON_BOOST : total)).append(pct).append(')');
			out.add(line(b.toString()));
		}
		return out;
	}

	/**
	 * A rough networth: the purse and bank plus every item in the inventory, ender chest,
	 * storage, accessory bag and wardrobe at the price database's value of that very item, its
	 * upgrades included (the NPC price when the market hasn't been seen). Pets aren't priced.
	 * {total, items priced, items}.
	 * {@code parts} gathers each item's count and value, {count, value} by id, so the biggest
	 * can be listed.
	 */
	static long[] networth(JsonObject profile, Map<String, long[]> parts) {
		long[] out = {Math.round(num(profile, "coins") + num(profile, "bank") + num(profile, "bank_balance")), 0, 0};
		for (String key : new String[]{"inventory", "ender_chest", "storage", "accessory_bag", "wardrobe"}) worth(profile.get(key), out, parts);
		return out;
	}

	private static void worth(JsonElement e, long[] out, Map<String, long[]> parts) {
		if (e == null || e.isJsonNull() || e.isJsonPrimitive()) return;
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) worth(x, out, parts);
			return;
		}
		JsonObject o = e.getAsJsonObject();
		String id = o.has("entry") ? str(o, "entry") : "";
		if (id.isEmpty()) {
			for (var x : o.entrySet()) worth(x.getValue(), out, parts);
			return;
		}
		out[2]++;
		dev.lauchy.lazarus.feature.general.PriceDatabase prices = dev.lauchy.lazarus.feature.general.PriceDatabase.INSTANCE;
		long each = prices != null ? prices.value(o, false).total() : Catalog.npcSell(id);
		if (each <= 0) return;
		long count = Math.max(1, (long) num(o, "count"));
		out[1]++;
		out[0] += each * count;
		long[] part = parts.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> new long[2]);
		part[0] += count;
		part[1] += each * count;
	}

	/** The {@code limit} items worth most, "Name ×count  value", biggest first: what the networth hover lists. */
	static List<String> topWorth(Map<String, long[]> parts, int limit) {
		return parts.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
				.limit(limit)
				.map(e -> "§f" + appliedName(e.getKey()) + (e.getValue()[0] > 1 ? " §8×" + e.getValue()[0] : "") + "  §6" + Text.compact(e.getValue()[1]))
				.toList();
	}

	/** Adds lines as their own block, after a blank line when something comes before. */
	private static void section(List<Component> lore, List<Component> lines) {
		if (lines.isEmpty()) return;
		if (!lore.isEmpty()) lore.add(line(""));
		lore.addAll(lines);
	}

	/** The ability keys an item has: its own list when it carries one, else the catalogue's defaults. */
	private static List<String> abilityKeys(JsonObject o, Catalog.Item def) {
		if (o.get("abilities") instanceof com.google.gson.JsonArray own && !own.isEmpty()) {
			return own.asList().stream().filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).toList();
		}
		return def.defaultAbilities();
	}

	/** What has been done to the item, listed under its stats as dragsim.co lists it: books, synchronizers, Art of War, reforge, enrichment. */
	private static List<Component> modifierLines(JsonObject mods, String reforge) {
		List<Component> out = new ArrayList<>();
		int hpb = (int) num(mods, "hot_potato_books"), golden = (int) num(mods, "golden_hot_potato_books"), sync = (int) num(mods, "final_synchronizers");
		if (hpb > 0) out.add(line("§8" + hpb + " Hot Potato Book" + (hpb == 1 ? "" : "s")));
		if (golden > 0) out.add(line("§8" + golden + " Golden Hot Potato Book" + (golden == 1 ? "" : "s")));
		if (sync > 0) out.add(line("§8" + sync + " Final Synchronizer" + (sync == 1 ? "" : "s")));
		if (bool(mods, "art_of_war")) out.add(line("§8The Art of War"));
		if (!reforge.isEmpty()) out.add(line("§8" + pretty(reforge) + " Reforge"));
		String enrichment = first(mods, "enrichment");
		if (!enrichment.isEmpty() && !enrichment.equals("null")) out.add(line("§8" + pretty(enrichment) + " Enrichment"));
		return out;
	}

	/** One stat as a lore line, "❁ Strength: +50", in the stat's colour. */
	private static Component statLine(String key, double value) {
		StatStyle st = STATS.getOrDefault(key, new StatStyle("", "§a", false));
		return line((st.icon().isEmpty() ? "" : st.colour() + st.icon() + " ") + "§7" + label(key) + ": " + st.colour() + signed(value) + (st.percent() ? "%" : ""));
	}

	/** Pieces of armour worn that carry each ability, for "Full Set Bonus: Old Blood (4/4)"; set by {@link #wearing} for the profile being read. */
	private static Map<String, Integer> worn = Map.of();

	/** Notes which abilities the profile's worn armour (inventory slots 41-44) carries, before its items are built. */
	static void wearing(JsonElement inventory) {
		Map<String, Integer> out = new java.util.HashMap<>();
		for (int slot = 41; slot <= 44; slot++) {
			JsonElement at = inventory instanceof JsonObject inv ? inv.get(String.valueOf(slot))
					: inventory instanceof com.google.gson.JsonArray list && slot < list.size() ? list.get(slot) : null;
			if (!(at instanceof JsonObject piece) || !piece.has("entry")) continue;
			Catalog.Item def = Catalog.item(str(piece, "entry"));
			if (def != null) for (String key : abilityKeys(piece, def)) out.merge(key.toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
		worn = out;
	}

	/** An ability the way the game lists it: name and trigger, its text, then cost and cooldown. */
	private static List<Component> abilityLines(Catalog.Ability a) {
		List<Component> out = new ArrayList<>();
		String trigger = a.trigger().toUpperCase(Locale.ROOT).replace('_', ' ');
		// Passive text comes with no name, and the game shows it without a heading.
		if (a.fullSet()) {
			int pieces = worn.getOrDefault(a.key().toLowerCase(Locale.ROOT), 0);
			out.add(line("§6Full Set Bonus: " + a.name() + (a.maxCount() > 0 ? " §8(" + pieces + "/" + a.maxCount() + ")" : "")));
		} else if (!a.name().isEmpty()) out.add(line("§6Ability: " + a.name() + (trigger.isEmpty() ? "" : "  §e§l" + trigger)));
		a.lines().forEach(l -> out.add(loreLine(l)));
		if (a.cost() > 0) out.add(line("§8" + (a.costType().isEmpty() ? "Mana" : label(a.costType())) + " Cost: §3" + String.format(Locale.ROOT, "%,.0f", a.cost())));
		if (a.cooldown() > 0) out.add(line("§8Cooldown: §a" + signed(a.cooldown()).substring(1) + "s"));
		return out;
	}

	/** A catalog lore line: grey unless it says otherwise, with "&" codes read like "§". */
	private static Component loreLine(String s) {
		return line("§7" + s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1"));
	}

	/**
	 * What the game lists under the item's text: kills, an applied skin and dye. Books,
	 * stars and recombobulation already show in the stats, name and rarity line.
	 */
	private static List<Component> appliedLines(JsonObject mods, JsonObject extra) {
		List<Component> out = new ArrayList<>();
		if (num(mods, "kills") > 0) out.add(line("§7Kills: §f" + Text.compact(num(mods, "kills"))));
		String skin = first(obj(extra, "item_skin_applied"), "skinId");
		if (skin.isEmpty()) skin = first(obj(extra, "item_skin"), "skinId");
		if (!skin.isEmpty()) out.add(line("§7Skin: §d" + appliedName(skin)));
		String dye = first(obj(extra, "item_dye"), "dyeId");
		if (!dye.isEmpty()) {
			Catalog.Item d = Catalog.item(dye);
			int rgb = d == null || rgb(d.color()) < 0 ? -1 : Recolour.readable(rgb(d.color())); // a black dye in its own black would vanish on the tooltip
			String swatch = rgb >= 0 ? "" : "§d";
			Component c = Component.literal("§7Dye: ").append(rgb >= 0
					? Component.literal(appliedName(dye)).withStyle(st -> st.withColor(rgb))
					: Component.literal(swatch + appliedName(dye))).withStyle(st -> st.withItalic(false));
			out.add(c);
		}
		return out;
	}

	private static String appliedName(String id) {
		Catalog.Item def = Catalog.item(id);
		return def != null && !def.name().isEmpty() ? Text.plain(def.name()) : pretty(id);
	}

	/**
	 * The item's kind for the rarity line ("HELMET", "SWORD"): from the site's item data
	 * when it says, else from the vanilla item it looks like. Empty when it can't tell.
	 */
	private static String itemType(JsonObject data, Item item) {
		String t = first(data, "item_type", "type", "category");
		if (!t.isEmpty()) return t.toUpperCase(Locale.ROOT).replace('_', ' ');
		String id = BuiltInRegistries.ITEM.getKey(item).getPath();
		for (String a : ARMOUR) if (id.endsWith("_" + a.toLowerCase(Locale.ROOT))) return a;
		if (id.endsWith("_sword")) return "SWORD";
		if (id.equals("bow") || id.equals("crossbow")) return "BOW";
		return "";
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && (e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : num(o, key) > 0);
	}

	/** "+262.5", "-3", "+11.25": at most two decimals, trailing zeros dropped. */
	private static String signed(double v) {
		String n = new java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		return (v >= 0 ? "+" : "") + n;
	}

	/** The vanilla item for a site material name: "minecraft:leather_helmet", "skull_item", ... */
	private static Item material(String material) {
		String m = material.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
		if (m.equals("skull_item") || m.equals("skull")) m = "player_head";
		Identifier id = m.isEmpty() ? null : Identifier.tryParse("minecraft:" + m);
		Item found = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return found == Items.AIR ? Items.BARRIER : found;
	}

	/** The head texture: an applied cosmetic skin first, then one on the item, then the catalog's. */
	private static String appliedSkin(JsonObject o, JsonObject extra, Catalog.Item def) {
		for (String field : new String[]{"item_skin_applied", "item_skin"}) {
			Catalog.Item cosmetic = Catalog.item(first(obj(extra, field), "skinId"));
			if (cosmetic != null && !cosmetic.skin().isEmpty()) return cosmetic.skin();
		}
		JsonElement own = o.get("item_skin");
		if (own != null && own.isJsonPrimitive()) return own.getAsString();
		if (own != null && own.isJsonObject() && own.getAsJsonObject().has("value")) return first(own.getAsJsonObject(), "value");
		return def != null ? def.skin() : "";
	}

	private static boolean isHelmetSkin(JsonObject o, JsonObject extra) {
		return obj(extra, "item_skin_applied").has("skinId") || obj(extra, "item_skin").has("skinId") || o.has("item_skin");
	}

	/** Leather colour: an applied dye first, then the item's own colour, then the catalog's. */
	private static String dye(JsonObject data, JsonObject extra, Catalog.Item def) {
		Catalog.Item dye = Catalog.item(first(obj(extra, "item_dye"), "dyeId"));
		if (dye != null && !dye.color().isEmpty()) return dye.color();
		String own = first(data, "color");
		return !own.isEmpty() ? own : def != null ? def.color() : "";
	}

	/** "#a06540" or "160,101,64" as 0xRRGGBB, or -1. */
	private static int rgb(String color) {
		try {
			String c = color.trim();
			if (c.startsWith("#")) return Integer.parseInt(c.substring(1), 16) & 0xFFFFFF;
			String[] bits = c.split(",");
			if (bits.length == 3) return Integer.parseInt(bits[0].trim()) << 16 | Integer.parseInt(bits[1].trim()) << 8 | Integer.parseInt(bits[2].trim());
		} catch (NumberFormatException ignored) {
			// not a colour
		}
		return -1;
	}

	/** Gives a player head this base64 texture, already resolved so no skin lookup is made. */
	static void skin(ItemStack s, String texture) {
		PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "Lazarus", properties);
		s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
	}

	private static Component line(String s) {
		return Component.literal(s).withStyle(st -> st.withItalic(false));
	}

	static String colour(String rarity) {
		return dev.lauchy.lazarus.core.LootRows.rarity(rarity);
	}

	/** "slayer_zombie_xp" -> "Slayer zombie xp". */
	static String label(String key) {
		String s = key.replace('_', ' ').trim();
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** "witching_wand" -> "Witching Wand". */
	static String pretty(String id) {
		StringBuilder b = new StringBuilder();
		for (String w : id.split("[_ ]+")) {
			if (w.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return b.toString();
	}

	static String first(JsonObject o, String... keys) {
		for (String k : keys) if (o.has(k) && o.get(k).isJsonPrimitive()) return o.get(k).getAsString();
		return "";
	}

	static String str(JsonObject o, String key) {
		return o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	static JsonObject obj(JsonObject parent, String key) {
		JsonElement e = parent.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}
}
