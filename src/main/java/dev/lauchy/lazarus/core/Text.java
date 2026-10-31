package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** String helpers for reading server text. */
public final class Text {
	private static final Pattern CODES = Pattern.compile("§.");

	private Text() {
	}

	/** Plain text with colour codes removed and whitespace trimmed. */
	public static String plain(String s) {
		return s == null ? "" : CODES.matcher(s).replaceAll("").trim();
	}

	public static String plain(Component c) {
		return c == null ? "" : plain(c.getString());
	}

	public static String lower(String s) {
		return plain(s).toLowerCase(Locale.ROOT);
	}

	/** Parses "1,234", "12.5k", "3m" style numbers; returns -1 when it isn't one. */
	public static double number(String s) {
		String t = s.trim().replace(",", "").toLowerCase(Locale.ROOT);
		if (t.isEmpty()) return -1;
		double mul = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k') mul = 1e3;
		else if (last == 'm') mul = 1e6;
		else if (last == 'b') mul = 1e9;
		if (mul != 1) t = t.substring(0, t.length() - 1);
		try {
			return Double.parseDouble(t) * mul;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** 1234567 -> "1.23M". */
	public static String compact(double n) {
		double a = Math.abs(n);
		if (a >= 1e9) return trim(n / 1e9) + "B";
		if (a >= 1e6) return trim(n / 1e6) + "M";
		if (a >= 1e3) return trim(n / 1e3) + "k";
		return String.valueOf(Math.round(n));
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		return s.replaceAll("\\.?0+$", "");
	}

	/** 125 -> "2:05". */
	public static String clock(long seconds) {
		if (seconds < 0) seconds = 0;
		long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
