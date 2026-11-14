package dev.lauchy.lazarus.feature.general;

/**
 * Spreads a number of clicks a second evenly over the game's 20 ticks: at 8 a second a click comes every
 * 2.5 ticks on average, so the gaps alternate 2 and 3 ticks rather than rounding to 6 or 10 a second. Never
 * more than one click a tick. Counted in twentieths of a click, so it's exact. No Minecraft types, so it's
 * tested on its own.
 */
final class ClickRate {
	/** The game's ticks a second, and so the most clicks a second. */
	private static final int TICKS = 20;
	/** Clicks owed so far, in twentieths; a click goes out each time it reaches a whole one. */
	private int owed;

	/** One tick at {@code perSecond} clicks a second: whether a click goes out now. */
	boolean tick(int perSecond) {
		owed += Math.clamp(perSecond, 0, TICKS);
		if (owed < TICKS) return false;
		owed -= TICKS;
		return true;
	}

	/** Starts afresh with a click owed, so the first tick clicks at once, as when the button has just gone down. */
	void reset() {
		owed = TICKS;
	}
}
