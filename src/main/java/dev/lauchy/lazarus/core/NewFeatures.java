package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * A new feature starts switched off: it's on only once the player turns it on, and that is saved. 1.42.0 broke
 * that: everything it added was on from the start, and so was saved as on in every settings file without anyone
 * choosing it. Those are switched off once, the first time a settings file from before this rule is read; after
 * that the file is marked and whatever is switched on in it stays on. No game types, so it's tested on its own.
 */
public final class NewFeatures {
	/** The features 1.42.0 added switched on. */
	public static final Set<String> STARTED_ON = Set.of("dragonHitbox", "roomTrails", "roomTracker", "doorHighlight", "boulder", "terminalStatus", "iceFill",
			"creeperBeams", "teleportMaze", "arrowAlign", "lights", "teammateOutline", "roomGlow", "terminalHighlight");
	/** The mark a settings file carries once those have been switched off in it. */
	public static final String MARK = "newFeaturesOff";

	private NewFeatures() {
	}

	/** Whether a settings file is from before new features started off, and so has those to switch off, once. */
	public static boolean toSwitchOff(JsonObject settings) {
		return !settings.has(MARK);
	}
}
