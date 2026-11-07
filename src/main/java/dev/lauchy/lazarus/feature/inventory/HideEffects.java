package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;

/**
 * Hides the list of potion effects beside your inventory and other windows: on Dragsim most are
 * endless (Night Vision, Jump Boost) and only take up room. Drawn by EffectsInInventoryMixin.
 */
public final class HideEffects extends Feature {
	public static HideEffects INSTANCE;

	public HideEffects() {
		super("hideEffects", "Hide Inventory Effects", "Hides the potion effects listed beside your inventory.", Category.INVENTORY, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
	}

	/** Whether the effects beside a window are hidden. */
	public static boolean hidden() {
		return INSTANCE != null && INSTANCE.enabled();
	}
}
