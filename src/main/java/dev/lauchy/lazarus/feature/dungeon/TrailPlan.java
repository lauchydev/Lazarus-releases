package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Solid;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A trail planned door by door: from where you stand to the middle of the doorway you leave the room by, and on
 * through the next ones when the whole way is asked for. A doorway is found in the wall itself, near where the map
 * puts it; the map is only good to a block or two, so each doorway found for sure (a shut door, or a tunnel through
 * a thick wall) says how far out the map is, and the others are looked for where that puts them ({@link Fix}). A
 * trail never ends anywhere but a doorway: where none is found, or there's no clear way to it, a marker stands
 * where the map puts the door instead. No Minecraft types, so a dungeon saved from the game ({@link DungeonSnap})
 * can be planned again in a test, exactly as the game planned it.
 */
final class TrailPlan {
	/** A place the trail goes through, as the map puts it: a door (the way through it runs along 'x' or 'z') or a plain spot ('.'). */
	record Stop(int x, int z, char runs) {
	}

	/** What was planned: the spots the trail turns at (fewer than two when there's no trail), where a marker stands for a door not reached (or null), and whether it got through every stop asked for. */
	record Route(List<Spot> line, Spot marker, boolean complete) {
	}

	/** The blocks a plan reads: what's solid, what's solid once a shut door is counted open, and which columns the game has loaded. */
	interface Blocks {
		boolean solid(int x, int y, int z);

		boolean shut(int x, int y, int z);

		boolean loaded(int x, int z);
	}

	/**
	 * How far out the map is for this dungeon: where each doorway found for sure really was, against where the map
	 * put it. The same for every door (the map is out by one shift, not a different one each), so it's kept for the
	 * whole run and the middle of what's been seen is used.
	 */
	static final class Fix {
		private final Map<Stop, int[]> seen = new HashMap<>();

		synchronized void learn(Stop stop, Spot real) {
			seen.put(stop, new int[]{real.x() - stop.x(), real.z() - stop.z()});
		}

		/** The shift to add to where the map puts a door, or null before any doorway has been found for sure. */
		synchronized int[] shift() {
			if (seen.isEmpty()) return null;
			int[] xs = seen.values().stream().mapToInt(o -> o[0]).sorted().toArray(), zs = seen.values().stream().mapToInt(o -> o[1]).sorted().toArray();
			return new int[]{xs[xs.length / 2], zs[zs.length / 2]};
		}
	}

	/** How many spots the hop across one room may look at, and how far up or down from where it starts. */
	static final int BUDGET = 20_000, BAND = 12;

	private final Blocks blocks;
	private final Fix fix;
	private final Solid solid, shut;
	/** The same hop (to the first door, say) serves every trail that goes that way; so does a doorway once found. */
	private final Map<List<Spot>, TrailFinder.Found> hops = new HashMap<>();
	private final Map<Stop, Spot> doorways = new HashMap<>();

	TrailPlan(Blocks blocks, Fix fix) {
		this.blocks = blocks;
		this.fix = fix;
		this.solid = blocks::solid;
		this.shut = blocks::shut;
	}

	/** Where a trail starts for someone whose feet are in this block: the open spot there, or null when there's none. */
	Spot start(int x, int y, int z) {
		return stand(x, y, z, 0);
	}

	/**
	 * Where a stop really is: for a door, the middle of its doorway in the wall; for a plain spot, the open spot
	 * nearest it. Null when its blocks aren't loaded, or no doorway there can be told for one.
	 */
	Spot place(Stop stop, int y) {
		if (!blocks.loaded(stop.x(), stop.z())) return null;
		if (stop.runs() == '.') return stand(stop.x(), y, stop.z(), 2);
		Spot known = doorways.get(stop);
		if (known != null) return known;
		int[] shift = fix.shift();
		// With the map's shift known the doorway is looked for close by; without, wider, and only a sure one is taken.
		Spot about = new Spot(stop.x() + (shift == null ? 0 : shift[0]), y, stop.z() + (shift == null ? 0 : shift[1]));
		TrailFinder.Doorway found = TrailFinder.doorway(solid, shut, about, stop.runs() == 'x', shift == null ? 7 : 3, shift == null ? 3 : 2);
		if (found == null || !found.sure() && shift == null) return null;
		if (found.sure()) fix.learn(stop, found.spot());
		doorways.put(stop, found.spot());
		return found.spot();
	}

