package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Gate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffActionsTest {
	@Test
	void everyServerCommandMatchesTheUsageTheServerPrints() {
		// Usage as /staff printed it on 2026-11-07 (docs/dragsim/staff-commands.md): the player first, then what the command says follows.
		java.util.Map<StaffActions, String> usage = new java.util.EnumMap<>(StaffActions.class);
		usage.put(StaffActions.BAN, "ban Steve 7d Spam");
		usage.put(StaffActions.IP_BAN, "ipban Steve 7d Spam");
		usage.put(StaffActions.MUTE, "mute Steve 7d Spam");
		usage.put(StaffActions.IP_MUTE, "ipmute Steve 7d Spam");
		usage.put(StaffActions.KICK, "kick Steve Spam");
		usage.put(StaffActions.WARN, "warn Steve Spam");
		usage.put(StaffActions.UNBAN, "unban Steve");
		usage.put(StaffActions.UNMUTE, "unmute Steve");
		usage.put(StaffActions.UNWARN, "unwarn Steve");
		usage.put(StaffActions.CAPTCHA, "captcha Steve");
		usage.put(StaffActions.HISTORY, "history Steve");
		usage.put(StaffActions.WARNS, "warns Steve");
		usage.put(StaffActions.ALTS, "alts Steve");
		usage.put(StaffActions.ACCOUNT_HISTORY, "accounthistory Steve");
		usage.put(StaffActions.LOOKUP, "lookup Steve");
		usage.put(StaffActions.AC_INFO, "dragsimac info Steve");
		usage.put(StaffActions.CAPTCHA_STATUS, "captcha status Steve");
		usage.put(StaffActions.SCAN, "scan Steve");
		usage.put(StaffActions.AUDIT_LOG, "auditlog Steve");
		usage.put(StaffActions.PROFILE_ADMIN, "profileadmin Steve");
		usage.put(StaffActions.GO_TO, "goto Steve");
		usage.put(StaffActions.BRING, "tp Steve Lauchy");
		usage.put(StaffActions.SEND, "send Steve hub");
		usage.put(StaffActions.VANISH, "vanish");
		usage.put(StaffActions.LOCK_CHAT, "lockchat");
		usage.put(StaffActions.HOP, "server hub");
		for (StaffActions a : StaffActions.values()) {
			if (!a.server()) continue;
			String expected = usage.get(a);
			org.junit.jupiter.api.Assertions.assertNotNull(expected, a + " sends a command no usage was checked against");
			String player = a.kind == StaffActions.Kind.SELF ? "" : "Steve";
			assertEquals(expected, StaffActions.fill(a, player, "Lauchy", "7d", "Spam", "hub"), a.toString());
		}
	}

	@Test
	void commandsAreFilledAsTheServerTakesThem() {
		assertEquals("ban Steve 7d 1.0 - Cheating", StaffActions.fill(StaffActions.BAN, "Steve", "Lauchy", "7d", "1.0 - Cheating", ""));
		assertEquals("ban Steve", StaffActions.fill(StaffActions.BAN, "Steve", "Lauchy", "", "", ""), "a permanent ban with no reason");
		assertEquals("tp Steve Lauchy", StaffActions.fill(StaffActions.BRING, "Steve", "Lauchy", "", "", ""));
		assertEquals("send Steve end", StaffActions.fill(StaffActions.SEND, "Steve", "Lauchy", "", "", "end"));
		assertEquals("dragsimac info Steve", StaffActions.fill(StaffActions.AC_INFO, "Steve", "", "", "", ""));
		assertEquals("warn Steve spam", StaffActions.fill(StaffActions.WARN, "Steve", "", "", "spam", ""));
	}

	@Test
	void helpersRunNoServerCommandsModsTheModOnesAndAdminsEveryOne() {
		assertEquals("MOD+", StaffActions.blocked(StaffActions.BAN, Gate.Tier.NONE, true));
		assertEquals("", StaffActions.blocked(StaffActions.WATCH, Gate.Tier.NONE, true), "Lazarus's own actions are anyone's on staff");
		assertEquals("", StaffActions.blocked(StaffActions.BAN, Gate.Tier.MOD, true));
		assertEquals("ADMIN+", StaffActions.blocked(StaffActions.SEND, Gate.Tier.MOD, true));
		assertEquals("", StaffActions.blocked(StaffActions.SEND, Gate.Tier.ADMIN, true));
		assertEquals("offline", StaffActions.blocked(StaffActions.KICK, Gate.Tier.ADMIN, false));
		assertEquals("", StaffActions.blocked(StaffActions.BAN, Gate.Tier.ADMIN, false), "an offline player can still be banned");
	}

	@Test
	void punishmentsThatEndAPlayersSessionHandleTheirReportsAndAWarningDoesnt() {
		for (StaffActions a : List.of(StaffActions.BAN, StaffActions.IP_BAN, StaffActions.MUTE, StaffActions.IP_MUTE, StaffActions.KICK)) assertTrue(a.handlesReports, a.name());
		assertFalse(StaffActions.WARN.handlesReports);
	}

	@Test
	void ranksReachTheirTier() {
		assertEquals(Gate.Tier.ADMIN, Gate.tier("OWNER"));
		assertEquals(Gate.Tier.ADMIN, Gate.tier("§c[ADMIN]"));
		assertEquals(Gate.Tier.MOD, Gate.tier("Mod"));
		assertEquals(Gate.Tier.NONE, Gate.tier("HELPER"));
		assertEquals(Gate.Tier.NONE, Gate.tier("MVP+"));
		assertEquals(Gate.Tier.NONE, Gate.tier(null));
		assertTrue(Gate.Tier.ADMIN.allows(Gate.Tier.MOD));
		assertFalse(Gate.Tier.NONE.allows(Gate.Tier.MOD));
	}

	@Test
	void aReplyLosesItsDecoration() {
		ServerReply r = ServerReply.of("lookup Steve", List.of("▬▬▬▬▬▬", "Account: Steve", "", "Profiles: 2\nSelected: Superior", "[Back to staff commands]"), 5);
		assertEquals(List.of("Account: Steve", "Profiles: 2", "Selected: Superior"), r.lines());
		assertFalse(ServerReply.of("x", List.of("", "▬▬"), 0).answered());
	}

	@Test
	void theDossierAsksItsRecordsInTurnAndCanAskAgain() {
		Dossier d = new Dossier("Steve");
		assertEquals(StaffActions.HISTORY, d.next());
		d.asked(StaffActions.HISTORY);
		assertTrue(d.asking());
		d.answered(StaffActions.HISTORY, ServerReply.of("history Steve", List.of("History of Steve (page 1)"), 1));
		assertEquals(StaffActions.WARNS, d.next());
		d.asked(StaffActions.WARNS);
		d.answered(StaffActions.WARNS, ServerReply.of("warns Steve", List.of(), 2));
		assertEquals(Dossier.Status.NO_ANSWER, d.status(StaffActions.WARNS));
		assertEquals(StaffActions.ALTS, d.next());
		d.retry(StaffActions.WARNS);
		assertEquals(StaffActions.WARNS, d.next(), "a record asked again goes back in the queue");
		d.asked(StaffActions.LOOKUP);
		d.retry(StaffActions.LOOKUP);
		assertNull(d.reply(StaffActions.LOOKUP));
	}
}
