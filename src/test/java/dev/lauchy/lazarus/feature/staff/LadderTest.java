package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderTest {
	@Test
	void readsEachRuleWithItsOffenceSteps() {
		List<Ladder.Rule> rules = Ladder.parse("1.0 Cheating Through Unfair Advantages: 7d/30d/90d/1y; 1.6 Duping: perm; 1.2 Mass Rule Breaking: set");
		assertEquals(3, rules.size());
		assertEquals("1.0", rules.get(0).code());
		assertEquals("Cheating Through Unfair Advantages", rules.get(0).name());
		assertEquals(List.of("7d", "30d", "90d", "1y"), rules.get(0).steps());
		assertEquals(List.of("perm"), rules.get(1).steps());
		assertTrue(rules.get(2).set());
	}

	@Test
	void theReasonIsWrittenTheWayStaffWriteIt() {
		Ladder.Rule rule = Ladder.parse("2.0 Chat Abuse: 30m/12h/7d").get(0);
		assertEquals("2.0 - Chat Abuse", rule.reason());
		assertEquals("2.0 Chat Abuse", rule.chip());
		assertEquals("1.0 Cheating", Ladder.parse("1.0 Cheating Through Unfair Advantages: 7d").get(0).chip());
		assertEquals("1.2 Mass Rule", Ladder.parse("1.2 Mass Rule Breaking: set").get(0).chip());
	}

	@Test
	void skipsAnEntryItCannotRead() {
		List<Ladder.Rule> rules = Ladder.parse("nonsense; 1.4 Alt Abuse: 30d/1y; : 7d; 9.9: 1d");
		assertEquals(1, rules.size());
		assertEquals("1.4", rules.get(0).code());
		assertTrue(Ladder.parse("").isEmpty());
		assertTrue(Ladder.parse(null).isEmpty());
	}

	@Test
	void findsARuleByCodeAndNamesEachOffence() {
		List<Ladder.Rule> rules = Ladder.parse("2.2 Provocation: 6h/24h/30d");
		assertEquals("Provocation", Ladder.find(rules, " 2.2 ").name());
		assertNull(Ladder.find(rules, "2.3"));
		assertEquals("1st", Ladder.ordinal(1));
		assertEquals("2nd", Ladder.ordinal(2));
		assertEquals("3rd", Ladder.ordinal(3));
		assertEquals("4th", Ladder.ordinal(4));
		assertEquals("11th", Ladder.ordinal(11));
		assertEquals("2.2 - Provocation · 6h / 24h / 30d", Ladder.describe(rules.get(0)));
	}
}
