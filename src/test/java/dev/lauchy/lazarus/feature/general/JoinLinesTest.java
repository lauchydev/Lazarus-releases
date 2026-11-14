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

	@Test
	void aJoinReadsAsAMarkTheMembersNameAndTheirLineWithNoLazarusBeforeIt() {
		assertEquals("> Steve Ready to grind!", JoinLines.shown("Steve", "Ready to grind!").getString());
	}

	@Test
	void theOwnerHearsWhatRemovingAndAllowingDid() {
		assertEquals("Lazarus > Removed Steve's join message. Allow again", JoinLines.moderated("remove", "Steve", true).getString());
		assertEquals("Lazarus > Steve had no join message. They can't set one now. Allow again", JoinLines.moderated("remove", "Steve", false).getString());
		assertEquals("Lazarus > Steve can write a join message again.", JoinLines.moderated("allow", "Steve", true).getString());
		assertEquals("Lazarus > Steve was never stopped from writing one.", JoinLines.moderated("allow", "Steve", false).getString());
	}
}
