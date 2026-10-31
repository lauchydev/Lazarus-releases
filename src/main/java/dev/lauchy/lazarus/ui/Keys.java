package dev.lauchy.lazarus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lauchy.lazarus.setting.KeySetting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Reading raw key state, for binds that work whether or not a KeyMapping would fire. */
public final class Keys {
	private Keys() {
	}

	public static boolean down(int key) {
		if (key == KeySetting.NONE) return false;
		if (KeySetting.isMouse(key)) return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), KeySetting.button(key)) == GLFW.GLFW_PRESS;
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
	}

	/**
	 * Whether the bind is held: its key, and exactly its Ctrl and Alt (so Alt+W never also
	 * fires a plain W bind). Shift only counts when the bind asks for it, since you are
	 * often sneaking anyway.
	 */
	public static boolean down(KeySetting bind) {
		if (!bind.bound() || !down(bind.key())) return false;
		int want = bind.mods(), held = held();
		if ((want & KeySetting.SHIFT) != 0 && (held & KeySetting.SHIFT) == 0) return false;
		int strict = KeySetting.CTRL | KeySetting.ALT;
		return (want & strict) == (held & strict);
	}

	/** The modifiers held right now, as {@link KeySetting} bits. */
	public static int held() {
		int m = 0;
		if (down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT)) m |= KeySetting.SHIFT;
		if (down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL)) m |= KeySetting.CTRL;
		if (down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT)) m |= KeySetting.ALT;
		return m;
	}

	/**
	 * Keeps vanilla from also acting on a Ctrl or Alt combo's key, e.g. Alt + E opening the
	 * inventory: while the combo's modifiers are held, presses of game key mappings on the
	 * same key are dropped. Call from the start of the client tick, before vanilla reads them.
	 */
	public static void suppressVanilla(KeySetting bind) {
		int strict = bind.mods() & (KeySetting.CTRL | KeySetting.ALT);
		if (!bind.bound() || strict == 0 || (held() & strict) != strict) return;
		for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
			InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(m);
			boolean mouse = KeySetting.isMouse(bind.key());
			if (k.getType() != (mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM)) continue;
			if (k.getValue() != (mouse ? KeySetting.button(bind.key()) : bind.key())) continue;
			while (m.consumeClick()) {
				// dropped
			}
			m.setDown(false);
		}
	}

	/** Whether {@code key} is itself Shift, Ctrl, Alt or Super, which can't take modifiers of their own. */
	public static boolean isModifier(int key) {
		return key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER;
	}

	public static String name(int key) {
		if (key == KeySetting.NONE) return "None";
		if (KeySetting.isMouse(key)) return InputConstants.Type.MOUSE.getOrCreate(KeySetting.button(key)).getDisplayName().getString();
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	/** "Alt + W". */
	public static String name(KeySetting bind) {
		if (!bind.bound()) return "None";
		int m = bind.mods();
		return ((m & KeySetting.CTRL) != 0 ? "Ctrl + " : "") + ((m & KeySetting.ALT) != 0 ? "Alt + " : "")
				+ ((m & KeySetting.SHIFT) != 0 ? "Shift + " : "") + name(bind.key());
	}
}
