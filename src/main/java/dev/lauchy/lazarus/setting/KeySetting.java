package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A key or mouse button, optionally with Shift, Ctrl or Alt held (GLFW modifier bits), or
 * {@link #NONE}. Stored as one number: the GLFW key code in the low 16 bits (a mouse button
 * as {@link #MOUSE} plus its number, above any key code), the modifiers above it, so binds
 * saved before modifiers existed load unchanged.
 */
public final class KeySetting extends Setting<Integer> {
	public static final int NONE = -1;
	public static final int SHIFT = 1, CTRL = 2, ALT = 4;
	/** Where mouse buttons sit among key codes: button 3 (the first side button) is MOUSE + 3. */
	public static final int MOUSE = 0x4000;

	public KeySetting(String key, String name, String description) {
		this(key, name, description, NONE);
	}

	public KeySetting(String key, String name, String description, int fallback) {
		super(key, name, description, fallback);
	}

	public static int of(int key, int mods) {
		return key < 0 ? NONE : key | (mods & (SHIFT | CTRL | ALT)) << 16;
	}

	/** The key code for a GLFW mouse button (0 left, 1 right, 2 middle, 3 and 4 the side buttons). */
	public static int mouse(int button) {
		return MOUSE + button;
	}

	/** Whether a key code is a mouse button. */
	public static boolean isMouse(int key) {
		return key >= MOUSE && key != NONE;
	}

	/** The GLFW mouse button a mouse key code stands for. */
	public static int button(int key) {
		return key - MOUSE;
	}

	public boolean bound() {
		return value != NONE;
	}

	/** The GLFW key code without modifiers. */
	public int key() {
		return value == NONE ? NONE : value & 0xFFFF;
	}

	public int mods() {
		return value == NONE ? 0 : value >>> 16;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) value = json.getAsInt();
	}
}
