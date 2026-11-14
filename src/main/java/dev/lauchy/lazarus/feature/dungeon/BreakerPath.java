package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Dug;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Breaker Path: while you hold the Dungeonbreaker, which breaks the dungeon's own blocks, the quickest way to the
 * blood room is drawn, straight through walls where breaking them beats walking round, and out past the edge of
 * the map where that's shorter. The blocks to break along it are boxed. Each block broken counts as a few blocks
 * of walking (yours to set), so a short way round is still walked. What can't be broken (bedrock, barriers, chests,
 * a wither door) is gone round. The blood room is where the map shows it; before the map does, it's found by its
 * own blocks, since the whole dungeon is there from the start and the blood room is the one full of red clay. So
 * the way shows from the spawn room on. The way is worked out once a run, from where you stand when the run's map
 * is first read, and kept: it's a route to follow from the start, not one that follows you about. Only the part
 * still ahead of you is drawn, so the line shortens as you go. A Lazarus+ feature, off unless you turn it on.
 */
public final class BreakerPath extends Feature {
	/** How far up and down of where you stand the way may go, how many spots a search looks at before giving up, and how near the blood room's middle counts as there. */
	private static final int BAND = 10, BUDGET = 120_000;
	private static final double NEAR = 12;
	private static final int LINE = 0xFFFF5555, BREAK = 0xFFFFAA00;
	/** How many cells each way of the map's first the blood room is looked for, and how much red clay (counting every other block each way) makes a room the blood room. */
	private static final int CELLS = 6, RED = 40;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Lazarus breaker path");
		t.setDaemon(true);
		return t;
	});

	private final IntSetting toll = add(new IntSetting("toll", "Block cost", "How many blocks of walking one broken block is worth. Higher keeps the Dungeonbreaker's charges for where they save the most.", 3, 1, 20, " blocks"));
	private final BoolSetting boxes = add(new BoolSetting("boxes", "Box the blocks to break", "Box each block on the way that has to be broken.", true));
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Draw the way over walls.", true));

	private volatile List<Vec3> line = List.of();
	private volatile List<BlockPos> breaks = List.of();
	private volatile boolean busy;
	/** Whether this run's way has been worked out (it's worked out once), and whether it's to be drawn: you're holding the Dungeonbreaker. */
	private volatile boolean done;
	private boolean showing;
	/** The blood room's middle as its blocks gave it away, {x, z}, the world that's in, and when it was last looked for. */
	private volatile int[] byBlocks;
	private Object byBlocksIn;
	private long lookedAt;
	private volatile int asked;
	private int ticks;

	public BreakerPath() {
		super("breakerPath", "Breaker Path", "While you hold the Dungeonbreaker, draws the quickest way from the start of the run to the blood room, through the walls it can break.", Category.DUNGEONS, false);
	}

	@Override
	public boolean plus() {
		return true;
	}

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.player == null || mc.level == null) {
				showing = false;
				clear();
				return;
			}
			Level level = mc.level;
			if (level != byBlocksIn) {
				// Another run: its way is worked out afresh, once.
				byBlocksIn = level;
				byBlocks = null;
				lookedAt = 0;
				clear();
			}
			showing = holding(mc);
			if (done || !Dungeon.placed() || ++ticks % 10 != 0 || busy) return;
			DungeonLayout layout = Dungeon.layout();
			MapFit fit = Dungeon.fit();
			DungeonLayout.Room blood = blood();
			double[] px = blood == null ? null : blood.centre(layout);
			int[] onMap = px == null ? null : new int[]{(int) Math.floor(fit.worldX(px[0])), (int) Math.floor(fit.worldZ(px[1]))};
			// Until the map shows the blood room it's looked for by its blocks, again every few seconds as more of the dungeon loads.
			long now = System.currentTimeMillis();
			boolean look = onMap == null && now - lookedAt > 4000;
			if (look) lookedAt = now;
			int cornerX = (int) Math.round(fit.worldX(layout.originX)), cornerZ = (int) Math.round(fit.worldZ(layout.originY));
			BlockPos feet = BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
			Spot start = new Spot(feet.getX(), feet.getY(), feet.getZ());
			int mine = asked, cost = toll.get();
			busy = true;
			WORKER.execute(() -> {
				try {
					if (look) {
						int[] found = redRoom(level, cornerX, cornerZ, feet.getY());
						if (found != null && (byBlocks == null || found[0] != byBlocks[0] || found[1] != byBlocks[1])) Lazarus.LOG.info("[Breaker] The blood room is taken to be at {}, {} by its red clay", found[0], found[1]);
						if (found != null) byBlocks = found;
					}
					int[] to = onMap != null ? onMap : byBlocks;
					if (to == null) return;
					Dug dug = find(level, start, new Spot(to[0], feet.getY(), to[1]), cost);
					if (mine != asked) return;
					done = true;
					Lazarus.LOG.info("[Breaker] The way to the blood room: {} blocks, {} to break{}", dug.path().size(), dug.breaks().size(), dug.reached() ? "" : " (stopped short)");
					line = TrailFinder.corners(dug.path()).stream().map(s -> new Vec3(s.x() + 0.5, s.y() + 1.0, s.z() + 0.5)).toList();
					breaks = dug.breaks().stream().map(s -> new BlockPos(s.x(), s.y(), s.z())).toList();
				} catch (RuntimeException e) {
					// The world changed under the search: the next one starts afresh.
					Lazarus.LOG.debug("Breaker path search failed", e);
				} finally {
					busy = false;
				}
			});
		});
		World.paint(() -> {
			List<Vec3> points = line;
			if (!enabled() || !showing || points.size() < 2) return;
			Minecraft mc = Minecraft.getInstance();
			// The way itself is fixed; what's drawn of it is only what's still ahead, from the spot on it nearest you.
			int from = 0;
			Vec3 near = points.getFirst();
			if (mc.player != null) {
				Vec3 you = mc.player.position().add(0, 1, 0);
				double best = Double.MAX_VALUE;
				for (int i = 0; i + 1 < points.size(); i++) {
					Vec3 a = points.get(i), ab = points.get(i + 1).subtract(a);
					double along = ab.lengthSqr() < 1e-9 ? 0 : Math.clamp(you.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
					Vec3 on = a.add(ab.scale(along));
					double far = on.distanceToSqr(you);
					if (far < best) {
						best = far;
						from = i;
						near = on;
					}
				}
			}
			for (int i = from; i + 1 < points.size(); i++) World.ribbon(i == from ? near : points.get(i), points.get(i + 1), LINE, Trail.WIDTH / 10.0, through.on());
			if (!boxes.on()) return;
			for (BlockPos b : breaks) {
				// Only the ones near enough to matter yet: the rest are along the line.
				if (mc.player != null && mc.level != null && b.distToCenterSqr(mc.player.position()) < 24 * 24 && !mc.level.getBlockState(b).isAir()) World.box(new AABB(b), BREAK, true, false, through.on());
			}
		});
	}

	private void clear() {
		done = false;
		if (line.isEmpty() && breaks.isEmpty()) return;
		asked++;
		line = List.of();
		breaks = List.of();
	}

	/** The blood room, once the map shows it. */
	private static DungeonLayout.Room blood() {
		for (DungeonLayout.Room r : Dungeon.layout().rooms) if (r.kind() == DungeonLayout.Kind.BLOOD) return r;
		return null;
	}

	/**
	 * The middle of the room with the most red clay in it, {x, z}: the blood room, which is full of it. The rooms sit
	 * on a grid of 32 blocks from the map's first cell; each is looked through where it's loaded, every other block.
	 * Null when no room has enough to be it.
	 */
	private static int[] redRoom(Level level, int cornerX, int cornerZ, int y) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int[] best = null;
		int most = RED - 1;
		for (int gx = -CELLS; gx <= CELLS; gx++) {
			for (int gz = -CELLS; gz <= CELLS; gz++) {
				int x0 = cornerX + gx * 32, z0 = cornerZ + gz * 32, red = 0;
				if (!level.hasChunk((x0 + 15) >> 4, (z0 + 15) >> 4)) continue;
				for (int x = x0; x < x0 + 31; x += 2) {
					for (int z = z0; z < z0 + 31; z += 2) {
						for (int h = y - 12; h <= y + 24; h += 2) if (level.getBlockState(at.set(x, h, z)).is(Blocks.RED_TERRACOTTA)) red++;
					}
				}
				if (red > most) {
					most = red;
					best = new int[]{x0 + 15, z0 + 15};
				}
			}
		}
		return best;
	}

	private static boolean holding(Minecraft mc) {
		return Items.name(mc.player.getMainHandItem()).toLowerCase(Locale.ROOT).contains("dungeonbreaker");
	}

	/** The way from where you stand to the blood room, read from the world's own blocks. Where nothing is loaded counts as open. */
	private static Dug find(Level level, Spot start, Spot goal, int toll) {
		// 0 open, 1 can be broken, 2 can't.
		Map<Long, Byte> seen = new HashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		java.util.function.ToIntFunction<long[]> kind = p -> {
			long key = BlockPos.asLong((int) p[0], (int) p[1], (int) p[2]);
			Byte known = seen.get(key);
			if (known != null) return known;
			BlockState s = level.getBlockState(at.set((int) p[0], (int) p[1], (int) p[2]));
			byte is = s.isAir() || s.getCollisionShape(level, at).isEmpty() ? 0 : unbreakable(level, at, s) ? (byte) 2 : 1;
			seen.put(key, is);
			return is;
		};
		return TrailFinder.dig((x, y, z) -> kind.applyAsInt(new long[]{x, y, z}) != 0, (x, y, z) -> kind.applyAsInt(new long[]{x, y, z}) == 2, start, goal, NEAR, BAND, BUDGET, toll);
	}

	/** What the Dungeonbreaker doesn't break: what nothing breaks, anything that holds something (a chest, a command block), and a wither door's coal. */
	private static boolean unbreakable(Level level, BlockPos at, BlockState s) {
		return s.getDestroySpeed(level, at) < 0 || s.getBlock() instanceof BaseEntityBlock || s.is(Blocks.COAL_BLOCK) || s.is(Blocks.BARRIER) || s.is(Blocks.BEDROCK);
	}
}
