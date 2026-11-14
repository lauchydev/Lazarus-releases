package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Dug;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Solid;
import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailFinderDigTest {
	private static final Solid NOTHING = (x, y, z) -> false;
	private static final Spot START = new Spot(0, 70, 0), GOAL = new Spot(20, 70, 0);

	/** A wall one block thick at x = 10, from z = -{@code half} to {@code half}, floor to ceiling. */
	private static Solid wall(int half) {
		return (x, y, z) -> x == 10 && Math.abs(z) <= half;
	}

	@Test
	void aLongWallIsDugStraightThrough() {
		// Round the end of the wall is forty blocks out of the way; through it is two blocks to break.
		Dug dug = TrailFinder.dig(wall(40), NOTHING, START, GOAL, 1, 8, 50_000, 3);
		assertTrue(dug.reached());
		assertEquals(2, dug.breaks().size(), "your feet's block and your head's");
		assertTrue(dug.breaks().stream().allMatch(b -> b.x() == 10));
	}

	@Test
	void aShortWallIsWalkedRound() {
		// Round the end is a few blocks; breaking two costs more than that.
		Dug dug = TrailFinder.dig(wall(1), NOTHING, START, GOAL, 1, 8, 50_000, 3);
		assertTrue(dug.reached());
		assertTrue(dug.breaks().isEmpty());
	}

	@Test
	void whatCannotBeBrokenIsGoneRound() {
		Solid wall = wall(12);
		Dug dug = TrailFinder.dig(wall, wall, START, GOAL, 1, 30, 200_000, 3);
		assertTrue(dug.reached());
		assertTrue(dug.breaks().isEmpty());
		assertTrue(dug.path().stream().noneMatch(p -> wall.at(p.x(), p.y(), p.z()) || wall.at(p.x(), p.y() + 1, p.z())));
	}

	@Test
	void aGoalBoxedInByWhatCannotBeBrokenIsNotReached() {
		Solid box = (x, y, z) -> Math.max(Math.abs(x - 20), Math.abs(z)) == 3;
		Dug dug = TrailFinder.dig(box, box, START, GOAL, 1, 4, 20_000, 3);
		assertFalse(dug.reached());
	}
}
