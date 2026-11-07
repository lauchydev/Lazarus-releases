package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The punishment ladder: each rule of the server's rulebook with the duration for a first, second,
 * third… offence, read from a Staff Tools setting written as {@code 1.0 Cheating: 7d/30d/90d/1y; 1.6
 * Duping: perm; 1.2 Mass Rule Breaking: set}. A rule whose steps are {@code set} leaves the duration
 * to the staff member. Pure, so it is tested without the game.
 */
public final class Ladder {
	private Ladder() {
	}

	/** One rule: its code, its name and the duration of each offence in turn (empty when staff set it themselves). */
	public record Rule(String code, String name, List<String> steps) {
		/** The reason sent with the punishment, the way Dragsim staff write it: {@code 1.0 - Cheating Through Unfair Advantages}. */
		public String reason() {
			return code + " - " + name;
		}

		/** Whether the duration is left to the staff member ("set punishment"). */
		public boolean set() {
			return steps.isEmpty();
		}

		/** The chip label: the code and as much of the name as fits in a short chip. */
		public String chip() {
			String[] words = name.split(" ");
			StringBuilder out = new StringBuilder(words[0]);
			for (int i = 1; i < words.length && out.length() + 1 + words[i].length() <= 13; i++) out.append(' ').append(words[i]);
			return code + " " + out;
		}
	}

	/** The rules in a setting's text, in the order written; a malformed entry is skipped. */
	public static List<Rule> parse(String text) {
		List<Rule> rules = new ArrayList<>();
		if (text == null) return rules;
		for (String entry : text.split(";")) {
			int colon = entry.lastIndexOf(':');
			if (colon < 0) continue;
			String head = entry.substring(0, colon).trim(), tail = entry.substring(colon + 1).trim();
			int space = head.indexOf(' ');
			if (space < 0 || head.isEmpty()) continue;
			String code = head.substring(0, space).trim(), name = head.substring(space + 1).trim();
			if (code.isEmpty() || name.isEmpty()) continue;
			List<String> steps = tail.equalsIgnoreCase("set") || tail.isEmpty() ? List.of()
					: Arrays.stream(tail.split("/")).map(String::trim).filter(s -> !s.isEmpty()).toList();
			rules.add(new Rule(code, name, steps));
		}
		return rules;
	}

	/** "1st", "2nd", "3rd", "4th"… for the offence chips. */
	public static String ordinal(int n) {
		String suffix = n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return n + suffix;
	}

	/** The rule whose code matches, ignoring case, or null. */
	public static Rule find(List<Rule> rules, String code) {
		for (Rule r : rules) if (r.code.equalsIgnoreCase(code.trim())) return r;
		return null;
	}

	/** A rule's full line, the way the setting spells it, for showing what a chip stands for. */
	public static String describe(Rule r) {
		return r.reason() + " · " + (r.set() ? "set the time yourself" : String.join(" / ", r.steps).toLowerCase(Locale.ROOT));
	}
}
