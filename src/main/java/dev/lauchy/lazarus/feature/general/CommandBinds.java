package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Keys that each run a command or send a chat line. A bind can include Ctrl, Alt or Shift: hold them
 * while picking the key, e.g. Alt + W. The mod's own binds come first (wardrobe, look-up, and the staff
 * ones: history, captcha, server hop), each with its command there to change, and eight more are free to
 * set. In a command, {@code %p} is the player you're looking at and {@code %me} is you.
 */
public final class CommandBinds extends Feature {
	/** The look-up bind with its command left empty: the staff panel for staff, the profile for everyone else. */
	static final String STAFF_LOOKUP = "/lazarus staff %p", PLAYER_LOOKUP = "/l %p";
	private static final int SLOTS = 8;
	/** The mod's own binds, before the free slots. */
	private static final int WARDROBE = 0, LOOKUP = 1, HISTORY = 2, CAPTCHA = 3, HOP = 4, BUILT_IN = 5;

	private final KeySetting[] keys = new KeySetting[BUILT_IN + SLOTS];
	private final TextSetting[] commands = new TextSetting[BUILT_IN + SLOTS];
	private final boolean[] held = new boolean[BUILT_IN + SLOTS];

	public CommandBinds() {
		super("commandBinds", "Command Binds", "Bind keys (Alt + key combos too) to commands like /warp end or /pets; the mod's own binds live here too.", Category.GENERAL, true);
		builtIn(WARDROBE, "wardrobe", "Wardrobe", "Opens your wardrobe.", GLFW.GLFW_KEY_W, "/wardrobe");
		builtIn(LOOKUP, "lookup", "Look-up", "Opens the player you're looking at, through walls and at any distance. Empty: the staff panel for staff, the profile for everyone else.", GLFW.GLFW_KEY_E, "");
		builtIn(HISTORY, "history", "History (staff)", "The punishment history of the player you're looking at.", GLFW.GLFW_KEY_H, "/lazarus history %p");
		builtIn(CAPTCHA, "captcha", "Captcha (staff)", "Sends a captcha to the player you're looking at.", GLFW.GLFW_KEY_C, "/lazarus captcha %p");
		builtIn(HOP, "hop", "Server hop (staff)", "Puts the server buttons in chat.", KeySetting.NONE, "/lazarus hop");
		for (int i = 0; i < SLOTS; i++) {
			commands[BUILT_IN + i] = add(new TextSetting("command" + i, "Bind " + (i + 1) + " command", "Starts with / for a command, otherwise sent as chat. %p is the player you're looking at.", "", 100));
			keys[BUILT_IN + i] = add(new KeySetting("key" + i, "Bind " + (i + 1) + " key", "Hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
		}
	}

	private void builtIn(int i, String id, String name, String what, int key, String command) {
		commands[i] = add(new TextSetting(id + "Command", name + " command", "What the " + name.toLowerCase(java.util.Locale.ROOT) + " bind runs. " + what, command, 100));
		keys[i] = add(new KeySetting(id + "Key", name + " key", what + " Right-click to clear.", key == KeySetting.NONE ? KeySetting.NONE : KeySetting.of(key, KeySetting.ALT)));
	}

	@Override
	public void init() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) for (KeySetting k : keys) Keys.suppressVanilla(k); // Alt + E must not open your inventory
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean live = enabled() && mc.screen == null && mc.getConnection() != null && mc.player != null;
			for (int i = 0; i < keys.length; i++) {
				boolean down = live && keys[i].bound() && Keys.down(keys[i]);
				if (down && !held[i]) run(mc, i == LOOKUP ? lookup(commands[i].get(), Staff.is()) : commands[i].get());
				held[i] = down;
			}
		});
	}

	/** The look-up command: what's set, or when it's left empty, the staff panel for staff and the profile for anyone else. */
	static String lookup(String set, boolean staff) {
		return set.isBlank() ? (staff ? STAFF_LOOKUP : PLAYER_LOOKUP) : set;
	}

	private static void run(Minecraft mc, String text) {
		text = text.trim();
		if (text.isEmpty()) return;
		if (text.contains("%p")) {
			String target = Players.lookedAt(mc);
			if (target == null) {
				Message.lazarus().error("Look at a player.").send();
				return;
			}
			text = text.replace("%p", target);
		}
		text = text.replace("%me", Players.me());
		if (text.startsWith("/")) mc.getConnection().sendCommand(text.substring(1));
		else mc.getConnection().sendChat(text);
	}
}
