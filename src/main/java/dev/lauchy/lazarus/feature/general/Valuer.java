package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What Lazarus values an item at, from what's {@link Known}. The worker's pricing.js is the same rule
 * for rule, and both run the cases in {@code valuation/vectors.json}, so the Discord bot and {@code /l}
 * agree.
 * <p>
 * One clean item, best evidence first: a price the owner forced; the bazaar; three or more sales read
 * clean; this computer's own record of sales; what Lazarus users traded it for (settled once three
 * different pairs of traders have); what dragsim.co's networth said; one or two sales, capped by the
 * cheapest listing; a price the owner set; the cheapest clean listing; the parts of its recipe; the fire
 * sale; dragsim.co's craft cost. Unless that is settled, never below its recipe's known parts; never
 * below what an NPC pays.
 * <p>
 * An upgraded item, best first: its clean item's forced price plus its upgrades; dragsim.co's sales of
 * exactly that item (High confidence, three or more; a pet's are matched by level, so three of any
 * confidence do); the cheapest listing of exactly that item, not the owner's; the clean item plus each
 * upgrade at its share; and last the floors, on which only parts with a market of their own add.
 * Pure, so it is tested without the game.
 */
public final class Valuer {
	/** How sure a value is, surest first. */
	public static final List<String> CONFIDENCE = List.of("High", "Medium", "Low", "Floor");
	/** Different pairs of traders it takes to settle an item's price; fewer only fill in. */
	static final int TRADE_PAIRS = 3;
	/** Trades older than this don't set a price. */
	private static final long TRADE_MS = 30 * 86_400_000L;
	/** How deep a recipe's parts are priced by their own recipes before giving up. */
	private static final int RECIPE_DEPTH = 4;
	/** dragsim.co's word for sales of exactly this item. */
	private static final String SITE_MATCHED = "AH matched sold median";
	/** A pet's id ("enderdragon_pet_legendary"). */
	private static final Pattern PET = Pattern.compile("(.+)_pet_(common|uncommon|rare|epic|legendary|mythic)");

	/** A clean item's value, where it came from ("3 sales"), how sure it is, and whether it stands below its recipe's cost. */
	public record Base(long value, String source, String confidence, boolean settled) {
		/** Nothing known. */
		public static final Base NONE = new Base(-1, "", null, false);
	}

	/**
	 * What a networth row on dragsim.co says of its item: its price for one, the source and confidence
	 * of that, how many sales it's drawn from, the site's NPC-floor or gem-cost price (-1 when it isn't
	 * one), what an NPC pays for one by the item itself (-1), and for a bazaar stack how many the buy
	 * orders up now won't take (0 when all sell, or it isn't one).
	 */
	public record Site(double price, String source, String confidence, int samples, double floor, double npc, long unfilled) {
		/** A row that says nothing. */
		public static final Site NONE = new Site(-1, "", null, 0, -1, -1, 0);

		/** What a networth row ({@code valuation}, {@code item}, {@code bazaar_depth}) says, as pricing.js's siteOf reads it. */
		public static Site of(JsonObject row) {
			JsonObject item = obj(row, "item"), data = obj(item, "item_data"), depth = obj(row, "bazaar_depth");
			double npc = num(data, "npc_sell_price") > 0 ? num(data, "npc_sell_price") : -1;
			long unfilled = (long) Math.floor(num(depth, "unfilled"));
			if (!(row != null && row.get("valuation") instanceof JsonObject v)) return new Site(-1, "", null, 0, -1, npc, unfilled);
			double price = num(v, "price") > 0 ? num(v, "price") : -1;
			String source = str(v, "source"), confidence = str(v, "confidence");
			double floor = price > 0 && (source.equals("NPC sell floor") || source.startsWith("Gem cost")) ? price : -1;
			return new Site(price, source, confidence.isEmpty() ? null : confidence, (int) Math.floor(num(v, "samples")), floor, npc, unfilled);
		}
	}

