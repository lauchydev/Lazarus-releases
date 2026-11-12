package dev.lauchy.lazarus.feature.staff;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The anticheat's lines in staff chat, read into what they say about a player:
 * <ul>
 * <li>"[FLAG REVIEW] JLLM has priority score 67.5. Use /staff to review the evidence." — a {@link Priority};</li>
 * <li>"DragSim AC » Avxyl's position was updated for being on a ghost block! (minestom-3ae7…, -669, 10, -274) [11]"
 * — a {@link Setback}, the count in brackets being the anticheat's running count;</li>
 * <li>"DragSim AC » _nephis joined using Lunarclient:v2.23.14-2645,fabric!" — a {@link Brand};</li>
 * <li>"[DragSim AC] DeloxR Click rate: CPS=14.5 (review only)" — a {@link Detection}, one check the anticheat caught
 * a player on, sent to staff to look at.</li>
 * </ul>
 * The anticheat's answer to {@code /dragsimac info} starts "[DragSim AC]" too ("[DragSim AC] Avxyl checks=176; vl=0; …")
 * and is none of these.
 * No Minecraft types, so it's tested on its own.
 */
public final class AcLine {
	/** What one line says. */
	public sealed interface Line permits Priority, Setback, Brand, Detection {
		String player();
	}

	/** A player's priority score for review. */
	public record Priority(String player, double score) implements Line {
	}

	/** A position setback: why ("being on a ghost block"), where ("&lt;instance&gt;, x, y, z") and the running count (1 when unsaid). */
	public record Setback(String player, String why, String place, int count) implements Line {
	}

	/** The client a player joined with. */
	public record Brand(String player, String client) implements Line {
	}

	/** One check the anticheat caught a player on ("Click rate") and what it measured ("CPS=14.5"). */
	public record Detection(String player, String check, String detail) implements Line {
	}

	private static final Pattern PRIORITY = Pattern.compile("^\\[FLAG REVIEW]\\s+(\\w{1,16}) has priority score (-?\\d+(?:\\.\\d+)?)\\.?(?:\\s+Use /staff to review the evidence\\.?)?$");
	private static final Pattern SETBACK = Pattern.compile("^DragSim AC\\s*»\\s*(\\w{1,16})'s position was updated(?: for (.+?))?!?\\s*\\(([^,)]+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)(?:\\s*\\[(\\d+)])?$");
	private static final Pattern BRAND = Pattern.compile("^DragSim AC\\s*»\\s*(\\w{1,16}) joined using (.+?)!?$");
	/** The check is words only ("Click rate"), so the info answer's "checks=176; …" is never read as one; the note in brackets ends it. */
	private static final Pattern DETECTION = Pattern.compile("^\\[DragSim AC]\\s+(\\w{1,16})\\s+([A-Za-z][A-Za-z -]{0,39}?):\\s*(.*?)\\s*\\((?i:review only)\\)$");

	private AcLine() {
	}

	/** What a chat line says, or null when it isn't one of the anticheat's. */
	public static Line parse(String line) {
		String l = line.trim();
		Matcher m;
		if ((m = PRIORITY.matcher(l)).matches()) return new Priority(m.group(1), Double.parseDouble(m.group(2)));
		if ((m = SETBACK.matcher(l)).matches()) {
			String place = m.group(3).trim() + ", " + m.group(4) + ", " + m.group(5) + ", " + m.group(6);
			int count = m.group(7) == null ? 1 : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(m.group(7)));
			return new Setback(m.group(1), m.group(2) == null ? "" : m.group(2).trim(), place, count);
		}
		if ((m = BRAND.matcher(l)).matches()) return new Brand(m.group(1), m.group(2).trim());
		if ((m = DETECTION.matcher(l)).matches()) return new Detection(m.group(1), m.group(2).trim(), m.group(3).trim());
		return null;
	}

	/**
	 * Whether a line is any of the anticheat's, read or not ("DragSim AC » …", "[FLAG REVIEW] …", a detection), for
	 * hiding it from chat. Its other "[DragSim AC]" lines answer a staff member's own command, so they stay.
	 */
	public static boolean isAnticheat(String line) {
		String l = line.trim();
		return l.startsWith("[FLAG REVIEW]") || l.startsWith("DragSim AC") || isDetection(l);
	}

	/** Whether a line is a detection, which answers no command. */
	public static boolean isDetection(String line) {
		return DETECTION.matcher(line.trim()).matches();
	}
}
