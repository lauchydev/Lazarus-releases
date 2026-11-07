package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollDebugTest {
	@Test
	void yourOwnRollIsReadWithItsItemAndWhetherItHit() {
		RollDebug.Roll r = RollDebug.parse("[Debug] Golden Dragon · Roll 78.0/100 · MF 167.697 · Haunted Candy; MF not applied HIT", 5);
		assertNotNull(r);
		assertEquals("", r.player());
		assertEquals("Golden Dragon", r.mob());
		assertEquals("Roll", r.kind());
		assertEquals(78.0, r.roll());
		assertEquals("Haunted Candy", r.item());
		assertTrue(r.hit());
		assertEquals(5, r.at());
	}

	@Test
	void anotherPlayersRollCarriesTheirName() {
		RollDebug.Roll r = RollDebug.parse("[WHALE] sStepbr0: [Debug] Roll 99.73854/100 · MF 394.59486 · 12 item odds (hover)", 0);
		assertNotNull(r);
		assertEquals("sStepbr0", r.player());
		assertEquals("", r.mob());
		assertEquals("", r.item());
		assertFalse(r.hit());
	}

	@Test
	void spawnRollsNameWhatCame() {
		RollDebug.Roll r = RollDebug.parse("[Debug] Wise Dragon · Spawn 90.91/100 · MF 167.697 · Wise Dragon", 0);
		assertNotNull(r);
		assertEquals("Spawn", r.kind());
		assertEquals("Wise Dragon", r.mob());
		assertEquals("", r.item(), "the spawn's name is the mob, not an item");
		assertTrue(r.row().contains("Wise Dragon") && r.row().contains("90.9") && !r.row().contains("miss"), r.row());
	}

	@Test
	void tableRollsNameTheItem() {
		RollDebug.Roll r = RollDebug.parse("[Debug] Golden Dragon · Table 99.77/100 · MF 177.15 · PL 86.9 · Luck 1x · Chance 13.2% · Armor range [0, 100) · Odds 1/8 · Golden Dragon Boots", 0);
		assertNotNull(r);
		assertEquals("Golden Dragon Boots", r.item());
	}

	@Test
	void otherLinesAreNotRolls() {
		assertNull(RollDebug.parse("RARE DROP! Haunted Candy", 0));
		assertNull(RollDebug.parse("[MVP+] Steve: [Debug] is a word I said", 0));
		assertNull(RollDebug.parse("Staff > [ADMIN] Lauchy: hi", 0));
	}

	@Test
	void aRollThatNamesItsItemLandedEvenWithoutHit() {
		// Real lines: the Warden's roll names what it dropped, and the drop is announced on the next line.
		RollDebug.Roll r = RollDebug.parse("[Debug] Warden · Roll 3.194/100 · MF 266.695 · Tier Boost Core", 0);
		assertNotNull(r);
		assertEquals("Tier Boost Core", r.item());
		assertTrue(r.hit());
		assertFalse(RollDebug.parse("[Debug] Warden · Roll 88.1/100 · MF 266.695 · Golem Upgrader miss", 0).hit());
	}
}
