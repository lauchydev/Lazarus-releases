package dev.lauchy.lazarus.core;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads loot out of a server chat line, in every shape the server tells it:
 * <ul>
 * <li>the announcement, "RARE DROP! 2x Item (+50% Magic Find)" or "EPIC DROP! (Summoning Eye) (✯ 239%)";</li>
 * <li>a bonus, "BONUS! You received 3x Item!";</li>
 * <li>the staff-only roll, "[Debug] Riftborn Seraph · Roll 78.0/100 · MF 349.6 · Null Ovoid; fixed odds HIT",
 * which names the mob a tick before its drop is announced (players without staff never see one).</li>
 * </ul>
 * A line someone typed always has a colon before the drop and is never one.
 */
public final class Drops {
	/** Where a drop was read from: a "RARE DROP!" line, a "BONUS!" line, a dragon's loot summary, or your inventory. */
	public enum Source { ANNOUNCEMENT, BONUS, SUMMARY, INVENTORY }

	/** One drop credited to you: the item, how many, and where it was read from. */
	public record Drop(String item, int count, Source source) {
		/** A drop from an announcement line. */
		public Drop(String item, int count) {
			this(item, count, Source.ANNOUNCEMENT);
		}
	}

	/** One staff roll: the mob that rolled, what it rolled, and whether a chance roll landed. */
	public record Roll(String mob, String item, boolean hit) {
	}

	private static final Pattern DROP = Pattern.compile("^[A-Z][A-Z ]*DROP!\\s*(.+)$");
	private static final Pattern BONUS = Pattern.compile("^BONUS! You received (.+?)!?$");
	/** "[Debug] <mob> · Roll|Table|Spawn N/100 · <segments>", the segments being stats and, among them, the item. */
	private static final Pattern ROLL = Pattern.compile("^\\[Debug\\]\\s+(.+?)\\s+·\\s+(Roll|Table|Spawn)\\s+[\\d.]+/\\d+\\s*·?\\s*(.*)$");
	/** A segment that is a number about the roll rather than the item: "MF 349.6", "PL 76.7", "Luck 2x", "Chance 0.03%", "Range [0, 10)", "1 item odds (hover)". */
	private static final Pattern ROLL_STAT = Pattern.compile("(?i)^(MF|PL|Luck|Chance|Base|Range)\\b.*|^\\d+ item odds.*");
	/** The server's table that holds the dragon loot, named in a cache roll; a table, not an item. */
	private static final String TABLE_NAME = "DragonLootDrop";
	private static final Pattern TRAILING = Pattern.compile("\\s*\\([^()]*\\)\\s*$");
	private static final Pattern PREFIX_COUNT = Pattern.compile("^([\\d,]+)\\s*[x×]\\s+(.+)$");
	private static final Pattern SUFFIX_COUNT = Pattern.compile("^(.+?)\\s+[x×]([\\d,]+)$");

	private Drops() {
	}

	/** The drop a server line announces to you (an announcement or a bonus), or null if it isn't one. */
	public static Drop parse(String line) {
		String l = line.trim();
		Matcher m;
		if ((m = BONUS.matcher(l)).matches()) return item(m.group(1), Source.BONUS);
		if (Chat.fromPlayer(l)) return null;
		return (m = DROP.matcher(l)).matches() ? item(m.group(1), Source.ANNOUNCEMENT) : null;
	}

