package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Opens the staff desk ({@link StaffDeskScreen}) from {@code /lazarus online}, a key or the Lazarus menu's
 * Staff page. It is for Dragsim staff with Staff Tools on, the same as the rest of the staff drawer.
 */
public final class OnlineRoster {
	/** Whether the key was down last tick, so holding it opens the roster once. */
	private static boolean held;

	private OnlineRoster() {
	}

	/** Registers the command and the key, and starts sharing with other staff; called once by {@link StaffTools}. */
	static void init(KeySetting key) {
		FlagSync.init();
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("online").executes(c -> {
			// The chat box closes itself after running a command, which would close a screen opened now:
			// schedule always waits for the next pass of the game's task queue, after it has closed.
			Minecraft mc = Minecraft.getInstance();
			mc.schedule(OnlineRoster::open);
			return 1;
		})));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean down = StaffTools.on() && mc.screen == null && mc.player != null && Keys.down(key);
			if (down && !held) open();
			held = down;
		});
	}

	/** Whether this account may open the roster: Dragsim staff with Staff Tools on. */
	public static boolean allowed() {
		return StaffTools.on() && Staff.is();
	}

	/** Opens the roster, or says why it can't be opened. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		StaffDeskScreen.open(null);
	}
}
