package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB colour, picked from a fixed palette in the menu. */
public final class ColorSetting extends Setting<Integer> {
	public static final int[] PALETTE = {
			0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF00AA00, 0xFF55FFFF,
			0xFF00AAAA, 0xFF5555FF, 0xFFAA00AA, 0xFFFF55FF, 0xFFFFFFFF, 0xFFAAAAAA
	};

	public ColorSetting(String key, String name, String description, int fallback) {
		super(key, name, description, fallback);
	}

	public int argb() {
		return value;
	}

	public void cycle(int direction) {
		int at = 0;
		for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == value) at = i;
		value = PALETTE[Math.floorMod(at + direction, PALETTE.length)];
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(String.format("#%08X", value));
	}

	@Override
	public void load(JsonElement json) {
		if (!json.isJsonPrimitive()) return;
		try {
			value = (int) Long.parseLong(json.getAsString().replace("#", ""), 16);
		} catch (NumberFormatException ignored) {
			// Keep the default.
		}
	}
}
