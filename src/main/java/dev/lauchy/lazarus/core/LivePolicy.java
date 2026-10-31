package dev.lauchy.lazarus.core;

import java.util.function.LongSupplier;

/**
 * What the mod's live connection does and when: when it connects, which connection is the current one,
 * when it pings, when silence means the connection is dead, when it checks in instead, when it asks for the
 * snapshot again, and when the server counts as heard from. Pure: nothing here
 * touches the game or the network. {@link LiveConnection} does what it asks ({@link Actions}), tells it what
 * happened to each connection ({@link #opened}, {@link #heard}, {@link #closed}), and ticks it every second.
 * Safe to call from any thread.
 */
public final class LivePolicy {
	/** How long to wait before connecting again after the first failure; it doubles up to {@link #MAX_WAIT}. */
	public static final long FIRST_WAIT = 2_000;
	/** The longest wait between tries to connect. */
	public static final long MAX_WAIT = 60_000;
	/** How long a connect may take before it's given up on. */
	public static final long CONNECT_FOR = 20_000;
	/** How often to say "ping", which keeps the connection from being closed as idle; the server answers without waking. */
	public static final long PING_EVERY = 30_000;
	/** How long an open connection may go unheard (two pings unanswered, and a little more) before it's taken for dead. */
	public static final long SILENT_FOR = 2 * PING_EVERY + 5_000;
	/** How often to check in while the connection is down; the server keeps a player online for about two of these (its CHECKIN_FOR). */
	public static final long CHECKIN_EVERY = 60_000;
	/** How often an open connection asks for the snapshot again, so a change it missed arrives; one message, so it's cheap for the server. */
	public static final long RESYNC_EVERY = 10 * 60_000;
	/** The time of something that isn't due at all. */
	private static final long NEVER = Long.MAX_VALUE;

	/** One connection to the server: being made, open, or given up on. */
	public interface Socket {
		/** Sends a text message once the connection is open; does nothing otherwise. */
		void send(String text);

		/** Closes the connection, or closes it as soon as it opens when it's still being made. */
		void close();
	}

	/**
	 * What the policy has done: the network calls, and handing on what the server said. None of them may wait
	 * on the network, so a slow connect or check-in never holds up what else is due.
	 */
	public interface Actions {
		/** Starts connecting and returns the connection being made; how it goes comes back through {@link #opened} and {@link #closed}. */
		Socket connect();

		/** Starts a check-in with the server ({@code /live/checkin}). */
		void checkIn();

		/** Hands a message from the server to {@link Live}, which counts it as contact. */
		void apply(String message);

		/** The server was heard from with nothing to apply. */
		void reached();
	}

	private final Actions actions;
	private final LongSupplier clock;
	/** Added to every wait, so not everyone comes back at once. */
	private final long jitter;
	/** The connection being made; null when none is. */
	private Socket attempt;
	/** When {@link #attempt} started, in ms. */
	private long attemptAt;
	/** The open connection; null when there's none. */
	private Socket socket;
	/** When {@link #socket} was last heard from, in ms. */
	private long heardAt;
	/** When {@link #socket} next asks for the snapshot again, in ms. */
	private long resyncAt;
	/** How long to wait before the next try to connect, if this one fails. */
	private long wait = FIRST_WAIT;
	/** When to connect next, in ms; {@link #NEVER} while connecting or connected. */
	private long connectAt;
	/** When to ping next, in ms. */
	private long pingAt;
	/** When to check in next, in ms, should the connection be down then. */
	private long checkInAt;
	/** Whether the game is closing, so nothing more happens. */
	private boolean stopping;

	/**
	 * A policy that connects at once. {@code clock} is the time in ms (any start), and {@code jitter} (under a
	 * second, picked at random by each game) is added to every wait so not everyone comes back at once.
	 */
	public LivePolicy(Actions actions, LongSupplier clock, long jitter) {
		this.actions = actions;
		this.clock = clock;
		this.jitter = jitter;
		long now = clock.getAsLong();
		connectAt = now;
		pingAt = now + PING_EVERY;
		checkInAt = now + CHECKIN_EVERY;
	}

	/** Runs every second: connects, drops a dead connection, pings, resyncs or checks in, whichever is due. */
	public synchronized void tick() {
		if (stopping) return;
		long now = clock.getAsLong();
		if (attempt != null && now - attemptAt >= CONNECT_FOR) {
			// Given up on: closed so that, should it open after all, it doesn't stay open beside the next one.
			attempt.close();
			attempt = null;
			retry(now);
		}
		if (socket != null && now - heardAt >= SILENT_FOR) {
			// Half-open: the server is gone and nothing said so. Check in until a new connection opens.
			socket.close();
			retry(now);
		}
		if (now >= pingAt) {
			pingAt += PING_EVERY;
			if (socket != null) socket.send("ping");
		}
		if (socket != null && now >= resyncAt) {
			resyncAt += RESYNC_EVERY;
			socket.send("resync");
		}
		if (now >= checkInAt) {
			checkInAt += CHECKIN_EVERY;
			if (socket == null) actions.checkIn();
		}
		if (now >= connectAt) {
			connectAt = NEVER;
			attempt = actions.connect();
			attemptAt = now;
		}
	}

	/**
	 * A connection's handshake finished: the connection being made is the open one. The server counts as
	 * reached only once it says something, as the snapshot carrying this player's receipt comes first. One
	 * that opens after it was given up on is closed, so there's only ever one.
	 */
	public synchronized void opened(Socket s) {
		if (s != attempt) {
			s.close();
			return;
		}
		wait = FIRST_WAIT;
		attempt = null;
		socket = s;
		heardAt = clock.getAsLong();
		resyncAt = heardAt + RESYNC_EVERY;
	}

	/**
	 * A message arrived on a connection: a "pong", or one for {@link Live}. Either shows the connection is alive.
	 * Only the current connection counts: one given up on can still deliver a snapshot older than what's been heard since.
	 */
	public synchronized void heard(Socket s, String text) {
		if (s != socket) return;
		heardAt = clock.getAsLong();
		if (text.equals("pong")) actions.reached();
		else actions.apply(text);
	}

	/** A connection closed, failed, or couldn't be made: when it was the current one, connects again after a wait. */
	public synchronized void closed(Socket s) {
		if (s == attempt) attempt = null;
		else if (s != socket) return;
		retry(clock.getAsLong());
	}

	/** The game is closing: closes the connection, and nothing more happens. */
	public synchronized void stop() {
		stopping = true;
		if (socket != null) socket.close();
	}

	/** Whether a connection is open, so a check-in's answer, older than what it says, isn't wanted. */
	public synchronized boolean connected() {
		return socket != null;
	}

	/** Connects again after a wait that grows with each failure in a row. */
	private void retry(long now) {
		socket = null;
		if (stopping) return;
		connectAt = now + wait + jitter;
		wait = Math.min(wait * 2, MAX_WAIT);
	}
}
