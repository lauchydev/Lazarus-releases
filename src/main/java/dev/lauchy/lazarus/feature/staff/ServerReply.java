package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A server command's answer as the staff desk shows it: the lines it sent, less the decoration (rules of "▬",
 * blank lines, "[Back to staff commands]"), with the command and when it was asked. Answers whose shape Lazarus
 * doesn't know yet are shown as they came. No Minecraft types, so it's tested on its own.
 */
record ServerReply(String command, List<String> lines, long at) {
	/** A line with no letters or digits at all: a rule, a divider, a blank. */
	private static final Pattern DECORATION = Pattern.compile("^[^\\p{L}\\p{N}]*$");

	/** The answer to {@code command}, asked at {@code at}, with its decoration taken out. */
	static ServerReply of(String command, List<String> raw, long at) {
		return new ServerReply(command, rows(raw), at);
	}

	/** Whether the server said anything at all. */
	boolean answered() {
		return !lines.isEmpty();
	}

	/** The lines worth showing, trimmed. */
	static List<String> rows(List<String> raw) {
		List<String> out = new ArrayList<>();
		for (String text : raw) {
			for (String line : text.split("\\R")) {
				String l = line.strip();
				if (l.isEmpty() || DECORATION.matcher(l).matches() || l.equalsIgnoreCase("[Back to staff commands]")) continue;
				out.add(l);
			}
		}
		return List.copyOf(out);
	}
}
