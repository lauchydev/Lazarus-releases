package dev.lauchy.lazarus.feature.slayer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardWindowTest {
	private final RewardWindow w = new RewardWindow();

	@Test
	void withNoSnapshotBeforeTheKillNothingIsWatched() {
		w.open(2_000);
		w.seen(2_100, Map.of("Null Ovoid", 40)); // everything already carried, not a reward
		assertFalse(w.watching(2_100));
		assertFalse(w.due(6_000));
	}

	@Test
	void whatYouGainAfterTheKillIsTheReward() {
		w.seen(0, Map.of("Null Ovoid", 4));
		w.seen(1_000, Map.of("Null Ovoid", 4));
		w.open(2_000);
		w.seen(2_500, Map.of("Null Ovoid", 5));
		assertFalse(w.due(4_000));
		assertTrue(w.due(5_001));
		assertEquals(Map.of("Null Ovoid", 1), w.close());
	}

	@Test
	void aPickupInTheSameSecondAsTheKillStillCounts() {
		w.seen(0, Map.of("Null Ovoid", 4));
		w.seen(1_900, Map.of("Null Ovoid", 5)); // picked up a nest drop just before the boss died
		w.open(2_000);
		w.seen(2_100, Map.of("Null Ovoid", 6));
		assertEquals(Map.of("Null Ovoid", 2), w.close());
	}

	@Test
	void whatTheServerAnnouncedInsideTheWindowIsNotCountedTwice() {
		w.seen(0, Map.of("Null Ovoid", 0));
		w.open(2_000);
		w.seen(2_500, Map.of("Null Ovoid", 2));
		w.announced("Null Ovoid", 1);
		assertEquals(Map.of("Null Ovoid", 1), w.close());
	}

	@Test
	void nothingGainedMeansNoReward() {
		w.seen(0, Map.of("Revenant Viscera", 3));
		w.open(2_000);
		w.seen(2_500, Map.of("Revenant Viscera", 3));
		assertTrue(w.close().isEmpty());
		assertFalse(w.due(10_000));
	}
}
