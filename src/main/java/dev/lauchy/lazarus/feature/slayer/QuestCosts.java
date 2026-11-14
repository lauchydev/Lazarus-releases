package dev.lauchy.lazarus.feature.slayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What starting a slayer quest costs, by tier, as you've written it in the tracker's settings ("I 2000, II 7500,
 * V 150k"), and what a run of quests cost in all. The server takes the coins without a word in chat, so the price
 * list is yours to keep right. No Minecraft types, so it's tested on its own.
 */
final class QuestCosts {
	private static final Pattern ENTRY = Pattern.compile("\\b(V|IV|III|II|I)\\b[\\s:=]*([\\d,.]+)\\s*([kKmM]?)");

	private QuestCosts() {
	}

	/** The cost of each tier named in a list like "I 2000, II 7,500, V 150k"; a tier not named costs nothing. */
	static Map<String, Long> parse(String list) {
		Map<String, Long> out = new HashMap<>();
		Matcher m = ENTRY.matcher(list);
		while (m.find()) {
			try {
				double n = Double.parseDouble(m.group(2).replace(",", ""));
				String unit = m.group(3).toLowerCase(Locale.ROOT);
				out.put(m.group(1), Math.round(n * (unit.equals("k") ? 1e3 : unit.equals("m") ? 1e6 : 1)));
			} catch (NumberFormatException ignored) {
				// Not a number: that tier is left out.
			}
		}
		return out;
	}

	/** What these quests cost to start: how many of each tier were killed and how many failed, at each tier's cost. */
	static long spent(Map<String, Integer> killed, Map<String, Integer> failed, Map<String, Long> costs) {
		long total = 0;
		for (var e : killed.entrySet()) total += costs.getOrDefault(e.getKey(), 0L) * e.getValue();
		for (var e : failed.entrySet()) total += costs.getOrDefault(e.getKey(), 0L) * e.getValue();
		return total;
	}
}
