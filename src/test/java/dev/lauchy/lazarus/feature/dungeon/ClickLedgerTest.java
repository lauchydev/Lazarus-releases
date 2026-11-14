package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickLedgerTest {
	private static final long PATIENCE = 1500;

	@Test
	void aSlotThatNeedsOneClickTakesOneUntilTheServerAnswers() {
		ClickLedger ledger = new ClickLedger(0);
		assertTrue(ledger.take(12, 100, -1, 1, 0, PATIENCE));
		assertFalse(ledger.take(12, 100, -1, 1, 50, PATIENCE), "the first is still on its way");
	}

	@Test
	void aPaneThatNeedsTwoTurnsIsNotGivenAThirdWhenTheFirstAnswerComesBack() {
		// The colour terminal: a pane two steps short of its colour. Two quick clicks, then the first lands.
		ClickLedger ledger = new ClickLedger(5);
		assertTrue(ledger.take(21, 100, 1, 2, 0, PATIENCE));
		assertTrue(ledger.take(21, 100, 1, 2, 60, PATIENCE));
		assertFalse(ledger.take(21, 100, 1, 2, 90, PATIENCE));
		// The window now shows it one step on, needing one more: but that one is already in flight.
		assertEquals(1, ledger.flying(21, 101, 2, 300, PATIENCE));
		assertFalse(ledger.take(21, 101, 2, 1, 300, PATIENCE), "a third click would turn it past its colour");
		// The second lands: nothing left to do, nothing in flight.
		assertEquals(0, ledger.flying(21, 102, 3, 520, PATIENCE));
	}

	@Test
	void aClickTheServerNeverAnsweredIsForgottenSoTheSlotIsNotLockedForGood() {
		ClickLedger ledger = new ClickLedger(0);
		assertTrue(ledger.take(12, 100, -1, 1, 0, PATIENCE));
		assertFalse(ledger.take(12, 100, -1, 1, 1000, PATIENCE));
		assertTrue(ledger.take(12, 100, -1, 1, 1600, PATIENCE), "dropped: it may be clicked again");
	}

	@Test
	void slotsKeepTheirOwnCount() {
		ClickLedger ledger = new ClickLedger(0);
		assertTrue(ledger.take(12, 100, -1, 1, 0, PATIENCE));
		assertTrue(ledger.take(13, 100, -1, 1, 10, PATIENCE));
		assertEquals(0, ledger.flying(14, 100, -1, 20, PATIENCE));
	}
}
