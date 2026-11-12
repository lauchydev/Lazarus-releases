package dev.lauchy.lazarus.feature.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Auto Chest: in a dungeon, a chest within your reach is opened for you, each chest once a run ({@link AutoReach}).
 * Not in a puzzle room, where opening the wrong chest loses the puzzle. Lazarus+, and marked BANNABLE.
 */
public final class AutoChest extends AutoReach {
	public AutoChest() {
		super("autoChest", "Auto Chest", "Opens the dungeon chests within your reach for you, each once. It plays for you: servers ban for it.");
	}

	@Override
	protected boolean wanted(Minecraft mc, BlockPos pos, BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
	}
}
