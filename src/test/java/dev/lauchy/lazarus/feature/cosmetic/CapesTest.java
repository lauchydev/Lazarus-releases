package dev.lauchy.lazarus.feature.cosmetic;

import dev.lauchy.lazarus.core.Live;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapesTest {
	@Test
	void everyLazarusUserWearsTheLazarusCapeAndNobodyElseWearsOne() {
		assertEquals("lazarus", CapeChoice.worn(Live.Badge.PURPLE, "", false));
		assertEquals("lazarus", CapeChoice.worn(Live.Badge.PURPLE, "mojang", false), "a pick from past membership gives way");
		assertEquals("", CapeChoice.worn(Live.Badge.GREY, "", false));
		assertEquals("", CapeChoice.worn(Live.Badge.NONE, "", false));
	}

	@Test
	void anyLazarusUserMayTakeTheirCapeOffAndPutItBackOn() {
		assertEquals("", CapeChoice.worn(Live.Badge.PURPLE, "none", false));
		assertEquals("lazarus", CapeChoice.worn(Live.Badge.PURPLE, "lazarus", false));
		assertTrue(CapeChoice.free("none"));
		assertTrue(CapeChoice.free("lazarus"));
		assertTrue(CapeChoice.free(""));
		assertFalse(CapeChoice.free("mojang"), "any other cape is a member's");
		assertFalse(CapeChoice.free("lazarusPlus"));
	}

	@Test
	void aMemberWearsTheLazarusPlusCapeUntilTheyPickAnotherOrNone() {
		assertEquals("lazarusPlus", CapeChoice.worn(Live.Badge.GOLD, "", true));
		assertEquals("mojang", CapeChoice.worn(Live.Badge.GOLD, "mojang", true));
		assertEquals("lazarus", CapeChoice.worn(Live.Badge.GOLD, "lazarus", true), "the plain Lazarus cape is theirs to pick too");
		assertEquals("", CapeChoice.worn(Live.Badge.GOLD, "none", true));
	}
}
