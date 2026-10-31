package dev.lauchy.lazarus.core;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The worker's time, as its answers' {@code Date} header gives it. The worker turns away a signed
 * request stamped more than two minutes from its own clock ("stale request"), so a PC whose clock
 * is off could never sign in; requests are stamped by the worker's clock instead. Pure, so it is
 * tested without the game.
 */
public final class ServerClock {
	/** Closer than this, the clocks are taken to agree: the header only counts whole seconds. */
	static final long CLOSE_MS = 30_000;

	private static volatile long skew;

	private ServerClock() {
	}

	/** The time now by the worker's clock, as far as its last answer said. */
	public static long now() {
		return System.currentTimeMillis() + skew;
	}

	/** Learns the worker's clock from an answer's {@code Date} header, sent at {@code local} by this PC's clock. */
	public static void heard(String date, long local) {
		skew = skew(date, local, skew);
	}

	/** How far the worker's clock is ahead of this PC's, by a {@code Date} header; {@code was} when the header says nothing. */
	static long skew(String date, long local, long was) {
		if (date == null || date.isBlank()) return was;
		try {
			long by = ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - local;
			return Math.abs(by) < CLOSE_MS ? 0 : by;
		} catch (DateTimeParseException e) {
			return was;
		}
	}
}
