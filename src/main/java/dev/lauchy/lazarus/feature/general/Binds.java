package dev.lauchy.lazarus.feature.general;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the growing bind lists share (command, pet and wardrobe binds): there is always one empty bind after
 * the last one set, filling it brings the next, a saved file brings back every bind it holds, and binds past
 * the free allowance are Lazarus+. No Minecraft types, so it's tested on its own.
 */
final class Binds {
	private Binds() {
	}

	/**
	 * How many binds a saved file holds, from the numbers on the settings {@code numbered} matches (its first
	 * group the number, counted from 0): one past the highest, and at least one.
	 */
	static int saved(Collection<String> savedKeys, Pattern numbered) {
		int highest = -1;
		for (String key : savedKeys) {
			Matcher m = numbered.matcher(key);
			if (m.matches()) highest = Math.max(highest, Integer.parseInt(m.group(1)));
		}
		return Math.max(1, highest + 2);
	}

	/** Whether the last bind is in use, so another should follow it. */
	static boolean wantsAnother(boolean lastKeyBound, String lastCommand) {
		return lastKeyBound || !lastCommand.isBlank();
	}

	/** Whether bind number {@code i} (from 0) is past the free allowance of {@code free}, and so Lazarus+. */
	static boolean plus(int i, int free) {
		return i >= free;
	}
}
