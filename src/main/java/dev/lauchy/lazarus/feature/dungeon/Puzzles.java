package dev.lauchy.lazarus.feature.dungeon;

/** The server's puzzle room announcements: "PUZZLE! Name - flavour", "PUZZLE SOLVED! Name", "PUZZLE FAIL! Name". */
final class Puzzles {
	private Puzzles() {
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
