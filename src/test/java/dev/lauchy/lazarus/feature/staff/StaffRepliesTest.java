package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's real answers, as captured on 2026-11-07 (docs/dragsim/staff-replies.md). */
class StaffRepliesTest {
	@Test
	void punishmentEntriesReadEveryStateTheServerWrites() {
		StaffReplies.Entry active = StaffReplies.entry("#290 MUTE TestAlt by Lauchy on Nov 7, 2026 05:01 - Lazarus test (active, 59m left)");
		assertEquals(new StaffReplies.Entry(290, "MUTE", "TestAlt", "Lauchy", "Nov 7, 2026 05:01", "Lazarus test", StaffReplies.State.ACTIVE, "59m", ""), active);

		StaffReplies.Entry undone = StaffReplies.entry("#289 WARN TestAlt by Lauchy on Nov 7, 2026 05:01 - Lazarus test (undone by Lauchy)");
		assertEquals(StaffReplies.State.UNDONE, undone.state());

		assertEquals(StaffReplies.State.ENDED, StaffReplies.entry("#291 KICK TestAlt by Lauchy on Nov 7, 2026 05:02 - Lazarus test (undone)").state());
		StaffReplies.Entry perm = StaffReplies.entry("#279 BAN CallMeDaddyy_ by Porier on Nov 6, 2026 20:50 - No reason specified (active, Permanent left)");
		StaffReplies.Entry dashes = StaffReplies.entry("#284 BAN Ticarofangirl by StylerWKeys on Nov 6, 2026 22:02 - 1.0 - Cheating Through Unfair Advantages (active, 6d 17h left)");
		assertEquals("1.0 - Cheating Through Unfair Advantages", dashes.reason());
		assertEquals("IPBAN", StaffReplies.entry("#275 IPBAN MrShadowIsSo67 by DragSim on Nov 6, 2026 17:28 - Ban evading (active, 6d 12h 26m left)").kind());
	}

	@Test
	void chatStackingsCountIsIgnored() {
		StaffReplies.Entry e = StaffReplies.entry("#292 BAN TestAlt by Lauchy on Nov 7, 2026 05:02 - Lazarus test (active, 59m left) (x2)");
		assertEquals(292, e.id());
		assertEquals("59m", e.left());
	}

	@Test
	void headersNameTheirList() {
		assertEquals("History of TestAlt (page 1)", StaffReplies.header("-------- History of TestAlt (page 1) --------"));
		assertEquals("Warnings of TestAlt (0)", StaffReplies.header("-------- Warnings of TestAlt (0) --------"));
		assertNull(StaffReplies.header("#289 WARN TestAlt by Lauchy on Nov 7, 2026 05:01 - x (undone)"));
	}

	@Test
	void lookupAndAccountHistoryAreKeyValueLines() {
		List<String> lookup = List.of("-----------------------------", "Lookup: TestAlt", "Rank: DRAGON", "UUID: 00000000-0000-4000-8000-000000000001",
				"IP: /203.0.113.42:60418", "Balance: 1,000 coins", "Health: 1043", "Mana: 300", "----------------------------- (x2)");
		Map<String, String> f = StaffReplies.fields(lookup);
		assertEquals("DRAGON", f.get("Rank"));
		assertEquals("1,000 coins", f.get("Balance"));
		assertEquals("/203.0.113.42:60418", f.get("IP"));
		assertTrue(lookup.stream().allMatch(StaffReplies::lookupLine));
		assertFalse(StaffReplies.lookupLine("kortx: whos selling endstone bushes"));

		Map<String, String> history = StaffReplies.fields(List.of("-------- Account history of TestAlt --------", "Names: TestAlt", "Addresses: 3 (hidden; use /alts)"));
		assertEquals("TestAlt", history.get("Names"));
		assertEquals("3 (hidden; use /alts)", history.get("Addresses"));
	}

	@Test
	void theAnticheatsLineIsKeyValuePairs() {
		Map<String, String> ac = StaffReplies.acInfo(List.of("[DragSim AC] TestAlt checks=176; vl=0; transaction=219ms; received=1656; sent=8886; alerts=false"));
		assertEquals("176", ac.get("checks"));
		assertEquals("0", ac.get("vl"));
		assertEquals("219ms", ac.get("transaction"));
		assertEquals("false", ac.get("alerts"));
	}

	@Test
	void altsAreTheNamesUnderTheirHeader() {
		assertEquals(List.of("Lauchy", "OtherAlt"), StaffReplies.alts(List.of("-------- Alts of TestAlt (2) --------", "Lauchy", "OtherAlt",
				"Online Nicknames (0)", "No online players are nicked.")));
	}

