package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keys that each run a command or send a chat line. A bind can include Ctrl, Alt or Shift: hold them
 * while picking the key, e.g. Alt + W. The mod's own binds come first (wardrobe, look-up, and the staff
 * ones: history, captcha, server hop), each with its command there to change, then the free binds: there is
 * always one empty bind after the last one set, and filling it brings the next. The first {@link #FREE} are
 * everyone's; the rest are Lazarus+. In a command, {@code %p} is the player you're looking at and
 * {@code %me} is you.
 */
public final class CommandBinds extends Feature {
	/** The look-up bind with its command left empty: the staff panel for staff, the profile for everyone else. */
	static final String STAFF_LOOKUP = "/lazarus staff %p", PLAYER_LOOKUP = "/l %p";
	/** How many free binds everyone gets; the rest are Lazarus+. */
	static final int FREE = 5;
	/** The mod's own binds, before the free ones. */
	private static final int LOOKUP = 1, BUILT_IN = 5;
	/** A free bind's saved key or command, numbered from 0: "command3", "key3". */
	private static final Pattern SAVED = Pattern.compile("^(?:command|key)(\\d{1,6})$");

	/** Every bind's key and command, the mod's own first, then the free binds; the lists grow together. */
	private final List<KeySetting> keys = new ArrayList<>();
	private final List<TextSetting> commands = new ArrayList<>();
	/** Whether each bind's key was down last tick, so holding it runs the command once. */
	private final List<Boolean> held = new ArrayList<>();

	public CommandBinds() {
		super("commandBinds", "Command Binds", "Bind keys (Alt + key combos too) to commands like /warp end or /pets; the mod's own binds live here too. Five free, the rest Lazarus+.", Category.GENERAL, true);
		builtIn("wardrobe", "Wardrobe", "Opens your wardrobe.", GLFW.GLFW_KEY_W, "/wardrobe");
		builtIn("lookup", "Look-up", "Opens the player you're looking at, through walls and at any distance. Empty: the staff desk for staff, the profile for everyone else.", GLFW.GLFW_KEY_E, "");
		builtIn("history", "History (staff)", "The punishment history of the player you're looking at.", GLFW.GLFW_KEY_H, "/lazarus history %p");
		builtIn("captcha", "Captcha (staff)", "Sends a captcha to the player you're looking at.", GLFW.GLFW_KEY_C, "/lazarus captcha %p");
		builtIn("hop", "Server hop (staff)", "Puts the server buttons in chat.", KeySetting.NONE, "/lazarus hop");
		free();
	}

	/** One of the mod's own binds, with its command there to change. */
	private void builtIn(String id, String name, String what, int key, String command) {
		commands.add(add(new TextSetting(id + "Command", name + " command", "What the " + name.toLowerCase(java.util.Locale.ROOT) + " bind runs. " + what, command, 100)));
		keys.add(add(new KeySetting(id + "Key", name + " key", what + " Right-click to clear.", key == KeySetting.NONE ? KeySetting.NONE : KeySetting.of(key, KeySetting.ALT))));
		held.add(false);
	}

	/** Adds the next free bind, numbered after the ones there are; past the allowance it's Lazarus+. */
	private void free() {
		int i = keys.size() - BUILT_IN;
		TextSetting command = add(new TextSetting("command" + i, "Bind " + (i + 1) + " command", "Starts with / for a command, otherwise sent as chat. %p is the player you're looking at.", "", 100));
		KeySetting key = add(new KeySetting("key" + i, "Bind " + (i + 1) + " key", "Hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
		if (Binds.plus(i, FREE)) {
			command.plusOnly();
			key.plusOnly();
		}
		commands.add(command);
		keys.add(key);
		held.add(false);
	}

	/** The file holds however many free binds were saved: they're all added before their values are read. */
	@Override
	protected void loading(JsonObject saved) {
		int want = savedBinds(saved);
		while (keys.size() - BUILT_IN < want) free();
	}

	/** How many free binds a saved file holds: up to the highest "commandN" written or "keyN" bound, then an empty one. */
	static int savedBinds(JsonObject saved) {
		return Binds.saved(saved, SAVED);
	}

	/** Whether the last free bind is in use (a key bound or a command written), so another should follow it. */
	static boolean wantsAnother(boolean lastKeyBound, String lastCommand) {
		return Binds.wantsAnother(lastKeyBound, lastCommand);
	}

	@Override
	public void init() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) for (KeySetting k : keys) if (usable(k)) Keys.suppressVanilla(k); // Alt + E must not open your inventory
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// A free bind just filled in (in the menu, say) brings the next, so there's always one to set.
			int last = keys.size() - 1;
			if (wantsAnother(keys.get(last).bound(), commands.get(last).get())) free();
			boolean live = enabled() && mc.screen == null && mc.getConnection() != null && mc.player != null;
			for (int i = 0; i < keys.size(); i++) {
				boolean down = live && usable(keys.get(i)) && Keys.down(keys.get(i));
				if (down && !held.get(i)) run(mc, i == LOOKUP ? lookup(commands.get(i).get(), Staff.is()) : commands.get(i).get());
				held.set(i, down);
			}
		});
	}

	/** Whether a bind's key may run: bound, and not a Lazarus+ bind on an account without Lazarus+. */
	private static boolean usable(KeySetting key) {
		return key.bound() && (!key.plus() || Plus.unlocked());
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
