package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lauchy.lazarus.setting.KeySetting;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the growing bind lists share (command, pet and wardrobe binds): there is always one empty bind after
 * the last one set, filling it brings the next, a saved file brings back every bind it holds up to the last one
 * in use, and binds past the free allowance are Lazarus+. No Minecraft types, so it's tested on its own.
 */
final class Binds {
	private Binds() {
	}

	/**
	 * How many binds a saved file holds, from the settings {@code numbered} matches (its first group the number,
	 * counted from 0): every bind up to the highest one in use (a key bound or a command written), then one empty
	 * bind after it. Every bind is saved, the empty one after the last too, so counting the empty ones would add a
	 * bind each time the game starts.
	 */
	static int saved(JsonObject saved, Pattern numbered) {
		int highest = -1;
		for (Map.Entry<String, JsonElement> e : saved.entrySet()) {
			Matcher m = numbered.matcher(e.getKey());
			if (m.matches() && inUse(e.getValue())) highest = Math.max(highest, Integer.parseInt(m.group(1)));
		}
		return highest + 2;
	}

	/** Whether a saved key or command is in use: a key that's bound, or a command that isn't blank. */
	private static boolean inUse(JsonElement value) {
		if (!value.isJsonPrimitive()) return false;
		JsonPrimitive p = value.getAsJsonPrimitive();
		return p.isNumber() ? p.getAsInt() != KeySetting.NONE : p.isString() && !p.getAsString().isBlank();
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
