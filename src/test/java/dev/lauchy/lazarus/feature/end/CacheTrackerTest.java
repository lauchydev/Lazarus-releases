package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Drops;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CacheTrackerTest {
	@Test
	void anIndentedRewardCounts() {
		assertEquals(new Drops.Drop("Null Atom", 1), drop(CacheTracker.reward("  Null Atom")));
		assertEquals(new Drops.Drop("Revenant Viscera", 3), drop(CacheTracker.reward("  3x Revenant Viscera")));
	}

	@Test
	void theSlayerQuestARewardStartsIsNotAReward() {
		// A Null Atom starts a slayer quest, and the server's quest lines are indented like the rewards (4 Nov 2026).
		assertNull(CacheTracker.reward("  SLAYER QUEST STARTED!"));
		assertNull(CacheTracker.reward("  » Slay 13,750 Combat XP worth of Zombies."));
	}

	@Test
	void anUnindentedLineAndNothingAreNotRewards() {
		assertNull(CacheTracker.reward("Null Atom"));
		assertNull(CacheTracker.reward("  (nothing)"));
		assertNull(CacheTracker.reward("  No loot!"));
		assertNull(CacheTracker.reward("   "));
	}

	/** The drop without its source, which the tracker doesn't keep. */
	private static Drops.Drop drop(Drops.Drop d) {
		return d == null ? null : new Drops.Drop(d.item(), d.count());
	}
}