	/**
	 * One stack of an item valued: its id, one's value and the stack's (-1 when nothing prices it), where it
	 * came from, how sure it is, how it was reached ("variant", "rebuild", "floor", "" when unpriced), the
	 * clean item's value, source and confidence, each upgrade, and how many are past the bazaar's buy orders.
	 */
	public record Valuation(String id, long unit, long total, String source, String confidence, String kind, long base, String baseSource,
			String baseConfidence, List<ItemValue.Part> parts, long past) {
		/** What the upgrades add together. */
		public long upgrades() {
			return parts.stream().mapToLong(ItemValue.Part::value).filter(v -> v > 0).sum();
		}
	}

	private final Known known;
	private final Map<String, Base> memo = new HashMap<>();
	private final Map<String, Fresh> fresh = new HashMap<>();
	/** Prices as the upgrade reader asks for them. */
	private final ItemValue.Prices prices = new ItemValue.Prices() {
		@Override
		public long of(String id) {
			return base(id).value;
		}

		@Override
		public List<String> stones(String reforge) {
			return known.stones(reforge);
		}
	};

	/** A valuer over {@code known}; it remembers what it worked out, so make a new one when the evidence changes. */
	public Valuer(Known known) {
		this.known = known;
	}

	/** One step less sure: High to Medium, Medium to Low; Low and Floor stay. */
	public static String lessSure(String confidence) {
		if ("High".equals(confidence)) return "Medium";
		if ("Medium".equals(confidence)) return "Low";
		return confidence;
	}

	/** The less sure of two confidences. */
	public static String leastSure(String a, String b) {
		if (a == null) return b;
		if (b == null) return a;
		return CONFIDENCE.indexOf(a) >= CONFIDENCE.indexOf(b) ? a : b;
	}

	/** The trade price of an item: each pair's middle price once (an unnamed pair is one), then the middle of those; and how many pairs. */
	static long[] tradePrice(List<Known.Traded> trades, long now) {
		Map<String, List<Long>> byPair = new LinkedHashMap<>();
		for (Known.Traded t : trades) {
			if (t.price() <= 0 || t.at() < now - TRADE_MS) continue;
			byPair.computeIfAbsent(t.pair() == null || t.pair().isEmpty() ? "?" : t.pair(), k -> new ArrayList<>()).add(t.price());
		}
		List<Long> medians = byPair.values().stream().map(Trade::median).sorted().toList();
		return new long[]{medians.isEmpty() ? -1 : Trade.median(medians), medians.size()};
	}

	/** What one clean {@code id} is worth, as {@link Valuer} lists, never less than an NPC pays. */
	public Base base(String id) {
		return base(id, 0, new HashSet<>());
	}

	private Base base(String id, int depth, Set<String> pricing) {
		if (id == null || id.isEmpty()) return Base.NONE;
		id = known.alias(id.toLowerCase(Locale.ROOT));
		boolean top = depth == 0 && pricing.isEmpty();
		if (top && memo.containsKey(id)) return memo.get(id);
		long npc = known.npc(id);
		Base found = market(id, depth, pricing);
		String twin = ItemValue.twin(id);
		if (found.value <= 0 && twin != null) found = market(known.alias(twin), depth, pricing);
		Base out = npc > 0 && npc >= found.value ? new Base(npc, "NPC", "Floor", false) : found;
		if (top) memo.put(id, out);
		return out;
	}

	/** The best evidence ({@link #ranked}), but unless it's settled, never less than what its recipe's known parts cost. */
	private Base market(String id, int depth, Set<String> pricing) {
		boolean inside = pricing.contains(id); // already being priced further up, as part of its own recipe
		Base found = ranked(id, depth, pricing);
		if (depth >= RECIPE_DEPTH || inside || found.settled) return found;
		Set<String> parts = new HashSet<>(pricing);
		parts.add(id);
		long floor = recipeCost(id, depth, parts, true);
		return floor > found.value ? new Base(floor, "recipe parts", "Low", false) : found;
	}