	/**
	 * The staff roll a line is, or null: a spawn roll (which dragon came) and a roll that landed on
	 * the dragon loot table rather than an item aren't drops. "HIT" and the notes after ";" ("fixed
	 * odds", "MF not applied") come off the item; "miss" makes it a roll that gave nothing.
	 */
	public static Roll roll(String line) {
		Matcher m = ROLL.matcher(line.trim());
		if (!m.matches() || m.group(2).equals("Spawn")) return null;
		for (String segment : m.group(3).split("\\s+·\\s+")) {
			String s = segment.trim();
			if (s.isEmpty() || ROLL_STAT.matcher(s).matches()) continue;
			int semi = s.indexOf(';');
			String note = semi < 0 ? "" : s.substring(semi + 1).trim();
			if (semi >= 0) s = s.substring(0, semi).trim();
			boolean hit = !(s.matches("(?i).*\\bmiss$") || note.matches("(?i).*\\bmiss$"));
			s = s.replaceFirst("(?i)\\s+(hit|miss)$", "").trim();
			if (s.isEmpty() || s.equalsIgnoreCase("nothing") || s.equals(TABLE_NAME)) return null;
			return new Roll(m.group(1), s, hit);
		}
		return null;
	}

	/**
	 * "2x Item", "Item x2", "(Item)" or "[Lvl 1] Pet", with any trailing bracketed notes
	 * such as Magic Find dropped. Null for an empty name.
	 */
	public static Drop item(String text) {
		return item(text, Source.ANNOUNCEMENT);
	}

	/** Reads "2x Item", "Item x2", "(Item)" or "[Lvl 1] Pet" into a drop from {@code source}. */
	public static Drop item(String text, Source source) {
		String item = text.trim();
		// Notes such as "(✯ 239%)" or "(+50% Magic Find)" come off the end first, but never the
		// last group left: dragsim wraps some names in brackets, "(Summoning Eye) (✯ 239%)".
		Matcher t;
		while ((t = TRAILING.matcher(item)).find() && !item.substring(0, t.start()).isBlank()) item = item.substring(0, t.start());
		if (item.startsWith("(") && item.endsWith(")") && item.indexOf('(', 1) < 0) item = item.substring(1, item.length() - 1);
		item = item.replaceFirst("^\\[Lvl \\d+\\]\\s*", "");
		int count = 1;
		Matcher c;
		if ((c = PREFIX_COUNT.matcher(item)).matches()) {
			count = Integer.parseInt(c.group(1).replace(",", ""));
			item = c.group(2);
		} else if ((c = SUFFIX_COUNT.matcher(item)).matches()) {
			count = Integer.parseInt(c.group(2).replace(",", ""));
			item = c.group(1);
		}
		item = item.trim();
		return item.isEmpty() || count <= 0 ? null : new Drop(item, count, source);
	}

	/** A server line that says something rather than naming an item: "OOPS! You don't have enough mana!", "Teleported to the hub." */
	private static final Pattern SENTENCE = Pattern.compile(".*[!?:].*|.*\\.$");
	/** A stat, not an item: it starts with the stat's icon ("☘ Mining Fortune", "❤ Health"). Runes start with ◆ and are items. */
	private static final Pattern STAT = Pattern.compile("^[^\\p{L}\\p{N}◆(\\[].*");

	/**
	 * One line of a boss's drop list ("13x Rift Goo", "Crypt Cache"), or null when it isn't an
	 * item: other server lines arrive in the middle of the list ("OOPS! You don't have enough
	 * mana!"), so a line must read like a name and be an item {@code known} knows, or carry a count.
	 */
	public static Drop listed(String line, Predicate<String> known) {
		String text = line.trim();
		if (SENTENCE.matcher(text).matches()) return null;
		Drop d = item(text);
		if (d == null || notAnItem(d.item())) return null;
		boolean counted = PREFIX_COUNT.matcher(text).matches() || SUFFIX_COUNT.matcher(text).matches();
		return counted || known.test(d.item()) ? d : null;
	}

	/**
	 * Whether {@code name} is a server sentence or a stat rather than an item, as the Goop
	 * tracker took some by mistake before 1.17.5 ("OOPS! You don't have enough mana!", "☘ Mining Fortune").
	 */
	public static boolean notAnItem(String name) {
		String n = name.trim();
		return SENTENCE.matcher(n).matches() || STAT.matcher(n).matches();
	}

	/** Two names for one item: case and surrounding space don't count. */
	public static boolean same(String a, String b) {
		return a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
	}
}