	/**
	 * The trail from {@code start} to the first stop, or through them all when {@code whole}. It ends at the last
	 * doorway reached; a stop that can't be placed, or reached by a clear way, gets a marker and ends the trail.
	 */
	Route route(Spot start, List<Stop> stops, boolean whole) {
		List<Spot> spots = new ArrayList<>(List.of(start));
		Spot from = start, marker = null;
		boolean complete = true;
		for (Stop stop : stops) {
			// Past what the game has loaded there's nothing to plan through yet: the trail ends at the last doorway known, with no marker.
			if (!blocks.loaded(stop.x(), stop.z())) {
				complete = false;
				break;
			}
			Spot to = place(stop, from.y());
			if (to == null) {
				marker = new Spot(stop.x(), from.y(), stop.z());
				complete = false;
				break;
			}
			Spot f = from, t = to;
			TrailFinder.Found hop = hops.computeIfAbsent(List.of(f, t), k -> TrailFinder.walk(solid, f, t, 2.5, BAND, BUDGET));
			// A shut door is solid right through, and its middle may lie nearer its far side: getting up against the
			// near face of it is getting there.
			Spot got = hop.path().getLast();
			boolean atDoor = !hop.reached() && !TrailFinder.free(solid, to) && got.across(to) <= SHUT_REACH && Math.abs(got.y() - to.y()) <= 3;
			if (!hop.reached() && !atDoor) {
				marker = to;
				complete = false;
				break;
			}
			spots.addAll(hop.path().subList(1, hop.path().size()));
			// Right to the doorway's middle, when it's open to stand in.
			if (TrailFinder.free(solid, to) && !to.equals(spots.getLast())) spots.add(to);
			from = spots.getLast();
			if (!whole) break;
		}
		// Pulled taut over the whole way: a door in sight is gone straight through, not turned at.
		return new Route(spots.size() > 1 ? TrailFinder.pulled(solid, spots) : List.of(), marker, complete);
	}

	/** How near a shut door's middle counts as at the door: a door is up to three blocks thick, and you stand a block off it. */
	static final double SHUT_REACH = 5.5;
	/** How far off a trail you can be and still be on it. */
	static final double ON_TRAIL = 2.5;

	/**
	 * A trail already planned, as it's shown to someone part of the way along it or off to one side. The trail
	 * itself is kept as it was first planned, from where you came into the room: it doesn't shift about as you
	 * move. On it, what's behind you is dropped. Off it, a way back is found to the nearest point of it and the
	 * rest follows from there. Null when there's no way back: the trail is to be planned afresh from where you are.
	 */
	List<Spot> follow(Spot you, List<Spot> planned) {
		if (planned.size() < 2) return null;
		// The nearest point of the trail: on which run of it, and how far along that run.
		int run = 0;
		double along = 0, nearest = Double.MAX_VALUE;
		for (int i = 0; i + 1 < planned.size(); i++) {
			Spot a = planned.get(i), b = planned.get(i + 1);
			double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z(), length = dx * dx + dy * dy + dz * dz;
			double f = length == 0 ? 0 : Math.clamp(((you.x() - a.x()) * dx + (you.y() - a.y()) * dy + (you.z() - a.z()) * dz) / length, 0, 1);
			double far = Math.pow(a.x() + dx * f - you.x(), 2) + Math.pow(a.y() + dy * f - you.y(), 2) + Math.pow(a.z() + dz * f - you.z(), 2);
			if (far <= nearest) { // of two as near, the one further along
				nearest = far;
				run = i;
				along = f;
			}
		}
		Spot a = planned.get(run), b = planned.get(run + 1);
		Spot join = new Spot((int) Math.round(a.x() + (b.x() - a.x()) * along), (int) Math.round(a.y() + (b.y() - a.y()) * along), (int) Math.round(a.z() + (b.z() - a.z()) * along));
		List<Spot> rest = new ArrayList<>(planned.subList(run + 1, planned.size()));
		List<Spot> out = new ArrayList<>();
		if (Math.sqrt(nearest) <= ON_TRAIL) {
			out.add(you);
		} else {
			// Off the trail: only the way back to it is found; the rest stays as planned.
			TrailFinder.Found back = hops.computeIfAbsent(List.of(you, join), k -> TrailFinder.walk(solid, you, join, 1.5, BAND, BUDGET));
			if (!back.reached()) return null;
			out.addAll(back.path());
			if (!join.equals(out.getLast()) && TrailFinder.free(solid, join)) out.add(join);
		}
		for (Spot s : rest) if (!s.equals(out.getLast())) out.add(s);
		// Spots along one straight run are left out; the trail itself is not pulled again, so it stays where it was planned.
		return out.size() > 1 ? TrailFinder.corners(out) : null;
	}

	/** The open spot nearest a place: within {@code reach} blocks of it on the flat, as near its height as there is one, on a floor for choice. Null when there's none. */
	private Spot stand(int x, int y, int z, int reach) {
		Spot best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = -8; dy <= 8; dy++) {
					Spot s = new Spot(x + dx, y + dy, z + dz);
					if (!TrailFinder.free(solid, s)) continue;
					double d = Math.abs(dy) + (Math.abs(dx) + Math.abs(dz)) * 3 + (TrailFinder.standable(solid, s) ? 0 : 6);
					if (d < bestD) {
						best = s;
						bestD = d;
					}
				}
			}
		}
		return best;
	}
}