	/** An item's shared evidence, weighed once. */
	private Fresh fresh(String id) {
		return fresh.computeIfAbsent(id, known::prices);
	}

	/** Market evidence's confidence once its newest read is older than a half-life: one step less sure. */
	private static String aged(String confidence, long age) {
		return age > PriceMath.HALF_LIFE_MS ? lessSure(confidence) : confidence;
	}

	private Base ranked(String id, int depth, Set<String> pricing) {
		long set = known.manual(id);
		if (set > 0 && known.forced(id)) return new Base(set, "set price", "High", true);
		Fresh p = fresh(id);
		if (p.bazaar() > 0) return new Base(Math.round(p.bazaar()), "bazaar", aged("High", p.age()), true);
		Known.Local local = known.local(id);
		if (local.bazaar() > 0) return new Base(local.bazaar(), "bazaar", "High", true);
		if (p.clean() > 0) return new Base(Math.round(p.clean()), Math.max(p.sales(), PriceMath.MIN_SALES) + " sales", aged("High", p.age()), true);
		if (local.sales() > 0) return new Base(local.sales(), "recent sales", "High", true);
		long[] traded = tradePrice(known.trades(id), known.now());
		if (traded[0] > 0) {
			boolean settled = traded[1] >= TRADE_PAIRS;
			return new Base(traded[0], traded[1] + (traded[1] == 1 ? " trade" : " trades"), settled ? "High" : "Medium", settled);
		}
		long site = known.siteBase(id);
		if (site > 0) return new Base(site, "dragsim.co", "Medium", false);
		// A sale or two says more than a recipe's parts; nobody pays more than the cheapest listing.
		double thin = p.thin(), listed = p.listed();
		if (thin > 0 && (listed <= 0 || thin <= listed)) return new Base(Math.round(thin), Math.max(1, p.sales()) + (p.sales() == 1 ? " sale" : " sales"), aged("Medium", p.age()), false);
		// The owner's price stands even below the recipe's cost (the owner's rule): only the market overrides it.
		if (set > 0) return new Base(set, "set price", "Medium", true);
		if (listed > 0) return new Base(Math.round(listed), "listed", aged("Medium", p.age()), false);
		// Something anyone can craft is worth its parts, whatever the fire sale once asked for it (a 100k-gem Atomsplit Katana).
		long recipe = depth < RECIPE_DEPTH && pricing.add(id) ? recipeCost(id, depth, pricing, false) : -1;
		if (recipe > 0) return new Base(recipe, "recipe", "Low", false);
		long sale = known.fireSale(id);
		if (sale > 0) return new Base(sale, "fire sale", "Low", false);
		long craft = known.siteCraft(id);
		if (craft > 0) return new Base(craft, "dragsim.co recipe", "Low", false);
		return Base.NONE;
	}

	/** What {@code id}'s cheapest known recipe costs per item made; -1 when a part has no price, or with {@code priced} just the priced parts. */
	private long recipeCost(String id, int depth, Set<String> pricing, boolean priced) {
		long best = -1;
		for (ItemValue.Recipe r : known.recipes(id)) {
			long total = r.fee();
			for (var part : r.parts().entrySet()) {
				long each = base(part.getKey(), depth + 1, new HashSet<>(pricing)).value;
				if (each <= 0) {
					if (priced) continue; // only the parts that have a price, for a floor
					total = -1;
					break;
				}
				total += each * part.getValue();
			}
			if (total > 0 && (best < 0 || total / r.makes() < best)) best = total / r.makes();
		}
		return best;
	}

	/** The cheapest listing of exactly this item still up that isn't {@code owner}'s; -1 when none. */
	private long variantListing(String id, String variant, String owner) {
		String mine = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
		long best = -1;
		for (Known.Listing l : known.listings(id)) {
			if (!l.variant().equals(variant) || l.price() <= 0 || !mine.isEmpty() && l.seller().toLowerCase(Locale.ROOT).equals(mine)) continue;
			if (best < 0 || l.price() < best) best = l.price();
		}
		return best;
	}

