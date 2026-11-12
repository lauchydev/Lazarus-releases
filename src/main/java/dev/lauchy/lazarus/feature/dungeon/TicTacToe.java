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

/**
 * Tic Tac Toe against the room. The board comes two ways. One is a 3x3 of blocks: iron
 * with a button in front is a free square, blue concrete is the room's mark, red concrete
 * is yours. The other is a 3x3 on a wall: a frame holding a map of an X or an O is a
 * square taken, a button is a free one, and the room (which moves first) is whichever
 * mark there is more of. When it is your turn the best square (by full minimax, so a draw
 * at worst) has its button boxed. Nothing is clicked for you.
 */
public final class TicTacToe extends Feature {
	private static final int EMPTY = 0, YOU = 1, ROOM = 2;
	private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

	private int ticks;
	private BlockPos best;
	private boolean loggedBoard;
	private Object boardLevel;

	public TicTacToe() {
		super("ticTacToe", "Tic Tac Toe", "Boxes the best move on the Tic Tac Toe board.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		// The server never says you've entered the room, so the board is looked for wherever you are in a dungeon.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				best = null;
				return;
			}
			if (mc.level != boardLevel) {
				boardLevel = mc.level;
				loggedBoard = false;
			}
			if (++ticks % 5 != 0) return;
			best = find(mc.level, mc.player.blockPosition());
			if (best == null) best = framed(mc);
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

	/**
	 * The board of frames on a wall: the button to press, or null when there's no such board or it isn't your turn.
	 * What was found goes to the log once a run, so a board read wrong can be put right from it.
	 */
	private BlockPos framed(net.minecraft.client.Minecraft mc) {
		// Frames holding maps near you, by the wall they hang on.
		java.util.Map<String, java.util.List<net.minecraft.world.entity.decoration.ItemFrame>> walls = new java.util.HashMap<>();
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof net.minecraft.world.entity.decoration.ItemFrame f) || f.distanceToSqr(mc.player) > 14 * 14 || f.getDirection().getAxis().isVertical()) continue;
			if (f.getItem().get(net.minecraft.core.component.DataComponents.MAP_ID) == null) continue;
			Direction d = f.getDirection();
			walls.computeIfAbsent(d + ":" + (d.getAxis() == Direction.Axis.X ? f.blockPosition().getX() : f.blockPosition().getZ()), k -> new java.util.ArrayList<>()).add(f);
		}
		for (var wall : walls.values()) {
			Direction facing = wall.getFirst().getDirection(), right = facing.getClockWise();
			java.util.Map<BlockPos, Integer> marks = new java.util.HashMap<>();
			for (var f : wall) marks.put(f.blockPosition(), mark(mc, f));
			// The free squares: buttons on the same wall, beside the frames.
			BlockPos any = wall.getFirst().blockPosition();
			java.util.Set<BlockPos> free = new java.util.HashSet<>();
			for (int r = -3; r <= 3; r++) {
				for (int c = -3; c <= 3; c++) {
					BlockPos p = any.relative(Direction.DOWN, r).relative(right, c);
					BlockState s = mc.level.getBlockState(p);
					if (s.getBlock() instanceof ButtonBlock && s.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing && !marks.containsKey(p)) free.add(p.immutable());
				}
			}
			java.util.Set<BlockPos> all = new java.util.HashSet<>(marks.keySet());
			all.addAll(free);
			int top = Integer.MIN_VALUE, left = Integer.MAX_VALUE, bottom = Integer.MAX_VALUE, far = Integer.MIN_VALUE;
			for (BlockPos p : all) {
				int col = p.getX() * right.getStepX() + p.getZ() * right.getStepZ();
				top = Math.max(top, p.getY());
				bottom = Math.min(bottom, p.getY());
				left = Math.min(left, col);
				far = Math.max(far, col);
			}
			if (!loggedBoard) {
				loggedBoard = true;
				dev.lauchy.lazarus.Lazarus.LOG.info("[Tic Tac Toe] A wall with {} map frames (marks {}) and {} buttons beside them, {} wide and {} high", wall.size(), marks.values(), free.size(), far - left + 1, top - bottom + 1);
			}
			if (all.size() != 9 || top - bottom != 2 || far - left != 2) continue;
			int[] board = new int[9];
			BlockPos[] cells = new BlockPos[9];
			int xs = 0, os = 0;
			for (int v : marks.values()) {
				if (v == 1) xs++;
				else os++;
			}
			// The room moves first, so its mark is the one there's more of; level, it's the room's turn.
			if (xs == os) continue;
			int roomMark = xs > os ? 1 : 2;
			for (BlockPos p : all) {
				int i = (top - p.getY()) * 3 + (p.getX() * right.getStepX() + p.getZ() * right.getStepZ() - left);
				cells[i] = p;
				Integer m = marks.get(p);
				board[i] = m == null ? EMPTY : m == roomMark ? ROOM : YOU;
			}
			if (Math.abs(xs - os) != 1 || winner(board) != EMPTY) continue;
			int move = bestMove(board);
			if (move >= 0) return cells[move];
		}
		return null;
	}

	/** The mark on a frame's map: 1 for an X (its middle is drawn on), 2 for an O (its middle is bare). */
	private static int mark(net.minecraft.client.Minecraft mc, net.minecraft.world.entity.decoration.ItemFrame f) {
		var id = f.getItem().get(net.minecraft.core.component.DataComponents.MAP_ID);
		var data = id == null ? null : mc.level.getMapData(id);
		if (data == null) return 2;
		int[] counts = new int[256];
		for (byte b : data.colors) counts[b & 0xFF]++;
		int bare = 0;
		for (int i = 1; i < 256; i++) if (counts[i] > counts[bare]) bare = i;
		int drawn = 0;
		for (int y = 56; y < 72; y++) for (int x = 56; x < 72; x++) if ((data.colors[y * 128 + x] & 0xFF) != bare) drawn++;
		return drawn > 40 ? 1 : 2;
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
