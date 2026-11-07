package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLineTest {
	@Test
	void aFlagReviewLineGivesThePriorityScore() {
		assertEquals(new AcLine.Priority("icanseeparis", 47.3), AcLine.parse("[FLAG REVIEW] icanseeparis has priority score 47.3. Use /staff to review the evidence."));
		assertEquals(new AcLine.Priority("JLLM", 67.5), AcLine.parse("  [FLAG REVIEW] JLLM has priority score 67.5. Use /staff to review the evidence.  "));
		assertEquals(new AcLine.Priority("Steve", 12), AcLine.parse("[FLAG REVIEW] Steve has priority score 12."));
	}

	@Test
	void aSetbackLineGivesWhyWhereAndTheRunningCount() {
		assertEquals(new AcLine.Setback("Avxyl", "being on a ghost block", "minestom-3ae7ecbe-508d-4f00-8c66-a984827ae9c0, -669, 10, -274", 11),
				AcLine.parse("DragSim AC » Avxyl's position was updated for being on a ghost block! (minestom-3ae7ecbe-508d-4f00-8c66-a984827ae9c0, -669, 10, -274) [11]"));
		assertEquals(new AcLine.Setback("Avxyl", "", "end, 1, 2, 3", 1), AcLine.parse("DragSim AC » Avxyl's position was updated! (end, 1, 2, 3)"));
	}

	@Test
	void aJoinLineGivesTheClient() {
		assertEquals(new AcLine.Brand("_nephis", "Lunarclient:v2.23.14-2645,fabric"), AcLine.parse("DragSim AC » _nephis joined using Lunarclient:v2.23.14-2645,fabric!"));
	}

	@Test
	void aReviewOnlyLineGivesTheCheckAndWhatItMeasured() {
		assertEquals(new AcLine.Detection("DeloxR", "Click rate", "CPS=14.5"), AcLine.parse("[DragSim AC] DeloxR Click rate: CPS=14.5 (review only)"));
		assertEquals(new AcLine.Detection("Steve", "Reach", "distance=3.4; ping=80ms"), AcLine.parse("[DragSim AC] Steve Reach: distance=3.4; ping=80ms (review only)"));
		assertTrue(AcLine.isAnticheat("[DragSim AC] DeloxR Click rate: CPS=14.5 (review only)"));
	}

	@Test
	void theAnticheatsAnswersToStaffCommandsAreNotDetectionsAndStayInChat() {
		String info = "[DragSim AC] Avxyl checks=176; vl=0; transaction=128ms; received=392282; sent=236118; alerts=false";
		String status = "[DragSim AC] 176 checks; monitor; sessions=68; flags=2443; exempt=1153; failures=0; adapterErrors=0";
		assertNull(AcLine.parse(info));
		assertNull(AcLine.parse(status));
		assertFalse(AcLine.isAnticheat(info));
		assertFalse(AcLine.isDetection(info));
		assertFalse(AcLine.isAnticheat(status));
		assertNull(AcLine.parse("[DRAGON] YetiOwO: [DragSim AC] Steve Click rate: CPS=99 (review only)"));
	}

	@Test
	void playerChatAndOtherLinesAreNotTheAnticheats() {
		assertNull(AcLine.parse("[DRAGON] YetiOwO: [FLAG REVIEW] Steve has priority score 99. Use /staff to review the evidence."));
		assertNull(AcLine.parse("Steve: DragSim AC » Bob joined using cheats!"));
		assertNull(AcLine.parse("[Captcha] Steve passed the check Lauchy sent, in 8s."));
		assertTrue(AcLine.isAnticheat("DragSim AC » something new it says"));
		assertTrue(AcLine.isAnticheat("[FLAG REVIEW] x"));
		assertFalse(AcLine.isAnticheat("[DRAGON] YetiOwO: DragSim AC » hi"));
	}
}
