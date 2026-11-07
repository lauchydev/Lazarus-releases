package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenWaitTest {
	@Test
	void aClickWaitsUntilTheWindowHasBeenOpenLongEnough() {
		OpenWait wait = new OpenWait(1100);
		wait.reset();
		assertFalse(wait.ready(0), "no window yet");
		wait.seen(7, 10_000);
		wait.seen(7, 10_500); // the same window, seen again a tick later, doesn't start over
		assertFalse(wait.ready(11_000));
		assertTrue(wait.ready(11_100));
	}

	@Test
	void aPageThatComesAsANewWindowWaitsAgain() {
		OpenWait wait = new OpenWait(1100);
		wait.seen(7, 0);
		assertTrue(wait.ready(1100));
		wait.seen(8, 1200);
		assertFalse(wait.ready(1300));
		assertTrue(wait.ready(2300));
	}

	@Test
	void aMenuWithNoWaitTakesAClickAtOnce() {
		OpenWait wait = new OpenWait(0);
		wait.seen(3, 500);
		assertTrue(wait.ready(500));
	}

	@Test
	void aClickTurnedDownGoesAgainOnceAFullWaitLater() {
		OpenWait wait = new OpenWait(1100);
		wait.seen(7, 0);
		wait.clicked(1100);
		assertTrue(wait.turnedDown(1300));
		assertFalse(wait.ready(2300), "a full wait from when the server said so");
		assertTrue(wait.ready(2400));
		wait.clicked(2400);
		assertFalse(wait.turnedDown(2600), "only once a pick");
	}

	@Test
	void aTooSoonThatIsntAboutThisPicksClickIsLeftAlone() {
		OpenWait wait = new OpenWait(1100);
		wait.seen(7, 0);
		assertFalse(wait.turnedDown(500), "nothing clicked yet");
		wait.clicked(1100);
		assertFalse(wait.turnedDown(1100 + OpenWait.ANSWER_MS + 1), "too long after the click");
	}

	@Test
	void aNewPickMayGoAgainItself() {
		OpenWait wait = new OpenWait(1100);
		wait.seen(7, 0);
		wait.clicked(1100);
		assertTrue(wait.turnedDown(1200));
		wait.reset();
		assertFalse(wait.ready(5000), "a new pick waits for its own window");
		wait.seen(9, 5000);
		wait.clicked(6100);
		assertTrue(wait.turnedDown(6200));
	}
}
