package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chat cards the staff features build, checked as text. */
class StaffCardsTest {
	@Test
	void theHopCardMarksWhereYouAre() {
		String card = ServerHop.card(List.of("hub", "end", "rift"), "end", "Lauchy").build().getString();
		assertTrue(card.contains("end (here)"), card);
		assertTrue(card.contains("hub"), card);
		assertEquals("/send Lauchy rift", ServerHop.command("Lauchy", "rift"));
	}

	@Test
	void theHistoryCardSaysWhenTheServerStayedQuiet() {
		String card = LookHistory.card("Alex", List.of(), false).build().getString();
		assertTrue(card.contains("didn't answer"), card);
		card = LookHistory.card("Alex", List.of("1 ban, 2 mutes"), true).build().getString();
		assertTrue(card.contains("1 ban, 2 mutes"), card);
		assertTrue(card.contains("from a moment ago"), card);
		assertTrue(card.contains("Staff"), card);
	}

	@Test
	void theHistoryListIsReadIntoEntries() {
		List<String> lines = List.of("———————— History of ressentiram (page 1) ————————",
				"#205 BAN ressentiram by Porier on Oct 28, 2026 02:15 - No reason specified (expired)",
				"#39 BAN ressentiram by Porier on Sep 15, 2026 21:43 - Profile Restoration (undone by Porier)",
				"#12 MUTE ressentiram by Porier on Sep 11, 2026 07:31 - Go Message Fuyq Privately <3 (expired)",
				"#74 KICK ressentiram by Porier on Sep 19, 2026 06:05 - No reason specified (undone)");
		List<LookHistory.Entry> entries = LookHistory.parse(lines);
		assertEquals(4, entries.size());
		assertEquals(new LookHistory.Entry(39, "BAN", "Porier", "Sep 15, 2026 21:43", "Profile Restoration", "undone by Porier"), entries.get(1));
		assertEquals("Go Message Fuyq Privately <3", entries.get(2).reason());
		assertEquals("2 bans · 1 mute · 1 kick", LookHistory.summary(entries));
		String card = LookHistory.card("ressentiram", lines, false).build().getString();
		assertTrue(card.contains("2 bans · 1 mute · 1 kick"), card);
		assertTrue(card.contains("BAN Sep 15, 2026 21:43 Profile Restoration (undone by Porier) by Porier"), card);
		assertTrue(LookHistory.isHistory(lines));
		assertTrue(LookHistory.card("Steve", List.of("———— History of Steve (page 1) ————"), false).build().getString().contains("Clean record"));
		for (String l : lines) assertTrue(LookHistory.looksLike(l), l);
		assertFalse(LookHistory.looksLike("You picked up 3 Enchanted Bones"));
	}

	@Test
	void theAltsListIsTheNamesUnderItsHeaderAndNothingElse() {
		assertEquals(List.of("plaus", "FreeLauchy"), AltFlag.names(List.of("———————— Alts of Lauchy (2) ————————", "plaus", "FreeLauchy"), "Lauchy"));
		assertEquals(List.of(), AltFlag.names(List.of("———————— Alts of Lauchy (0) ————————"), "Lauchy"));
		// Without the header, a sentence from the server is never mistaken for a list of names.
		assertEquals(List.of(), AltFlag.names(List.of("Could not find player Lauchy."), "Lauchy"));
		assertTrue(AltFlag.looksLike("———————— Alts of Lauchy (2) ————————"));
		assertTrue(AltFlag.looksLike("plaus"));
		assertFalse(AltFlag.looksLike("Could not find that player."));
	}

	@Test
	void theAltsCardLinksEachName() {
		String card = AltFlag.card("Lauchy", List.of("———— Alts of Lauchy (1) ————", "plaus"), List.of("plaus")).build().getString();
		assertTrue(card.contains("plaus"), card);
		assertTrue(card.contains("Staff"), card);
	}

	@Test
	void captchaResultsBecomeToasts() {
		assertEquals("§aLauchy passed the check §7in 8s", CaptchaKey.result("[Captcha] Lauchy passed the check Lauchy sent, in 8s."));
		assertEquals("§cSteve failed the check", CaptchaKey.result("[Captcha] Steve failed the check Lauchy sent."));
		assertNull(CaptchaKey.result("Sent a terminal check to Lauchy. They have 5 minutes."));
	}
}
