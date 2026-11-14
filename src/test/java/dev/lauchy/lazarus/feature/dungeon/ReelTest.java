package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReelTest {
	@Test
	void theWinnerSitsWhereTheStripStops() {
		List<String> strip = Reel.fill(List.of("hot_potato_book", "wither_boots"), s -> 1, "necron_handle", new Random(1));
		assertEquals(Reel.CARDS, strip.size());
		assertEquals("necron_handle", strip.get(Reel.WINNER));
		for (double land : new double[]{0, 0.5, 1}) {
			double stop = Reel.stop(52, 4, land);
			assertEquals(Reel.WINNER, Reel.under(stop, 52, 4));
		}
	}

	@Test
	void cheapItemsTurnUpMoreOften() {
		List<String> strip = Reel.fill(List.of("cheap", "dear"), s -> s.equals("cheap") ? 1 : 0.05, "won", new Random(7));
		long cheap = strip.stream().filter("cheap"::equals).count(), dear = strip.stream().filter("dear"::equals).count();
		assertTrue(cheap > dear * 3, cheap + " cheap vs " + dear + " dear");
	}

	@Test
	void theStripSlowsAndStopsOnTime() {
		double from = Reel.start(52, 4), stop = Reel.stop(52, 4, 0.5);
		assertEquals(from, Reel.offset(from, stop, 0, 6000), 1e-9);
		assertEquals(stop, Reel.offset(from, stop, 6000, 6000), 1e-9);
		double early = Reel.offset(from, stop, 1000, 6000) - Reel.offset(from, stop, 0, 6000);
		double late = Reel.offset(from, stop, 6000, 6000) - Reel.offset(from, stop, 5000, 6000);
		assertTrue(early > late * 20, "fast at first, slow at the end");
	}
}
