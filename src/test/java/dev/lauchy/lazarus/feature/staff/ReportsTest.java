package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportsTest {
	private static final String DEFAULT = Reports.DEFAULT_PATTERN;

	@Test
	void theDefaultPatternReadsTheUsualWordings() {
		Reports.Report r = Reports.parse("[Report] Steve reported Alex for flying", DEFAULT, 1, 9);
		assertNotNull(r);
		assertEquals(1, r.id());
		assertEquals("Steve", r.reporter());
		assertEquals("Alex", r.target());
		assertEquals("flying", r.reason());
		assertEquals(9, r.at());
		r = Reports.parse("Steve has reported Alex: macro", DEFAULT, 2, 0);
		assertNotNull(r);
		assertEquals("macro", r.reason());
		r = Reports.parse("Steve reported Alex.", DEFAULT, 3, 0);
		assertNotNull(r);
		assertEquals("", r.reason());
	}

	@Test
	void otherLinesAndBrokenPatternsGiveNothing() {
		assertNull(Reports.parse("[MVP+] Steve: I reported Alex yesterday", DEFAULT, 1, 0));
		assertNull(Reports.parse("Steve reported Alex", "(?<target>[", 1, 0));
		assertNull(Reports.parse("Steve reported Alex", "^(?<reporter>\\w+) reported (?<nobody>\\w+)$", 1, 0), "no target group, no report");
	}

	@Test
	void theCardNamesTheTargetAndHasItsButtons() {
		String card = Reports.card(new Reports.Report(4, "Steve", "Alex", "flying", 0)).build().getString();
		assertTrue(card.contains("Report #4"), card);
		assertTrue(card.contains("Alex by Steve"), card);
		assertTrue(card.contains("flying"), card);
		for (String b : new String[]{"Alex", "Staff", "Teleport", "Done"}) assertTrue(card.contains(b), b);
	}
}
