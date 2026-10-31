package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What one particular item is worth: the clean item's price plus what the market pays for each
 * upgrade on it. dragsim.co prices an item by the median of all its sales, modified or not, so a
 * bare Bonemerang and one with books, a recomb and five stars count alike. Every recorded sale
 * carries its upgrades, though, and {@code tools/item-values/fit_item_values.py} fits how much of
 * each upgrade's own price buyers actually pay (its share), pooled over every item, plus how much
 * each Dragon Star adds to the clean price. Those shares ship in {@code lazarus-item-values.json}.
 * <p>
 * Items are read in the site's shape ({@code item_data}, {@code item_modifiers}, {@code extra}),
 * which is also what the server stamps on items in game. Pure, so it is tested without the game.
 */
public final class ItemValue {
	/** Where the fitted shares and the recipes ship. */
	private static final String RESOURCE = "/lazarus-item-values.json";
	/** Books past ten are Fuming Potato Books. */
	private static final int HOT_BOOKS = 10;
	/** The scrolls a Wither blade can hold, by bit of its {@code wither_scrolls.mask}. */
	private static final String[] SCROLLS = {"implosion_scroll", "shadow_warp_scroll", "wither_shield_scroll"};
	/** What a Dragon Star is made with. */
	private static final String RESIDUE = "ritual_residue";
	/** Each scroll's name, as {@link #SCROLLS} lists them. */
	private static final String[] SCROLL_NAMES = {"Implosion", "Shadow Warp", "Wither Shield"};
	/** The share an upgrade gets when the shipped file doesn't name it: its full price, since no sales say buyers pay less (Lauchlan's rule). */
	private static final double UNFITTED = 1.0;

	/** A kind of upgrade, by its key in the shipped file, and how it's named on a tooltip. */
	public enum Kind {
		HOT_POTATO_BOOKS("hot_potato_books", "Hot Potato Books"),
		FUMING_POTATO_BOOKS("fuming_potato_books", "Fuming Potato Books"),
		GOLDEN_HOT_POTATO_BOOKS("golden_hot_potato_books", "Golden Hot Potato Books"),
		RECOMBOBULATOR("recombobulator", "Recombobulator 3000"),
		REFORGE("reforge", "Reforge"),
		STARS("stars", "Dragon Stars"),
		DYE("dye", "Dye"),
		ITEM_SKIN("item_skin", "Skin"),
		PET_SKIN("pet_skin", "Pet Skin"),
		PET_ITEM("pet_item", "Pet Item"),
		ART_OF_WAR("art_of_war", "Art of War"),
		WITHER_SCROLLS("wither_scrolls", "Scrolls"),
		ENRICHMENT("enrichment", "Enrichment"),
		RUNE("rune", "Rune"),
		DRILL_PARTS("drill_parts", "Drill Parts"),
		COATING("coating", "Coating");

		/** Its key in the shipped file. */
		final String key;
		/** Its name on a tooltip. */
		public final String label;

		Kind(String key, String label) {
			this.key = key;
			this.label = label;
		}
	}

	/** The prefix of a Furfsky skin's id, which its name in game leaves out ({@link #twin}). */
	private static final String FURFSKY = "furfsky_";

	/**
	 * The same skin under its other name, or null: a Furfsky skin applied to an item is called
	 * "furfsky_hyperion_skin", but the fire sale and the game name it "Hyperion Skin", which Lazarus
	 * learns as "hyperion_skin", so whichever has no price borrows the other's.
	 */
	static String twin(String id) {
		if (id.startsWith(FURFSKY)) return id.length() > FURFSKY.length() ? id.substring(FURFSKY.length()) : null;
		return id.endsWith("_skin") ? FURFSKY + id : null;
	}

	/**
	 * A part's row name: its kind and what it is ("Reforge Suspicious"), or just the name when it
	 * already says its kind, so a dye reads "Dye of the Pure Black" and not "Dye Dye of the Pure Black".
	 */
	public static String partName(Kind kind, String detail) {
		if (detail.isEmpty()) return kind.label;
		String last = kind.label.substring(kind.label.lastIndexOf(' ') + 1);
		return Pattern.compile("(?i)\\b" + Pattern.quote(last) + "\\b").matcher(detail).find() ? detail : kind.label + " " + detail;
	}

	/**
	 * One upgrade on an item: its kind, the items it's made of (each one's id and how many; a
	 * reforge lists every stone that applies it, and the cheapest counts), and a word for it
	 * ("Renowned", "x10"), or the part's own id for a dye, skin, pet item, enrichment, rune or drill part,
	 * which the tooltip names from the catalogue.
	 */
	public record Upgrade(Kind kind, List<String> parts, int count, String detail) {
	}

	/** One upgrade priced: its kind, what it adds ({@link #UNPRICED} when nothing prices it yet), and the word for it. */
	public record Part(Kind kind, long value, String detail) {
		/** The value of an upgrade nothing prices yet: it is shown, but adds nothing. */
		public static final long UNPRICED = -1;
	}

