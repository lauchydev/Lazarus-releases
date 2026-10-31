package dev.lauchy.lazarus.core;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The colour the server writes each dropped item's name in, which is its rarity's colour, as
 * seen in the loot lines. It settles items the catalogue can't: several items can share one
 * name there (each tier of Combat XP Boost), and the drop line says which one this was.
 */
public final class DropColours {
	private static final Map<String, String> SEEN = new ConcurrentHashMap<>();

	private DropColours() {
	}

	/** The colour code ("§6") an item's name was last seen in, or null when it hasn't dropped yet. */
	public static String of(String item) {
		return item == null ? null : SEEN.get(item.toLowerCase(Locale.ROOT));
	}

	/** Notes the colour {@code item} is written in on a loot line given with § codes. */
	static void learn(String legacy, String item) {
		String c = colourOf(legacy, item);
		if (c != null) SEEN.put(item.toLowerCase(Locale.ROOT), c);
	}

	/**
	 * The colour code the last letter of {@code item} is written in on a line with § codes
	 * ("§6§lRARE DROP! §7(§5Summoning Eye§7)" gives "§5" for Summoning Eye), or null when the
	 * name isn't on the line or has no colour. The last letter, so "§7[Lvl 1] §6Enderman" is gold.
	 */
	static String colourOf(String legacy, String item) {
		if (legacy == null || item == null || item.isEmpty()) return null;
		StringBuilder plain = new StringBuilder();
		StringBuilder colours = new StringBuilder();
		char colour = 0;
		for (int i = 0; i < legacy.length(); i++) {
			char ch = legacy.charAt(i);
			if (ch == '§' && i + 1 < legacy.length()) {
				char code = Character.toLowerCase(legacy.charAt(++i));
				if (Character.digit(code, 16) >= 0) colour = code;
				else if (code == 'r') colour = 0;
				continue;
			}
			plain.append(ch);
			colours.append(colour);
		}
		int at = plain.toString().toLowerCase(Locale.ROOT).lastIndexOf(item.toLowerCase(Locale.ROOT));
		if (at < 0) return null;
		char c = colours.charAt(at + item.length() - 1);
		return c == 0 ? null : "§" + c;
	}
}
