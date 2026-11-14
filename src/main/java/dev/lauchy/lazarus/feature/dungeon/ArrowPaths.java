package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arrow device at the gates: a wall of item frames with an arrow in each, to be turned until they lead along
 * their paths to the ends. This works out which way each arrow should point, and how many clicks that is.
 *
 * A frame's place is its {@link Cell}: how far left and how far up it is as you face the wall. Which frames hold
 * arrows gives the layout away: the device comes in nine known layouts (the table is Odin's, see
 * assets/lazarus/puzzles/ODIN-LICENSE.txt), matched by shape wherever on the wall they sit. A layout not in the
 * table is solved from the frames themselves: each arrow points at the next one on its way to an end. No Minecraft
 * types, so it's tested on its own.
 */
final class ArrowPaths {
	/** A frame's turn when its arrow points right, down, left and up, as you face it. Each click turns it one on, of eight. */
	static final int RIGHT = 1, DOWN = 3, LEFT = 5, UP = 7;

	/** A frame on the wall: {@code left} counts leftward and {@code up} upward as you face it. */
	record Cell(int left, int up) {
	}

	/** The known layouts: a turn for each of the 25 frames, at {@code up + left * 5}, or -1 where there's no arrow. */
	private static final int[][] KNOWN = {
			{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1},
			{-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1},
			{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3},
			{5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1},
			{5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
			{7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
			{-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
			{-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1},
			{-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
	};

	private ArrowPaths() {
	}

	/** How many clicks take a frame from one turn to another: each click turns it one on, and eight come back round. */
	static int clicks(int from, int to) {
		return Math.floorMod(to - from, 8);
	}

	/**
	 * The turn each arrow should end at, for the frames holding {@code arrows} and those holding the paths'
	 * {@code ends}; empty when the layout isn't known and the frames don't lead to an end.
	 */
	static Map<Cell, Integer> targets(Set<Cell> arrows, Set<Cell> ends) {
		if (arrows.isEmpty()) return Map.of();
		Map<Cell, Integer> known = known(arrows);
		return known != null ? known : led(arrows, ends);
	}

	/** The known layout whose arrows make the same shape as these, moved to where these are; null when none does. */
	private static Map<Cell, Integer> known(Set<Cell> arrows) {
		int left = arrows.stream().mapToInt(Cell::left).min().orElse(0), up = arrows.stream().mapToInt(Cell::up).min().orElse(0);
		for (int[] layout : KNOWN) {
			int minLeft = 5, minUp = 5, count = 0;
			for (int i = 0; i < 25; i++) {
				if (layout[i] < 0) continue;
				minLeft = Math.min(minLeft, i / 5);
				minUp = Math.min(minUp, i % 5);
				count++;
			}
			if (count != arrows.size()) continue;
			Map<Cell, Integer> out = new HashMap<>();
			for (int i = 0; i < 25; i++) {
				if (layout[i] < 0) continue;
				Cell at = new Cell(i / 5 - minLeft + left, i % 5 - minUp + up);
				if (!arrows.contains(at)) break;
				out.put(at, layout[i]);
			}
			if (out.size() == count) return out;
		}
		return null;
	}

	/** Each arrow pointed at the frame beside it that's a step nearer an end; empty unless every arrow reaches one. */
	private static Map<Cell, Integer> led(Set<Cell> arrows, Set<Cell> ends) {
		Map<Cell, Integer> steps = new HashMap<>(), out = new HashMap<>();
		ArrayDeque<Cell> queue = new ArrayDeque<>(ends);
		for (Cell e : ends) steps.put(e, 0);
		while (!queue.isEmpty()) {
			Cell at = queue.poll();
			for (int[] d : List.of(new int[]{-1, 0, LEFT}, new int[]{1, 0, RIGHT}, new int[]{0, -1, UP}, new int[]{0, 1, DOWN})) {
				// The frame beside this one, and the turn that points it back here.
				Cell beside = new Cell(at.left() + d[0], at.up() + d[1]);
				if (!arrows.contains(beside) || steps.containsKey(beside)) continue;
				steps.put(beside, steps.get(at) + 1);
				out.put(beside, d[2]);
				queue.add(beside);
			}
		}
		return out.size() == arrows.size() ? out : Map.of();
	}
}
