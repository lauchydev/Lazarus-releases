package dev.lauchy.lazarus.feature.dungeon;

/** The server's puzzle room announcements: "PUZZLE! Name - flavour", "PUZZLE SOLVED! Name", "PUZZLE FAIL! Name". */
final class Puzzles {
	private Puzzles() {
	}

	/** The puzzle a "PUZZLE! Name - flavour" line announces, or null for any other line. */
	static String named(String line) {
		String t = line.replace('–', '-').replace('—', '-');
		if (!t.startsWith("PUZZLE! ")) return null;
		String name = t.substring(8).split(" - |:", 2)[0].trim();
		return name.isEmpty() ? null : name;
	}

	/** Who a result line is about, and the puzzle it gives away. */
	record Result(String player, String puzzle) {
	}

	/** What a "PUZZLE SOLVED! ..." or "PUZZLE FAIL! ..." line tells: who did it and which puzzle, or null when it doesn't say. */
	static Result result(String line) {
		String rest = line.startsWith("PUZZLE SOLVED! ") ? line.substring(15) : line.startsWith("PUZZLE FAIL! ") ? line.substring(13) : null;
		if (rest == null) return null;
		String low = rest.toLowerCase(java.util.Locale.ROOT);
		String puzzle = low.contains("fooled by") ? "Three Weirdos" : low.contains("blaze") ? "Higher Or Lower" : low.contains("quiz") || low.contains("question") ? "Quiz"
				: low.contains("tic tac toe") ? "Tic Tac Toe" : low.contains("creeper") ? "Creeper Beams" : low.contains("ice path") || low.contains("silverfish") ? "Ice Path"
				: low.contains("ice fill") ? "Ice Fill" : low.contains("maze") ? "Teleport Maze" : low.contains("boulder") ? "Boulder" : null;
		if (puzzle == null) return null;
		java.util.regex.Matcher who = java.util.regex.Pattern.compile("^(?:\\[[^\\]]+] )?(\\w{1,16})\\b").matcher(rest);
		return new Result(who.find() ? who.group(1) : "", puzzle);
	}

	static boolean entered(String line, String puzzle) {
		String t = line.replace('–', '-').replace('—', '-');
		return t.equals("PUZZLE! " + puzzle) || t.startsWith("PUZZLE! " + puzzle + " -") || t.startsWith("PUZZLE! " + puzzle + ":");
	}

	/** This puzzle finished, or another one began (you left the room). */
	static boolean left(String line, String puzzle) {
		return line.startsWith("PUZZLE SOLVED! " + puzzle) || line.startsWith("PUZZLE FAIL! " + puzzle)
				|| line.startsWith("PUZZLE! ") && !entered(line, puzzle);
	}
}
