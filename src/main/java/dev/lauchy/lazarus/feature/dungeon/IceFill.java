package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.IceFillPath.Tile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ice Fill: walk over every tile of an ice sheet exactly once (each turns to packed ice behind you) to reach the
 * far side. When you step up to a fresh sheet, the one walk that covers it ({@link IceFillPath}) is drawn on the
 * ice, from the tile you're at. The room has no announcement, so a sheet is known by its ice under your feet.
 */
public final class IceFill extends Feature {
	/** The biggest sheet looked at, in tiles: past this it isn't the puzzle. */
	private static final int MAX_TILES = 80;

	private List<Tile> walk = List.of();
	private int floor, ticks;

	public IceFill() {
		super("iceFill", "Ice Fill", "Draws the one walk that covers every tile of the ice sheet.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				walk = List.of();
				return;
			}
			if (++ticks % 5 != 0) return;
			BlockPos under = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
			// Kept while you're on or near the sheet; gone once you've left it.
			if (!walk.isEmpty() && mc.player.distanceToSqr(walk.getFirst().x() + 0.5, floor + 1, walk.getFirst().z() + 0.5) > 40 * 40) walk = List.of();
			// A fresh sheet: plain ice under you (or in front), none of it walked yet.
			BlockPos start = null;
			for (BlockPos p : List.of(under, under.north(), under.south(), under.east(), under.west())) {
				if (tile(mc.level, p)) {
					start = p;
					break;
				}
			}
			if (start == null) return;
			// Ice of the walk already drawn is the way ahead. Any other is the room's next sheet: the room has three, one after another.
			if (!walk.isEmpty() && start.getY() == floor && walk.contains(new Tile(start.getX(), start.getZ()))) return;
			Set<Tile> tiles = sheet(mc.level, start);
			if (tiles.size() < 4 || tiles.size() > MAX_TILES) return;
			floor = start.getY();
			walk = IceFillPath.solve(tiles, new Tile(start.getX(), start.getZ()));
		});
		World.paint(() -> {
			if (!enabled() || walk.size() < 2) return;
			Minecraft mc = Minecraft.getInstance();
			boolean next = true;
			for (int i = 0; i + 1 < walk.size(); i++) {
				// Tiles already walked have turned to packed ice: only the way ahead is drawn, your next step in green.
				if (mc.level.getBlockState(new BlockPos(walk.get(i + 1).x(), floor, walk.get(i + 1).z())).is(Blocks.PACKED_ICE)) continue;
				World.line(top(walk.get(i)), top(walk.get(i + 1)), next ? 0xFF55FF55 : 0xFF55FFFF, next ? 5f : 3f, true);
				next = false;
			}
		});
	}

	private Vec3 top(Tile t) {
		return new Vec3(t.x() + 0.5, floor + 1.05, t.z() + 0.5);
	}

	/** A tile to walk: plain ice with nothing standing on it. */
	private static boolean tile(Level level, BlockPos p) {
		return level.getBlockState(p).is(Blocks.ICE) && level.getBlockState(p.above()).isAir();
	}

	/** Every tile joined to this one on its level. */
	private static Set<Tile> sheet(Level level, BlockPos start) {
		Set<Tile> out = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
		out.add(new Tile(start.getX(), start.getZ()));
		while (!queue.isEmpty() && out.size() <= MAX_TILES) {
			BlockPos p = queue.poll();
			for (BlockPos n : List.of(p.north(), p.south(), p.east(), p.west())) {
				if (tile(level, n) && out.add(new Tile(n.getX(), n.getZ()))) queue.add(n);
			}
		}
		return out;
	}
}
