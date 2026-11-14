package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.LightsWall.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightsWallTest {
	private static Set<Cell> wall(int left, int right, int low, int high) {
		Set<Cell> wall = new HashSet<>();
		for (int a = left; a <= right; a++) for (int u = low; u <= high; u++) wall.add(new Cell(a, u));
		return wall;
	}

	@Test
	void theSixLeversAreTheCornersAndTheTwoInnerOnesOfTheMiddleColumn() {
		// Hypixel's device, where the six are known by their coordinates: x 58 to 62, y 133 to 136.
		Set<Cell> expected = Set.of(new Cell(58, 136), new Cell(58, 133), new Cell(60, 135), new Cell(60, 134), new Cell(62, 136), new Cell(62, 133));
		assertEquals(expected, LightsWall.correct(wall(58, 62, 133, 136)));
	}

	@Test
	void itIsTheSameWhereverTheWallStands() {
		Set<Cell> expected = Set.of(new Cell(-7, 70), new Cell(-7, 67), new Cell(-5, 69), new Cell(-5, 68), new Cell(-3, 70), new Cell(-3, 67));
		assertEquals(expected, LightsWall.correct(wall(-7, -3, 67, 70)));
	}

	@Test
	void aFewStrayLeversAreNotTheDevice() {
		assertTrue(LightsWall.correct(wall(0, 1, 0, 1)).isEmpty());
		assertTrue(LightsWall.correct(Set.of()).isEmpty());
	}
}
