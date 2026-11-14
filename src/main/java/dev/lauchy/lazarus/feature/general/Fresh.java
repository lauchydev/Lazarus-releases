package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One item's price as every Lazarus user's history reads say it, each read counting less the older it
 * is ({@link PriceMath#HALF_LIFE_MS}), so a price that moved this week reads as moved. A read far above
 * or below what was known ({@link PriceMath#OUTLIER}) was held back by the server until something agrees
 * ({@link #released}). The worker's pricing.js reads them the same way (freshPrice there). Pure.
 *
 * @param bazaar the bazaar's value, or -1
 * @param clean the clean price from {@link PriceMath#MIN_SALES} sales up, or -1
 * @param thin one or two sales' price, or -1
 * @param listed the cheapest clean listing, or -1
 * @param sales the most sales any read was drawn from
 * @param age how old the newest read with a figure is, in ms ({@link Long#MAX_VALUE} with none)
 */
record Fresh(double bazaar, double clean, double thin, double listed, int sales, long age) {
	/** Nothing known. */
	static final Fresh NONE = new Fresh(-1, -1, -1, -1, 0, Long.MAX_VALUE);
	/** A held-back read counts on its own once this long has passed with nothing newer saying otherwise. */
	static final long HOLD_MS = 86_400_000L;
	/** The figures a read can carry, in the order one is judged by. */
	private static final String[] FIGURES = {"bazaar", "clean", "thin", "listed", "auction"};

	/**
	 * The evidence in a shared entry ({bazaar, clean, thin, listed, sales, reads?, pending?}, changed at
	 * {@code at}), weighed as of {@code now}; {@code site} is dragsim.co's own price for the item, which
	 * releases a held-back read it agrees with (-1 when none). An entry from before reads were kept is its
	 * one read; a read from before 1.19.0 (only the median of every sale) stands in as clean from {@link
	 * PriceMath#MIN_SALES} sales and thin under.
	 */
	static Fresh of(JsonObject entry, long at, long now, double site) {
		if (entry == null) return NONE;
		List<JsonObject> reads = new ArrayList<>(objects(entry.get("reads")));
		if (reads.isEmpty()) {
			JsonObject one = entry.deepCopy();
			one.addProperty("at", at);
			reads.add(one);
		}
		List<JsonObject> counted = new ArrayList<>(reads);
		for (JsonObject p : objects(entry.get("pending"))) if (released(p, reads, site, now)) counted.add(p);
		List<JsonObject> all = counted.stream().map(Fresh::legacy).toList();
		double[] weights = all.stream().mapToDouble(r -> PriceMath.weight(now - (long) num(r, "at"))).toArray();
		long age = all.stream().filter(r -> headline(r) > 0).mapToLong(r -> now - (long) num(r, "at")).min().orElse(Long.MAX_VALUE);
		int sales = (int) all.stream().mapToDouble(r -> Math.floor(num(r, "sales"))).max().orElse(0);
		return new Fresh(pick(all, weights, "bazaar"), pick(all, weights, "clean"), pick(all, weights, "thin"), pick(all, weights, "listed"), Math.max(0, sales), age);
	}

	/** The weighted median of one figure over the reads that have it. */
	private static double pick(List<JsonObject> reads, double[] weights, String figure) {
		double[] values = reads.stream().mapToDouble(r -> num(r, figure) > 0 ? num(r, figure) : -1).toArray();
		return PriceMath.weightedMedian(values, weights);
	}

	/**
	 * Whether a held-back read counts now: dragsim.co's own price ({@code site}) agrees with it, another
	 * uploader's newer read agrees, or {@link #HOLD_MS} has passed with no newer read saying otherwise.
	 */
	static boolean released(JsonObject pending, List<JsonObject> reads, double site, long now) {
		double value = headline(pending);
		if (PriceMath.agree(value, site)) return true;
		long at = (long) num(pending, "at");
		String by = str(pending, "by");
		List<JsonObject> newer = reads.stream().filter(r -> num(r, "at") > at).toList();
		if (newer.stream().anyMatch(r -> !str(r, "by").equals(by) && PriceMath.agree(headline(r), value))) return true;
		return now - at >= HOLD_MS && newer.stream().noneMatch(r -> headline(r) > 0 && !PriceMath.agree(headline(r), value));
	}

	/** The figure a read is judged by: its bazaar value, else its clean, thin or listed price; -1 when none. */
	static double headline(JsonObject read) {
		for (String f : FIGURES) if (num(read, f) > 0) return num(read, f);
		return -1;
	}

	/** A read from before 1.19.0 (the median of every sale, no clean or thin figure) as clean or thin. */
	private static JsonObject legacy(JsonObject r) {
		if (r.has("thin") || r.has("clean") || !(num(r, "auction") > 0)) return r;
		JsonObject out = r.deepCopy();
		boolean few = num(r, "sales") < PriceMath.MIN_SALES;
		out.addProperty("clean", few ? -1 : num(r, "auction"));
		out.addProperty("thin", few ? num(r, "auction") : -1);
		return out;
	}

	/** Every object in an array, or none. */
	private static List<JsonObject> objects(JsonElement e) {
		List<JsonObject> out = new ArrayList<>();
		if (e instanceof JsonArray a) for (JsonElement x : a) if (x instanceof JsonObject o) out.add(o);
		return out;
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

	/** A text field, or "" when it's missing. */
	private static String str(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
