package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.ui.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropAlertsTest {
	@Test
	void aDropBeatsTheValueSetInCoinsOrShorthand() {
		assertTrue(DropAlerts.beats(1_500_000, "1m"));
		assertTrue(DropAlerts.beats(2_000_000_000L, "1.5b"));
		assertTrue(DropAlerts.beats(600_000, "500k"));
		assertTrue(DropAlerts.beats(1_000_001, "1,000,000"));
		assertFalse(DropAlerts.beats(1_000_000, "1m"), "equal doesn't beat");
		assertFalse(DropAlerts.beats(900_000, "1m"));
	}

	@Test
	void aValueThatIsntANumberNeverAlerts() {
		assertFalse(DropAlerts.beats(5_000_000_000L, ""));
		assertFalse(DropAlerts.beats(5_000_000_000L, "lots"));
	}

	@Test
	void theServersPopUpIsKnownByItsDropFonts() {
		assertTrue(DropAlerts.isDropFont("dragsim:rare_drop"));
		assertTrue(DropAlerts.isDropFont("dragsim:rare_drop_banner"));
		assertFalse(DropAlerts.isDropFont("minecraft:default"));
		assertFalse(DropAlerts.isDropFont("dragsim:casino"));
	}

	@Test
	void namedItemsMatchByAnyPartOfTheName() {
		assertTrue(DropAlerts.named("Frozen Core", "Scythe Blade, core"));
		assertTrue(DropAlerts.named("Judgement Core", " CORE "));
		assertTrue(DropAlerts.named("Shard of the Shredded", "shard of the shredded"));
		assertTrue(DropAlerts.named("Null Ovoid", "void frag, null ovoid"));
		assertFalse(DropAlerts.named("Summoning Eye", "core, blade"));
		assertFalse(DropAlerts.named("Summoning Eye", ""), "an empty list matches nothing");
		assertFalse(DropAlerts.named("Summoning Eye", " , ,"));
	}

	@Test
	void aTierCountsFromTheOneChosenUp() {
		assertTrue(DropAlerts.tiered("RNGESUS", DropAlerts.From.RNGESUS));
		assertTrue(DropAlerts.tiered("LEGENDARY", DropAlerts.From.EPIC));
		assertTrue(DropAlerts.tiered("RARE", DropAlerts.From.RARE));
		assertFalse(DropAlerts.tiered("EPIC", DropAlerts.From.LEGENDARY));
		assertFalse(DropAlerts.tiered("", DropAlerts.From.RARE), "a bonus or summary line has no tier");
		assertFalse(DropAlerts.tiered("RNGESUS", DropAlerts.From.NEVER));
	}

	@Test
	void theFlashTakesTheDropsNameColourAndHaloWhenUnseen() {
		assertEquals(0xFFFFAA00, DropAlerts.colour("§6"), "gold for a Legendary");
		assertEquals(0xFFAA00AA, DropAlerts.colour("§5"), "dark purple for an Epic");
		assertEquals(Theme.HALO, DropAlerts.colour(null));
		assertEquals(Theme.HALO, DropAlerts.colour("§z"));
	}

	@Test
	void theRainbowGoesRoundAndIsOpaque() {
		int a = DropAlerts.rainbow(0), b = DropAlerts.rainbow(500), c = DropAlerts.rainbow(1500);
		assertEquals(a, c, "round the wheel every second and a half");
		assertTrue(a != b);
		assertEquals(0xFF000000, a & 0xFF000000);
	}
}
