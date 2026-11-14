package dev.lauchy.lazarus.feature.staff;

/**
 * A server menu the dashboard reader closed itself. Dragsim answers that close with a close of its own for the
 * same menu, and Minecraft closes whatever screen is open on any close, which would take the roster down
 * just after it was put back; a close that only echoes this one is ignored. No Minecraft types, so it's
 * tested on its own.
 */
public record ClosedMenu(int id, long at) {
	/** How long after the reader's close the server's echo of it may come. */
	static final long ECHO_MS = 5000;

	/** Whether the server closing menu {@code closing} at {@code now} only echoes this close. */
	public boolean echoedBy(int closing, long now) {
		return closing == id && now - at >= 0 && now - at <= ECHO_MS;
	}
}
