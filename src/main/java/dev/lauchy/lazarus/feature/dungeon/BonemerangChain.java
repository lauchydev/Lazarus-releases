package dev.lauchy.lazarus.feature.dungeon;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Right-click a Bonemerang and every Bonemerang in the same unbroken run on your hotbar
 * is thrown, leftmost first, spread evenly over one cooldown, so the first is back just
 * as the last leaves. Keep holding right-click and it goes round again. Your held slot
 * comes back when you let go.
 */
public final class BonemerangChain extends Feature {
	private final IntSetting cooldown = add(new IntSetting("cooldown", "Cooldown", "How long one Bonemerang takes to come back.", 1200, 200, 3000, " ms"));

	private int first = -1, count, tick, cycleTicks, restoreSlot = -1;

	public BonemerangChain() {
		super("bonemerangChain", "Bonemerang Chain", "Right-click a Bonemerang to throw every one in its hotbar row in turn.", Category.DUNGEONS, false);
	}

	@Override
	public boolean plus() {
		return true;
	}

	/** It's a macro, which Dragsim's rules can ban for. */
	@Override
	public boolean bannable() {
		return true;
	}

	@Override
	public void init() {
		// At the start of the tick, before vanilla reads the use key, so the right-click is ours alone.
		ClientTickEvents.START_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		if (!enabled() || mc.player == null || mc.gameMode == null || mc.screen != null) {
			stop(mc);
			return;
		}
		Inventory inv = mc.player.getInventory();
		boolean holding = useHeld(mc);
		if (first < 0) {
			if (!holding || !isBonemerang(inv.getSelectedItem())) return;
			restoreSlot = inv.getSelectedSlot();
			start(inv, restoreSlot);
		}
		silenceVanillaUse(mc);
		if (tick >= cycleTicks) { // one cooldown has passed: the first is back
			if (!holding) {
				stop(mc);
				return;
			}
			start(inv, restoreSlot); // the row may have changed; read it again around the slot you clicked
			if (count == 0) {
				stop(mc);
				return;
			}
		}
		for (int k = 0; k < count; k++) {
			if (tick == k * cycleTicks / count) throwFrom(mc, inv, first + k);
		}
		tick++;
	}

	/** Finds the run of Bonemerangs around {@code slot} and starts a fresh round from its left end. */
	private void start(Inventory inv, int slot) {
		int left = slot, right = slot;
		while (left > 0 && isBonemerang(inv.getItem(left - 1))) left--;
		while (right < 8 && isBonemerang(inv.getItem(right + 1))) right++;
		first = left;
		count = isBonemerang(inv.getItem(slot)) ? right - left + 1 : 0;
		cycleTicks = Math.max(count, Math.round(cooldown.get() / 50f));
		tick = 0;
	}

	private static void throwFrom(Minecraft mc, Inventory inv, int slot) {
		if (!isBonemerang(inv.getItem(slot))) return;
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // sends the slot change first
	}

	private void stop(Minecraft mc) {
		if (first < 0) return;
		if (mc.player != null && restoreSlot >= 0) mc.player.getInventory().setSelectedSlot(restoreSlot);
		first = -1;
		restoreSlot = -1;
	}

	/** Whether the use button is physically held; vanilla's own state is switched off while chaining. */
	private static boolean useHeld(Minecraft mc) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mc.options.keyUse);
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}
		return key.getType() == InputConstants.Type.KEYSYM && Keys.down(key.getValue());
	}

	private static void silenceVanillaUse(Minecraft mc) {
		while (mc.options.keyUse.consumeClick()) {
			// the chain throws instead
		}
		mc.options.keyUse.setDown(false);
	}

	private static boolean isBonemerang(ItemStack s) {
		return !s.isEmpty() && Items.name(s).toLowerCase(Locale.ROOT).contains("bonemerang");
	}
}
