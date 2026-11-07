package dev.lauchy.lazarus.feature.general;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Holds the sprint key down for you while you walk forward. The game still decides whether you
 * can sprint (hunger, sneaking, blindness, using an item), so this never does anything the key
 * itself couldn't. When you stop, or the feature goes off, the key goes back to what your finger says.
 */
public final class AutoSprint extends Feature {
	/** Whether the last tick pressed the key, so letting go is done once and never fights a real press. */
	private boolean pressed;

	public AutoSprint() {
		super("autoSprint", "Auto Sprint", "Sprints whenever you walk forward.", Category.GENERAL, false);
	}

	@Override
	public void init() {
		ClientTickEvents.START_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		KeyMapping sprint = mc.options.keySprint;
		if (wants(enabled(), mc.player != null && mc.options.keyUp.isDown(), mc.screen != null)) {
			sprint.setDown(true);
			pressed = true;
		} else if (pressed) {
			sprint.setDown(physicallyDown(mc, sprint));
			pressed = false;
		}
	}

	/** Whether the key is pressed for the player this tick: on, walking forward, and no screen taking the keys. */
	static boolean wants(boolean on, boolean forward, boolean screenOpen) {
		return on && forward && !screenOpen;
	}

	/** What the player's own finger is doing with the key's bound button, so letting go doesn't cancel a real press. */
	private static boolean physicallyDown(Minecraft mc, KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
	}
}
