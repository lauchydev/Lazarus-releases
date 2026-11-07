package dev.lauchy.lazarus.feature.slayer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeraphTagTest {
	@Test
	void readsHealthHitsAndTier() {
		SeraphTag t = SeraphTag.read("☠ Voidgloom Seraph IV 817.8M/2.5B❤ 57 Hits");
		assertEquals("IV", t.tier());
		assertEquals("817.8M", t.health());
		assertEquals("2.5B", t.maxHealth());
		assertEquals(817.8e6, t.healthValue(), 1);
		assertEquals("57", t.hits());
		assertFalse(t.radiating());
		assertEquals("Seraph IV", t.label());
	}

	@Test
	void aRiftbornTagHasNoTierAndCanBeRadiating() {
		SeraphTag t = SeraphTag.read("§5☠ §dRiftborn Seraph §a2.0B§f/§a2.5B§c❤ §dRadiation");
		assertEquals("V", t.tier());
		assertEquals("", t.hits());
		assertTrue(t.radiating());
		assertEquals("Riftborn Seraph", t.label());
	}

	@Test
	void otherNamesAreNotSeraphs() {
		assertNull(SeraphTag.read("Voidling Fanatic 12k/12k❤"));
		assertNull(SeraphTag.read("Dinnerbone"));
		assertNull(SeraphTag.read(null));
	}

	@Test
	void theLiveTagIsTheLowestHealthWithTheHitsFromWhicheverHasThem() {
		SeraphTag full = SeraphTag.read("Riftborn Seraph 2.5B/2.5B❤ 40 Hits");
		SeraphTag live = SeraphTag.read("Riftborn Seraph 1.2B/2.5B❤");
		SeraphTag shown = SeraphTag.live(List.of(full, live));
		assertEquals("1.2B", shown.health());
		assertEquals("40", shown.hits());
		assertNull(SeraphTag.live(List.of()));
	}

	@Test
	void readsTheOwnerLine() {
		assertEquals("Lauchy", SeraphTag.owner("§7Spawned by: §bLauchy"));
		assertEquals("Lauchy", SeraphTag.owner("Spawned by Lauchy"));
		assertNull(SeraphTag.owner("Voidgloom Seraph IV 1/2❤"));
	}
}
