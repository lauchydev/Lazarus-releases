package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSprintTest {
	@Test
	void sprintsOnlyWhileWalkingForwardInTheWorld() {
		assertTrue(AutoSprint.wants(true, true, false));
		assertFalse(AutoSprint.wants(true, false, false));
		assertFalse(AutoSprint.wants(true, true, true));
		assertFalse(AutoSprint.wants(false, true, false));
	}
}
