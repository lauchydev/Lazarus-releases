package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MelodyTimingTest {
	/** A pane seen stepping right from column 1, a step every 500 ms, up to column 3 at time 1000. */
	private static MelodyTiming watched() {
		MelodyTiming t = new MelodyTiming();
		t.see(2, 1, 0);
		t.see(2, 2, 500);
		t.see(2, 3, 1000);
		return t;
	}

	@Test
	void withNoPingThePaneIsWhereItLooks() {
		assertEquals(3, watched().at(1100, 0));
	}

	@Test
	void aClickLandsAPingLaterSoThePaneIsLedByThat() {
		// 100 ms after it reached column 3, with 450 ms of ping: it'll have stepped on to 4 by the time the click lands.
		assertEquals(4, watched().at(1100, 450));
		// With 250 ms it's still in 3 then.
		assertEquals(3, watched().at(1100, 250));
	}

	@Test
	void thePaneTurnsBackAtTheEndOfItsRow() {
		// From column 3 going right: 4, 5, then back to 4.
		assertEquals(4, watched().at(1000, 1500));
	}

	@Test
	void untilItsBeatIsKnownOrOnANewRowItIsTakenWhereItIs() {
		MelodyTiming t = new MelodyTiming();
		t.see(2, 4, 0);
		assertEquals(4, t.at(300, 400));
		MelodyTiming moved = watched();
		moved.see(3, 2, 1200);
		assertEquals(2, moved.at(1300, 400), "a new row's beat isn't known yet");
	}
}