	/**
	 * One stack of {@code quantity} of an item (in the site's shape) valued; {@code site} is what its
	 * networth row says ({@link Site#NONE} when there's none), {@code owner} whose it is, so their own
	 * listings don't price it.
	 */
	public Valuation value(JsonObject item, Site site, String owner, long quantity) {
		String entry = str(item, "entry").toLowerCase(Locale.ROOT);
		String id = entry.isEmpty() ? "" : known.alias(entry);
		List<ItemValue.Upgrade> ups = ItemValue.upgrades(item);
		Site s = site == null ? new Site(-1, "", null, 0, -1, num(obj(item, "item_data"), "npc_sell_price") > 0 ? num(obj(item, "item_data"), "npc_sell_price") : -1, 0) : site;
		boolean pet = PET.matcher(entry).matches();
		List<ItemValue.Part> parts = new ArrayList<>();
		Base clean = id.isEmpty() ? Base.NONE : base(id);
		boolean forced = known.manual(id) > 0 && known.forced(id);
		long unit = -1;
		String source = "", confidence = null, kind = "";
		// The site's sales of exactly this item: a pet's matched by its level, an upgraded item's by its upgrades.
		boolean matched = SITE_MATCHED.equals(s.source) && s.price > 0 && s.samples >= PriceMath.MIN_SALES && (pet || "High".equals(s.confidence));
		if (!forced && matched && (pet || !ups.isEmpty())) {
			unit = Math.round(s.price);
			source = "dragsim.co sales";
			confidence = "High".equals(s.confidence) ? "High" : "Medium";
			kind = "variant";
		}
		long listed = !forced && unit < 0 && !ups.isEmpty() ? variantListing(id, ItemValue.variant(item), owner) : -1;
		if (listed > 0) {
			unit = listed;
			source = "listed";
			confidence = "Medium";
			kind = "variant";
		}
		if (unit < 0 && clean.value > 0) {
			// On an item worth only what an NPC pays, only parts with a market of their own add anything.
			boolean floor = "Floor".equals(clean.confidence);
			ItemValue.Model model = known.shares();
			for (ItemValue.Upgrade u : ups) {
				boolean blocked = floor && u.kind().fitted();
				long each = blocked ? 0 : ItemValue.partPrice(u, prices);
				long v = blocked ? 0 : each > 0 ? Math.round(each * model.share(u.kind()) * u.count()) : ItemValue.Part.UNPRICED;
				if (v != 0 || blocked) parts.add(new ItemValue.Part(u.kind(), v, u.detail()));
			}
			long added = parts.stream().mapToLong(ItemValue.Part::value).filter(v -> v > 0).sum();
			unit = clean.value + added;
			source = clean.source;
			confidence = added > 0 && !forced ? lessSure(clean.confidence) : clean.confidence;
			kind = "rebuild";
		}
		// The last word before calling an item unpriced: what the site says an NPC pays or its gems cost.
		if (unit < 0) {
			double floor = Math.max(s.floor, s.npc);
			if (floor > 0) {
				unit = Math.round(floor);
				source = s.floor > 0 && s.source.startsWith("Gem cost") && s.floor >= s.npc ? "gem cost" : "NPC";
				confidence = "Floor";
				kind = "floor";
			}
		}
		long q = Math.max(1, quantity);
		long past = Math.min(q, Math.max(0, s.unfilled));
		return new Valuation(id, unit, unit > 0 ? unit * q : -1, source, unit > 0 && past > 0 ? leastSure(confidence, "Low") : confidence,
				kind, clean.value, clean.source, clean.confidence, parts, past);
	}

	/** A child object, or an empty one. */
	private static JsonObject obj(JsonObject o, String key) {
		return o != null && o.get(key) instanceof JsonObject x ? x : new JsonObject();
	}

	/** A text field, or "". */
	private static String str(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** A number field, or 0. */
	private static double num(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}
}
