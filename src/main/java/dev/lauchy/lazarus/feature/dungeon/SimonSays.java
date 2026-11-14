package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Simon Says: the wall's tiles light up one by one (obsidian turning to sea lantern),
 * then you press their buttons in that order, and each round shows the whole run again
 * with one more on the end. The flashes are recorded as they happen ({@link SimonSteps})
 * and the next button to press is boxed. Pressing stays yours.
 */
public final class SimonSays extends Feature {
	/** How far the board is looked for: across, below you and above you. The wall stands taller than you, so further up than down. */
	private static final int ACROSS = 12, BELOW = 6, ABOVE = 12;
	/** How long after a button is pressed its own tile lighting is taken for the press showing, not a flash of the run. */
	private static final long OWN_PRESS_MS = 600;

	/** Buttons on obsidian or sea lantern near you: the board. */
	private final Set<BlockPos> board = new HashSet<>();
	private final Set<BlockPos> lit = new HashSet<>(), down = new HashSet<>();
	private final SimonSteps steps = new SimonSteps();
	/** When each button was last pressed. */
	private final Map<BlockPos, Long> pressedAt = new HashMap<>();
	private int ticks;

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
			// The board is looked for again every second: a wall you walk up to comes into view a row at a time,
			// and a row missed is a step of the run never seen.
			ticks++;
			if (ticks % 20 == 0 || board.isEmpty() && ticks % 10 == 0) scan(mc.level, mc.player.blockPosition());
			if (board.isEmpty()) return;
			if (board.iterator().next().distToCenterSqr(mc.player.position()) > 30 * 30) {
				reset();
				return;
			}
			observe(mc.level, System.currentTimeMillis());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && enabled() && board.contains(hit.getBlockPos())) pressedAt.put(hit.getBlockPos().immutable(), System.currentTimeMillis());
			return InteractionResult.PASS;
		});
		// The device says so when a press was wrong: the run starts again from nothing.
		Chat.listen(l -> {
			if (board.isEmpty()) return;
			String line = l.text().toLowerCase(Locale.ROOT);
			if (line.contains("starts over") || line.contains("wrong button")) steps.clear();
		});
		World.paint(() -> {
			BlockPos next = enabled() ? steps.next() : null;
			if (next != null) World.box(new AABB(next).deflate(0.25), 0xFF55FF55, true, true, true);
		});
	}

	private void reset() {
		board.clear();
		lit.clear();
		down.clear();
		pressedAt.clear();
		steps.clear();
	}

	private void scan(Level level, BlockPos around) {
		Set<BlockPos> found = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-ACROSS, -BELOW, -ACROSS), around.offset(ACROSS, ABOVE, ACROSS))) {
			Direction f = wallFacing(level.getBlockState(p));
			if (f == null) continue;
			BlockState behind = level.getBlockState(p.relative(f.getOpposite()));
			if (behind.is(Blocks.OBSIDIAN) || behind.is(Blocks.SEA_LANTERN)) found.add(p.immutable());
		}
		if (found.size() >= 4) board.addAll(found); // a stray button is not a board
	}

	private void observe(Level level, long now) {
		for (BlockPos button : board) {
			BlockState state = level.getBlockState(button);
			Direction f = wallFacing(state);
			if (f == null) continue;
			boolean on = level.getBlockState(button.relative(f.getOpposite())).is(Blocks.SEA_LANTERN);
			boolean pressed = state.getValue(ButtonBlock.POWERED);
			if (pressed && down.add(button)) {
				pressedAt.put(button, now);
				steps.pressed(button);
			} else if (!pressed) down.remove(button);
			if (on && lit.add(button)) {
				Long at = pressedAt.get(button);
				if (at == null || now - at > OWN_PRESS_MS) steps.flashed(button, now);
			} else if (!on) lit.remove(button);
		}
	}

	private static Direction wallFacing(BlockState s) {
		if (!(s.getBlock() instanceof ButtonBlock) || s.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.WALL) return null;
		return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
	}
}
