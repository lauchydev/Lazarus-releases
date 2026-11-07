package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simon Says: the wall's tiles light up one by one (obsidian turning to sea lantern),
 * then you press their buttons in that order, and each round adds one. The flashes are
 * recorded as they happen and the next button to press is boxed. Pressing stays yours.
 */
public final class SimonSays extends Feature {
	private static final int SCAN = 10;

	/** Buttons on obsidian or sea lantern near you: the board. */
	private final Set<BlockPos> board = new HashSet<>();
	private final Set<BlockPos> lit = new HashSet<>(), pressed = new HashSet<>();
	private final List<BlockPos> sequence = new ArrayList<>();
	private int done, ticks;
	private BlockPos lastPressed;
	private long lastPressedAt;
	private boolean inputStarted;

	public SimonSays() {
		super("simonSays", "Simon Says", "Boxes the next Simon Says button to press.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				reset();
				return;
			}
			if (board.isEmpty() && ++ticks % 10 == 0) scan(mc.level, mc.player.blockPosition());
			if (board.isEmpty()) return;
			if (board.iterator().next().distToCenterSqr(mc.player.position()) > 24 * 24) {
				reset();
				return;
			}
			observe(mc.level);
		});
		World.paint(() -> {
			if (!enabled() || done >= sequence.size()) return;
			World.box(new AABB(sequence.get(done)).deflate(0.25), 0xFF55FF55, true, true, true);
		});
	}

	private void reset() {
		board.clear();
		lit.clear();
		pressed.clear();
		sequence.clear();
		done = 0;
		inputStarted = false;
	}

	private void scan(Level level, BlockPos around) {
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-SCAN, -5, -SCAN), around.offset(SCAN, 5, SCAN))) {
			Direction f = wallFacing(level.getBlockState(p));
			if (f == null) continue;
			BlockState behind = level.getBlockState(p.relative(f.getOpposite()));
			if (behind.is(Blocks.OBSIDIAN) || behind.is(Blocks.SEA_LANTERN)) board.add(p.immutable());
		}
		if (board.size() < 4) board.clear(); // a stray button is not a board
	}

	private void observe(Level level) {
		for (BlockPos button : board) {
			BlockState state = level.getBlockState(button);
			Direction f = wallFacing(state);
			if (f == null) continue;
			boolean on = level.getBlockState(button.relative(f.getOpposite())).is(Blocks.SEA_LANTERN);
			boolean down = state.getValue(ButtonBlock.POWERED);
			if (on && lit.add(button)) flashed(button);
			else if (!on) lit.remove(button);
			if (down && pressed.add(button)) pressedButton(button);
			else if (!down) pressed.remove(button);
		}
	}

	/** A tile lit: the start of a new round if you had begun pressing, else the next step of this one. */
	private void flashed(BlockPos button) {
		// A tile can light as feedback for your own press; that is not a new round.
		if (button.equals(lastPressed) && System.currentTimeMillis() - lastPressedAt < 1_000) return;
		if (inputStarted) {
			sequence.clear();
			done = 0;
			inputStarted = false;
		}
		sequence.add(button);
	}

	private void pressedButton(BlockPos button) {
		inputStarted = true;
		lastPressed = button;
		lastPressedAt = System.currentTimeMillis();
		if (done < sequence.size() && sequence.get(done).equals(button)) done++;
	}

	private static Direction wallFacing(BlockState s) {
		if (!(s.getBlock() instanceof ButtonBlock) || s.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.WALL) return null;
		return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
	}
}
