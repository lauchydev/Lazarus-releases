package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickRateTest {
	/** How many clicks {@code ticks} ticks at {@code cps} give, with nothing owed to begin with. */
	private static int clicks(int cps, int ticks) {
		ClickRate rate = new ClickRate();
		int n = 0;
		for (int i = 0; i < ticks; i++) if (rate.tick(cps)) n++;
		return n;
	}

	@Test
	void aSecondGivesTheRateAsked() {
		for (int cps = 1; cps <= 10; cps++) assertEquals(cps, clicks(cps, 20), cps + " a second");
		assertEquals(80, clicks(8, 200));
	}

	@Test
	void aFreshStartClicksAtOnceThenKeepsTheRate() {
		ClickRate rate = new ClickRate();
		rate.reset();
		assertTrue(rate.tick(1));
		int n = 0;
		for (int i = 0; i < 20; i++) if (rate.tick(1)) n++;
		assertEquals(1, n);
	}

	@Test
	void clicksAreSpreadNotBunched() {
		// 8 a second: never two ticks running, never more than three apart.
		ClickRate rate = new ClickRate();
		rate.reset();
		int since = 0;
		for (int i = 0; i < 100; i++) {
			if (rate.tick(8)) {
				assertTrue(i == 0 || since >= 2, "two ticks running at tick " + i);
				since = 1;
			} else {
				since++;
				assertTrue(since <= 3, "a gap over three ticks at tick " + i);
			}
		}
	}

	@Test
	void neverMoreThanOneClickATickAndNoneAtZero() {
		assertEquals(20, clicks(50, 20));
		assertEquals(0, clicks(0, 20));
		assertFalse(new ClickRate().tick(-5));
	}
}