	@Test
	void anActionsAnswerBecomesOneTidyLine() {
		assertEquals("Muted TestAlt for 1h · Lazarus test · #290", StaffReplies.outcome(List.of(
				"[Punish] Lauchy muted TestAlt for 1h - Lazarus test (#290)", "You muted TestAlt for 1h. Reason: Lazarus test (#290)")));
		assertEquals("Warned TestAlt · Lazarus test · #289", StaffReplies.outcome(List.of("You warned TestAlt. Reason: Lazarus test (#289)")));
		assertEquals("Kicked TestAlt · Lazarus test · #291", StaffReplies.outcome(List.of("You kicked TestAlt. Reason: Lazarus test (#291)")));
		assertEquals("Removed the ban on TestAlt", StaffReplies.outcome(List.of("Removed the ban on TestAlt.")));
		assertEquals("Captcha sent to TestAlt · 5 minutes to answer", StaffReplies.outcome(List.of("Sent a terminal check to TestAlt. They have 5 minutes.")));
		assertEquals("Teleported TestAlt to you", StaffReplies.outcome(List.of("Teleported TestAlt to you.")));
		assertEquals("You're vanished", StaffReplies.outcome(List.of("You are now vanished. Other players cannot see you.")));
		assertEquals("Dupe scan started · results go to MOD+ chat", StaffReplies.outcome(List.of("Scanning TestAlt...", "Scan started. Results will be reported to MOD+ chat.")));
		assertTrue(StaffReplies.outcomeLine("You banned TestAlt for 1h. Reason: Lazarus test (#292)"));
		assertFalse(StaffReplies.outcomeLine("[Punish] Lauchy banned TestAlt for 1h - Lazarus test (#292)"));
	}

	@Test
	void datesDropThisYear() {
		assertEquals("Nov 7, 05:01", StaffReplies.shortDate("Nov 7, 2026 05:01", 2026));
		assertEquals("Oct 17, 2025 02:55", StaffReplies.shortDate("Oct 17, 2025 02:55", 2026));
	}

	/** /auditlog as the server sent it on 2026-11-07 (colour codes already gone, as chat hands them over). */
	@Test
	void auditLogReadsEveryTransaction() {
		List<String> reply = List.of(
				"50 transactions for Finnzius (last 7 days):",
				"[11/07 00:01:35] CREATE pet_item_lucky_clover (pet_take_out_equipped)",
				"[11/07 00:01:38] TRANSFER golden_dragon_pet_legendary ↔ MaciekByr (trade)",
				"[11/07 00:07:54] DESTROY summoning_eye x7 (npc_sell)",
				"[11/07 00:08:43] GIVE rift_goo x15 (loot_drop)");
		for (String l : reply) assertTrue(StaffReplies.auditLine(l), l);
		assertFalse(StaffReplies.auditLine("[DRAGON] Steve: hi"));
		List<StaffReplies.Audit> a = StaffReplies.audits(reply);
		assertEquals(4, a.size());
		assertEquals(new StaffReplies.Audit("11/07 00:01:35", "CREATE", "pet_item_lucky_clover", 1, "", "pet_take_out_equipped"), a.get(0));
		assertEquals(new StaffReplies.Audit("11/07 00:01:38", "TRANSFER", "golden_dragon_pet_legendary", 1, "MaciekByr", "trade"), a.get(1));
		assertEquals(7, a.get(2).count());
		assertEquals("Golden Dragon Pet Legendary", a.get(1).itemName());
		assertEquals("last 7 days", StaffReplies.auditSpan(reply));
		assertTrue(StaffReplies.auditEmpty(List.of("No transactions found for TestAlt.")));
		assertTrue(StaffReplies.auditLine("No transactions found for TestAlt."));
	}

	/** /warns lists a warning still in force with no state in brackets (desk check, 2026-11-07). */
	@Test
	void anActiveWarningHasNoStateInBrackets() {
		StaffReplies.Entry w = StaffReplies.entry("#293 WARN TestAlt by Lauchy on Nov 7, 2026 06:41 - Lazarus desk check");
		assertEquals(StaffReplies.State.ACTIVE, w.state());
		assertEquals("Lazarus desk check", w.reason());
		StaffReplies.Entry again = StaffReplies.entry("#294 WARN TestAlt by Lauchy on Nov 7, 2026 06:42 - Spam (again)");
		assertEquals("Spam (again)", again.reason(), "brackets that aren't a state stay in the reason");
		assertEquals(StaffReplies.State.ACTIVE, again.state());
		assertEquals(1, StaffReplies.entries(List.of("-------- Warnings of TestAlt (1) --------", "#293 WARN TestAlt by Lauchy on Nov 7, 2026 06:41 - Lazarus desk check")).size());
	}

	/** /captcha status for someone with no captcha (seen 2026-11-07). */
	@Test
	void captchaStatusWithNoneOnRecord() {
		assertEquals("No captcha on record for greenum.", StaffReplies.captchaStatus("greenum: no activity tracked"));
		assertEquals("something else", StaffReplies.captchaStatus("something else"));
	}

	/** A ban the server replaced with a newer one (desk walk, 2026-11-07). */
	@Test
	void aReplacedBanSaysWhatReplacedIt() {
		StaffReplies.Entry e = StaffReplies.entry("#296 BAN TestAlt by Captcha on Nov 7, 2026 06:41 - Suspicious Activity has been Detected on your Account. (undone by superseded by #297)");
	}
}
