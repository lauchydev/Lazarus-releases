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
	void playerChatAndOtherLinesAreNotTheAnticheats() {
		assertNull(AcLine.parse("[DRAGON] YetiOwO: [FLAG REVIEW] Steve has priority score 99. Use /staff to review the evidence."));
		assertNull(AcLine.parse("Steve: DragSim AC » Bob joined using cheats!"));
		assertNull(AcLine.parse("[Captcha] Steve passed the check Lauchy sent, in 8s."));
		assertTrue(AcLine.isAnticheat("DragSim AC » something new it says"));
		assertTrue(AcLine.isAnticheat("[FLAG REVIEW] x"));
		assertFalse(AcLine.isAnticheat("[DRAGON] YetiOwO: DragSim AC » hi"));
	}
}
