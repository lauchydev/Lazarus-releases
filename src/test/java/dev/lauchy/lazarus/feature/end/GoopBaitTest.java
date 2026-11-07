package dev.lauchy.lazarus.feature.end;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoopBaitTest {
	@Test
	void readsHowLongTheBaitLasts() {
		assertEquals(600_000, GoopBait.lasts("The bait sinks in. Rift Obsidian Defenders and Watchers turn into Goop 50% more often for 10 minutes."));
		assertEquals(60_000, GoopBait.lasts("The bait sinks in. Rift Obsidian Defenders and Watchers turn into Goop 50% more often for 1 minute."));
		assertEquals(90_000, GoopBait.lasts("The bait sinks in. Watchers turn into Goop more often for 90 seconds."));
	}

	@Test
	void otherLinesAreNotTheBait() {
		assertEquals(0, GoopBait.lasts("The rift will hold you for 37 minutes. It does not like being kept waiting."));
		assertEquals(0, GoopBait.lasts("Your Rift Hourglass buys you 3 more minutes."));
		assertEquals(0, GoopBait.lasts("[DRAGON] Finnzius: The bait sinks in. for 10 minutes"));
	}

	@Test
	void clockRoundsUp() {
		assertEquals("10:00", GoopBait.clock(600_000));
		assertEquals("6:12", GoopBait.clock(371_200));
		assertEquals("0:01", GoopBait.clock(1));
		assertEquals("0:00", GoopBait.clock(0));
	}
}
