package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Drops;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingTest {
	private final Landing landing = new Landing();

	@Test
	void aDropThatLandsAfterItsLineIsFoundWhenItLands() {
		// A slayer boss's drop is announced before its reward lands: nothing yet with the line, the shard a moment later.
		landing.expect(Drops.parse("RNGESUS DROP! Shard of the Shredded").item(), 1_000);
		assertEquals(-1, landing.find(List.of("Revenant Flesh", ""), 1_000));
		assertTrue(landing.waiting(1_050));
		assertEquals(1, landing.find(List.of("Revenant Flesh", "Shard of the Shredded"), 1_450));
		assertEquals(-1, landing.find(List.of("Revenant Flesh", "Shard of the Shredded"), 1_500), "found once");
	}

	@Test
	void aPetIsFoundUnderItsItemName() {
		landing.expect(Drops.parse("CRAZY RARE DROP! Scatha Pet (Legendary)").item(), 0);
		assertEquals(2, landing.find(List.of("Amethyst", "", "[Lvl 1] Scatha"), 0));
	}

	@Test
	void aDropThatNeverLandsIsGivenUp() {
		landing.expect("Flesh Fang", 0);
		assertTrue(landing.waiting(Landing.WAIT_MS));
		assertFalse(landing.waiting(Landing.WAIT_MS + 1));
		assertEquals(-1, landing.find(List.of("Flesh Fang"), Landing.WAIT_MS + 1));
	}

	@Test
	void theServersPopUpStopsTheWait() {
		landing.expect("Flesh Fang", 0);
		landing.cancel();
		assertFalse(landing.waiting(0));
		assertEquals(-1, landing.find(List.of("Flesh Fang"), 10));
	}

	@Test
	void aNewDropTakesThePlaceOfOneStillWaitedFor() {
		landing.expect("Flesh Fang", 0);
		landing.expect("Scythe Blade", 100);
		assertEquals(1, landing.find(List.of("Flesh Fang", "Scythe Blade"), 200));
	}

	@Test
	void nothingIsFoundWhenNothingIsWaitedFor() {
		assertFalse(landing.waiting(0));
		assertEquals(-1, landing.find(List.of("Flesh Fang"), 0));
	}
}
