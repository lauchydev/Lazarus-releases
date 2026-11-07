package dev.lauchy.lazarus.core;

import java.util.List;
import java.util.Map;

/**
 * Telling apart items that share a name by their lore: Dragsim names each dragon armour set's
 * Baby Skin (and Shimmer Skin) the same, and only the lore says which helmet it goes on. Pure, so it is tested
 * without the game.
 */
public final class SameName {
	private SameName() {
	}

	/**
	 * Of items that share a name (each id with its description's lines), the one whose description
	 * best matches {@code lore}: the most of its lines found in the lore, read as one run of text so
	 * wrapping doesn't matter. Null when none matches or two match equally.
	 */
	public static String byLore(Map<String, List<String>> descriptions, List<String> lore) {
		String text = flat(String.join(" ", lore));
		String best = null;
		int most = 0;
		boolean tie = false;
		for (var e : descriptions.entrySet()) {
			int found = 0;
			for (String line : e.getValue()) {
				String l = flat(line);
				if (!l.isEmpty() && text.contains(l)) found++;
			}
			if (found > most) {
				best = e.getKey();
				most = found;
				tie = false;
			} else if (found == most && found > 0) tie = true;
		}
		return tie ? null : best;
	}

	private static String flat(String s) {
		return Text.lower(s).replaceAll("\\s+", " ");
	}
}