	/** An item priced: the clean item, each upgrade, and their total; {@code base} is -1 when the clean item has no price. */
	public record Valued(long base, List<Part> parts) {
		/** What the upgrades add together. */
		public long upgrades() {
			return parts.stream().mapToLong(Part::value).filter(v -> v > 0).sum();
		}

		/** The clean item and its upgrades, or -1 when the clean item has no price. */
		public long total() {
			return base < 0 ? -1 : base + upgrades();
		}
	}

	/** Prices to value with: an item's own (clean) price, and the stones that apply a reforge. */
	public interface Prices {
		/** What one clean {@code id} is worth, or -1 when nothing is known. */
		long of(String id);

		/** The ids of the stones that apply this reforge ("renowned"), empty when none does. */
		List<String> stones(String reforge);
	}

	/** The shares, as fitted: what part of each upgrade's own price the market pays. */
	public record Model(Map<Kind, Double> share) {
		/** The share of a kind, or {@link #UNFITTED} when the file doesn't name it; Fuming books go as Hot Potato Books do. */
		public double share(Kind kind) {
			if (kind == Kind.FUMING_POTATO_BOOKS && !share.containsKey(kind)) kind = Kind.HOT_POTATO_BOOKS;
			return share.getOrDefault(kind, UNFITTED);
		}

		/** Reads the shipped file's shares; anything missing keeps its default. */
		static Model read(JsonObject o) {
			Map<Kind, Double> share = new EnumMap<>(Kind.class);
			if (o != null && o.get("share") instanceof JsonObject s) {
				for (Kind k : Kind.values()) if (s.get(k.key) instanceof JsonElement e && e.isJsonPrimitive()) share.put(k, e.getAsDouble());
			}
			return new Model(share);
		}
	}

	/** A crafting recipe as shipped: how many it makes, the coins it costs, and each part's id and count. */
	public record Recipe(int makes, long fee, Map<String, Integer> parts) {
	}

	/** The shipped file, read once. */
	private static volatile JsonObject shipped;

	private ItemValue() {
	}

	/** The fitted shares shipped with the mod. */
	public static Model model() {
		return Model.read(file());
	}

	/** The recipes the site prices items with, shipped with the mod, by result id. */
	public static Map<String, Recipe> recipes() {
		Map<String, Recipe> out = new HashMap<>();
		if (!(file().get("recipes") instanceof JsonObject all)) return out;
		for (var e : all.entrySet()) {
			if (!(e.getValue() instanceof JsonObject r) || !(r.get("parts") instanceof JsonObject p)) continue;
			Map<String, Integer> parts = new HashMap<>();
			for (var part : p.entrySet()) if (part.getValue().isJsonPrimitive()) parts.put(part.getKey(), part.getValue().getAsInt());
			if (!parts.isEmpty()) out.put(e.getKey(), new Recipe(Math.max(1, (int) num(r, "makes")), (long) num(r, "fee"), parts));
		}
		return out;
	}

	/** The shipped file, read on first use; empty when it can't be read. */
	private static JsonObject file() {
		JsonObject f = shipped;
		if (f != null) return f;
		try (InputStream in = ItemValue.class.getResourceAsStream(RESOURCE)) {
			f = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			f = new JsonObject();
		}
		shipped = f;
		return f;
	}

