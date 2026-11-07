package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Http;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
 * @param listed the cheapest Buy It Now listing of a clean item (no upgrades) still up, not the reader's own, or -1
 * @param quartile the lower quartile of the sales read clean, the newest counting most: what one goes for sold quickly; or -1
 * @param listings every Buy It Now listing still up: its price, which copy of the item it is, and its seller
 */
record MarketHistory(double bazaar, long auction, int sales, long clean, long thin, long listed, long quartile, List<Known.Listing> listings) {
	private static final long DAY = 86_400_000L;

	/** Nothing known: no bazaar value, no auction value. */
	static final MarketHistory NONE = new MarketHistory(-1, -1, 0, -1, -1, -1, -1, List.of());

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
	 * none); the auction value is {@link PriceMath#auctionValue(long[], long[])} over every sale in the
	 * response, the newest counting most, and the clean one the same over the sales read clean by {@code cleaner}.
	 * A listing only says what a clean item is worth when it has no upgrades: an upgraded one up for P is
	 * that exact item's price, which {@link Valuer} uses as such. {@code owner}'s own listings (the player
	 * reading) are left out of the clean listing, so nobody sets their own items' value by listing them.
	 */
	static MarketHistory read(JsonObject o, long now, Cleaner cleaner, String owner) {
		double[] quotes = o.has("quotes") && o.get("quotes").isJsonArray() ? o.getAsJsonArray("quotes").asList().stream()
				.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
				.filter(q -> num(q, "ts") >= now - DAY)
				.mapToDouble(q -> PriceMath.bazaarValue(num(q, "bid"), num(q, "ask")))
				.filter(v -> v > 0).sorted().toArray() : new double[0];
		double bazaar = quotes.length > 0 ? quotes[quotes.length / 2] : -1;
		if (bazaar < 0 && o.get("product") instanceof JsonObject p) bazaar = PriceMath.bazaarValue(num(p, "best_bid"), num(p, "best_ask"));
		JsonObject[] sold = Arrays.stream(list(o, "sales")).filter(s -> unit(s) > 0).toArray(JsonObject[]::new);
		long[] prices = Arrays.stream(sold).mapToLong(s -> (long) unit(s)).toArray();
		long[] clean = Arrays.stream(sold).mapToLong(s -> Math.round(cleaner.clean(unit(s), item(s)))).toArray();
		// How long ago each sold: the newest say the most about what one is worth now.
		long[] ages = Arrays.stream(sold).mapToLong(s -> {
			long at = Http.time(s.get("sold_at"));
			return at > 0 ? now - at : 0;
		}).toArray();
		long thin = -1;
		if (clean.length > 0 && clean.length < PriceMath.MIN_SALES) {
			long[] sorted = clean.clone();
			Arrays.sort(sorted);
			thin = sorted.length == 2 ? (sorted[0] + sorted[1]) / 2 : sorted[0];
		}
		String mine = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
		List<Known.Listing> listings = Arrays.stream(list(o, "auctions"))
				.filter(a -> bool(a, "bin") && !"sold".equals(str(a, "state")) && unit(a) > 0)
				.map(a -> new Known.Listing(Math.round(unit(a)), ItemValue.variant(item(a)), str(a, "seller"))).toList();
		long listed = Arrays.stream(list(o, "auctions"))
				.filter(a -> bool(a, "bin") && !"sold".equals(str(a, "state")) && unit(a) > 0 && ItemValue.upgrades(item(a)).isEmpty())
				.filter(a -> mine.isEmpty() || !str(a, "seller").toLowerCase(Locale.ROOT).equals(mine))
				.mapToLong(a -> Math.round(unit(a))).min().orElse(-1);
		return new MarketHistory(bazaar, PriceMath.auctionValue(prices, ages), prices.length, PriceMath.auctionValue(clean, ages), thin, listed,
				PriceMath.lowerQuartile(clean, ages), listings);
	}

	/** {@link #read(JsonObject, long, Cleaner, String)} with no reader to leave out. */
	static MarketHistory read(JsonObject o, long now, Cleaner cleaner) {
		return read(o, now, cleaner, "");
	}

	/** {@link #read(JsonObject, long, Cleaner)} taking every sale as it is. */
	static MarketHistory read(JsonObject o, long now) {
		return read(o, now, Cleaner.NONE);
	}

	/** This read as one shared read ({@code /shared/prices}), taken at {@code at} by {@code by}, as {@link Fresh} weighs it. */
	JsonObject asRead(long at, String by) {
		JsonObject r = new JsonObject();
		r.addProperty("bazaar", bazaar);
		r.addProperty("auction", auction);
		r.addProperty("sales", sales);
		r.addProperty("clean", clean);
		r.addProperty("thin", thin);
		r.addProperty("listed", listed);
		r.addProperty("at", at);
		r.addProperty("by", by);
		return r;
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
