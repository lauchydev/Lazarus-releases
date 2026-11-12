package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.LightsWall.Cell;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lights device at the gates: a wall of lamps five wide and four high with a lever on the face of each. Only
 * six of the levers count ({@link LightsWall}): the four corners and the two inner ones of the middle column. Those
 * still to flick are boxed; with the bannable Auto Solve ({@link AutoSolve}) on, the ones in your reach are flicked
 * for you. The other levers are left alone.
 */
public final class Lights extends Feature {
	private static final int SCAN = 10;
	/** How long a flick is waited on before the lever is flicked again: the server has to show it. */
	private static final long ANSWER_MS = 1200;

	/** The six levers that count, and the ones of them still off. */
	private List<BlockPos> six = List.of(), todo = List.of();
	/** When each lever was last flicked for you. */
	private final Map<BlockPos, Long> flickedAt = new HashMap<>();
	private int ticks;

	public Lights() {
		super("lights", "Lights", "Boxes the six levers to flick at the gates' lights device.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				six = todo = List.of();
				flickedAt.clear();
				return;
			}
			ticks++;
			if (ticks % 10 == 0 || six.isEmpty() && ticks % 5 == 0) scan(mc.level, mc.player.blockPosition());
			List<BlockPos> off = new ArrayList<>();
			for (BlockPos lever : six) {
				BlockState s = mc.level.getBlockState(lever);
				if (s.is(Blocks.LEVER) && !s.getValue(LeverBlock.POWERED)) off.add(lever);
			}
			todo = off;
			// Auto solve: one lever in reach at a time, and not one whose last flick the server hasn't shown yet.
			if (mc.screen != null || !AutoSolve.on(AutoSolve.Kind.LIGHTS)) return;
			long now = Util.getMillis();
			for (BlockPos lever : todo) {
				Long at = flickedAt.get(lever);
				if (at != null && now - at < ANSWER_MS || !mc.player.isWithinBlockInteractionRange(lever, 0)) continue;
				if (AutoSolve.paced(AutoSolve.Kind.LIGHTS) && DeviceClicks.block(mc, lever)) flickedAt.put(lever, now);
				break;
			}
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (BlockPos lever : todo) World.box(new AABB(lever).deflate(0.2), 0xFF55FF55, true, true, true);
		});
	}

	/** The lamp a lever is fixed to, or null when it's fixed to something else. Such a lever belongs to the lights device. */
	static BlockPos lampOf(BlockGetter level, BlockPos pos, BlockState lever) {
		if (!lever.is(Blocks.LEVER)) return null;
		AttachFace face = lever.getValue(BlockStateProperties.ATTACH_FACE);
		BlockPos on = face == AttachFace.FLOOR ? pos.below() : face == AttachFace.CEILING ? pos.above() : pos.relative(lever.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
		return level.getBlockState(on).is(Blocks.REDSTONE_LAMP) ? on : null;
	}

	/** The device near you: the wall with the most levers fixed to lamps, a dozen at least, and the six of them that count. */
	private void scan(Level level, BlockPos around) {
		Map<String, Map<Cell, BlockPos>> walls = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-SCAN, -8, -SCAN), around.offset(SCAN, 8, SCAN))) {
			BlockState s = level.getBlockState(p);
			if (!s.is(Blocks.LEVER) || s.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.WALL || lampOf(level, p, s) == null) continue;
			Direction facing = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
			boolean alongZ = facing.getAxis() == Direction.Axis.X;
			String wall = facing + ":" + (alongZ ? p.getX() : p.getZ());
			walls.computeIfAbsent(wall, k -> new HashMap<>()).put(new Cell(alongZ ? p.getZ() : p.getX(), p.getY()), p.immutable());
		}
		Map<Cell, BlockPos> most = Map.of();
		for (Map<Cell, BlockPos> w : walls.values()) if (w.size() > most.size()) most = w;
		List<BlockPos> found = new ArrayList<>();
		if (most.size() >= 12) for (Cell c : LightsWall.correct(most.keySet())) found.add(most.get(c));
		six = found;
	}
}
