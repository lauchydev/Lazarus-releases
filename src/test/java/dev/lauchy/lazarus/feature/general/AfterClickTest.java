package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static dev.lauchy.lazarus.feature.general.AfterClick.Act.CLOSE;
import static dev.lauchy.lazarus.feature.general.AfterClick.Act.DONE;
import static dev.lauchy.lazarus.feature.general.AfterClick.Act.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterClickTest {
	@Test
	void theMenuIsClosedOnceTheClickHasReachedTheServer() {
		assertEquals(WAIT, AfterClick.act(1, true, false, true), "the click goes first");
		assertEquals(CLOSE, AfterClick.act(AfterClick.FIRST_CLOSE_TICKS, true, false, true));
	}

	@Test
	void aMenuTheServerOpensAgainAMomentLaterIsClosedToo() {
		// The pet menu: closed at tick 4, then opened again, refreshed, a few ticks on.
		assertEquals(WAIT, AfterClick.act(6, false, false, true), "nothing on screen: keep watching");
		assertEquals(CLOSE, AfterClick.act(9, true, false, true));
		assertEquals(DONE, AfterClick.act(AfterClick.WINDOW_TICKS + 1, true, false, true), "but not for ever");
	}

	@Test
	void watchingStopsForAnotherBindsMenuOrOneOfYourOwn() {
		assertEquals(DONE, AfterClick.act(5, true, false, false), "another bind has opened its menu since");
		assertEquals(DONE, AfterClick.act(5, false, true, true), "you opened chat, your inventory or the pause menu");
	}
}
