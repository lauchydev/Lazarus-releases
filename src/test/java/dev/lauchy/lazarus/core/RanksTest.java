package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RanksTest {
	@AfterEach
	void defaults() {
		Ranks.configure("");
	}

	@Test
	void eachStaffRankHasItsColour() {
		assertEquals('9', Ranks.code("OWNER"));
		assertEquals('c', Ranks.code("§c[ADMIN]"));
		assertEquals('2', Ranks.code("Mod"));
		assertEquals('2', Ranks.code("MODERATOR"));
		assertEquals('b', Ranks.code("helper"));
		assertEquals(0, Ranks.code("MVP+"));
		assertEquals("§cADMIN", Ranks.tag("admin"));
		assertNull(Ranks.tag("VIP"));
	}

	@Test
	void coloursCanBeChangedFromText() {
		Ranks.configure("owner=1, admin = A, mod=zz, nobody=3");
		assertEquals('1', Ranks.code("OWNER"));
		assertEquals('a', Ranks.code("ADMIN"), "codes are read in any case");
		assertEquals('2', Ranks.code("MOD"), "a bad code keeps the default");
		assertEquals(0, Ranks.code("NOBODY"), "only staff ranks are coloured");
	}
}
