package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HigherOrLowerTest {
	private static final List<Integer> HEALTHS = List.of(225000, 90000, 410000, 150000);

	@Test
	void theBlazesToKillComeInTheOrderTheRoomAsksFor() {
		assertEquals(List.of(410000, 225000, 150000), HigherOrLower.queue(HEALTHS, h -> h, true, 3));
		assertEquals(List.of(90000, 150000), HigherOrLower.queue(HEALTHS, h -> h, false, 2));
	}

	@Test
	void fewerBlazesLeftThanAreShownGivesOnlyThoseLeft() {
		assertEquals(List.of(7), HigherOrLower.queue(List.of(7), h -> h, true, 3));
		assertEquals(List.of(), HigherOrLower.queue(List.<Integer>of(), h -> h, false, 2));
	}
}
