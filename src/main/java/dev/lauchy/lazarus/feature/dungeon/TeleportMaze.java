package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Teleport Maze: a grid of cells, each with teleport pads (end portal frames) that throw you to another cell; one
 * chain of pads leads to the middle. Every pad you've stepped on, and the pad it threw you to, is marked red, so a
 * pad left unmarked is one not tried yet. The room has no announcement: a jump of several blocks in one tick that
 * starts and ends by a pad is a teleport.
 */
public final class TeleportMaze extends Feature {
	private final Set<BlockPos> used = new HashSet<>();
	private Vec3 was;
	private Object level;

	public TeleportMaze() {
		super("teleportMaze", "Teleport Maze", "Marks the teleport pads you've already been through.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				used.clear();
				was = null;
				return;
			}
			if (mc.level != level) {
				level = mc.level;
				used.clear();
				was = null;
			}
			Vec3 now = mc.player.position();
			if (was != null && was.distanceToSqr(now) > 9) {
				BlockPos from = pad(mc.level, BlockPos.containing(was)), to = pad(mc.level, BlockPos.containing(now));
				if (from != null && to != null) {
					used.add(from);
					used.add(to);
				}
			}
			was = now;
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (BlockPos p : used) World.block(p, 0xFFFF5555, true, true);
		});
	}

	/** The teleport pad at or beside a spot, a block either way and down, or null. */
	private static BlockPos pad(Level level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 0, 1))) {
			if (level.getBlockState(p).is(Blocks.END_PORTAL_FRAME)) return p.immutable();
		}
		return null;
	}
}
