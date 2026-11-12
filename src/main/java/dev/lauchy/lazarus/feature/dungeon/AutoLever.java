package dev.lauchy.lazarus.feature.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Auto Lever: in a dungeon, a lever within your reach is flicked for you, each lever once a run ({@link AutoReach}).
 * Not in a puzzle room, and not the levers of the lights device (the ones fixed to a lamp), where only six of the
 * twenty count ({@link Lights}, which Auto Solve can do). Lazarus+, and marked BANNABLE.
 */
public final class AutoLever extends AutoReach {
	public AutoLever() {
		super("autoLever", "Auto Lever", "Flicks the dungeon levers within your reach for you, each once. It plays for you: servers ban for it.");
	}

	@Override
	protected boolean wanted(Minecraft mc, BlockPos pos, BlockState state) {
		// A lever fixed to a lamp, on whichever face, belongs to the lights device: only six of those are to be flicked.
		return state.is(Blocks.LEVER) && Lights.lampOf(mc.level, pos, state) == null;
	}
}
