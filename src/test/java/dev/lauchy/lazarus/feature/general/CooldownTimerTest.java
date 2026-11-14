package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static dev.lauchy.lazarus.feature.general.CooldownTimer.Outcome.REPEAT;
import static dev.lauchy.lazarus.feature.general.CooldownTimer.Outcome.STARTED;
import static dev.lauchy.lazarus.feature.general.CooldownTimer.Outcome.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTimerTest {
	@Test
	void aUseStartsTheTimerForItsLength() {
		CooldownTimer t = new CooldownTimer();
		assertEquals(STARTED, t.use(100, 240));
		assertEquals(240, t.left(100));
		assertEquals(40, t.left(300));
		assertEquals(0, t.left(340));
		assertFalse(t.running(340));
	}

	@Test
	void theBarAndChatReportingOneUseCountOnce() {
		CooldownTimer t = new CooldownTimer();
		t.use(100, 240);
		assertEquals(REPEAT, t.use(102, 240));
		assertEquals(240, t.left(100));
	}

	@Test
	void aPressDuringTheCooldownWaitsAndIsCorrectedByTheRefusal() {
		CooldownTimer t = new CooldownTimer();
		t.use(100, 240);
		assertEquals(WAITING, t.use(200, 240));
		assertEquals(140, t.left(200), "a press the server may refuse doesn't restart the timer");
		t.refused(203, 74);
		assertEquals(74, t.left(203));
		assertNull(t.tick(220), "a refused press never starts the timer");
		assertEquals(57, t.left(220));
	}

	@Test
	void aPressTheServerNeverRefusedRestartsFromThePress() {
		CooldownTimer t = new CooldownTimer();
		t.use(100, 240);
		t.use(200, 240);
		assertNull(t.tick(200 + CooldownTimer.GRACE_TICKS - 1));
		assertEquals(STARTED, t.tick(200 + CooldownTimer.GRACE_TICKS));
		assertEquals(240 - CooldownTimer.GRACE_TICKS, t.left(200 + CooldownTimer.GRACE_TICKS));
		assertNull(t.tick(300), "it starts once");
	}

	@Test
	void aSecondPressWhileOneIsWaitingIsTheSamePress() {
		CooldownTimer t = new CooldownTimer();
		t.use(100, 240);
		t.use(200, 240);
		assertEquals(REPEAT, t.use(205, 240));
	}

	@Test
	void aRefusalWithNoTimerRunningStartsOne() {
		CooldownTimer t = new CooldownTimer();
		t.refused(50, 100);
		assertTrue(t.running(50));
		assertEquals(100, t.left(50));
	}

	@Test
	void aTimerThatNeverRanIsIdleForever() {
		CooldownTimer t = new CooldownTimer();
		assertEquals(Long.MAX_VALUE, t.idle(5));
		t.use(5, 20);
		assertEquals(75, t.idle(100));
	}
}
