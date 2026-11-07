package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StaffFlagsTest {
	@Test
	void theServersAnswerReadsEveryList() {
		StaffFlags.State s = StaffFlags.parse("""
				{"watch":[{"name":"Steve","note":"macro","by":"Lauchy","at":5},{"name":"Alex"}],
				 "captchas":[{"player":"Cat","passed":false,"by":"Lauchy","seconds":8,"at":7}],
				 "reports":[{"id":3,"target":"Dan","reporter":"Ann","reason":"flying","at":9}],
				 "dashboard":{"at":11,"by":"Lauchy","flags":[{"name":"Eve","type":"MACRO","detector":"GuiRateLimiter","score":11.8,"since":2}]},
				 "alts":{"at":12,"by":"Lauchy","links":[{"name":"Fay","why":"On a banned IP","others":"Gus","since":4}]}}""");

		assertEquals(List.of(new StaffFlags.Watch("Steve", "macro", "Lauchy", 5), new StaffFlags.Watch("Alex", "", "", 0)), s.watch());
		assertEquals(List.of(new StaffFlags.Captcha("Cat", false, "Lauchy", 8, 7)), s.captchas());
		assertEquals(List.of(new StaffFlags.Report(3, "Dan", "Ann", "flying", 9)), s.reports());
		assertEquals(List.of(new StaffFlags.Flag("Eve", "MACRO", "GuiRateLimiter", 11.8, 2)), s.flags());
		assertEquals(11, s.flagsAt());
		assertEquals(List.of(new StaffFlags.Alt("Fay", "On a banned IP", "Gus", 4)), s.alts());
		assertEquals(12, s.altsAt());
		assertEquals(List.of(), s.priorities(), "an answer without the anticheat's lists reads them as empty");
	}

	@Test
	void theAnticheatsListsAreRead() {
		StaffFlags.State s = StaffFlags.parse("""
				{"watch":[],"dashboard":{"at":0,"flags":[]},"alts":{"at":0,"links":[]},
				 "priorities":[{"player":"JLLM","score":67.5,"at":20}],
				 "setbacks":[{"player":"Avxyl","count":11,"place":"end, 1, 2, 3","since":15,"at":21}],
				 "brands":[{"player":"_nephis","client":"Lunarclient","at":22}],
				 "detections":[{"player":"DeloxR","check":"Click rate","detail":"CPS=14.5","at":23}]}""");

		assertEquals(List.of(new StaffFlags.Priority("JLLM", 67.5, 20)), s.priorities());
		assertEquals(List.of(new StaffFlags.Setback("Avxyl", 11, "end, 1, 2, 3", 15, 21)), s.setbacks());
		assertEquals(List.of(new StaffFlags.Brand("_nephis", "Lunarclient", 22)), s.brands());
		assertEquals(List.of(new StaffFlags.Detection("DeloxR", "Click rate", "CPS=14.5", 23)), s.detections());
	}

	@Test
	void aLineHeardGoesInBeforeTheServerAnswers() {
		StaffFlags.State s = StaffFlags.EMPTY.with(new StaffFlags.Priority("JLLM", 40, 1), null, null)
				.with(new StaffFlags.Priority("jllm", 67.5, 2), null, null);
		assertEquals(List.of(new StaffFlags.Priority("jllm", 67.5, 2, List.of(new StaffFlags.Reading(67.5, 2), new StaffFlags.Reading(40, 1)))), s.priorities(),
				"the newer score replaces the older, whatever the case, and keeps it as an earlier reading");
	}

	@Test
	void eachDetectionHeardIsKeptButTheSameLineAgainAtOnceIsNot() {
		StaffFlags.Detection first = new StaffFlags.Detection("DeloxR", "Click rate", "CPS=15.0", 1_000);
		StaffFlags.State s = StaffFlags.EMPTY.with(first)
				.with(new StaffFlags.Detection("deloxr", "Click rate", "CPS=15.0", 1_500)) // a second message of the same line
				.with(new StaffFlags.Detection("DeloxR", "Click rate", "CPS=14.5", 2_000))
				.with(new StaffFlags.Detection("DeloxR", "Click rate", "CPS=15.0", 150_000));
		assertEquals(List.of(150_000L, 2_000L, 1_000L), s.detections().stream().map(StaffFlags.Detection::at).toList());
	}

	@Test
	void anythingElseIsNoAnswer() {
		assertNull(StaffFlags.parse("not json"));
		assertNull(StaffFlags.parse("{\"error\":\"no\"}"));
		assertNull(StaffFlags.parse("[]"));
		// The old watch list's answer isn't this one.
		assertNull(StaffFlags.parse("{\"entries\":[]}"));
	}
}
