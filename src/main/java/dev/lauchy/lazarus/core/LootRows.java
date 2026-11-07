package dev.lauchy.lazarus.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The HUD rows every loot tracker shares: each drop in its rarity's colour with how often it
 * comes and, after a tab, its value (the panel draws that part right-aligned), "+N more"
 * when the list is cut, then the loot's total value. Prices and colours come in as functions
 * so this has no game in it.
 */
public final class LootRows {
	private LootRows() {
	}

	/**
	 * One row per drop, the most valuable first (drops with no price after, most common first),
	 * up to {@code limit}; then the value of all of them.
	 * {@code kills} gives each drop its rate ("1 in 12", "2.5 per kill"); {@code each} names
	 * the kill ("kill", "boss", "fight"). {@code price} is what one item is worth, or -1.
	 */
	public static List<String> drops(Map<String, Integer> drops, int kills, String each, int limit, ToLongFunction<String> price) {
		return drops(drops, kills, each, limit, price, LootRows::colour);
	}

	/** {@link #drops(Map, int, String, int, ToLongFunction)} with each item's colour from {@code colour}. */
	public static List<String> drops(Map<String, Integer> drops, int kills, String each, int limit, ToLongFunction<String> price,
			Function<String, String> colour) {
		List<String> out = new ArrayList<>();
		List<Map.Entry<String, Integer>> sorted = byValue(drops, price);
		for (int i = 0; i < sorted.size() && i < limit; i++) {
			var e = sorted.get(i);
			out.add("§f" + e.getValue() + "x " + colour.apply(e.getKey()) + e.getKey() + (kills > 0 ? " §8(" + rate(e.getValue(), kills, each) + ")" : "")
					+ "\t" + worth(e.getKey(), e.getValue(), price));
		}
		if (sorted.size() > limit) out.add("§8+" + (sorted.size() - limit) + " more");
		if (!sorted.isEmpty()) out.add("§7Value\t§a" + value(drops, price));
		return out;
	}

	/** The drops, the most valuable in all first; those with no price come after, most common first. */
	public static List<Map.Entry<String, Integer>> byValue(Map<String, Integer> drops, ToLongFunction<String> price) {
		Map<String, Long> worth = new HashMap<>();
		drops.forEach((item, n) -> {
			long each = price.applyAsLong(item);
			worth.put(item, each < 0 ? -1 : each * (long) n);
		});
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(drops.entrySet());
		sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingLong(e -> worth.get(e.getKey())).reversed()
				.thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed()));
		return sorted;
	}

	/** What {@code count} of an item are worth, compact and in green, or a grey "?" when it has no price. */
	public static String worth(String item, int count, ToLongFunction<String> price) {
		long each = price.applyAsLong(item);
		return each < 0 ? "§8?" : "§a" + Text.compact(each * (long) count);
	}

	/** The colour code for a catalogue rarity ("LEGENDARY" → gold), the way the game colours item names; white when unknown. */
	public static String rarity(String tier) {
		return switch (tier == null ? "" : tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> "§a";
			case "RARE" -> "§9";
			case "EPIC" -> "§5";
			case "LEGENDARY" -> "§6";
			case "MYTHIC" -> "§d";
			case "DIVINE" -> "§b";
			case "SPECIAL", "VERY_SPECIAL" -> "§c";
			default -> "§f";
		};
	}

	/**
	 * An item's name colour: the one the server wrote it in when it dropped, else from its rarity
	 * in the catalogue (one it lists with no rarity is a plain Common item); grey for an item
	 * neither knows.
	 */
	public static String colour(String item) {
		String seen = DropColours.of(item);
		if (seen != null) return seen;
		Catalog.Item i = Catalog.item(Catalog.id(item));
		return i == null ? "§7" : rarity(i.tier());
	}

	/** "1 in 12" for drops rarer than one a kill, else "2.5 per kill". */
	public static String rate(int drops, int kills, String each) {
		if (drops >= kills) return String.format(Locale.ROOT, "%.1f per %s", drops / (double) kills, each);
		return "1 in " + Math.round(kills / (double) drops);
	}

	/** The NPC value of all the drops, compact, with " + ?" when some item has no price. */
	public static String value(Map<String, Integer> drops, ToLongFunction<String> price) {
		long value = 0;
		boolean unpriced = false;
		for (var e : drops.entrySet()) {
			long each = price.applyAsLong(e.getKey());
			if (each < 0) unpriced = true;
			else value += each * e.getValue();
		}
		return Text.compact(value) + (unpriced ? " §7+ ?" : "");
	}

	/** The NPC value of all the drops as a number, counting unpriced items as nothing. */
	public static long total(Map<String, Integer> drops, ToLongFunction<String> price) {
		long value = 0;
		for (var e : drops.entrySet()) {
			long each = price.applyAsLong(e.getKey());
			if (each > 0) value += each * e.getValue();
		}
		return value;
	}

	/** "Bosses since last drop" rows: each rare that has a count, in its rarity's colour, under one heading. */
	public static List<String> since(Map<String, Integer> since, List<String> rares, String heading) {
		return since(since, rares, heading, LootRows::colour);
	}

	/** {@link #since(Map, List, String)} with each rare's colour from {@code colour}. */
	public static List<String> since(Map<String, Integer> since, List<String> rares, String heading, Function<String, String> colour) {
		List<String> out = new ArrayList<>();
		for (String rare : rares) {
			Integer n = since.get(rare);
			if (n == null) continue;
			if (out.isEmpty()) out.add("§8" + heading);
			out.add(colour.apply(rare) + rare + " §f" + n);
		}
		return out;
	}

	/** The rare in {@code rares} a drop counts as: its own name, or a pet such as "Golem Pet" for "Golem"; null for none. */
	public static String rareFor(String item, List<String> rares) {
		String i = item.toLowerCase(Locale.ROOT);
		for (String r : rares) {
			String l = r.toLowerCase(Locale.ROOT);
			if (i.equals(l) || i.equals(l + " pet")) return r;
		}
		return null;
	}

	/** A comma-separated setting as trimmed names, empty ones dropped. */
	public static List<String> names(String commaSeparated) {
		List<String> out = new ArrayList<>();
		for (String r : commaSeparated.split(",")) if (!r.isBlank()) out.add(r.trim());
		return out;
	}
}
