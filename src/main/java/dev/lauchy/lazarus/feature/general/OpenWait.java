package dev.lauchy.lazarus.feature.general;

/**
 * When a bind may click in a menu that turns a click down if it comes too soon after the menu opens (the wardrobe
 * does, against duping). The wait counts from when the open window first appeared, so a page that comes as a new
 * window waits again. A click the server still turns down goes once more, a full wait after it said so. No
 * Minecraft types, so it's tested on its own.
 */
final class OpenWait {
	/** How long after a click the server's "too soon" can come and still be about that click. */
	static final long ANSWER_MS = 2000;
	/** No window seen yet. */
	private static final int NONE = Integer.MIN_VALUE;

	private final long wait;
	/** The window open now, and when the wait for it started: when it first appeared, or when the server last said too soon. */
	private int window = NONE;
	private long since;
	/** When the pick's click went, or -1 when no click is waiting on an answer, and whether it has gone again already. */
	private long clickedAt = -1;
	private boolean again;

	/** A wait of {@code wait} milliseconds after each window opens; 0 never waits. */
	OpenWait(long wait) {
		this.wait = wait;
	}

	/** Starts a new pick: no window seen, no click waiting on an answer. */
	void reset() {
		window = NONE;
		clickedAt = -1;
		again = false;
	}

	/** Notes the window open at {@code now}; one not seen before starts the wait. */
	void seen(int window, long now) {
		if (window == this.window) return;
		this.window = window;
		since = now;
	}

	/** Whether a click may go at {@code now}. */
	boolean ready(long now) {
		return window != NONE && now - since >= wait;
	}

	/** Notes a click going at {@code now}, so a "too soon" just after is known to be about it. */
	void clicked(long now) {
		clickedAt = now;
	}

	/**
	 * Whether the server's "too soon", arriving at {@code now}, is about this pick's click, which should then go
	 * again: once a pick, within {@link #ANSWER_MS} of the click. The wait starts over from now.
	 */
	boolean turnedDown(long now) {
		if (clickedAt < 0 || again || now - clickedAt > ANSWER_MS) return false;
		again = true;
		clickedAt = -1;
		since = now;
		return true;
	}
}
