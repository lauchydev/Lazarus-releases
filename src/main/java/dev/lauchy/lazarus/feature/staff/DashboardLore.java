package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the server's Flag Dashboard says about a player, read from the name and lore of their item. Since Dragsim's
 * 2026-11 update an entry is named after the player, over "Review score: 586.8", "Unreviewed flags: 427" and
 * "Newest source: DragSim AC" (the flags themselves are on the player's own page); before it, a head named
 * "sixmas (11.8)" over "- MACRO (GuiRateLimiter)" lines. Both read as an entry with its flags. Economy anomalies are
 * left out, since staff don't act on them here, and a flag listed twice counts once. No Minecraft types, so it's
 * tested on its own.
 */
public final class DashboardLore {
	/** One active flag: its type ("MACRO", "ANTICHEAT") and what raised it ("GuiRateLimiter", "427 unreviewed · DragSim AC", or ""). */
	public record Flag(String type, String detector) {
	}

	/** A player on the dashboard: their name, the dashboard's score, and the flags that count. */
	public record Entry(String name, double score, List<Flag> flags) {
	}

	/** The flag type staff ignore. */
	static final String IGNORED = "ECONOMY_ANOMALY";
	/** A head's name: the player and, in the old dashboard, their score, "sixmas (11.8)". */
	private static final Pattern TITLE = Pattern.compile("^(\\w{1,16})\\s*(?:\\((-?[\\d.]+)\\))?$");
	/** One flag in an old head's lore, "- MACRO (GuiRateLimiter)". */
	private static final Pattern FLAG = Pattern.compile("^[-•*]\\s*([A-Z][A-Z0-9_]*)\\s*(?:\\(([^)]*)\\))?$");
	/** The new dashboard's lines: "Review score: 586.8", "Unreviewed flags: 427", "Newest source: DragSim AC". */
	private static final Pattern REVIEW = Pattern.compile("(?i)^review score:\\s*(-?[\\d.,]+)$");
	private static final Pattern UNREVIEWED = Pattern.compile("(?i)^unreviewed flags:\\s*([\\d,]+)$");
	private static final Pattern SOURCE = Pattern.compile("(?i)^newest source:\\s*(.+)$");

	private DashboardLore() {
	}

	/**
	 * The player an item on the dashboard stands for, or null when it isn't a player's entry (filler, a button). A
	 * player with only ignored flags comes back with none, so the caller can leave them out.
	 */
	public static Entry read(String title, List<String> lore) {
		Matcher t = TITLE.matcher(title.trim());
		if (!t.matches()) return null;
		Entry review = review(t.group(1), lore);
		if (review != null) return review;
		List<Flag> flags = new ArrayList<>();
		boolean listed = false;
		for (String raw : lore) {
			Matcher f = FLAG.matcher(raw.trim());
			if (!f.matches()) continue;
			listed = true;
			Flag flag = new Flag(f.group(1), f.group(2) == null ? "" : f.group(2).trim());
			if (!flag.type().equals(IGNORED) && !flags.contains(flag)) flags.add(flag);
		}
		if (!listed) return null; // a name with no flag lines isn't a dashboard entry
		return new Entry(t.group(1), score(t.group(2)), List.copyOf(flags));
	}

	/** The new dashboard's entry: its review score, how many flags await review, and where the newest came from; null when it isn't one. */
	private static Entry review(String name, List<String> lore) {
		Double score = null;
		String unreviewed = "", source = "";
		for (String raw : lore) {
			String l = raw.trim();
			Matcher m;
			if ((m = REVIEW.matcher(l)).matches()) score = score(m.group(1).replace(",", ""));
			else if ((m = UNREVIEWED.matcher(l)).matches()) unreviewed = m.group(1).replace(",", "");
			else if ((m = SOURCE.matcher(l)).matches()) source = m.group(1).trim();
		}
		if (score == null) return null;
		String type = type(source);
		String detector = (unreviewed.isEmpty() ? "" : unreviewed + " unreviewed") + (unreviewed.isEmpty() || source.isEmpty() ? "" : " · ") + source;
		List<Flag> flags = type.equals(IGNORED) ? List.of() : List.of(new Flag(type, detector));
		return new Entry(name, score, flags);
	}

	/** A flag type from where it came: the anticheat's ("DragSim AC") is ANTICHEAT, as the player's own page names it; an economy one is ignored. */
	static String type(String source) {
		String s = source.toLowerCase(Locale.ROOT);
		if (s.contains("economy")) return IGNORED;
		if (s.isEmpty() || s.contains("dragsim ac") || s.contains("anticheat")) return "ANTICHEAT";
		return source.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
	}

	/** A score as written, 0 when there's none or it isn't a number. */
	private static double score(String text) {
		if (text == null) return 0;
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
