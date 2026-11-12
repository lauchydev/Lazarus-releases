package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Drops;

import java.util.List;

/**
 * The drop a pop-up shows on the totem animation, looked for in what you carry until it lands there. The server
 * announces a slayer boss's drop before the boss's reward lands, so a drop you didn't already carry isn't there
 * yet when its line arrives; it is found as it lands, for a few seconds after.
 */
final class Landing {
	/** How long a drop is looked for after its line: a slayer boss's reward lands within three seconds of the kill. */
	static final long WAIT_MS = 3_000;

	/** The drop looked for, null for none, and when its line arrived. */
	private String item;
	private long since;

	/** A drop's line arrived at {@code now}: looks for {@code item}, in place of any drop still looked for. */
	void expect(String item, long now) {
		this.item = item;
		since = now;
	}

	/** Stops looking: the server's own pop-up is showing, or the pop-up was switched off. */
	void cancel() {
		item = null;
	}

	/** Whether a drop is still looked for at {@code now}. */
	boolean waiting(long now) {
		return item != null && now - since <= WAIT_MS;
	}

	/**
	 * Where among {@code held} (what you carry, by name) the drop is at {@code now}; -1 while it hasn't landed, once
	 * the wait is over, or when none is looked for. Found once: the drop is no longer looked for after.
	 */
	int find(List<String> held, long now) {
		if (!waiting(now)) {
			item = null;
			return -1;
		}
		for (int i = 0; i < held.size(); i++) {
			if (Drops.isDrop(held.get(i), item)) {
				item = null;
				return i;
			}
		}
		return -1;
	}
}
