package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ice Path: hit the silverfish and it slides across the packed ice until something stops
 * it; get it to the chest at the edge of the sheet. Each hit is a whole slide, so the
 * shortest answer is a breadth-first search over where slides end. The slides are drawn
 * on the ice, the next one brightest.
 */
public final class IcePath extends Feature {
	private static final String PUZZLE = "Ice Path";
	private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
	private static final int SCAN = 20;

	private boolean active;
	private int ticks;
	private List<BlockPos> path = List.of();

	public IcePath() {
		super("icePath", "Ice Path", "Draws the shortest way to slide the silverfish to the chest.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (Puzzles.entered(l.text(), PUZZLE)) active = true;
			else if (Puzzles.left(l.text(), PUZZLE)) {
				active = false;
				path = List.of();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !active || mc.level == null || mc.player == null) return;
			if (++ticks % 10 == 0) path = solve(mc);
		});
		World.paint(() -> {
			if (!enabled() || path.size() < 2) return;
			for (int i = 0; i + 1 < path.size(); i++) {
				int c = i == 0 ? 0xFF55FF55 : 0x9955FFFF;
				World.line(top(path.get(i)), top(path.get(i + 1)), c, i == 0 ? 4f : 2f, true);
			}
		});
	}

	private static Vec3 top(BlockPos ice) {
		return Vec3.atCenterOf(ice).add(0, 0.55, 0);
	}

	/** Ice cells (the sheet's top layer), from the fish to beside the chest. */
	private static List<BlockPos> solve(Minecraft mc) {
		Silverfish fish = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Silverfish s && e.distanceTo(mc.player) < SCAN && (fish == null || e.distanceTo(mc.player) < fish.distanceTo(mc.player))) fish = s;
		}
		if (fish == null) return List.of();
		Level level = mc.level;
		BlockPos start = fish.blockPosition().below();
		if (!level.getBlockState(start).is(Blocks.PACKED_ICE)) return List.of();
		int floor = start.getY();
		List<BlockPos> goals = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(start.offset(-SCAN, 1, -SCAN), start.offset(SCAN, 1, SCAN))) {
			if (!level.getBlockState(p).is(Blocks.CHEST) && !level.getBlockState(p).is(Blocks.TRAPPED_CHEST)) continue;
			for (Direction d : DIRS) {
				BlockPos beside = p.relative(d).below();
				if (level.getBlockState(beside).is(Blocks.PACKED_ICE)) goals.add(beside.immutable());
			}
		}
		if (goals.isEmpty()) return List.of();
		Map<BlockPos, BlockPos> from = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		from.put(start, start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			if (goals.contains(at)) {
				List<BlockPos> out = new ArrayList<>();
				for (BlockPos p = at; ; p = from.get(p)) {
					out.add(p);
					if (p.equals(start)) break;
				}
				Collections.reverse(out);
				return out;
			}
			for (Direction d : DIRS) {
				BlockPos end = slide(level, at, d, floor);
				if (!end.equals(at) && from.putIfAbsent(end, at) == null) queue.add(end);
			}
		}
		return List.of();
	}

	/** Where a slide from {@code at} going {@code d} stops: before a block standing on the ice, or the sheet's edge. */
	private static BlockPos slide(Level level, BlockPos at, Direction d, int floor) {
		BlockPos p = at;
		for (int i = 0; i < 64; i++) {
			BlockPos next = p.relative(d);
			if (next.getY() != floor || !level.getBlockState(next).is(Blocks.PACKED_ICE) || !level.getBlockState(next.above()).isAir()) return p;
			p = next;
		}
		return p;
	}
}
