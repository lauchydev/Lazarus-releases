package dev.lauchy.lazarus.feature.general;

/**
 * What a bind does in the moments after it clicks in a server menu. The server answers the click by leaving its menu
 * open, closing it, or opening it again refreshed a moment later (the pet menu does, once the pet is out), so for a
 * short while any menu that shows is closed, the first once the server has had the click. No Minecraft types, so
 * it's tested on its own.
 */
final class AfterClick {
	/** Ticks after the click before a menu is closed, so the click reaches the server first. */
	static final int FIRST_CLOSE_TICKS = 4;
	/** Ticks after the click that a menu the server opens again is still closed. */
	static final int WINDOW_TICKS = 30;

	/** Keep watching, close the menu on screen, or stop watching. */
	enum Act {WAIT, CLOSE, DONE}

	private AfterClick() {
	}

	/**
	 * What to do {@code ticks} ticks after the click: a server menu on screen is closed once the first close is due.
	 * Watching stops when the window runs out, when another bind has opened a menu since ({@code stillMine} false), or
	 * when you've opened a screen of your own (chat, your inventory, the pause menu).
	 */
	static Act act(int ticks, boolean serverMenu, boolean ownScreen, boolean stillMine) {
		if (!stillMine || ownScreen || ticks > WINDOW_TICKS) return Act.DONE;
		return serverMenu && ticks >= FIRST_CLOSE_TICKS ? Act.CLOSE : Act.WAIT;
	}
}
