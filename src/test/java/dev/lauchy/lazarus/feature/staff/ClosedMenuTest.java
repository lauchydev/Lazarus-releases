package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosedMenuTest {
	@Test
	void theServersCloseOfTheMenuTheReaderJustClosedIsAnEcho() {
		// As Dragsim does it: the reader closes menu 38 and puts the roster back, and the server's close of 38 follows.
		assertTrue(new ClosedMenu(38, 1000).echoedBy(38, 1050));
	}

	@Test
	void anyOtherCloseStillCloses() {
		ClosedMenu closed = new ClosedMenu(38, 1000);
		assertFalse(closed.echoedBy(39, 1050), "another menu");
		assertFalse(closed.echoedBy(38, 1000 + ClosedMenu.ECHO_MS + 1), "long after");
		assertFalse(closed.echoedBy(38, 999), "before it was closed");
	}
}
