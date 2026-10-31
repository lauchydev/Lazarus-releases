package dev.lauchy.lazarus.feature.discord;

import dev.lauchy.lazarus.core.Text;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the two lines a player writes for their Discord status ("{location}", "Purse: {purse}") into
 * what Discord shows. Kept apart from the game wiring so it can be checked on its own.
 */
final class PresenceLines {
	/** The placeholders a line can hold, as the settings list them. */
	static final String PLACEHOLDERS = "{location}, {purse}, {held}, {name}";
	/** The most Discord shows on one line of a status. */
	static final int MAX = 128;

	private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");
	private static final Pattern PURSE = Pattern.compile("(?i)\\bpurse:\\s*([\\d,.]+\\s*[kmb]?)");

	private PresenceLines() {
	}

	/**
	 * The line with each placeholder filled in, or null when it should be left off: a placeholder with
	 * nothing to show (no purse on the sidebar, an empty hand) drops the whole line, so "Holding {held}"
	 * never reads "Holding ". Unknown placeholders stay as typed. Discord turns down a line under two
	 * characters, so those are left off too, and a long one is cut to fit.
	 */
	static String fill(String template, Map<String, String> values) {
		if (template == null) return null;
		Matcher m = TOKEN.matcher(template);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String key = m.group(1).toLowerCase(Locale.ROOT);
			if (!values.containsKey(key)) {
				m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
				continue;
			}
			String value = values.get(key);
			if (value == null || value.isBlank()) return null;
			m.appendReplacement(out, Matcher.quoteReplacement(value.strip()));
		}
		m.appendTail(out);
		String line = out.toString().strip();
		if (line.length() < 2) return null;
		return line.length() > MAX ? line.substring(0, MAX - 1).strip() + "…" : line;
	}

	/** The purse from a plain sidebar line ("Purse: 12,345,678" -> "12.35M"), or null when the line isn't it. */
	static String purse(String sidebarLine) {
		Matcher m = PURSE.matcher(sidebarLine);
		if (!m.find()) return null;
		double coins = Text.number(m.group(1));
		return coins < 0 ? m.group(1).trim() : Text.compact(coins);
	}
}
