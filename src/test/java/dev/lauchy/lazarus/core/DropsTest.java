package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropsTest {
	@Test
	void bracketedNameWithMagicFind() {
		assertEquals(new Drops.Drop("Summoning Eye", 1), Drops.parse("RARE DROP! (Summoning Eye) (✯ 239%)"));
	}

	@Test
	void bracketedNameAlone() {
		assertEquals(new Drops.Drop("Summoning Eye", 1), Drops.parse("RARE DROP! (Summoning Eye)"));
	}

	@Test
	void plainNameAndCounts() {
		assertEquals(new Drops.Drop("Golden Eye", 1), Drops.parse("EPIC DROP! Golden Eye"));
		assertEquals(new Drops.Drop("Ender Pearl", 2), Drops.parse("RARE DROP! 2x Ender Pearl (+50% Magic Find)"));
		assertEquals(new Drops.Drop("Rift Goo", 13), Drops.parse("RARE DROP! Rift Goo x13"));
	}

	@Test
	void pet() {
		assertEquals(new Drops.Drop("Enderman", 1), Drops.parse("EPIC DROP! [Lvl 1] Enderman"));
		assertEquals(new Drops.Drop("Lustrous Orb", 1), Drops.parse("RNGESUS DROP! Lustrous Orb"));
	}

	@Test
	void bonus() {
		assertEquals(new Drops.Drop("Rift Goo", 3, Drops.Source.BONUS), Drops.parse("BONUS! You received 3x Rift Goo!"));
	}

	@Test
	void playerPastingADropIsNotOne() {
		assertNull(Drops.parse("[MVP+] Name: EPIC DROP! Golden Eye"));
		assertNull(Drops.parse("[DRAGON] gooncaves: [00:48:41] RARE DROP! (Summoning Eye) (✯ 32%)"));
	}

	@Test
	void aStaffRollNamesTheMobAndTheItem() {
		assertEquals(new Drops.Roll("Riftborn Seraph", "Null Ovoid", true), Drops.roll("[Debug] Riftborn Seraph · Roll 78.046/100 · MF 349.616 · Null Ovoid; fixed odds HIT"));
		assertEquals(new Drops.Roll("Special Zealot", "Golden Eye", true), Drops.roll("[Debug] Special Zealot · Roll 12.3/100 · MF 255.635 · Golden Eye"));
		assertEquals(new Drops.Roll("Special Zealot", "Rift Pass", true), Drops.roll("[Debug] Special Zealot · Roll 0.5/100 · MF 255.635 · Rift Pass HIT"));
		assertEquals(new Drops.Roll("Golden Dragon", "Haunted Candy", true), Drops.roll("[Debug] Golden Dragon · Roll 5.146/100 · MF 255.635 · Haunted Candy; MF not applied HIT"));
		assertEquals(new Drops.Roll("Protector Dragon", "Aspect of the Dragons", true), Drops.roll("[Debug] Protector Dragon · Table 75.828/100 · MF 255.635 · Aspect of the Dragons"));
	}

	@Test
	void aStaffRollSkipsTheNumbersAboutTheRoll() {
		assertEquals(new Drops.Roll("The Disturbed", "The Art of War", true),
				Drops.roll("[Debug] The Disturbed · Roll 0.10908/100 · MF 448.91888 · PL 76.7 · Luck 2x · The Art of War · Chance 0.25653%"));
		assertEquals(new Drops.Roll("Revenant Horror", "Warden Heart", true),
				Drops.roll("[Debug] Revenant Horror · Roll 50/100 · MF 300 · Warden Heart · Range [0, 10) · 1 item odds (hover)"));
	}

	@Test
	void aMissedRollIsNotAHit() {
		Drops.Roll miss = Drops.roll("[Debug] Unstable Dragon · Roll 60/100 · MF 300 · PL 70 · Frozen Core miss");
		assertEquals("Frozen Core", miss.item());
		assertFalse(miss.hit());
		assertTrue(Drops.roll("[Debug] Special Zealot · Roll 0.5/100 · MF 255.635 · Rift Pass HIT").hit());
	}

	@Test
	void spawnRollsTablesAndPastedRollsAreNotDrops() {
		assertNull(Drops.roll("[Debug] Young Dragon · Spawn 40/100 · MF 300 · Young Dragon"));
		assertNull(Drops.roll("[Debug] Crypt Cache · Roll 32.399/100 · MF 406.956 · DragonLootDrop"));
		assertNull(Drops.roll("[DRAGON] FinalCake: [Debug] The Disturbed · Roll 0.1/100 · MF 448 · The Art of War"));
		assertNull(Drops.roll("RARE DROP! Hot Potato Book"));
	}

	@Test
	void aServerLineInTheDropListIsNoDrop() {
		java.util.function.Predicate<String> known = item -> item.equals("Crypt Cache");
		assertNull(Drops.listed("OOPS! You don't have enough mana!", known));
		assertNull(Drops.listed("Teleported to the hub.", known));
		assertNull(Drops.listed("Something Unknown", known));
		assertEquals(new Drops.Drop("Crypt Cache", 1), Drops.listed("Crypt Cache", known));
		assertEquals(new Drops.Drop("Rift Goo", 13), Drops.listed("13x Rift Goo", known));
		assertNull(Drops.listed("5 ☘ Mining Fortune", known));
		assertTrue(Drops.notAnItem("OOPS! You don't have enough mana!"));
		assertTrue(Drops.notAnItem("☘ Mining Fortune"));
		assertFalse(Drops.notAnItem("Gelatinous Membrane"));
		assertFalse(Drops.notAnItem("◆ End Rune III"));
	}
}
