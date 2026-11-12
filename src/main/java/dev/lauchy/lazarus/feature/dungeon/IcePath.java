package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ice Path: hit the silverfish and it slides across the ice until something stops it; get
 * it to the way out at the edge of the sheet (beside the chest when there is one, else the
 * gap in the wall furthest from where the fish began). Each hit is a whole slide, so the
 * shortest answer is a breadth-first search over where slides end. The slides are drawn
 * on the ice, the next one brightest.
 */
public final class IcePath extends Feature {
	private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
	private static final int SCAN = 30;

	private int ticks;
	private List<BlockPos> path = List.of();
	/** Where the fish was when first seen this run (the way out is across the sheet from it), and the world it was in. */
	private BlockPos began;
	private Object level;
	/** Whether this run's room has gone to the log, so a room read wrong can be fixed from it. */
	private boolean logged;

	public IcePath() {
		super("icePath", "Ice Path", "Draws the shortest way to slide the silverfish to the chest.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		// The server never says you've entered the room, so the room is known by what's in it: a silverfish on packed ice.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				path = List.of();
				return;
			}
			if (mc.level != level) {
				level = mc.level;
				began = null;
				logged = false;
			}
			if (++ticks % 10 == 0) path = solve(mc);
		});
		World.paint(() -> {
			if (!enabled() || path.size() < 2) return;
			for (int i = 0; i + 1 < path.size(); i++) {
				int c = i == 0 ? 0xFF55FF55 : 0x9955FFFF;
				World.line(top(path.get(i)), top(path.get(i + 1)), c, i == 0 ? 4f : 2f, false);
			}
		});
	}

	private static Vec3 top(BlockPos ice) {
		return Vec3.atCenterOf(ice).add(0, 0.55, 0);
	}

	/** Ice cells (the sheet's top layer), from the fish to beside the chest. */
	private List<BlockPos> solve(Minecraft mc) {
		Entity fish = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Silverfish) && !Entities.name(e).contains("Silverfish")) continue;
			if (e.distanceTo(mc.player) < SCAN && (fish == null || e.distanceTo(mc.player) < fish.distanceTo(mc.player))) fish = e;
		}
		if (fish == null) return List.of();
		Level level = mc.level;
		// The ice under the fish: it may ride a little above it.
		BlockPos start = null;
		for (int down = 0; down <= 2 && start == null; down++) {
			BlockPos p = BlockPos.containing(fish.getX(), fish.getY() + 0.2 - down, fish.getZ());
			if (ice(level, p)) start = p;
		}
		if (start == null) {
			log(level, fish, fish.blockPosition().below(), "no ice under the fish");
			return List.of();
		}
		if (began == null) began = start;
		int floor = start.getY();
		// The sheet: every tile of ice joined to the fish's.
		Set<BlockPos> sheet = new HashSet<>(List.of(start));
		ArrayDeque<BlockPos> spread = new ArrayDeque<>(List.of(start));
		while (!spread.isEmpty() && sheet.size() < 2000) {
			BlockPos p = spread.poll();
			for (Direction d : DIRS) {
				BlockPos n = p.relative(d);
				if (ice(level, n) && level.getBlockState(n.above()).isAir() && sheet.add(n)) spread.add(n);
			}
		}
		// The way out: beside a chest when the room has one by the ice, else the gaps in the wall round the sheet.
		List<BlockPos> goals = new ArrayList<>(), gaps = new ArrayList<>();
		for (BlockPos p : sheet) {
			for (Direction d : DIRS) {
				BlockPos n = p.relative(d);
				if (sheet.contains(n)) continue;
				if (level.getBlockState(n.above()).is(Blocks.CHEST) || level.getBlockState(n.above()).is(Blocks.TRAPPED_CHEST)) goals.add(p);
				else if (!ice(level, n) && !level.getBlockState(n).isAir() && level.getBlockState(n.above()).isAir() && level.getBlockState(n.above(2)).isAir()) gaps.add(p);
			}
		}
		if (goals.isEmpty() && !gaps.isEmpty()) {
			// Of the gaps, the one across the sheet from where the fish began: the nearer ones are how you got in.
			BlockPos far = gaps.getFirst();
			for (BlockPos g : gaps) if (g.distSqr(began) > far.distSqr(began)) far = g;
			for (BlockPos g : gaps) if (g.distSqr(far) <= 4) goals.add(g);
		}
		log(level, fish, start, sheet.size() + " tiles, " + goals.size() + " ways out, " + gaps.size() + " gaps");
		// Only once you're at the ice: from the room next door the slides would show through the wall.
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : sheet) {
			minX = Math.min(minX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxX = Math.max(maxX, p.getX());
			maxZ = Math.max(maxZ, p.getZ());
		}
		if (mc.player.getX() < minX - 3 || mc.player.getZ() < minZ - 3 || mc.player.getX() > maxX + 4 || mc.player.getZ() > maxZ + 4 || Math.abs(mc.player.getY() - floor) > 5) return List.of();
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

	/** Any ice a fish slides on. */
	private static boolean ice(Level level, BlockPos p) {
		var s = level.getBlockState(p);
		return s.is(Blocks.PACKED_ICE) || s.is(Blocks.ICE) || s.is(Blocks.BLUE_ICE);
	}

	/** The room as the solver read it, once a run: the floor round the fish, a letter a block. */
	private void log(Level level, Entity fish, BlockPos start, String read) {
		if (logged) return;
		logged = true;
		StringBuilder map = new StringBuilder();
		// What the open floor beside the ice is made of, and what stands on the ice: the letters alone don't say.
		Set<String> floors = new java.util.TreeSet<>(), onIce = new java.util.TreeSet<>();
		for (int z = -20; z <= 20; z++) {
			map.append('\n');
			for (int x = -20; x <= 20; x++) {
				BlockPos p = start.offset(x, 0, z);
				var on = level.getBlockState(p.above());
				if (ice(level, p) && !on.isAir()) onIce.add(on.getBlock().getName().getString());
				else if (!ice(level, p) && !level.getBlockState(p).isAir() && on.isAir()) {
					for (Direction d : DIRS) if (ice(level, p.relative(d))) floors.add(level.getBlockState(p).getBlock().getName().getString());
				}
				// F the fish, C a chest, i ice with something on it, I open ice, w floor with something on it, . open floor, blank no floor.
				map.append(x == 0 && z == 0 ? 'F' : on.is(Blocks.CHEST) || on.is(Blocks.TRAPPED_CHEST) ? 'C' : ice(level, p) ? (on.isAir() ? 'I' : 'i') : level.getBlockState(p).isAir() ? ' ' : on.isAir() ? '.' : 'w');
			}
		}
		Lazarus.LOG.info("[Ice Path] {} at {}, on {}: {}. Open floor beside the ice is {}; on the ice stands {}. Floor at y={} (x left to right, z top to bottom):{}", fish.getType().toShortString(), fish.position(), level.getBlockState(start).getBlock().getName().getString(), read, floors, onIce, start.getY(), map);
	}

	/** Where a slide from {@code at} going {@code d} stops: before a block standing on the ice, or the sheet's edge. */
	private static BlockPos slide(Level level, BlockPos at, Direction d, int floor) {
		BlockPos p = at;
		for (int i = 0; i < 64; i++) {
			BlockPos next = p.relative(d);
			if (next.getY() != floor || !ice(level, next) || !level.getBlockState(next.above()).isAir()) return p;
			p = next;
		}
		return p;
	}
}
