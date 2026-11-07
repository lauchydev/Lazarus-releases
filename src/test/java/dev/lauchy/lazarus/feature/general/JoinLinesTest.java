package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinLinesTest {
	@Test
	void aLineIsTidiedTheWayTheServerKeepsIt() {
		assertEquals("Ready to grind!", JoinLines.tidy("  §6Ready   to grind! "));
		assertEquals("x".repeat(48), JoinLines.tidy("x".repeat(60)));
		assertEquals("", JoinLines.tidy("   "));
	}

	@Test
	void linksAndOddCharactersAreRefused() {
		assertEquals("", JoinLines.tidy("join discord.gg/abc"));
		assertEquals("", JoinLines.tidy("see lazaruss.co"));
		assertEquals("", JoinLines.tidy("http://x"));
		assertEquals("", JoinLines.tidy("hello <3"));
		assertEquals("", JoinLines.tidy("hi @everyone"));
	}
}
