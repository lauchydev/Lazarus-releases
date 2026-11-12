package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trails in the world: a line in the air from you to the middle of the doorway you should leave the room by. The
 * way between rooms comes from the map (which doors lead there: {@link DungeonLayout#route}), so it's always one
 * that exists; the doorway is found in the wall itself and the line to it goes round what's in the way
 * ({@link TrailPlan}). A trail is planned once, from where you were when it was first wanted, and then kept: as you
 * move only the part behind you is dropped, and if you leave it only the way back to it is found. A door whose
 * doorway can't be found, or reached, gets a marker where the map puts it instead of a line.
 */
final class Trail {
	/** Somewhere a trail leads: the doors to go through on the way, in order, ending at the place itself; its colour; and whether to draw the whole way or only to the first door. */
	record Goal(List<Via> via, int colour, boolean whole) {
	}

	/** A place a trail goes through: a door (which way the way through it runs: 'x', 'z') or a plain spot ('.'). */
	record Via(Vec3 at, char runs) {
		static Via spot(Vec3 at) {
			return new Via(at, '.');
		}

		/** A door of the map as a place in the world: the way through runs across the gap between its two cells. */
		static Via door(DungeonLayout layout, MapFit fit, DungeonLayout.Door d) {
			int[] at = fit.door(layout, d);
			return new Via(new Vec3(at[0], 0, at[1]), at[2] == 1 ? 'x' : 'z');
		}
	}

	/** A trail: the points it turns at, from your feet, and its colour. */
	record Line(List<Vec3> points, int colour) {
	}

	/** A marker for a door no trail reaches: where it stands, and its colour. */
	record Marker(Vec3 at, int colour) {
	}

	/** What a search found. */
	record Found(List<Line> lines, List<Marker> markers) {
		static final Found NONE = new Found(List.of(), List.of());
	}

	/** How far above the floor of the block it passes through a trail is drawn. */
	private static final double LIFT = 1.0;
	/** How wide a trail is drawn unless a feature says otherwise, in tenths of a block. */
	static final float WIDTH = 3f;
	/** Trails are worked out off the game's own thread, one search at a time, so they never stall a frame. */
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Lazarus trails");
		t.setDaemon(true);
		return t;
	});
	/** Every feature's trails, for a snap of the dungeon. */
	private static final List<Search> SEARCHES = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** How far out the map is in this dungeon, learnt from its doorways, and the world it was learnt in. */
	private static TrailPlan.Fix fix = new TrailPlan.Fix();
	private static Object fixFor;

	private Trail() {
	}

	static List<Search> searches() {
		return SEARCHES;
	}

	/** The doors to go through from the room you're in to another, as places in the world; null when the map shows no way. */
	static List<Via> via(DungeonLayout layout, MapFit fit, DungeonLayout.Room here, DungeonLayout.Room to) {
		List<DungeonLayout.Door> doors = DungeonLayout.route(layout.doors, here.id(), to.id());
		if (doors == null) return null;
		List<Via> out = new ArrayList<>();
		for (DungeonLayout.Door d : doors) {
			out.add(Via.door(layout, fit, d));
			if (d.lock() != DungeonLayout.Lock.NONE) break; // no further than a door still locked
		}
		return out;
	}

	/** The lock on the first door still locked on the way from the room you're in to another: where a trail to it stops. NONE when the way is open. */
	static DungeonLayout.Lock lockOnWay(DungeonLayout layout, DungeonLayout.Room here, DungeonLayout.Room to) {
		List<DungeonLayout.Door> doors = DungeonLayout.route(layout.doors, here.id(), to.id());
		if (doors != null) for (DungeonLayout.Door d : doors) if (d.lock() != DungeonLayout.Lock.NONE) return d.lock();
		return DungeonLayout.Lock.NONE;
	}

	/**
	 * One feature's trails, kept up to date in the background: {@link #ask} starts a search unless one is still
	 * running, and {@link #found} is what the last one found.
	 */
	static final class Search {
		private volatile Found found = Found.NONE;
		private volatile List<Goal> last = List.of();
		private volatile boolean busy;
		private volatile int asked;
		/** Each trail as first planned, kept for as long as it's asked for. */
		private final Map<Goal, List<Spot>> planned = new java.util.concurrent.ConcurrentHashMap<>();
		/** The trails that stopped short of where they were going, and when each was planned. */
		private final Map<Goal, Long> shortSince = new java.util.concurrent.ConcurrentHashMap<>();
		/** Where you stood, what was asked for and when, at the last search started. */
		private BlockPos foundFrom;
		private List<Goal> foundFor;
		private long foundAt;

		Search() {
			SEARCHES.add(this);
		}

		/** The goals last asked for, for a snap of the dungeon to save beside what was found. */
		List<Goal> asked() {
			return last;
		}

		/** Looks for trails from where you stand to these goals; the ones showing stay until it's done. */
		void ask(Minecraft mc, List<Goal> goals) {
			if (goals.isEmpty() || mc.player == null || mc.level == null) {
				clear();
				return;
			}
			last = List.copyOf(goals);
			if (busy) return;
			BlockPos standing = BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
			long now = System.currentTimeMillis();
			// Standing where you were with the same places to go: what was found less than a second ago still holds.
			if (standing.equals(foundFrom) && last.equals(foundFor) && now - foundAt < 1000) return;
			foundFrom = standing;
			foundFor = last;
			foundAt = now;
			busy = true;
			int mine = asked;
			Level level = mc.level;
			if (level != fixFor) {
				fixFor = level;
				fix = new TrailPlan.Fix();
			}
			TrailPlan.Fix shift = fix;
			BlockPos feet = standing;
			List<Goal> wanted = List.copyOf(goals);
			WORKER.execute(() -> {
				try {
					Found got = find(level, feet, wanted, shift, planned, shortSince);
					if (mine == asked) found = got;
				} catch (RuntimeException e) {
					// The world changed under the search: the next one starts afresh.
					Lazarus.LOG.debug("Trail search failed", e);
				} finally {
					busy = false;
				}
			});
		}

		/** No trails, and none from a search still running. */
		void clear() {
			asked++;
			planned.clear();
			shortSince.clear();
			found = Found.NONE;
			last = List.of();
			foundFrom = null;
		}

		Found found() {
			return found;
		}

		List<Line> lines() {
			return found.lines();
		}
	}

	/** The trails from where you stand to each goal ({@link TrailPlan}), read from the world's own blocks. */
	private static Found find(Level level, BlockPos feet, List<Goal> goals, TrailPlan.Fix shift, Map<Goal, List<Spot>> planned, Map<Goal, Long> shortSince) {
		Map<Long, Boolean> seen = new HashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		TrailPlan plan = new TrailPlan(new TrailPlan.Blocks() {
			@Override
			public boolean solid(int x, int y, int z) {
				long key = BlockPos.asLong(x, y, z);
				Boolean known = seen.get(key);
				if (known != null) return known;
				BlockState s = level.getBlockState(at.set(x, y, z));
				boolean is = !s.isAir() && !s.getCollisionShape(level, at).isEmpty();
				seen.put(key, is);
				return is;
			}

			@Override
			public boolean shut(int x, int y, int z) {
				// For finding a doorway, a door still shut counts as open: bars, a wither door's coal, the blood door's terracotta.
				if (!solid(x, y, z)) return false;
				BlockState s = level.getBlockState(new BlockPos(x, y, z));
				return !s.is(Blocks.IRON_BARS) && !s.is(Blocks.COAL_BLOCK) && !s.is(Blocks.RED_TERRACOTTA);
			}

			@Override
			public boolean loaded(int x, int z) {
				return level.hasChunk(x >> 4, z >> 4);
			}
		}, shift);
		List<Line> lines = new ArrayList<>();
		List<Marker> markers = new ArrayList<>();
		Spot start = plan.start(feet.getX(), feet.getY(), feet.getZ());
		if (start == null) return Found.NONE;
		planned.keySet().retainAll(goals); // a trail no longer asked for is forgotten
		shortSince.keySet().retainAll(goals);
		for (Goal goal : goals) {
			// A trail is planned once, from where you were when it was first wanted, and kept: after that only your
			// way along it, or back to it, is worked out.
			// One that stopped short (the rooms ahead weren't loaded yet) is planned again every couple of seconds.
			Long shortAt = shortSince.get(goal);
			if (shortAt != null && System.currentTimeMillis() - shortAt > 2000) planned.remove(goal);
			List<Spot> kept = planned.get(goal), shown = kept == null ? null : plan.follow(start, kept);
			if (shown == null) {
				List<TrailPlan.Stop> stops = goal.via().stream().map(v -> new TrailPlan.Stop((int) Math.floor(v.at().x), (int) Math.floor(v.at().z), v.runs())).toList();
				TrailPlan.Route route = plan.route(start, stops, goal.whole());
				if (route.marker() != null) markers.add(new Marker(point(route.marker()), goal.colour()));
				if (route.line().size() > 1) planned.put(goal, route.line());
				else planned.remove(goal);
				if (route.complete()) shortSince.remove(goal);
				else shortSince.put(goal, System.currentTimeMillis());
				shown = route.line();
			}
			if (shown.size() > 1) lines.add(new Line(shown.stream().map(Trail::point).toList(), goal.colour()));
		}
		return new Found(lines, markers);
	}

	private static Vec3 point(Spot s) {
		return new Vec3(s.x() + 0.5, s.y() + LIFT, s.z() + 0.5);
	}

	/**
	 * Draws what a search found: each trail a flat band this many tenths of a block wide, starting where you are
	 * this moment, and each marker a small box; seen through walls or not.
	 */
	static void draw(Found found, float width, boolean throughWalls) {
		Minecraft mc = Minecraft.getInstance();
		for (Line l : found.lines()) {
			for (int i = 0; i + 1 < l.points().size(); i++) {
				Vec3 a = i == 0 && mc.player != null ? mc.player.position().add(0, LIFT, 0) : l.points().get(i);
				World.ribbon(a, l.points().get(i + 1), l.colour(), width / 10.0, throughWalls);
			}
		}
		for (Marker m : found.markers()) World.box(AABB.ofSize(m.at(), 0.5, 0.5, 0.5), m.colour(), true, true, throughWalls);
	}
}
