package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunScoreTest {
	@Test
	void theScoreIsTheNumberInBracketsOnTheSidebarsClearedLine() {
		// The sidebar as Dragsim sent it (2026-11).
		assertEquals(272, RunScore.read(List.of("11/11/26 dungeons-1", "The Catacombs (F1)", "Time Elapsed: 56s", "Cleared: 43% (272)", "", "[M] Lauchy 5,210❤")));
		assertEquals(-1, RunScore.read(List.of("The Catacombs (F1)", "Cleared: 43%")));
		assertEquals(-1, RunScore.read(List.of("Dragon's Den", "Purse: 300")));
	}

	@Test
	void reachingTheTargetIsToldOnceARun() {
		RunScore run = new RunScore();
		assertFalse(run.reached(120, true, 300));
		assertFalse(run.reached(299, true, 300));
		assertTrue(run.reached(301, true, 300));
		assertFalse(run.reached(305, true, 300));
		// A death takes it under and the next room brings it back: still the same run.
		assertFalse(run.reached(297, true, 300));
		assertFalse(run.reached(300, true, 300));
	}

	@Test
	void aNewRunIsToldAgain() {
		RunScore run = new RunScore();
		assertTrue(run.reached(300, true, 300));
		// Straight into the next run without the sidebar ever going: the score starts low again.
		assertFalse(run.reached(40, true, 300));
		assertTrue(run.reached(300, true, 300));
		// Or out of the dungeon and back.
		assertFalse(run.reached(-1, false, 300));
		assertTrue(run.reached(310, true, 300));
	}

	@Test
	void nothingIsToldOutsideADungeonOrWhileTheSidebarShowsNoScore() {
		RunScore run = new RunScore();
		assertFalse(run.reached(300, false, 300));
		assertFalse(run.reached(-1, true, 300));
		// The boss fight may hide the line; that isn't a new run.
		assertTrue(run.reached(300, true, 300));
		assertFalse(run.reached(-1, true, 300));
		assertFalse(run.reached(300, true, 300));
	}
}
