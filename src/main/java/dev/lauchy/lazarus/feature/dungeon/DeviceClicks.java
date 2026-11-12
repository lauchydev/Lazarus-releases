package dev.lauchy.lazarus.feature.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The clicks Auto solve makes on a device for you: a right click on a block (a lever, a button) or on a thing (an
 * item frame), only when it's within your own reach. Used by nothing but the bannable Auto Solve ({@link AutoSolve}).
 */
final class DeviceClicks {
	private DeviceClicks() {
	}

	/** Right-clicks a block as you would; false when it's out of reach. */
	static boolean block(Minecraft mc, BlockPos pos) {
		if (mc.player == null || mc.gameMode == null || !mc.player.isWithinBlockInteractionRange(pos, 0)) return false;
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	/** Right-clicks a thing as you would; false when it's out of reach. */
	static boolean entity(Minecraft mc, Entity e) {
		if (mc.player == null || mc.gameMode == null || !mc.player.isWithinEntityInteractionRange(e, 0)) return false;
		mc.gameMode.interact(mc.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}
}