	/** Every upgrade on an item, in the order a tooltip lists them. */
	public static List<Upgrade> upgrades(JsonObject item) {
		JsonObject mods = obj(item, "item_modifiers"), extra = obj(item, "extra"), data = obj(item, "item_data");
		List<Upgrade> out = new ArrayList<>();
		String reforge = str(data, "reforge");
		if (!reforge.isEmpty()) out.add(new Upgrade(Kind.REFORGE, List.of(), 1, reforge));
		int books = (int) num(mods, "hot_potato_books");
		if (books > 0) out.add(new Upgrade(Kind.HOT_POTATO_BOOKS, List.of("hot_potato_book"), Math.min(books, HOT_BOOKS), "x" + Math.min(books, HOT_BOOKS)));
		if (books > HOT_BOOKS) out.add(new Upgrade(Kind.FUMING_POTATO_BOOKS, List.of("fuming_potato_book"), books - HOT_BOOKS, "x" + (books - HOT_BOOKS)));
		int golden = (int) num(mods, "golden_hot_potato_books");
		if (golden > 0) out.add(new Upgrade(Kind.GOLDEN_HOT_POTATO_BOOKS, List.of("golden_hot_potato_book"), golden, "x" + golden));
		if (bool(mods, "recombobulated")) out.add(new Upgrade(Kind.RECOMBOBULATOR, List.of("recombobulator_3000"), 1, ""));
		int stars = (int) num(mods, "star_level");
		if (stars > 0) out.add(new Upgrade(Kind.STARS, List.of(RESIDUE), residue(stars), "✪".repeat(Math.min(stars, 10))));
		String dye = str(obj(extra, "item_dye"), "dyeId");
		if (!dye.isEmpty()) out.add(new Upgrade(Kind.DYE, List.of(dye), 1, dye));
		String skin = str(obj(extra, "item_skin"), "skinId");
		if (!skin.isEmpty()) out.add(new Upgrade(Kind.ITEM_SKIN, List.of(skin), 1, skin));
		String petSkin = str(obj(extra, "pet_data"), "skinOverride");
		if (petSkin.isEmpty()) petSkin = str(item, "skinOverride");
		if (!petSkin.isEmpty()) out.add(new Upgrade(Kind.PET_SKIN, List.of(petSkin), 1, petSkin));
		String held = str(obj(extra, "pet_data"), "equippedItem");
		if (!held.isEmpty() && !held.equals("null")) out.add(new Upgrade(Kind.PET_ITEM, List.of(held), 1, held));
		if (bool(mods, "art_of_war")) out.add(new Upgrade(Kind.ART_OF_WAR, List.of("art_of_war"), 1, ""));
		int mask = (int) num(obj(extra, "wither_scrolls"), "mask");
		for (int bit = 0; bit < SCROLLS.length; bit++) {
			if ((mask >> bit & 1) != 0) out.add(new Upgrade(Kind.WITHER_SCROLLS, List.of(SCROLLS[bit]), 1, SCROLL_NAMES[bit]));
		}
		String enrichment = str(mods, "enrichment");
		if (!enrichment.isEmpty()) out.add(new Upgrade(Kind.ENRICHMENT, List.of(enrichment), 1, enrichment));
		String rune = str(mods, "rune_id");
		if (!rune.isEmpty()) out.add(new Upgrade(Kind.RUNE, List.of(rune), 1, rune));
		JsonObject drill = obj(extra, "drill");
		for (String part : new String[]{"engine", "tank", "module"}) {
			String id = str(drill, part);
			if (!id.isEmpty()) out.add(new Upgrade(Kind.DRILL_PARTS, List.of(id), 1, id));
		}
		if (bool(obj(extra, "coating"), "coated") || bool(drill, "coated")) out.add(new Upgrade(Kind.COATING, List.of("void_powder_coating"), 1, ""));
		return out;
	}

	/**
	 * Values one item: {@code base} for the clean item, then each upgrade at its share of its
	 * own price (the cheapest stone for a reforge, Hot Potato Books' price for Fuming ones the
	 * market hasn't priced, the Ritual Residue the stars took). An upgrade nothing prices adds
	 * nothing.
	 */
	public static Valued value(JsonObject item, long base, Prices prices, Model model) {
		List<Part> parts = new ArrayList<>();
		for (Upgrade u : upgrades(item)) {
			long each = partPrice(u, prices);
			// An upgrade nothing prices yet (a dye or skin that has never sold) still shows, so it isn't silently left out.
			long value = each > 0 ? Math.round(each * model.share(u.kind) * u.count) : Part.UNPRICED;
			if (value != 0) parts.add(new Part(u.kind, value, u.detail));
		}
		return new Valued(base, parts);
	}

	/**
	 * What a sale says the clean item is worth: its price with each upgrade's value taken off.
	 * Never below a tenth of the price, so an upgrade priced far above what the item sold for
	 * can't make it worthless.
	 */
	public static double clean(double price, JsonObject item, Prices prices, Model model) {
		double upgrades = 0;
		for (Upgrade u : upgrades(item)) upgrades += partPrice(u, prices) * model.share(u.kind) * u.count;
		return Math.max(price * 0.1, price - upgrades);
	}

	/** One of an upgrade's parts at its own price: the cheapest that's priced, 0 when none is. */
	private static long partPrice(Upgrade u, Prices prices) {
		List<String> ids = u.kind == Kind.REFORGE ? prices.stones(u.detail.toLowerCase(Locale.ROOT)) : u.parts;
		long best = -1;
		for (String id : ids) {
			long p = prices.of(id);
			if (p > 0 && (best < 0 || p < best)) best = p;
		}
		if (best < 0 && u.kind == Kind.FUMING_POTATO_BOOKS) best = prices.of("hot_potato_book");
		return Math.max(0, best);
	}

	/** The Ritual Residue {@code stars} stars took: the nth star takes n, so five took 15. */
	static int residue(int stars) {
		return stars * (stars + 1) / 2;
	}

	/** A child object, or an empty one when it's missing. */
	private static JsonObject obj(JsonObject o, String key) {
		return o != null && o.get(key) instanceof JsonObject x ? x : new JsonObject();
	}

	/** A text field, or "" when it's missing. */
	private static String str(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** A number field, or 0 when it's missing or not a number. */
	private static double num(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** A flag, true or a non-zero number (as game data stores it); false when missing. */
	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		if (e == null || !e.isJsonPrimitive()) return false;
		var p = e.getAsJsonPrimitive();
		return p.isBoolean() ? p.getAsBoolean() : p.isNumber() && p.getAsDouble() != 0;
	}
}
