package dev.lauchy.lazarus.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Wrapping HUD panel rows, measured by letters so no font is needed. */
class DrawWrapTest {
	/** Every visible character is one unit wide; § codes are free, as in the real font. */
	private static final ToIntFunction<String> LETTERS = s -> s.replaceAll("§.", "").length();

	@Test
	void shortLinesStayAsTheyAre() {
		assertEquals(List.of("§2MOD §ffcxxv§7: §fhi"), Draw.wrap(LETTERS, "§2MOD §ffcxxv§7: §fhi", 20));
	}

	@Test
	void longLinesBreakAtSpacesAndKeepTheirColour() {
		List<String> out = Draw.wrap(LETTERS, "§2MOD §ffcxxv§7: §fwhat is the leave alert meant to be", 26);
		assertEquals(List.of("§2MOD §ffcxxv§7: §fwhat is the", "  §fleave alert meant to be"), out);
	}

	@Test
	void boldCarriesOverAndAWordTooWideIsCut() {
		assertEquals(List.of("§d§lStaff chat", "  §d§lpanel"), Draw.wrap(LETTERS, "§d§lStaff chat panel", 12));
		assertEquals(List.of("abcdefgh", "  ijklmn"), Draw.wrap(LETTERS, "abcdefghijklmn", 8));
	}

	@Test
	void theCodesInForceAreTheLastColourAndBold() {
		assertEquals("§c", Draw.codesInForce("§2MOD §cfcxxv"));
		assertEquals("§c§l", Draw.codesInForce("§2MOD §c§lname"));
		assertEquals("", Draw.codesInForce("§c§lname§r plain"));
	}
}
