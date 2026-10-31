package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerClockTest {
	private static final String DATE = "Sat, 31 Oct 2026 06:07:26 GMT";
	private static final long AT = ZonedDateTime.parse("2026-10-31T06:07:26Z").toInstant().toEpochMilli();

	@Test
	void learnsHowFarOffThisPcIs() {
		assertEquals(10 * 60_000, ServerClock.skew(DATE, AT - 10 * 60_000, 0)); // the PC is ten minutes behind
		assertEquals(-3_600_000, ServerClock.skew(DATE, AT + 3_600_000, 0)); // an hour ahead
	}

	@Test
	void takesCloseClocksToAgree() {
		assertEquals(0, ServerClock.skew(DATE, AT + 800, 5_000));
		assertEquals(0, ServerClock.skew(DATE, AT - 20_000, 0));
	}

	@Test
	void keepsWhatItKnewWhenTheHeaderSaysNothing() {
		assertEquals(42, ServerClock.skew(null, AT, 42));
		assertEquals(42, ServerClock.skew("yesterday", AT, 42));
	}
}
