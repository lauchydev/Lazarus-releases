package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingTest {
	private final Moving moving = new Moving();

	@Test
	void standingStillIsNotMovingEvenOnTheFirstTick() {
		for (int i = 0; i < 10; i++) assertFalse(moving.at(5, 64, 5), "tick " + i);
	}

	@Test
	void walkingIsMoving() {
		moving.at(0, 64, 0);
		for (int i = 1; i <= 20; i++) assertTrue(moving.at(i * 0.2, 64, 0), "tick " + i);
	}

	@Test
	void jumpingOnTheSpotIsMoving() {
		// A jump as the game does it: up 0.42 then less each tick, a hang at the top, then down.
		double[] heights = {64, 64.42, 64.75, 65.0, 65.17, 65.25, 65.25, 65.17, 65.0, 64.75, 64.42, 64};
		moving.at(0, heights[0], 0);
		for (int i = 1; i < heights.length; i++) assertTrue(moving.at(0, heights[i], 0), "jump tick " + i);
	}

	@Test
	void sneakingIsMovingButALookRoundIsNot() {
		moving.at(0, 64, 0);
		assertTrue(moving.at(0.06, 64, 0));
		Moving look = new Moving();
		look.at(0, 64, 0);
		assertFalse(look.at(0.001, 64, 0.001)); // the server's own jitter while you turn
	}

	@Test
	void stoppingKeepsAShortGraceThenIsStill() {
		moving.at(0, 64, 0);
		moving.at(0.2, 64, 0);
		for (int i = 1; i <= Moving.GRACE; i++) assertTrue(moving.at(0.2, 64, 0), "grace tick " + i);
		assertFalse(moving.at(0.2, 64, 0));
		assertFalse(moving.at(0.2, 64, 0));
	}

	@Test
	void aStepAfterStandingStartsItAgain() {
		for (int i = 0; i < 10; i++) moving.at(0, 64, 0);
		assertTrue(moving.at(0.2, 64, 0));
	}

	@Test
	void forgettingMeansAJumpInPositionIsNotAStep() {
		moving.at(0, 64, 0);
		moving.forget();
		assertFalse(moving.at(100, 70, 100));
		assertFalse(moving.at(100, 70, 100));
	}
}
