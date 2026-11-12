package dev.lauchy.lazarus.feature.dungeon;

import java.util.Locale;

/**
 * Three Weirdos: three NPCs each say a line, and the reward is in the chest of whoever says one of six lines the
 * game marks as the right one's. It isn't a logic puzzle (some of the six aren't even true), so the lines are known,
 * not reasoned out. Each is matched by its telling phrases, with spelling and punctuation evened out, so a server
 * that words one a little differently still matches. No Minecraft types, so it's tested on its own.
 */
final class Weirdos {
	private Weirdos() {
	}

	/** Whether a line is one only the NPC with the reward says. */
	static boolean right(String line) {
		String s = " " + line.toLowerCase(Locale.ROOT).replace('’', '\'').replace("isn't", "is not").replace("doesn't", "does not").replace("i'm", "i am")
				.replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
		boolean allTruth = s.contains(" we are all telling the truth ");
		// "The reward is not in my chest!", alone.
		if (s.equals(" the reward is not in my chest ")) return true;
		// "At least one of them is lying, and the reward is not in X's chest!"
		if (s.contains(" at least one of them is lying ") && s.contains(" reward is not in ")) return true;
		// "My chest doesn't have the reward. We are all telling the truth."
		if (s.contains(" my chest does not have the reward ") && allTruth) return true;
		// "My chest has the reward and I'm telling the truth!"
		if (s.contains(" my chest has the reward ") && s.contains(" i am telling the truth ")) return true;
		// "The reward isn't in any of our chests."
		if (s.contains(" reward is not in any of our chests ")) return true;
		// "Both of them are telling the truth. Also, X has the reward in their chest!"
		return s.contains(" both of them are telling the truth ") && s.contains(" has the reward in ");
	}
}
