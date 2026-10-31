package dev.lauchy.lazarus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TallyTest {
	@Test
	void aRareDropEndsItsDryStreakAndStartsAgain() {
		Tally t = new Tally();
		t.miss("Judgement Core");
		t.miss("Judgement Core");
		assertEquals(2, t.hit("Judgement Core"));
		assertEquals(0, t.since().get("Judgement Core"));
	}

	@Test
	void itRoundTripsThroughJson() {
		Tally t = new Tally();
		t.add("kills", 3);
		t.add("damageSum", 1.5);
		t.drop("Rift Goo", 7);
		t.miss("Golem");
		Tally back = Tally.load(t.save());
		assertEquals(3, back.count("kills"));
		assertEquals(1.5, back.sum("damageSum"));
		assertEquals(Map.of("Rift Goo", 7), back.drops());
		assertEquals(Map.of("Golem", 1), back.since());
	}

	@Test
	void aFileFromBeforeTalliesWereSharedStillReads() {
		Tally t = Tally.load(JsonParser.parseString("""
				{"kills": 12, "fails": 1, "damageSum": 2400000.0, "tiers": {"V": 10, "IV": 2}, "drops": {"Null Ovoid": 9}, "since": {"Judgement Core": 4}}"""));
		assertEquals(12, t.count("kills"));
		assertEquals(1, t.count("fails"));
		assertEquals(2_400_000.0, t.sum("damageSum"));
		assertEquals(Map.of("V", 10, "IV", 2), t.counts("tiers."));
		assertEquals(9, t.drops().get("Null Ovoid"));
		assertEquals(4, t.since().get("Judgement Core"));
	}

	@Test
	void aDamagedNumberIsSkippedNotFatal() {
		Tally t = Tally.load(JsonParser.parseString("""
				{"counts": {"kills": "many", "fails": 2}, "drops": "nope"}"""));
		assertEquals(2, t.count("fails"));
		assertTrue(t.drops().isEmpty());
	}
}
