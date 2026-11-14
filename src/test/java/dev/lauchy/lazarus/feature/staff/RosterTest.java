package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterTest {
	private static final long NOW = 10 * 86_400_000L;

	/** Shared state with only the lists given; the rest empty. */
	private static StaffFlags.State state(List<StaffFlags.Watch> watch, List<StaffFlags.Captcha> captchas, List<StaffFlags.Report> reports,
			List<StaffFlags.Flag> flags, List<StaffFlags.Alt> alts) {
		return new StaffFlags.State(watch, captchas, reports, flags, NOW, alts, NOW);
	}

	private static Roster.Online on(String name, String rank) {
		return new Roster.Online(name, "", rank);
	}

	private static List<String> names(List<Roster.Row> rows) {
		return rows.stream().map(Roster.Row::name).toList();
	}

	private static Roster.Row row(List<Roster.Row> rows, String name) {
		return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void aDashboardHeadReadsItsFlagsOnceAndDropsEconomyAnomalies() {
		DashboardLore.Entry e = DashboardLore.read("sixmas (11.8)", List.of("Score: 11.8", "Active flags: 7", "",
				"- ECONOMY_ANOMALY (EconomyAnomalyDetector)", "- ECONOMY_ANOMALY (EconomyAnomalyDetector)",
				"- MACRO (GuiRateLimiter)", "- MACRO (GuiRateLimiter)", "- FLY", "Click for details!"));
		assertNotNull(e);
		assertEquals("sixmas", e.name());
		assertEquals(11.8, e.score());
		assertEquals(List.of(new DashboardLore.Flag("MACRO", "GuiRateLimiter"), new DashboardLore.Flag("FLY", "")), e.flags());
	}

	@Test
	void aPlayerWithOnlyEconomyFlagsComesBackWithNone() {
		DashboardLore.Entry e = DashboardLore.read("Steve (3.0)", List.of("- ECONOMY_ANOMALY (EconomyAnomalyDetector)"));
		assertNotNull(e);
		assertTrue(e.flags().isEmpty());
	}

	@Test
	void fillerAndButtonsAreNotDashboardEntries() {
		assertNull(DashboardLore.read(" ", List.of()));
		// The dashboard since 2026-11: the player's name over a review score, unreviewed flags and the newest source.
		DashboardLore.Entry now = DashboardLore.read("yilmaz", List.of("Review score: 586.8", "Unreviewed flags: 427", "Newest source: DragSim AC",
				"6e7c02ed-1dd1-4d54-80f8-eb4f80f8aacd", "Click to inspect evidence"));
		assertEquals(new DashboardLore.Entry("yilmaz", 586.8, List.of(new DashboardLore.Flag("ANTICHEAT", "427 unreviewed · DragSim AC"))), now);
		assertEquals(List.of(), DashboardLore.read("Trader", List.of("Review score: 3.0", "Unreviewed flags: 2", "Newest source: Economy Monitor")).flags(),
				"an economy flag is left out");
		assertNull(DashboardLore.read("Close", List.of()));
		assertNull(DashboardLore.read("Next Page", List.of("Click to turn")));
		assertNull(DashboardLore.read("Steve", List.of("Just a head")));
		// A player whose name starts like a button is still a player.
		assertNotNull(DashboardLore.read("Nextgen (2.0)", List.of("- MACRO (GuiRateLimiter)")));
	}

	@Test
	void checkaltsFlagsEveryoneInABannedGroupNamingTheRest() {
		List<String> page = List.of("Scanning all known players... [Online] [Offline] [Banned]",
				"Scanned 4574 players (139 online). 16 matching groups. Page 1/2",
				"» Banned account linked", "0rose7, Smurgius",
				"» Banned account linked Banned IP", "Aegleseer, ImCert",
				"» 2 online on the same IP", "KenjiWong, lukegod10",
				"» Banned IP (x2)", "Loner",
				"Next: /checkalts 2");
		assertEquals(List.of(
				new AltPages.Link("0rose7", "Linked to a banned account", "Smurgius"),
				new AltPages.Link("Smurgius", "Linked to a banned account", "0rose7"),
				new AltPages.Link("Aegleseer", "Linked to a banned account on a banned IP", "ImCert"),
				new AltPages.Link("ImCert", "Linked to a banned account on a banned IP", "Aegleseer"),
				new AltPages.Link("Loner", "On a banned IP", "")), AltPages.parse(page));
		assertEquals(2, AltPages.nextPage(page));
		assertEquals(0, AltPages.nextPage(List.of("Shared IPs show possible links, not proof of the same person.")));
		assertTrue(page.stream().allMatch(AltPages::looksLike));
		assertTrue(AltPages.parse(List.of("> Banned IP", "Steve")).isEmpty());
	}

	/** The answer as it comes, coloured: a red name is the banned one, named first and marked among the rest. */
	@Test
	void checkaltsNamesWhichLinkedAccountIsBanned() {
		List<String> page = List.of("§7Scanning all known players... §a[Online] §7[Offline] §c[Banned]",
				"§8» §cBanned account linked", "§aSmurgius§7, §c0rose7§7, §7Quiet",
				"§8» §cBanned IP", "§aLoner§7, §#ff5555Gone",
				"§8Shared IPs show possible links, not proof of the same person.");
		assertEquals(List.of(
				new AltPages.Link("Smurgius", "Linked to a banned account", "0rose7 (banned), Quiet"),
				new AltPages.Link("0rose7", "Linked to a banned account", "Smurgius, Quiet"),
				new AltPages.Link("Quiet", "Linked to a banned account", "0rose7 (banned), Smurgius"),
				new AltPages.Link("Loner", "On a banned IP", "Gone (banned)"),
				new AltPages.Link("Gone", "On a banned IP", "Loner")), AltPages.parse(page));
		assertTrue(page.stream().allMatch(AltPages::looksLike));
		assertEquals(List.of("0rose7"), AltPages.banned("0rose7 (banned), Quiet"));
		assertEquals(List.of("0rose7", "Quiet"), AltPages.names("0rose7 (banned), Quiet"));
		assertEquals(List.of(), AltPages.banned("Smurgius"));
		// The roster names the banned account rather than the whole group.
		StaffFlags.State s = state(List.of(), List.of(), List.of(), List.of(), List.of(new StaffFlags.Alt("Quiet", "Linked to a banned account", "0rose7 (banned), Smurgius", NOW)));
		assertEquals("Linked to 0rose7 (banned)", Roster.build(List.of(on("Quiet", "")), s, NOW).getFirst().reasons().getFirst().text());
	}

	@Test
	void aCaptchaLineGivesWhoTookItWhoSentItAndHowLong() {
		assertEquals(new CaptchaLine.Result("Steve", false, "Lauchy", 8), CaptchaLine.parse("[Captcha] Steve failed the check Lauchy sent, in 8s."));
		assertEquals(-1, CaptchaLine.parse("[Captcha] Steve passed the check Lauchy sent").seconds());
		assertNull(CaptchaLine.parse("Steve: [Captcha] Steve passed the check Lauchy sent"));
	}

	@Test
	void theRankIsTheTagInFrontOfTheTabName() {
		assertEquals("ADMIN", Roster.rankOf("§c[ADMIN] Lauchy"));
		assertEquals("MVP+", Roster.rankOf("[MVP_PLUS] Steve"));
		assertEquals("", Roster.rankOf("Steve"));
	}

	@Test
	void everyoneElseFollowsDragsimsRankOrderWithDefaultLast() {
		List<Roster.Online> online = List.of(on("vip", "VIP"), on("none", ""), on("mvp", "MVP"), on("admin", "ADMIN"), on("odd", "BUILDER"),
				on("dragon", "DRAGON"), on("vipplus", "VIP+"), on("owner", "OWNER"), on("mod", "MOD"), on("mvpplus", "MVP+"), on("manager", "MANAGER"),
				on("patrick", "PATRICK"), on("whale", "WHALE"));

		assertEquals(List.of("owner", "manager", "admin", "mod", "whale", "patrick", "dragon", "mvpplus", "mvp", "vipplus", "vip", "odd", "none"),
				names(Roster.build(online, StaffFlags.EMPTY, NOW)));
	}

	@Test
	void theRankTagIsTheLastTagBeforeTheNameSoAVanishTagIsSkipped() {
		// As Dragsim's tab list shows a vanished admin: "[V] [ADMIN] Lauchy".
		assertEquals("ADMIN", Roster.rankOf("§7[V] §c[ADMIN] Lauchy"));
		assertEquals(4, Roster.tagStart("[V] [ADMIN] Lauchy"));
		assertEquals(11, Roster.tagEnd("[V] [ADMIN] Lauchy"));
		assertEquals("[ADMIN]", "[V] [ADMIN] Lauchy".substring(4, 11));
		// A vanished player with no rank: the name, after the vanish tag.
		assertEquals("", Roster.rankOf("[V] Steve"));
		assertEquals(4, Roster.tagStart("[V] Steve"));
		assertEquals(4, Roster.tagEnd("[V] Steve"));
		// Plain ones, and a leading space.
		assertEquals(0, Roster.tagStart("[DRAGON] AnnoyingApple"));
		assertEquals(1, Roster.tagStart(" [MVP+] Celtic_Dancer"));
		assertEquals(0, Roster.tagStart("Steve"));
		assertEquals(0, Roster.tagStart(""));
	}

	@Test
	void flaggedPlayersComeFirstHighestScoreOnTopThenEveryoneByRank() {
		List<Roster.Online> online = List.of(on("Bob", "VIP"), on("Ann", "ADMIN"), on("Cat", ""), on("Dan", "MVP+"), on("Old", ""), on("Eve", ""), on("Fay", ""));
		StaffFlags.State s = state(
				List.of(new StaffFlags.Watch("Old", "", "Lauchy", NOW - 5 * 86_400_000L)),
				List.of(new StaffFlags.Captcha("cat", true, "Lauchy", 4, NOW - 60_000)),
				List.of(new StaffFlags.Report(1, "Dan", "Ann", "flying", NOW - 3_600_000)),
				List.of(new StaffFlags.Flag("Eve", "MACRO", "GuiRateLimiter", 4, NOW - 7_200_000), new StaffFlags.Flag("Fay", "ANTICHEAT", "", 586.8, NOW - 9_000_000)),
				List.of());

		List<Roster.Row> rows = Roster.build(online, s, NOW);

		// The highest score first, those without one after by their newest reason, then everyone else by rank.
		assertEquals(List.of("Fay", "Eve", "Cat", "Dan", "Old", "Ann", "Bob"), names(rows));
		assertTrue(rows.get(4).flagged());
		assertFalse(rows.get(5).flagged());
	}

	@Test
	void aPassedCaptchaFlagsTooButItsChipIsntBad() {
		StaffFlags.State s = state(List.of(), List.of(
				new StaffFlags.Captcha("Passer", true, "Lauchy", 5, NOW - 1000),
				new StaffFlags.Captcha("Failer", false, "Lauchy", -1, NOW - 2000),
				new StaffFlags.Captcha("Both", true, "Lauchy", -1, NOW - 1000),
				new StaffFlags.Captcha("Both", false, "Lauchy", -1, NOW - 9000)), List.of(), List.of(), List.of());

		List<Roster.Row> rows = Roster.build(List.of(on("Passer", ""), on("Failer", ""), on("Both", "")), s, NOW);

		Roster.Row passer = row(rows, "Passer");
		assertEquals(List.of(new Roster.Chip("CAPTCHA", Roster.Source.CAPTCHA, false)), passer.chips());
		assertEquals("Passed a captcha from Lauchy in 5s, just now", passer.reasons().getFirst().text());
		assertTrue(row(rows, "Failer").chips().getFirst().bad());
		// The chip goes by the newest check.
		Roster.Row both = row(rows, "Both");
		assertFalse(both.chips().getFirst().bad());
		assertEquals(2, both.reasons().size());
	}

	@Test
	void chipsNameEachFlagTypeCountReportsAndFollowInOrder() {
		StaffFlags.State s = state(
				List.of(new StaffFlags.Watch("Sus", "macro", "Lauchy", NOW - 10)),
				List.of(),
				List.of(new StaffFlags.Report(1, "Sus", "A", "", NOW - 20), new StaffFlags.Report(2, "sus", "B", "x", NOW - 30)),
				List.of(new StaffFlags.Flag("Sus", "MACRO", "GuiRateLimiter", 9, NOW - 40), new StaffFlags.Flag("Sus", "MACRO", "ClickPattern", 9, NOW - 50),
						new StaffFlags.Flag("Sus", "AUTO_CLICK", "", 9, NOW - 60)),
				List.of(new StaffFlags.Alt("Sus", "On a banned IP", "Other", NOW - 70)));

		Roster.Row r = Roster.build(List.of(on("Sus", "")), s, NOW).getFirst();

		assertEquals(List.of("MACRO", "AUTO_CLICK", "REPORT ×2", "ALT", "WATCH"), r.chips().stream().map(Roster.Chip::label).toList());
		assertEquals(9, r.score());
		assertEquals("Watched by Lauchy: macro", r.reasons().getFirst().text());
		assertEquals("Flagged for Auto click, just now", r.reasons().get(5).text());
		assertEquals("On a banned IP: Other", r.reasons().get(6).text());
	}

	@Test
	void aReasonUnderANickCountsForThePlayerBehindIt() {
		StaffFlags.State s = state(List.of(), List.of(), List.of(new StaffFlags.Report(1, "Disguise", "Ann", "", NOW)), List.of(), List.of());

		Roster.Row r = Roster.build(List.of(new Roster.Online("Realname", "Disguise", "")), s, NOW).getFirst();

		assertTrue(r.flagged());
		assertEquals("Disguise", r.nick());
		assertEquals(1, Roster.filter(List.of(r), "disg").size());
		assertEquals(1, Roster.filter(List.of(r), "REAL").size());
		assertEquals(0, Roster.filter(List.of(r), "zzz").size());
		assertEquals(1, Roster.filter(List.of(r), "  ").size());
	}

	@Test
	void theArrowKeysMoveWithinTheList() {
		List<Roster.Row> rows = Roster.build(List.of(on("a", ""), on("b", ""), on("c", "")), StaffFlags.EMPTY, NOW);
		assertEquals("b", Roster.move(rows, "a", 1));
		assertEquals("a", Roster.move(rows, "a", -1));
		assertEquals("c", Roster.move(rows, "c", 1));
		assertEquals("a", Roster.move(rows, "gone", 1));
		assertEquals("", Roster.move(List.of(), "a", 1));
	}

	@Test
	void agesReadPlainly() {
		assertEquals("just now", Roster.ago(30_000));
		assertEquals("4 min ago", Roster.ago(4 * 60_000 + 5_000));
		assertEquals("2 h ago", Roster.ago(125 * 60_000L));
		assertEquals("3 d ago", Roster.ago(3 * 86_400_000L));
		assertEquals("just now", Roster.ago(-5000));
	}
	@Test
	void theAnticheatsScoreAndSetbacksAreReasonsWithTheirOwnChips() {
		StaffFlags.State s = new StaffFlags.State(List.of(), List.of(), List.of(), List.of(), 0, List.of(), 0,
				List.of(new StaffFlags.Priority("Avxyl", 67.5, NOW - 120_000)),
				List.of(new StaffFlags.Setback("Avxyl", 11, "end, 1, 2, 3", NOW - 600_000, NOW - 300_000)),
				List.of(new StaffFlags.Brand("Avxyl", "Lunarclient:v2", NOW - 60_000)));
		Roster.Row r = Roster.build(List.of(on("Avxyl", ""), on("Clean", "")), s, NOW).getFirst();
		assertEquals("Avxyl", r.name());
		assertEquals(List.of(new Roster.Chip("SCORE 67.5", Roster.Source.PRIORITY, false), new Roster.Chip("SETBACK ×11", Roster.Source.SETBACK, false)), r.chips());
		assertEquals(67.5, r.score());
		assertEquals("Lunarclient:v2", r.brand());
		assertEquals("Priority score 67.5, 2 min ago", r.reasons().getFirst().text());
	}

	@Test
	void eachCheckTheAnticheatCaughtAPlayerOnIsAReasonWithItsOwnChip() {
		StaffFlags.State s = new StaffFlags.State(List.of(), List.of(), List.of(), List.of(), 0, List.of(), 0, List.of(), List.of(), List.of(), List.of(
				new StaffFlags.Detection("DeloxR", "Click rate", "CPS=15.0", NOW - 240_000),
				new StaffFlags.Detection("deloxr", "Click rate", "CPS=14.5", NOW - 600_000),
				new StaffFlags.Detection("DeloxR", "Reach", "distance=3.4", NOW - 120_000)));
		Roster.Row r = Roster.build(List.of(on("Clean", ""), on("DeloxR", "")), s, NOW).getFirst();
		assertEquals("DeloxR", r.name(), "a detection flags the player");
		assertEquals(List.of(new Roster.Chip("REACH", Roster.Source.DETECTION, false), new Roster.Chip("CLICK RATE ×2", Roster.Source.DETECTION, false)), r.chips());
		assertEquals("Caught on click rate ×2, CPS up to 15, last 4 min ago", r.reasons().get(1).text());
	}

	@Test
	void theFlagScoreIsTheNewerOfTheDashboardAndThePriorityScore() {
		List<StaffFlags.Flag> board = List.of(new StaffFlags.Flag("Sus", "MACRO", "GuiRateLimiter", 9, NOW - 1000));
		long read = NOW - 60_000;
		StaffFlags.State older = new StaffFlags.State(List.of(), List.of(), List.of(), board, read, List.of(), 0,
				List.of(new StaffFlags.Priority("Sus", 40, read - 1)), List.of(), List.of());
		StaffFlags.State newer = new StaffFlags.State(List.of(), List.of(), List.of(), board, read, List.of(), 0,
				List.of(new StaffFlags.Priority("Sus", 40, read + 1)), List.of(), List.of());
		assertEquals(9, Roster.score(older, "Sus"));
		assertEquals(40, Roster.score(newer, "sus"));
		assertEquals(0, Roster.score(StaffFlags.EMPTY, "Sus"));
	}

	@Test
	void aLookedUpPlayerGetsTheirRowEvenWhenNotOnline() {
		StaffFlags.State s = new StaffFlags.State(List.of(new StaffFlags.Watch("Gone", "alt of X", "Lauchy", NOW)), List.of(), List.of(), List.of(), 0, List.of(), 0);
		Roster.Row r = Roster.row(new Roster.Online("Gone", "", ""), s, NOW);
		assertTrue(r.flagged());
		assertEquals("WATCH", r.chips().getFirst().label());
	}

	@Test
	void scoresAreWrittenWithOneDecimalAtMost() {
		assertEquals("67.5", Roster.scoreText(67.5));
		assertEquals("12", Roster.scoreText(12));
		assertEquals("3.1", Roster.scoreText(3.14));
	}
}
