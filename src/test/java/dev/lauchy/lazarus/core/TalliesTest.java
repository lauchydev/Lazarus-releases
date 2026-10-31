package dev.lauchy.lazarus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TalliesTest {
	private final Tallies tallies = new Tallies(Path.of("unused.json"));

	@Test
	void aChangeCountsInBothPeriodsAndAResetKeepsTheTotal() {
		tallies.both(t -> t.add("kills", 1));
		assertEquals(1, tallies.shown(Period.SESSION).count("kills"));
		assertEquals(1, tallies.shown(Period.ALL_TIME).count("kills"));
		tallies.resetSession();
		assertEquals(0, tallies.shown(Period.SESSION).count("kills"));
		assertEquals(1, tallies.shown(Period.ALL_TIME).count("kills"));
	}

	@Test
	void keysListOnlyWhatHasCountsInThatPeriod() {
		tallies.read(JsonParser.parseString("""
				{"Golden Dragon": {"kills": 6, "drops": {}}, "Warden": {"kills": 2, "drops": {}}}"""));
		tallies.both("Warden", t -> t.add("kills", 1));
		assertEquals(List.of("Golden Dragon", "Warden"), tallies.keys(Period.ALL_TIME));
		assertEquals(List.of("Warden"), tallies.keys(Period.SESSION));
		assertEquals(3, tallies.total("Warden").count("kills"));
	}

	@Test
	void aSingleTrackersOldFileReadsAsTheOneKey() {
		tallies.read(JsonParser.parseString("""
				{"opened": 12, "drops": {"Summoning Eye": 3}}"""));
		assertEquals(12, tallies.shown(Period.ALL_TIME).count("opened"));
		assertEquals(3, tallies.shown(Period.ALL_TIME).drops().get("Summoning Eye"));
	}

	@Test
	void writingThenReadingGivesTheSameTotals() {
		tallies.both("voidgloom", t -> t.add("kills", 2));
		Tallies again = new Tallies(Path.of("unused.json"));
		again.read(tallies.write());
		assertEquals(2, again.total("voidgloom").count("kills"));
		assertTrue(again.keys(Period.SESSION).isEmpty());
	}
}
