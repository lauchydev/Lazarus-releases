package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagAlertsTest {
	private static final long NOW = Instant.parse("2026-11-07T11:40:00Z").toEpochMilli();

	/** An alert's lore as the page gives it, at {@code time} ("11:25:22"), with {@code evidence} after the blank line. */
	private static List<String> lore(String time, double score, String server, String... evidence) {
		List<String> out = new java.util.ArrayList<>(List.of("Awaiting manual review", "Score contribution: " + score, "Source: DragSim AC", "Server: " + server,
				"Time: 11-07 " + time + " UTC", ""));
		out.addAll(List.of(evidence));
		return out;
	}

	private static long at(String time) {
		return Instant.parse("2026-11-07T" + time + "Z").toEpochMilli();
	}

	@Test
	void anAlertReadsItsFieldsAndItsWrappedEvidence() {
		FlagAlerts.Alert a = FlagAlerts.read("ANTICHEAT", lore("11:25:22", 1.99, "hub-1",
				"Click rate; coalesced=1; ping=212ms; TPS=20.00; CPS=20.0; window=2.0s;", " main-hand swings=40; attacks=0; independent streams, not summed"), NOW);
		assertEquals(new FlagAlerts.Alert("ANTICHEAT", "Awaiting manual review", 1.99, "DragSim AC", "hub-1", at("11:25:22"),
				"Click rate; coalesced=1; ping=212ms; TPS=20.00; CPS=20.0; window=2.0s; main-hand swings=40; attacks=0; independent streams, not summed"), a);
		assertEquals("Click rate", a.check());
	}

	@Test
	void aButtonOrFillerIsNoAlert() {
		assertNull(FlagAlerts.read("Next Page", List.of("Click to go to page 2"), NOW));
		assertNull(FlagAlerts.read(" ", List.of(), NOW));
	}

	@Test
	void anAlertWithoutANamedCheckIsKnownByItsType() {
		FlagAlerts.Alert a = FlagAlerts.read("MACRO", lore("11:00:00", 5, "hub-2", "interval=50ms; samples=30", "", "Click to mark reviewed", "Right-click: dismiss"), NOW);
		assertEquals("interval=50ms; samples=30", a.detail(), "the hint after the evidence isn't evidence");
		assertEquals("Macro", a.check());
	}

	@Test
	void thePageTitleNamesThePlayer() {
		assertEquals("ChillGuy87964", FlagAlerts.player("Flags: ChillGuy87964"));
		assertNull(FlagAlerts.player("Flag Dashboard (1/12)"));
	}

	@Test
	void aTimeWithoutAYearIsTheLatestThatIsntInTheFuture() {
		assertEquals(at("11:25:22"), FlagAlerts.time("11-07 11:25:22 UTC", NOW));
		long newYear = Instant.parse("2027-01-01T00:10:00Z").toEpochMilli();
		assertEquals(Instant.parse("2026-12-31T23:55:00Z").toEpochMilli(), FlagAlerts.time("12-31 23:55:00 UTC", newYear));
		assertEquals(0, FlagAlerts.time("yesterday", NOW));
	}

	@Test
	void measuresKeepTheirUnits() {
		assertEquals(List.of(new FlagAlerts.Metric("ping", 212, "ms"), new FlagAlerts.Metric("CPS", 20, ""), new FlagAlerts.Metric("main-hand swings", 40, "")),
				FlagAlerts.metrics("Click rate; ping=212ms; CPS=20.0; main-hand swings=40; independent streams, not summed"));
	}

	@Test
	void theLastHourIsSummedUpByCheckTheMostCaughtFirst() {
		List<FlagAlerts.Alert> page = List.of(
				FlagAlerts.read("ANTICHEAT", lore("11:25:22", 1.99, "hub-1", "Click rate; ping=212ms; TPS=20.00; CPS=20.0; window=2.0s"), NOW),
				FlagAlerts.read("ANTICHEAT", lore("11:20:00", 1.5, "hub-2", "Click rate; ping=180ms; TPS=19.90; CPS=12.5; window=2.0s"), NOW),
				FlagAlerts.read("ANTICHEAT", lore("11:30:00", 4, "hub-1", "Reach; distance=3.4; ping=90ms"), NOW),
				FlagAlerts.read("ANTICHEAT", lore("10:20:00", 9, "hub-1", "Click rate; CPS=40.0"), NOW)); // over an hour ago
		List<FlagAlerts.Check> checks = FlagAlerts.summarise(page, NOW - 60_000, List.of(), "ChillGuy87964", "", NOW);

		assertEquals(2, checks.size());
		FlagAlerts.Check clicks = checks.getFirst();
		assertEquals("Click rate ×2", clicks.counted());
		assertEquals(3.49, clicks.score());
		assertEquals("CPS up to 20", clicks.peak(), "ping, TPS and the window describe the sample, not the player");
		assertEquals(List.of("CPS 12.5–20", "ping 180–212 ms", "TPS 19.9–20", "window 2 s"), clicks.ranges().stream().map(FlagAlerts.Range::text).toList(),
				"the player's own measures before the sample's");
		assertEquals(List.of("hub-1", "hub-2"), clicks.servers());
		assertEquals(at("11:25:22"), clicks.newest());
		assertEquals("Reach", checks.get(1).counted());
		assertEquals("distance up to 3.4", checks.get(1).peak());
		assertEquals("Click rate ×2, Reach", FlagAlerts.line(checks));
	}

	@Test
	void whatChatHeardSinceThePageWasReadJoinsIt() {
		List<FlagAlerts.Alert> page = List.of(FlagAlerts.read("ANTICHEAT", lore("11:25:22", 1.99, "hub-1", "Click rate; CPS=20.0"), NOW));
		long readAt = at("11:30:00");
		List<StaffFlags.Detection> heard = List.of(
				new StaffFlags.Detection("chillguy87964", "Click rate", "CPS=36.0", at("11:35:00")),
				new StaffFlags.Detection("ChillGuy87964", "Click rate", "CPS=20.0", at("11:25:22")), // on the page already
				new StaffFlags.Detection("Someone", "Click rate", "CPS=10.5", at("11:36:00")));
		FlagAlerts.Check clicks = FlagAlerts.summarise(page, readAt, heard, "ChillGuy87964", "", NOW).getFirst();
		assertEquals(2, clicks.count());
		assertEquals("CPS up to 36", clicks.peak());
	}

	@Test
	void aPlayerNotYetOpenedIsSummedUpFromChatAloneUnderTheirNameOrNick() {
		List<StaffFlags.Detection> heard = List.of(
				new StaffFlags.Detection("DeloxR", "Click rate", "CPS=15.0", at("11:24:11")),
				new StaffFlags.Detection("Delo", "Click rate", "CPS=14.5", at("11:30:49")),
				new StaffFlags.Detection("DeloxR", "Click rate", "CPS=11.0", at("10:30:00"))); // over an hour ago
		List<FlagAlerts.Check> checks = FlagAlerts.summarise(List.of(), 0, heard, "DeloxR", "Delo", NOW);
		assertEquals("Click rate ×2", checks.getFirst().counted());
		assertEquals("CPS 14.5–15", checks.getFirst().ranges().getFirst().text());
		assertEquals(0, checks.getFirst().score(), "chat doesn't say what a detection adds to the score");
		assertTrue(FlagAlerts.summarise(List.of(), 0, heard, "Nobody", "", NOW).isEmpty());
	}
}
