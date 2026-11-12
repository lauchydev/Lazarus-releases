package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ice Fill, worked out: one walk over a sheet of ice that steps on every tile exactly once, from where you get on
 * to the far side where you get off. No Minecraft types, so it's tested on its own.
 */
final class IceFillPath {
	/** A tile of the sheet. */
	record Tile(int x, int z) {
	}

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private IceFillPath() {
	}

	/**
	 * A walk from {@code start} over every tile once, ending as far from the start as a walk can (the way off is
	 * across the sheet from the way on); empty when there is none.
	 */
	static List<Tile> solve(Set<Tile> tiles, Tile start) {
		if (!tiles.contains(start)) return List.of();
		List<Tile> best = new ArrayList<>();
		List<Tile> path = new ArrayList<>(List.of(start));
		Set<Tile> seen = new HashSet<>(Set.of(start));
		int[] budget = {400_000};
		walk(tiles, path, seen, best, start, budget);
		return best;
	}

	private static void walk(Set<Tile> tiles, List<Tile> path, Set<Tile> seen, List<Tile> best, Tile start, int[] budget) {
		if (budget[0]-- <= 0) return;
		Tile at = path.getLast();
		if (path.size() == tiles.size()) {
			if (best.isEmpty() || far(at, start) > far(best.getLast(), start)) {
				best.clear();
				best.addAll(path);
			}
			return;
		}
		if (!joined(tiles, seen, at) || deadEnds(tiles, seen, at) > 1) return; // some tile left is cut off, or two are dead ends: no walk from here covers them all
		for (int[] s : SIDES) {
			Tile next = new Tile(at.x() + s[0], at.z() + s[1]);
			if (!tiles.contains(next) || !seen.add(next)) continue;
			path.add(next);
			walk(tiles, path, seen, best, start, budget);
			path.removeLast();
			seen.remove(next);
		}
	}

	private static int far(Tile a, Tile b) {
		return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
	}

	/** How many tiles not yet walked have one way in and no way out: a walk can end at one of them, not two. */
	private static int deadEnds(Set<Tile> tiles, Set<Tile> seen, Tile at) {
		int ends = 0;
		for (Tile t : tiles) {
			if (seen.contains(t)) continue;
			int ways = 0;
			for (int[] s : SIDES) {
				Tile n = new Tile(t.x() + s[0], t.z() + s[1]);
				if (n.equals(at) || tiles.contains(n) && !seen.contains(n)) ways++;
			}
			if (ways <= 1) ends++;
		}
		return ends;
	}

	/** Whether every tile not yet walked can still be reached from where the walk is. */
	private static boolean joined(Set<Tile> tiles, Set<Tile> seen, Tile at) {
		Set<Tile> reach = new HashSet<>();
		ArrayDeque<Tile> queue = new ArrayDeque<>(List.of(at));
		while (!queue.isEmpty()) {
			Tile t = queue.poll();
			for (int[] s : SIDES) {
				Tile n = new Tile(t.x() + s[0], t.z() + s[1]);
				if (tiles.contains(n) && !seen.contains(n) && reach.add(n)) queue.add(n);
			}
		}
		return reach.size() == tiles.size() - seen.size();
	}
}
