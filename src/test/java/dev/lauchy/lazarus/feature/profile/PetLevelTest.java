package dev.lauchy.lazarus.feature.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetLevelTest {
	@Test
	void aPetThatDoesntLevelIsAlwaysAHundred() {
		assertEquals(100, PetLevel.of("ENDERMAN", "LEGENDARY", 0, 2, false, 0));
	}

	@Test
	void aGoldenDragonClimbsFromAHundredAndOne() {
		assertEquals(101, PetLevel.of("GOLDEN_DRAGON", "LEGENDARY", 0, 2, false, 200));
		assertEquals(104, PetLevel.of("GOLDEN_DRAGON", "LEGENDARY", 650 + 3 * 204_075 - 1, 2, false, 200));
		assertEquals(200, PetLevel.of("GOLDEN_DRAGON", "LEGENDARY", 237_603_991, 2, false, 200));
	}

	@Test
	void aLevellingPetFollowsItsRaritysCurve() {
		assertEquals(1, PetLevel.of("WOLF", "LEGENDARY", 900, 2, true, 0));
		assertEquals(2, PetLevel.of("WOLF", "LEGENDARY", 959, 2, true, 0));
	}
}
