package dev.lauchy.lazarus.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Dragsim's staff ranks and the colour each is shown in: owner blue, admin red, manager dark red,
 * mod green, helper aqua, as § colour codes. One place, so a rank tag on a profile and a staff name
 * coloured in chat always match. The codes can be overridden from a setting ("owner=9, admin=c").
 */
public final class Ranks {
	/** The default colour code of each staff rank, by {@link Staff#rankKey} spelling. */
	private static final Map<String, Character> DEFAULT = Map.of("OWNER", '9', "ADMIN", 'c', "MANAGER", '4', "MOD", '2', "MODERATOR", '2', "HELPER", 'b');
	private static Map<String, Character> codes = DEFAULT;

	private Ranks() {
	}

	/** The § colour code of a staff rank ("c" for ADMIN), or 0 when it isn't a staff rank. */
	public static char code(String rank) {
		Character c = codes.get(Staff.rankKey(rank));
		return c == null ? 0 : c;
	}

	/** The rank as a coloured tag, "§cADMIN", or null when it isn't a staff rank. */
	public static String tag(String rank) {
		char c = code(rank);
		return c == 0 ? null : "§" + c + Staff.rankKey(rank);
	}

	/**
	 * Replaces the colours from "owner=9, admin=c, mod=a" style text; a rank left out keeps its default,
	 * and a bad entry is skipped. Returns what's now in force, for a test or a listing.
	 */
	public static Map<String, Character> configure(String text) {
		Map<String, Character> out = new HashMap<>(DEFAULT);
		for (String part : text.split(",")) {
			String[] kv = part.split("=");
			if (kv.length != 2) continue;
			String rank = Staff.rankKey(kv[0]), code = kv[1].trim().toLowerCase(java.util.Locale.ROOT);
			if (code.length() == 1 && "0123456789abcdef".indexOf(code.charAt(0)) >= 0 && DEFAULT.containsKey(rank)) out.put(rank, code.charAt(0));
		}
		codes = out;
		return out;
	}
}
