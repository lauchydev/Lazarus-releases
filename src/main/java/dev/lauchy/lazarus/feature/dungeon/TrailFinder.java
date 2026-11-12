package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds a way through the air from one spot to another: round walls and anything solid, never through them. It
 * needs no floor (the trail is a line to follow, not footsteps), keeps level where it can, and rises or dips only
 * to get past what's in the way. Meant for short hops (across one room to its door), which is what keeps it quick.
 * No Minecraft types, so it's tested on its own.
 */
public final class TrailFinder {
	/** Whether the block at a place is solid: something in the way. */
	@FunctionalInterface
	public interface Solid {
		boolean at(int x, int y, int z);
	}

	/** A block position: where your feet would be. */
	public record Spot(int x, int y, int z) {
		/** How far apart on the flat. */
		double across(Spot o) {
			return Math.sqrt((x - o.x) * (double) (x - o.x) + (z - o.z) * (double) (z - o.z));
		}
	}

	/** A way found: the spots it turns at, start first, and whether it got there (else it ends as near as it could get). */
	public record Found(List<Spot> path, boolean reached) {
	}

	private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}, {0, 1, 0}, {0, -1, 0}};
	/** What a block up or down costs beside a block along: enough that a trail stays level unless it has to climb. */
	private static final double CLIMB = 1.5;
	/** How hard the search leans toward the goal: over 1 it looks at far fewer spots, for a way a little longer than the shortest. */
	private static final double LEAN = 1.2;

	private TrailFinder() {
	}

	/**
	 * The way through the air from {@code start} to {@code goal}, which counts as reached within {@code near} blocks on the
	 * flat and two up or down. It keeps within {@code band} blocks of the start's height and gives up after
	 * {@code budget} spots looked at.
	 */
	public static Found walk(Solid solid, Spot start, Spot goal, double near, int band, int budget) {
		Map<Spot, Spot> from = new HashMap<>();
		Map<Spot, Double> cost = new HashMap<>();
		record Open(Spot spot, double cost, double rank) {
		}
		// Nearest the goal first; of two as near, the one further along, so open floor is crossed in a straight run.
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> a.rank() != b.rank() ? Double.compare(a.rank(), b.rank()) : Double.compare(b.cost(), a.cost()));
		from.put(start, start);
		cost.put(start, 0.0);
		open.add(new Open(start, 0, start.across(goal) * LEAN));
		Spot end = start;
		double endAcross = Double.MAX_VALUE;
		boolean reached = false;
		while (!open.isEmpty() && budget-- > 0) {
			Open o = open.poll();
			Spot at = o.spot();
			if (o.cost() > cost.get(at)) continue;
			double across = at.across(goal);
			// The nearest so far, height counted too: a spot beside the goal beats one far above it.
			if (across + Math.abs(at.y() - goal.y()) < endAcross) {
				end = at;
				endAcross = across + Math.abs(at.y() - goal.y());
			}
			if (across <= near && Math.abs(at.y() - goal.y()) <= 2) {
				end = at;
				reached = true;
				break;
			}
			for (int[] s : STEPS) {
				Spot next = new Spot(at.x() + s[0], at.y() + s[1], at.z() + s[2]);
				if (Math.abs(next.y() - start.y()) > band || !free(solid, next)) continue;
				// A diagonal doesn't cut a corner: both sides of it must be free.
				if (s[0] != 0 && s[2] != 0 && (!free(solid, new Spot(at.x() + s[0], at.y(), at.z())) || !free(solid, new Spot(at.x(), at.y(), at.z() + s[2])))) continue;
				double c = o.cost() + (s[1] != 0 ? CLIMB : s[0] != 0 && s[2] != 0 ? 1.41 : 1);
				Double known = cost.get(next);
				if (known != null && known <= c) continue;
				cost.put(next, c);
				from.put(next, at);
				open.add(new Open(next, c, c + next.across(goal) * LEAN));
			}
		}
		List<Spot> path = new ArrayList<>();
		for (Spot p = end; ; p = from.get(p)) {
			path.add(p);
			if (p.equals(start)) break;
		}
		Collections.reverse(path);
		return new Found(pulled(solid, path), reached);
	}

	/** A way found by breaking through what's in the way: every spot of it, start first, the blocks to break along it, and whether it got there. */
	public record Dug(List<Spot> path, List<Spot> breaks, boolean reached) {
	}

	/**
	 * The quickest way from {@code start} to {@code goal} for someone who can break blocks: through the air where
	 * it's open, and through what's solid where that beats going round, each block broken costing {@code toll}
	 * blocks of walking. What's {@code hard} can't be broken and is gone round. The goal counts as reached within
	 * {@code near} blocks on the flat and two up or down; the way keeps within {@code band} blocks of the start's
	 * height and the search gives up after {@code budget} spots looked at, ending as near as it got.
	 */
	public static Dug dig(Solid solid, Solid hard, Spot start, Spot goal, double near, int band, int budget, double toll) {
		Map<Spot, Spot> from = new HashMap<>();
		Map<Spot, Double> cost = new HashMap<>();
		record Open(Spot spot, double cost, double rank) {
		}
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> a.rank() != b.rank() ? Double.compare(a.rank(), b.rank()) : Double.compare(b.cost(), a.cost()));
		from.put(start, start);
		cost.put(start, 0.0);
		open.add(new Open(start, 0, start.across(goal) * LEAN));
		Spot end = start;
		double endAcross = Double.MAX_VALUE;
		boolean reached = false;
		while (!open.isEmpty() && budget-- > 0) {
			Open o = open.poll();
			Spot at = o.spot();
			if (o.cost() > cost.get(at)) continue;
			double across = at.across(goal);
			if (across + Math.abs(at.y() - goal.y()) < endAcross) {
				end = at;
				endAcross = across + Math.abs(at.y() - goal.y());
			}
			if (across <= near && Math.abs(at.y() - goal.y()) <= 2) {
				end = at;
				reached = true;
				break;
			}
			for (int[] s : STEPS) {
				Spot next = new Spot(at.x() + s[0], at.y() + s[1], at.z() + s[2]);
				if (Math.abs(next.y() - start.y()) > band) continue;
				int broken = 0;
				boolean stopped = false;
				for (int up = 0; up <= 1; up++) {
					if (!solid.at(next.x(), next.y() + up, next.z())) continue;
					if (hard.at(next.x(), next.y() + up, next.z())) stopped = true;
					broken++;
				}
				if (stopped) continue;
				// A diagonal is only taken across the open: through a wall it's dug square on.
				if (s[0] != 0 && s[2] != 0 && (broken > 0 || !free(solid, new Spot(at.x() + s[0], at.y(), at.z())) || !free(solid, new Spot(at.x(), at.y(), at.z() + s[2])))) continue;
				double c = o.cost() + (s[1] != 0 ? CLIMB : s[0] != 0 && s[2] != 0 ? 1.41 : 1) + broken * toll;
				Double known = cost.get(next);
				if (known != null && known <= c) continue;
				cost.put(next, c);
				from.put(next, at);
				open.add(new Open(next, c, c + next.across(goal) * LEAN));
			}
		}
		List<Spot> path = new ArrayList<>();
		for (Spot p = end; ; p = from.get(p)) {
			path.add(p);
			if (p.equals(start)) break;
		}
		Collections.reverse(path);
		List<Spot> breaks = new ArrayList<>();
		for (Spot p : path) {
			for (int up = 0; up <= 1; up++) if (solid.at(p.x(), p.y() + up, p.z())) breaks.add(new Spot(p.x(), p.y() + up, p.z()));
		}
		return new Dug(List.copyOf(path), List.copyOf(breaks), reached);
	}

	/** A doorway found: its middle, on its floor, and whether it's surely a door between rooms and not some gap in a wall. */
	public record Doorway(Spot spot, boolean sure) {
	}

	/**
	 * The middle of a doorway near where the map puts a door. The map is only good to a block or two, so the wall
	 * is looked for round {@code about}, {@code reach} blocks along it and {@code depth} through it: a gap two to
	 * five blocks wide with wall on both sides and a floor under it. Rooms are full of such gaps (arches, windows),
	 * so one is {@link Doorway#sure sure} only when a shut door fills it or it tunnels through a thick wall, as the
	 * walls between rooms are; of several, a sure one wins, then the nearest. {@code alongX} says the way through
	 * runs east-west (so the gap is measured north-south); {@code shut} is {@code solid} with a shut door's blocks
	 * counted open. Null when there's no such gap.
	 */
	public static Doorway doorway(Solid solid, Solid shut, Spot about, boolean alongX, int reach, int depth) {
		Doorway best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dy = -6; dy <= 6; dy++) {
			int y = about.y() + dy;
			for (int through = -depth; through <= depth; through++) {
				int run = 0, doors = 0;
				for (int side = -reach - 1; side <= reach + 1; side++) {
					Spot at = cell(about, alongX, through, side, y);
					if (gap(solid, shut, at)) {
						run++;
						if (solid.at(at.x(), at.y(), at.z())) doors++;
						continue;
					}
					// A run just ended against wall: a doorway if wall began it too, and it's a door's width.
					if (run >= 2 && run <= 5 && side - run > -reach - 1) {
						int mid = side - 1 - run / 2, thick = 1;
						for (int step = 1; step <= 2 && walled(solid, shut, cell(about, alongX, through + step, mid, y), alongX); step++) thick++;
						for (int step = 1; step <= 2 && walled(solid, shut, cell(about, alongX, through - step, mid, y), alongX); step++) thick++;
						boolean sure = doors >= 2 || thick >= 3;
						double score = Math.abs(mid) + Math.abs(through) * 0.5 + Math.abs(dy) * 0.5 - (sure ? 20 : 0);
						if (score < bestScore) {
							bestScore = score;
							best = new Doorway(cell(about, alongX, through, mid, y), sure);
						}
					}
					run = 0;
					doors = 0;
				}
			}
		}
		return best;
	}

	/** The spot {@code through} blocks on through a wall and {@code side} blocks along it from a place. */
	private static Spot cell(Spot about, boolean alongX, int through, int side, int y) {
		return new Spot(about.x() + (alongX ? through : side), y, about.z() + (alongX ? side : through));
	}

	/** Whether a spot is part of a way through: open (a shut door counts) two blocks high, with a floor under it. */
	private static boolean gap(Solid solid, Solid shut, Spot p) {
		return !shut.at(p.x(), p.y(), p.z()) && !shut.at(p.x(), p.y() + 1, p.z()) && solid.at(p.x(), p.y() - 1, p.z());
	}

	/** Whether a spot is a way through with wall close on both sides of it: the inside of a doorway, not open room. */
	private static boolean walled(Solid solid, Solid shut, Spot p, boolean alongX) {
		if (!gap(solid, shut, p)) return false;
		for (int way = -1; way <= 1; way += 2) {
			boolean wall = false;
			for (int step = 1; step <= 3 && !wall; step++) wall = !gap(solid, shut, new Spot(p.x() + (alongX ? 0 : way * step), p.y(), p.z() + (alongX ? way * step : 0)));
			if (!wall) return false;
		}
		return true;
	}

	/** Whether you can stand at a spot: room for you, and something under your feet. */
	public static boolean standable(Solid solid, Spot p) {
		return free(solid, p) && solid.at(p.x(), p.y() - 1, p.z());
	}

	/** Room for you at a spot: its block and the one above are not solid. */
	public static boolean free(Solid solid, Spot p) {
		return !solid.at(p.x(), p.y(), p.z()) && !solid.at(p.x(), p.y() + 1, p.z());
	}

	/**
	 * A path pulled taut: as few, as long straight lines as its walls allow. A teleport covers a long line in one go
	 * and a turn costs another, so each line runs on to the furthest spot of the path it can reach without touching
	 * anything solid, up and down as well as along.
	 */
	public static List<Spot> pulled(Solid solid, List<Spot> path) {
		// First along the path, each line as far as it stays clear; then again over what's left, each line to the furthest spot in sight.
		List<Spot> once = new ArrayList<>();
		once.add(path.getFirst());
		for (int i = 0; i + 1 < path.size(); ) {
			int j = i + 1;
			while (j + 1 < path.size() && clear(solid, path.get(i), path.get(j + 1))) j++;
			once.add(path.get(j));
			i = j;
		}
		List<Spot> out = new ArrayList<>();
		out.add(once.getFirst());
		for (int i = 0; i + 1 < once.size(); ) {
			int j = once.size() - 1;
			while (j > i + 1 && !clear(solid, once.get(i), once.get(j))) j--;
			out.add(once.get(j));
			i = j;
		}
		return corners(out);
	}

	/** Whether the straight line from one spot to another is clear: nothing solid within your width of it, at your height along it. */
	private static boolean clear(Solid solid, Spot a, Spot b) {
		double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z(), flat = Math.sqrt(dx * dx + dz * dz), length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length == 0) return true;
		double sideX = flat == 0 ? 0 : -dz / flat * 0.3, sideZ = flat == 0 ? 0 : dx / flat * 0.3;
		for (double t = 0; t <= length; t += 0.2) {
			double x = a.x() + 0.5 + dx / length * t, y = a.y() + dy / length * t, z = a.z() + 0.5 + dz / length * t;
			// On a slope the line passes between two heights: both must be open.
			for (int h = (int) Math.floor(y); h <= (int) Math.ceil(y); h++) {
				for (int side = -1; side <= 1; side++) {
					if (!free(solid, new Spot((int) Math.floor(x + sideX * side), h, (int) Math.floor(z + sideZ * side)))) return false;
				}
			}
		}
		return true;
	}

	/** A path with the spots along a straight run left out: only where it turns, and its two ends. */
	static List<Spot> corners(List<Spot> path) {
		if (path.size() < 3) return List.copyOf(path);
		List<Spot> out = new ArrayList<>();
		out.add(path.getFirst());
		for (int i = 1; i + 1 < path.size(); i++) {
			Spot a = path.get(i - 1), b = path.get(i), c = path.get(i + 1);
			// Straight on when the two steps point the same way, whatever their lengths.
			long ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - b.x(), vy = c.y() - b.y(), vz = c.z() - b.z();
			boolean same = ux * vy == uy * vx && ux * vz == uz * vx && uy * vz == uz * vy && ux * vx + uy * vy + uz * vz > 0;
			if (!same) out.add(b);
		}
		out.add(path.getLast());
		return List.copyOf(out);
	}
}
