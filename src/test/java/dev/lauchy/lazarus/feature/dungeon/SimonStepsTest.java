package dev.lauchy.lazarus.feature.dungeon;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimonStepsTest {
	private static final BlockPos A = new BlockPos(1, 70, 5), B = new BlockPos(1, 73, 3), C = new BlockPos(1, 71, 4);

	@Test
	void eachRoundShowsTheWholeRunAgainOneLonger() {
		// As Dragsim's device ran in a recording (2026-11): a tile, a press, then that tile and another half a second apart.
		SimonSteps steps = new SimonSteps();
		steps.flashed(A, 3000);
		assertEquals(A, steps.next());
		steps.pressed(A);
		assertNull(steps.next());
		steps.flashed(A, 5000); // two seconds on: a new showing, from its first tile
		steps.flashed(B, 5500);
		assertEquals(2, steps.length());
		assertEquals(A, steps.next());
		steps.pressed(A);
		assertEquals(B, steps.next());
		steps.pressed(B);
		assertNull(steps.next());
	}

	@Test
	void aShowingThatComesStraightAfterAPressIsStillANewOne() {
		SimonSteps steps = new SimonSteps();
		steps.flashed(A, 3000);
		steps.pressed(A);
		steps.flashed(A, 4000); // only a second after the last flash, but a button has been pressed since
		steps.flashed(B, 4500);
		assertEquals(2, steps.length());
		assertEquals(A, steps.next());
	}

	@Test
	void pressingWaitsUntilTheShowingIsOver() {
		SimonSteps steps = new SimonSteps();
		steps.flashed(A, 5000);
		assertFalse(steps.settled(5400), "another flash may be coming");
		steps.flashed(B, 5500);
		assertFalse(steps.settled(6200), "nine tenths of a second after the last, at the least");
		assertTrue(steps.settled(6450));
	}

	@Test
	void aSlowDeviceIsWaitedOnLonger() {
		// Flashes a second apart: the wait after the last is most of two of those gaps.
		SimonSteps steps = new SimonSteps();
		steps.flashed(A, 10_000);
		steps.flashed(B, 11_000);
		steps.flashed(C, 12_000);
		assertFalse(steps.settled(13_500));
		assertTrue(steps.settled(13_900));
	}

	@Test
	void aPressOfTheWrongButtonDoesNotMoveOnAndStartingOverForgetsTheRun() {
		SimonSteps steps = new SimonSteps();
		steps.flashed(A, 1000);
		steps.flashed(B, 1500);
		steps.pressed(B);
		assertEquals(A, steps.next());
		steps.clear();
		assertNull(steps.next());
		steps.flashed(C, 1600); // straight after starting over, this is a new run all the same
		assertEquals(C, steps.next());
		assertEquals(1, steps.length());
	}
}
