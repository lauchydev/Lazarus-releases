package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one tracker has counted over one period: named counts (kills, fights, firsts…), named
 * sums (damage, share…), the drops by item, and how many kills since each rare drop last came.
 * No game types, so the arithmetic every tracker shares is tested once.
 */
public final class Tally {
	/** Named whole-number counts: kills, fights, firsts, "tiers.V". */
	private final Map<String, Integer> counts = new LinkedHashMap<>();
	/** Named running totals: damage, share. */
	private final Map<String, Double> sums = new LinkedHashMap<>();
	/** How many of each item dropped. */
	private final Map<String, Integer> drops = new LinkedHashMap<>();
	/** Paid kills since each rare last dropped, by rare name. */
	private final Map<String, Integer> since = new LinkedHashMap<>();

	/** The count named {@code what}, 0 when nothing was counted yet. */
	public int count(String what) {
		return counts.getOrDefault(what, 0);
	}

	/** Adds {@code n} to the count named {@code what}. */
	public void add(String what, int n) {
		counts.merge(what, n, Integer::sum);
	}

	/** The running total named {@code what}, 0 when nothing was added yet. */
	public double sum(String what) {
		return sums.getOrDefault(what, 0.0);
	}

	/** Adds {@code n} to the running total named {@code what}. */
	public void add(String what, double n) {
		sums.merge(what, n, Double::sum);
	}

	/** Every count whose name starts with {@code prefix}, keyed by the rest of the name: "tier." gives {V=12, IV=30}. */
	public Map<String, Integer> counts(String prefix) {
		Map<String, Integer> out = new LinkedHashMap<>();
		counts.forEach((k, v) -> {
			if (k.startsWith(prefix)) out.put(k.substring(prefix.length()), v);
		});
		return out;
	}

	/** Every item that dropped and how many, in the order first seen. */
	public Map<String, Integer> drops() {
		return drops;
	}

	/** {@code n} more of {@code item} dropped. */
	public void drop(String item, int n) {
		drops.merge(item, n, Integer::sum);
	}

	/** Paid kills since each rare last dropped, by rare name. */
	public Map<String, Integer> since() {
		return since;
	}

	/** A paid kill passed without {@code rare} dropping; the dry streak grows. */
	public void miss(String rare) {
		since.merge(rare, 1, Integer::sum);
	}

	/** {@code rare} dropped: the streak it ends, then the count starts again at 0. */
	public int hit(String rare) {
		int after = since.getOrDefault(rare, 0);
		since.put(rare, 0);
		return after;
	}

	/** True when nothing was counted and nothing dropped. */
	public boolean empty() {
		return counts.isEmpty() && drops.isEmpty();
	}

	/** The tally as JSON: counts, sums, drops and since, each an object of name to number. */
	public JsonObject save() {
		JsonObject o = new JsonObject();
		JsonObject c = new JsonObject();
		counts.forEach(c::addProperty);
		o.add("counts", c);
		JsonObject s = new JsonObject();
		sums.forEach(s::addProperty);
		o.add("sums", s);
		JsonObject d = new JsonObject();
		drops.forEach(d::addProperty);
		o.add("drops", d);
		JsonObject r = new JsonObject();
		since.forEach(r::addProperty);
		o.add("since", r);
		return o;
	}

	/**
	 * Reads a saved tally; a damaged or missing part is skipped rather than failing the start.
	 * Files from before tallies were shared are read too: a number at the top level is a count (or
	 * a sum when it has a decimal point), and any other object there, such as "tiers", becomes
	 * counts named "tiers.V".
	 */
	public static Tally load(JsonElement json) {
		Tally t = new Tally();
		if (json == null || !json.isJsonObject()) return t;
		JsonObject o = json.getAsJsonObject();
		for (var e : o.entrySet()) {
			String key = e.getKey();
			JsonElement v = e.getValue();
			try {
				switch (key) {
					case "counts" -> ints(v, "", t.counts);
					case "drops" -> ints(v, "", t.drops);
					case "since" -> ints(v, "", t.since);
					case "sums" -> {
						if (v.isJsonObject()) for (var s : v.getAsJsonObject().entrySet()) t.sums.put(s.getKey(), s.getValue().getAsDouble());
					}
					default -> {
						if (v.isJsonObject()) ints(v, key + ".", t.counts);
						else if (v.isJsonPrimitive() && ((JsonPrimitive) v).isNumber()) {
							if (v.getAsString().contains(".")) t.sums.put(key, v.getAsDouble());
							else t.counts.put(key, v.getAsInt());
						}
					}
				}
			} catch (RuntimeException ignored) {
				// one bad part, not the whole file
			}
		}
		return t;
	}

	private static void ints(JsonElement v, String prefix, Map<String, Integer> into) {
		if (!v.isJsonObject()) return;
		for (var e : v.getAsJsonObject().entrySet()) {
			try {
				into.put(prefix + e.getKey(), e.getValue().getAsInt());
			} catch (RuntimeException ignored) {
				// one bad number, not the whole file
			}
		}
	}
}
