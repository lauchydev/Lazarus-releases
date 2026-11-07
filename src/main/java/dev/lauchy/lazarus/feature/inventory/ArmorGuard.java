package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Right-clicking with armour in hand no longer swaps it onto you, so a click that misses
 * the salvage altar can't put your armour on and salvage the piece you were wearing.
 * Sneak to right-click armour abilities as normal.
 */
public final class ArmorGuard extends Feature {
	public ArmorGuard() {
		super("armorGuard", "Armor Swap Guard", "Stops right-click armor swaps unless you sneak.", Category.INVENTORY, true);
	}

	@Override
	public void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!enabled() || !level.isClientSide() || player.isShiftKeyDown()) return InteractionResult.PASS;
			Equippable wear = player.getItemInHand(hand).get(DataComponents.EQUIPPABLE);
			if (wear == null || !wear.swappable() || !wear.slot().isArmor()) return InteractionResult.PASS;
			return InteractionResult.FAIL; // FAIL stops the use packet; PASS would let vanilla swap
		});
	}
}
