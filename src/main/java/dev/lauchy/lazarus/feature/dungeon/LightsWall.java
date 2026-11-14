package dev.lauchy.lazarus.feature.dungeon;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which levers of the lights device are the ones to flick. The device is a wall of lamps five wide and four high
 * with a lever on each, and only six of them count: the four corners, and the two in the middle column that sit
 * one in from its top and its bottom. The rest do nothing. (The same six as on Hypixel's device, where they are
 * at x 58, 60 and 62 and y 133 to 136; here they're found from the wall's own shape, wherever it stands.) No
 * Minecraft types, so it's tested on its own.
 */
final class LightsWall {
	/** A lever's place on the wall: how far along it, and how high. */
	record Cell(int along, int up) {
	}

	private LightsWall() {
	}

	/** The levers to flick, of a wall with levers at these places; none when the wall is too small to be the device. */
	static Set<Cell> correct(Set<Cell> wall) {
		if (wall.isEmpty()) return Set.of();
		int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		for (Cell c : wall) {
			left = Math.min(left, c.along());
			right = Math.max(right, c.along());
			low = Math.min(low, c.up());
			high = Math.max(high, c.up());
		}
		if (right - left < 2 || high - low < 3) return Set.of();
		int middle = (left + right) / 2;
		Set<Cell> out = new LinkedHashSet<>();
		for (Cell c : new Cell[]{new Cell(left, high), new Cell(right, high), new Cell(middle, high - 1), new Cell(middle, low + 1), new Cell(left, low), new Cell(right, low)}) {
			if (wall.contains(c)) out.add(c);
		}
		return out;
	}
}
