package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffTest {
	@Test
	void ranksAreReducedToOneSpelling() {
		assertEquals("MVP+", Staff.rankKey("§b[MVP_PLUS]"));
		assertEquals("MVP+", Staff.rankKey("MVP PLUS"));
		assertEquals("MOD", Staff.rankKey(" mod "));
	}

	@Test
	void helperAndAboveAreStaff() {
		for (String rank : new String[] {"[HELPER]", "Mod", "MODERATOR", "§cADMIN", "Manager", "OWNER"}) assertTrue(Staff.RANKS.contains(Staff.rankKey(rank)), rank);
		for (String rank : new String[] {"", "VIP", "MVP_PLUS", "DRAGON"}) assertFalse(Staff.RANKS.contains(Staff.rankKey(rank)), rank);
	}
}
