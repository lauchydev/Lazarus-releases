package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Creeper Beams: a creeper stands in the middle of the room with sea lanterns in the walls round it, and each lantern
 * has a partner straight across, through the creeper. Hitting a pair fires a beam at it. Each pair is boxed and
 * joined by a line in its own colour; a pair done (its lanterns gone dark) drops out. The room has no announcement,
 * so it's known by a creeper with lanterns round it.
 */
public final class CreeperBeams extends Feature {
	private static final int REACH = 16, UP = 12;
	private static final int[] COLOURS = {0xFF55FFFF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FF55, 0xFFFFAA00, 0xFFFF5555};

	private List<BlockPos[]> pairs = List.of();
	private int ticks;

	public CreeperBeams() {
		super("creeperBeams", "Creeper Beams", "Pairs up the lanterns that fire through the creeper.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				pairs = List.of();
				return;
			}
			if (++ticks % 10 != 0) return;
			Creeper creeper = null;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Creeper c && e.distanceToSqr(mc.player) < 30 * 30 && (creeper == null || e.distanceToSqr(mc.player) < creeper.distanceToSqr(mc.player))) creeper = c;
			}
			if (creeper == null) {
				pairs = List.of();
				return;
			}
			BlockPos mid = creeper.blockPosition();
			List<BlockPos> lanterns = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(mid.offset(-REACH, -UP, -REACH), mid.offset(REACH, UP, REACH))) {
				if (mc.level.getBlockState(p).is(Blocks.SEA_LANTERN)) lanterns.add(p.immutable());
			}
			// Two lanterns are a pair when the straight line between them passes through the creeper.
			AABB body = creeper.getBoundingBox().inflate(0.4);
			List<BlockPos[]> found = new ArrayList<>();
			List<BlockPos> used = new ArrayList<>();
			for (int i = 0; i < lanterns.size(); i++) {
				if (used.contains(lanterns.get(i))) continue;
				for (int j = i + 1; j < lanterns.size(); j++) {
					if (used.contains(lanterns.get(j))) continue;
					if (body.clip(Vec3.atCenterOf(lanterns.get(i)), Vec3.atCenterOf(lanterns.get(j))).isEmpty()) continue;
					found.add(new BlockPos[]{lanterns.get(i), lanterns.get(j)});
					used.add(lanterns.get(i));
					used.add(lanterns.get(j));
					break;
				}
			}
			pairs = found;
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (int i = 0; i < pairs.size(); i++) {
				int c = COLOURS[i % COLOURS.length];
				World.block(pairs.get(i)[0], c, true, true);
				World.block(pairs.get(i)[1], c, true, true);
				World.line(Vec3.atCenterOf(pairs.get(i)[0]), Vec3.atCenterOf(pairs.get(i)[1]), c, 2f, true);
			}
		});
	}
}
