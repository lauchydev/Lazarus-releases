package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipTest {
	private static final long DAY = 86_400_000L;
	private static final long NOW = 1_790_000_000_000L;

	@Test
	void activeBeforeTheEndDate() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 3 * DAY), NOW);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + 3 * DAY - 1));
	}

	@Test
	void endedOnceTheEndDateArrives() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 3 * DAY), NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.ENDED), m.decide(NOW + 3 * DAY));
	}

	@Test
	void notAMemberIsLocked() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(0), NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.NEVER_HAD), m.decide(NOW));
	}

	@Test
	void lockedWhenTheServerNeverSignedAReceipt() {
		assertEquals(new Membership.Decision(false, Membership.Reason.NEVER_HAD), new Membership().decide(NOW));
	}

	@Test
	void offlineTheSavedReceiptCountsUntilItsEndDate() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + DAY), NOW - DAY, NOW - DAY);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW));
		assertEquals(new Membership.Decision(false, Membership.Reason.ENDED), m.decide(NOW + DAY));
	}

	@Test
	void offlineStaysOnSevenDaysAfterTheServerWasLastReached() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY), NOW, NOW);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + 7 * DAY));
	}

	@Test
	void lockedAfterMoreThanSevenDaysWithoutReachingTheServer() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY), NOW, NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.OUT_OF_TOUCH), m.decide(NOW + 7 * DAY + 1));
	}

	@Test
	void lockedWhenTheClockIsEarlierThanTheLatestTimeSeen() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW);
		m.saw(NOW + 2 * DAY);
		assertEquals(new Membership.Decision(false, Membership.Reason.CLOCK_BACK), m.decide(NOW + DAY));
	}

	@Test
	void aClockSetBackBeforeAnOfflineStartLocks() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY), NOW, NOW + DAY);
		assertEquals(new Membership.Decision(false, Membership.Reason.CLOCK_BACK), m.decide(NOW + DAY - 1));
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + DAY));
	}

	@Test
	void reachingTheServerUnlocksAfterTheClockWentBack() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW);
		m.saw(NOW + 2 * DAY);
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW + DAY);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + DAY));
	}

	@Test
	void theLatestTimeSeenNeverMovesBack() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW);
		m.saw(NOW + 2 * DAY);
		m.saw(NOW + DAY);
		assertEquals(NOW + 2 * DAY, m.seen());
	}

	@Test
	void reachingTheServerAgainBringsPlusBackAfterTooLongOffline() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY), NOW, NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.OUT_OF_TOUCH), m.decide(NOW + 8 * DAY));
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW + 8 * DAY);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + 8 * DAY));
	}

	@Test
	void aFrozenReceiptIsLocked() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW, true), NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.FROZEN), m.decide(NOW));
	}

	@Test
	void aFrozenReceiptStaysLockedOffline() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY, true), NOW, NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.FROZEN), m.decide(NOW + DAY));
	}

	@Test
	void theServerUnfreezingUnlocks() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW, true), NOW);
		m.heard(new Membership.Receipt(NOW + 20 * DAY, false), NOW + DAY);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + DAY));
	}

	@Test
	void tooMuchLeftToExtendWithMoreThanSixtyDaysToGo() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 61 * DAY), NOW);
		assertTrue(m.tooMuchLeft(NOW));
		assertFalse(m.tooMuchLeft(NOW + DAY));
	}

	@Test
	void aFrozenPassIsNeverRefusedForTimeLeft() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 90 * DAY, true), NOW);
		assertFalse(m.tooMuchLeft(NOW));
	}

	@Test
	void noPassIsNeverRefusedForTimeLeft() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(0), NOW);
		assertFalse(m.tooMuchLeft(NOW));
		assertFalse(new Membership().tooMuchLeft(NOW));
	}

	@Test
	void aLiveConnectionWithNoNewsKeepsPlusOnPastSevenDays() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW);
		m.reached(NOW + 5 * DAY);
		m.reached(NOW + 10 * DAY);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW + 16 * DAY));
	}

	@Test
	void reachingTheServerOverTheLiveConnectionBringsPlusBackAfterTooLongOffline() {
		Membership m = new Membership();
		m.restored(new Membership.Receipt(NOW + 30 * DAY), NOW - 8 * DAY, NOW);
		assertEquals(Membership.Reason.OUT_OF_TOUCH, m.decide(NOW).reason());
		m.reached(NOW);
		assertEquals(new Membership.Decision(true, Membership.Reason.ACTIVE), m.decide(NOW));
	}

	@Test
	void reachingTheServerKeepsTheReceiptItLastSigned() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + DAY), NOW);
		m.reached(NOW + 2 * DAY);
		assertEquals(Membership.Reason.ENDED, m.decide(NOW + 2 * DAY).reason());
	}

	@Test
	void outOfTouchEvenWhileOnline() {
		Membership m = new Membership();
		m.heard(new Membership.Receipt(NOW + 30 * DAY), NOW);
		assertEquals(new Membership.Decision(false, Membership.Reason.OUT_OF_TOUCH), m.decide(NOW + 8 * DAY));
	}
}
