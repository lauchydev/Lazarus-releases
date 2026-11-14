package dev.lauchy.lazarus.feature.dungeon;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dungeon run's score as the sidebar shows it, and the one moment in a run it first reaches a target.
 * No game types, so it's tested on its own.
 */
public final class RunScore {
	/** The sidebar's line for how far the run is: "Cleared: 43% (272)", the score in the brackets. */
	private static final Pattern LINE = Pattern.compile("Cleared:\\s*\\d+%\\s*\\((\\d+)\\)");

	/** Whether this run's score has already been told. */
	private boolean told;

	/** The run's score from the sidebar, or -1 when it shows none. */
	public static int read(List<String> sidebar) {
		for (String line : sidebar) {
			Matcher m = LINE.matcher(line);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return -1;
	}

	/**
	 * Takes the score as it stands now ({@code -1} when the sidebar shows none) and says whether this is the moment
	 * it first reached {@code target} in this run. A score that dips under the target and comes back isn't told
	 * again; leaving the dungeon, or a score back under half the target, is a new run.
	 */
	public boolean reached(int score, boolean inDungeon, int target) {
		if (!inDungeon || score >= 0 && score < target / 2) told = false;
		if (told || !inDungeon || score < target) return false;
		told = true;
		return true;
	}
}
