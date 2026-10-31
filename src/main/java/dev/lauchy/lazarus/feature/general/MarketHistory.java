package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * One item's price history as dragsim.co keeps it ({@code /api/market/item/<id>}): every
 * auction sale and bazaar quote the site has recorded, read into the values
 * {@link PriceDatabase} prices with, by the same rules as its own records ({@link PriceMath}).
 * Each sale is also read as the clean item it would have been, its upgrades' value taken off
 * ({@link ItemValue#clean}), since the site's own median mixes clean and upgraded sales.
 * Pure, so it is tested without the game.
 *
 * @param bazaar what the bazaar says one is worth, or -1
 * @param auction the median of every sale, upgraded or not (the site's own figure), or -1
 * @param sales how many sales there were
 * @param clean the median of the sales read as clean items, from {@link PriceMath#MIN_SALES} sales up, or -1
 * @param thin the same from one or two sales, which a coin transfer could set, so it's trusted last; or -1
 * @param listed the cheapest Buy It Now listing still up, as asked, or -1
 */
record MarketHistory(double bazaar, long auction, int sales, long clean, long thin, long listed) {
	private static final long DAY = 86_400_000L;

	/** Nothing known: no bazaar value, no auction value. */
	static final MarketHistory NONE = new MarketHistory(-1, -1, 0, -1, -1, -1);

	/** Reads a sale's price as a clean item's: the price with its upgrades taken off. */
	@FunctionalInterface
	interface Cleaner {
		/** What a clean item would have sold for, given what this one ({@code item}, in the site's shape) did. */
		double clean(double price, JsonObject item);

		/** Takes every sale as it is. */
		Cleaner NONE = (price, item) -> price;
	}

	/**
	 * The bazaar value is the median of the last day's quotes (the current one when the day has
	 * none); the auction value is {@link PriceMath#auctionValue} over every sale in the response,
	 * and the clean one the same over the sales read clean by {@code cleaner}.
	 */
	static MarketHistory read(JsonObject o, long now, Cleaner cleaner) {
		double[] quotes = o.has("quotes") && o.get("quotes").isJsonArray() ? o.getAsJsonArray("quotes").asList().stream()
				.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
				.filter(q -> num(q, "ts") >= now - DAY)
				.mapToDouble(q -> PriceMath.bazaarValue(num(q, "bid"), num(q, "ask")))
				.filter(v -> v > 0).sorted().toArray() : new double[0];
		double bazaar = quotes.length > 0 ? quotes[quotes.length / 2] : -1;
		if (bazaar < 0 && o.get("product") instanceof JsonObject p) bazaar = PriceMath.bazaarValue(num(p, "best_bid"), num(p, "best_ask"));
		JsonObject[] sold = list(o, "sales");
		long[] prices = Arrays.stream(sold).mapToLong(s -> (long) unit(s)).filter(p -> p > 0).toArray();
		long[] clean = Arrays.stream(sold).filter(s -> unit(s) > 0).mapToLong(s -> Math.round(cleaner.clean(unit(s), item(s)))).toArray();
		long thin = -1;
		if (clean.length > 0 && clean.length < PriceMath.MIN_SALES) {
			long[] sorted = clean.clone();
			Arrays.sort(sorted);
			thin = sorted.length == 2 ? (sorted[0] + sorted[1]) / 2 : sorted[0];
		}
		// A listing is taken as asked, upgrades and all: an upgraded item up for P says a clean one is worth
		// at most P, while taking its upgrades off undershoots whenever the seller underprices (a 90M
		// Riftwalker Helmet with 10 books read as a 9M clean one, below the NPC price).
		long listed = Arrays.stream(list(o, "auctions"))
				.filter(a -> bool(a, "bin") && !"sold".equals(str(a, "state")) && unit(a) > 0)
				.mapToLong(a -> Math.round(unit(a))).min().orElse(-1);
		return new MarketHistory(bazaar, PriceMath.auctionValue(prices), prices.length, PriceMath.auctionValue(clean), thin, listed);
	}

	/** {@link #read(JsonObject, long, Cleaner)} taking every sale as it is. */
	static MarketHistory read(JsonObject o, long now) {
		return read(o, now, Cleaner.NONE);
	}

	/**
	 * The clean item's value from the auction house, best evidence first: three or more clean
	 * sales, then one or two, then the cheapest listing; -1 when there's none.
	 */
	long cleanValue() {
		return clean > 0 ? clean : thin > 0 ? thin : listed;
	}

	/** Every object in an array field. */
	private static JsonObject[] list(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key).asList().stream()
				.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).toArray(JsonObject[]::new) : new JsonObject[0];
	}

	/** The item a sale or listing is for, or an empty one. */
	private static JsonObject item(JsonObject sale) {
		return sale.get("item") instanceof JsonObject item ? item : new JsonObject();
	}

	/** What one of a sale's items went for: its unit price, else the price over the count. */
	private static double unit(JsonObject sale) {
		double unit = num(sale, "unit_price");
		if (unit > 0) return unit;
		double count = sale.get("item") instanceof JsonObject item ? Math.max(1, num(item, "count")) : 1;
		return num(sale, "price") / count;
	}

	/** A number field, or 0 when it's missing or not a number. */
	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** A text field, or "" when it's missing. */
	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** A true/false field, false when missing. */
	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}
}
