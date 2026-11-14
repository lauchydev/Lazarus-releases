package dev.lauchy.lazarus.feature.dungeon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's lines for the Floor VII gates: "Steve activated a terminal! (3/7)", "Steve activated a lever! (1/2)",
 * "Steve completed a device! (1/1)". No Minecraft types, so it's tested on its own.
 */
final class TerminalProgress {
	/** What was done. */
	enum Kind {
		TERMINAL("Terminal"), LEVER("Lever"), DEVICE("Device");

		final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	/** One thing done: who did it, what, and how many of that kind are done of how many. */
	record Done(String who, Kind kind, int done, int of) {
	}

	private static final Pattern LINE = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w{1,16}) (activated a terminal|activated a lever|completed a device)! \\((\\d+)/(\\d+)\\).*");

	private TerminalProgress() {
	}

	/** What a chat line says was done, or null when it isn't one of these. */
	static Done read(String line) {
		Matcher m = LINE.matcher(line.trim());
		if (!m.matches()) return null;
		Kind kind = m.group(2).contains("terminal") ? Kind.TERMINAL : m.group(2).contains("lever") ? Kind.LEVER : Kind.DEVICE;
		return new Done(m.group(1), kind, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
	}
}
