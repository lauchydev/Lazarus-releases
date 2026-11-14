package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivePolicyTest {
	/** What the server says first on each connection. */
	private static final String SNAPSHOT = "{\"type\":\"snapshot\",\"online\":[]}";
	/** A minute, in ms. */
	private static final long MINUTE = 60_000;

	/** The fake clock, in ms; the policy starts at 0. */
	private long now;
	/** Every connection the policy started, in order. */
	private final List<FakeSocket> sockets = new ArrayList<>();
	/** When each check-in was made. */
	private final List<Long> checkIns = new ArrayList<>();
	/** The messages handed on to Live. */
	private final List<String> applied = new ArrayList<>();
	/** How often the server counted as reached with nothing to apply. */
	private int reached;

	/** The policy under test, on the fake clock with no jitter, recording what it does. */
	private final LivePolicy policy = new LivePolicy(new LivePolicy.Actions() {
		@Override
		public LivePolicy.Socket connect() {
			FakeSocket s = new FakeSocket();
			sockets.add(s);
			return s;
		}

		@Override
		public void checkIn() {
			checkIns.add(now);
		}

		@Override
		public void apply(String message) {
			applied.add(message);
		}

		@Override
		public void reached() {
			reached++;
		}
	}, () -> now, 0);

	/** A connection whose handshake finishes only when a test says so (policy.opened); the server behind it answers each "ping" until it goes quiet. */
	private static final class FakeSocket implements LivePolicy.Socket {
		/** What the policy sent on it. */
		final List<String> sent = new ArrayList<>();
		/** Whether the policy closed it. */
		boolean closed;
		/** Whether the server behind it still answers. */
		boolean answers = true;
		/** How many of {@link #sent} the server has seen. */
		int seen;

		@Override
		public void send(String text) {
			sent.add(text);
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	/** Moves the clock on to {@code until} a second at a time, ticking the policy each second as the game does; each "ping" is answered at once. */
	private void runUntil(long until) {
		while (now < until) {
			now += 1_000;
			policy.tick();
			for (FakeSocket s : List.copyOf(sockets)) {
				for (; s.seen < s.sent.size(); s.seen++) {
					if (s.answers && !s.closed && s.sent.get(s.seen).equals("ping")) policy.heard(s, "pong");
				}
			}
		}
	}

	@Test
	void checksInOnTimeWhileAConnectIsStillUnderWay() {
		// No handshake ever finishes, so a connect is always under way or about to start.
		runUntil(LivePolicy.CHECKIN_EVERY + 10_000);

		assertEquals(List.of(LivePolicy.CHECKIN_EVERY), checkIns);
	}

	@Test
	void closesAConnectionThatOpensTooLateAndOnlyTheCurrentOnesCloseConnectsAgain() {
		runUntil(1_000);
		FakeSocket late = sockets.get(0);
		runUntil(1_000 + LivePolicy.CONNECT_FOR + LivePolicy.FIRST_WAIT);
		FakeSocket current = sockets.get(1);
		policy.opened(current);

		policy.opened(late);
		boolean lateClosed = late.closed;
		policy.closed(late);
		runUntil(now + 2 * LivePolicy.MAX_WAIT);

		assertAll(
				() -> assertTrue(lateClosed, "the connection that opened after the policy gave up on it is closed"),
				() -> assertEquals(2, sockets.size(), "connections made"),
				() -> assertFalse(current.closed, "the current connection stays open"));
	}

	@Test
	void closesAConnectionThatStopsAnsweringItsPingsAndChecksInInstead() {
		runUntil(1_000);
		FakeSocket s = sockets.get(0);
		policy.opened(s);
		policy.heard(s, SNAPSHOT);
		runUntil(3 * LivePolicy.PING_EVERY);
		boolean openWhileAnswering = !s.closed;

		// Half-open: the server behind it is gone, and nothing says so.
		s.answers = false;
		runUntil(now + 2 * LivePolicy.PING_EVERY + 10_000);
		boolean closedOnceSilent = s.closed;
		runUntil(now + LivePolicy.CHECKIN_EVERY);

		assertAll(
				() -> assertTrue(openWhileAnswering, "a connection that answers its pings stays open"),
				() -> assertTrue(closedOnceSilent, "a connection silent for about two pings is closed"),
				() -> assertFalse(checkIns.isEmpty(), "check-ins start once it's closed"));
	}

	@Test
	void theServerCountsAsReachedOnceItAnswersNotWhenTheConnectionOpens() {
		runUntil(1_000);
		FakeSocket s = sockets.get(0);

		policy.opened(s);
		int onOpening = reached;
		policy.heard(s, "pong");
		policy.heard(s, SNAPSHOT);

		assertAll(
				() -> assertEquals(0, onOpening, "contact when the connection opened, before the snapshot with the receipt"),
				() -> assertEquals(1, reached, "contact from the pong"),
				() -> assertEquals(List.of(SNAPSHOT), applied, "messages handed to Live, which counts each as contact"));
	}

	@Test
	void onlyTheCurrentConnectionIsHandedToLive() {
		runUntil(1_000);
		FakeSocket late = sockets.get(0);
		runUntil(1_000 + LivePolicy.CONNECT_FOR + LivePolicy.FIRST_WAIT);
		FakeSocket current = sockets.get(1);
		policy.opened(current);
		policy.heard(current, SNAPSHOT);
		String leave = "{\"type\":\"leave\",\"id\":\"a\"}";
		policy.heard(current, leave);

		// The connect it gave up on opens after all, and the server's older snapshot arrives on it.
		policy.opened(late);
		policy.heard(late, "{\"type\":\"snapshot\",\"online\":[{\"id\":\"a\"}]}");

		assertEquals(List.of(SNAPSHOT, leave), applied, "messages handed to Live");
	}

	@Test
	void aConnectedModAsksForTheSnapshotAgainEveryTenMinutes() {
		runUntil(1_000);
		FakeSocket s = sockets.get(0);
		policy.opened(s);
		policy.heard(s, SNAPSHOT);

		runUntil(1_000 + 10 * MINUTE);
		long inTen = s.sent.stream().filter("resync"::equals).count();
		runUntil(1_000 + 20 * MINUTE);
		long inTwenty = s.sent.stream().filter("resync"::equals).count();

		assertAll(
				() -> assertEquals(1, inTen, "resyncs in the first ten minutes"),
				() -> assertEquals(2, inTwenty, "resyncs in twenty"));
	}
}
