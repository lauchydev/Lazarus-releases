package dev.lauchy.lazarus.feature.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Quiz room as chat tells it: "Question #1", the question on the next line, then its three answers ("ⓐ 5 Fairy
 * Souls"), and who got it right ("[STATUE] Oruo the Omniscient: Steve answered Question #1 correctly!"). Fed each
 * chat line in turn, it holds the question being asked. Also the answers that are the same on every server of this
 * kind, to go on until your own book knows better. No Minecraft types, so it's tested on its own.
 */
final class QuizChat {
	private static final Pattern HEADING = Pattern.compile("^Question #(\\d+)$");
	private static final Pattern OPTION = Pattern.compile("^([ⓐⓑⓒ])\\s+(.+)$");
	private static final Pattern RIGHT = Pattern.compile("^\\[STATUE] Oruo the Omniscient: (?:\\[[^\\]]+] )?(\\w{1,16}) answered (?:Question #\\d+|the final question) correctly!$");

	/**
	 * Answers known from the game this one copies: the question (as {@link #normal} writes it) and every answer that
	 * has been right for it. One is given only when exactly one of them is among the three on offer.
	 */
	private static final Map<String, List<String>> KNOWN = Map.ofEntries(
			Map.entry("what is the status of the watcher", List.of("Stalker")),
			Map.entry("what is the status of bonzo", List.of("New Necromancer")),
			Map.entry("what is the status of scarf", List.of("Apprentice Necromancer")),
			Map.entry("what is the status of the professor", List.of("Professor")),
			Map.entry("what is the status of thorn", List.of("Shaman Necromancer")),
			Map.entry("what is the status of livid", List.of("Master Necromancer")),
			Map.entry("what is the status of sadan", List.of("Necromancer Lord")),
			Map.entry("what is the status of maxor, storm, goldor, and necron", List.of("The Wither Lords")),
			Map.entry("what is the status of maxor, storm, goldor and necron", List.of("The Wither Lords")),
			Map.entry("which brother is on the spider's den", List.of("Rick")),
			Map.entry("what is the name of rick's brother", List.of("Pat")),
			Map.entry("what is the name of the painter in the hub", List.of("Marco")),
			Map.entry("what is the name of the person that upgrades pets", List.of("Kat")),
			Map.entry("what is the name of the lady of the nether", List.of("Elle")),
			Map.entry("which villager in the village gives you a rogue sword", List.of("Jamie")),
			Map.entry("which of these enemies does not spawn in the spider's den", List.of("Zombie Spider", "Cave Spider", "Wither Skeleton", "Dashing Spooder", "Broodfather", "Night Spider")),
			Map.entry("which of these monsters only spawns at night", List.of("Zombie Villager", "Ghast")),
			Map.entry("which of these is not a dragon in the end", List.of("Zoomer Dragon", "Weak Dragon", "Stonk Dragon", "Holy Dragon", "Boomer Dragon", "Booger Dragon", "Older Dragon", "Elder Dragon", "Stable Dragon", "Professor Dragon")),
			Map.entry("how many fairy souls are there in jerry's workshop", List.of("5 Fairy Souls")),
			Map.entry("how many fairy souls are there in the end", List.of("12 Fairy Souls")),
			Map.entry("how many fairy souls are there in spider's den", List.of("19 Fairy Souls")),
			Map.entry("how many fairy souls are there in the farming islands", List.of("20 Fairy Souls")),
			Map.entry("how many fairy souls are there in crimson isle", List.of("29 Fairy Souls")),
			Map.entry("how many fairy souls are there in gold mine", List.of("12 Fairy Souls")),
			Map.entry("how many fairy souls are there in deep caverns", List.of("21 Fairy Souls")),
			Map.entry("how many fairy souls are there in dungeon hub", List.of("7 Fairy Souls")));

	/** The question being asked (as {@link #normal} writes it), null before one is; and its answers by letter, in the order given. */
	String question;
	final Map<String, String> options = new LinkedHashMap<>();
	private boolean heading;

	/** Takes one chat line. True when it completed a question: the third answer has just been read. */
	boolean read(String line) {
		String t = line.strip();
		if (HEADING.matcher(t).matches()) {
			heading = true;
			question = null;
			options.clear();
			return false;
		}
		if (heading && !t.isEmpty()) {
			heading = false;
			question = normal(t);
			return false;
		}
		Matcher m = OPTION.matcher(t);
		if (question != null && options.size() < 3 && m.matches()) {
			options.put(m.group(1), answer(m.group(2)));
			return options.size() == 3;
		}
		return false;
	}

	/** An answer as it's kept and compared: its label in the room ends in a full stop ("12 Fairy Souls.") that chat's doesn't. */
	static String answer(String text) {
		return text.strip().replaceAll("[.!?]+$", "").strip();
	}

	/** Who a line says answered right, or null when it says no such thing. */
	static String right(String line) {
		Matcher m = RIGHT.matcher(line.strip());
		return m.matches() ? m.group(1) : null;
	}

	/** The letter of an answer on offer ("ⓑ"), or null when it isn't one of the three. */
	String letter(String answer) {
		for (var e : options.entrySet()) if (e.getValue().equalsIgnoreCase(answer)) return e.getKey();
		return null;
	}

	/** The answer known from the game this one copies, when exactly one of its answers is on offer; else null. */
	String known() {
		if (question == null) return null;
		String found = null;
		for (String a : KNOWN.getOrDefault(question, List.of())) {
			if (letter(a) == null) continue;
			if (found != null) return null;
			found = a;
		}
		return found;
	}

	/** A question as the book keys it: lower case, no mark at its end. */
	static String normal(String q) {
		return q.replaceAll("[.?!]+$", "").trim().toLowerCase(Locale.ROOT);
	}
}
