package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
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

/**
 * Tic Tac Toe against the room. The board is a 3x3 of blocks: iron with a button in
 * front is a free square, blue concrete is the room's mark, red concrete is yours. When
 * it is your turn the best square (by full minimax, so a draw at worst) has its button
 * boxed. Nothing is clicked for you.
 */
public final class TicTacToe extends Feature {
	private static final String PUZZLE = "Tic Tac Toe";
	private static final int EMPTY = 0, YOU = 1, ROOM = 2;
	private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

	private boolean active;
	private int ticks;
	private BlockPos best;

	public TicTacToe() {
		super("ticTacToe", "Tic Tac Toe", "Boxes the best move on the Tic Tac Toe board.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (Puzzles.entered(l.text(), PUZZLE)) active = true;
			else if (Puzzles.left(l.text(), PUZZLE)) {
				active = false;
				best = null;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !active || mc.level == null || mc.player == null) {
				best = null;
				return;
			}
			if (++ticks % 5 == 0) best = find(mc.level, mc.player.blockPosition());
		});
		World.paint(() -> {
			if (best != null) World.box(new AABB(best).deflate(0.3), 0xFF55FF55, true, true, true);
		});
	}

	/** The button to press, or null when there is no board or it isn't your turn. */
	private static BlockPos find(Level level, BlockPos around) {
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-12, -5, -12), around.offset(12, 5, 12))) {
			BlockState s = level.getBlockState(p);
			Direction facing = facing(s);
			if (facing == null) continue;
			BlockPos support = p.relative(facing.getOpposite());
			if (!level.getBlockState(support).is(Blocks.IRON_BLOCK)) continue;
			BlockPos move = solveBoardAround(level, support.immutable(), facing);
			if (move != null) return move;
		}
		return null;
	}

	/** Every 3x3 window in the board's plane that holds this free square; the first complete one is the board. */
	private static BlockPos solveBoardAround(Level level, BlockPos free, Direction facing) {
		Direction right = facing.getAxis() == Direction.Axis.Y ? Direction.EAST : facing.getClockWise();
		Direction down = facing.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.DOWN;
		for (int r0 = -2; r0 <= 0; r0++) {
			for (int c0 = -2; c0 <= 0; c0++) {
				BlockPos corner = free.relative(down, r0).relative(right, c0);
				int[] board = new int[9];
				BlockPos[] cells = new BlockPos[9];
				boolean complete = true;
				for (int i = 0; i < 9 && complete; i++) {
					cells[i] = corner.relative(down, i / 3).relative(right, i % 3);
					BlockState s = level.getBlockState(cells[i]);
					if (s.is(Blocks.RED_CONCRETE)) board[i] = YOU;
					else if (s.is(Blocks.BLUE_CONCRETE)) board[i] = ROOM;
					else if (s.is(Blocks.IRON_BLOCK) && facing(level.getBlockState(cells[i].relative(facing))) == facing) board[i] = EMPTY;
					else complete = false;
				}
				if (!complete) continue;
				int you = 0, room = 0;
				for (int v : board) {
					if (v == YOU) you++;
					else if (v == ROOM) room++;
				}
				if (room != you + 1 || winner(board) != EMPTY) return null; // the room moves first; not your turn
				int move = bestMove(board);
				return move < 0 ? null : cells[move].relative(facing);
			}
		}
		return null;
	}

	private static Direction facing(BlockState s) {
		if (!(s.getBlock() instanceof ButtonBlock) || s.getValue(ButtonBlock.POWERED)) return null;
		AttachFace face = s.getValue(BlockStateProperties.ATTACH_FACE);
		if (face == AttachFace.FLOOR) return Direction.UP;
		if (face == AttachFace.CEILING) return Direction.DOWN;
		return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	private static int bestMove(int[] b) {
		int best = -1, bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 9; i++) {
			if (b[i] != EMPTY) continue;
			b[i] = YOU;
			int score = minimax(b, false, 1);
			b[i] = EMPTY;
			if (score > bestScore || score == bestScore && i == 4) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	private static int minimax(int[] b, boolean yourTurn, int depth) {
		int w = winner(b);
		if (w == YOU) return 10 - depth;
		if (w == ROOM) return depth - 10;
		boolean full = true;
		int best = yourTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (int i = 0; i < 9; i++) {
			if (b[i] != EMPTY) continue;
			full = false;
			b[i] = yourTurn ? YOU : ROOM;
			int s = minimax(b, !yourTurn, depth + 1);
			b[i] = EMPTY;
			best = yourTurn ? Math.max(best, s) : Math.min(best, s);
		}
		return full ? 0 : best;
	}

	private static int winner(int[] b) {
		for (int[] l : LINES) if (b[l[0]] != EMPTY && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
		return EMPTY;
	}
}
