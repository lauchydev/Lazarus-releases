package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.ArrowPaths.Cell;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArrowPathsTest {
	@Test
	void aClickTurnsAnArrowOneOnAndEightComeBackRound() {
		assertEquals(0, ArrowPaths.clicks(3, 3));
		assertEquals(2, ArrowPaths.clicks(1, 3));
		assertEquals(6, ArrowPaths.clicks(3, 1));
		assertEquals(7, ArrowPaths.clicks(0, 7));
	}

	@Test
	void aKnownLayoutIsMatchedByItsShapeWhereverTheWallStands() {
		// Three upright runs of three arrows, two frames apart: the layout whose arrows all point right. The wall
		// here is far from the map's zero, as in a real arena.
		Set<Cell> arrows = Set.of(
				new Cell(41, 121), new Cell(41, 123), new Cell(41, 125),
				new Cell(42, 121), new Cell(42, 123), new Cell(42, 125),
				new Cell(43, 121), new Cell(43, 123), new Cell(43, 125));
		Map<Cell, Integer> targets = ArrowPaths.targets(arrows, Set.of());
		assertEquals(arrows, targets.keySet());
		for (int turn : targets.values()) assertEquals(ArrowPaths.RIGHT, turn);
	}

	@Test
	void aLayoutNotKnownIsSolvedByLeadingEachArrowToTheEnd() {
		// An L: three arrows along the bottom from the left, then two going up to the end above them.
		//   E
		//   a
		//   a a a   (left is the higher number: you face the wall)
		Set<Cell> arrows = Set.of(new Cell(2, 0), new Cell(1, 0), new Cell(0, 0), new Cell(0, 1));
		Map<Cell, Integer> targets = ArrowPaths.targets(arrows, Set.of(new Cell(0, 2)));
		assertEquals(Map.of(new Cell(2, 0), ArrowPaths.RIGHT, new Cell(1, 0), ArrowPaths.RIGHT, new Cell(0, 0), ArrowPaths.UP, new Cell(0, 1), ArrowPaths.UP), targets);
	}

	@Test
	void arrowsThatReachNoEndAreLeftAlone() {
		assertEquals(Map.of(), ArrowPaths.targets(Set.of(new Cell(0, 0), new Cell(5, 5)), Set.of(new Cell(0, 1))));
		assertEquals(Map.of(), ArrowPaths.targets(Set.of(), Set.of()));
	}

	@Test
	void theServersPuzzleAnnouncementNamesTheRoom() {
		// As Dragsim sent them (2026-10).
		assertEquals("Quiz", Puzzles.named("PUZZLE! Quiz - Answer three questions. Press the button under the right answer."));
		assertEquals("Higher Or Lower", Puzzles.named("PUZZLE! Higher Or Lower - Kill the blazes in order of health. The chest shows which way."));
		assertNull(Puzzles.named("PUZZLE SOLVED! Quiz"));
		assertNull(Puzzles.named("PUZZLE FAIL! Lauchy answered a quiz question wrong"));
	}

	@Test
	void aPuzzlesResultSaysWhichPuzzleItWasAndWhoDidIt() {
		// As Dragsim sent them (2026-11).
		assertEquals(new Puzzles.Result("sStepbr0", "Three Weirdos"), Puzzles.result("PUZZLE FAIL! sStepbr0 was fooled by Virginia! Yikes!"));
		assertEquals(new Puzzles.Result("sStepbr0", "Three Weirdos"), Puzzles.result("PUZZLE SOLVED! sStepbr0 wasn't fooled by Carver! Good job!"));
		assertEquals(new Puzzles.Result("sStepbr0", "Higher Or Lower"), Puzzles.result("PUZZLE FAIL! sStepbr0 killed a Blaze in the wrong order! Yikes!"));
		assertEquals(new Puzzles.Result("Lauchy", "Quiz"), Puzzles.result("PUZZLE FAIL! [MOD] Lauchy answered a quiz question wrong"));
		assertNull(Puzzles.result("PUZZLE! Quiz - Answer three questions."));
		assertNull(Puzzles.result("Steve: PUZZLE FAIL! I was fooled by Carver"));
	}
}
