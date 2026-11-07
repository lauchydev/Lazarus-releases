package dev.lauchy.lazarus.feature.general;

/**
 * One ability's cooldown as the mod believes it, in client ticks, with no game types so the
 * rules can be tested. A use while the timer still runs is not trusted straight away: the
 * server answers a refused press with "on cooldown for Ns" a round trip later, so the use
 * waits {@link #GRACE_TICKS} for that line. A refusal corrects the timer; silence means the
 * server took the press, so the timer restarts from the moment of the press.
 */
final class CooldownTimer {
	/** How long a use seen during a running cooldown waits for the server's refusal. */
	static final int GRACE_TICKS = 10;
	/** The action bar and chat both report one use, a tick or two apart. */
	private static final int REPEAT_TICKS = 10;

	/** Ticks before the first use: negative, which no running tick count reaches. */
	private static final long NEVER = -1_000;

	private long start = NEVER, end = NEVER;
	private long pendingAt = -1, pendingLength;

	/** A use seen now, with the cooldown the lore or catalog gives in ticks. Returns what became of it. */
	Outcome use(long now, long length) {
		if (start != NEVER && now >= start && now - start < REPEAT_TICKS) return Outcome.REPEAT;
		if (pendingAt >= 0) return Outcome.REPEAT;
		if (now < end) {
			pendingAt = now;
			pendingLength = length;
			return Outcome.WAITING;
		}
		start(now, length);
		return Outcome.STARTED;
	}

	/** The server said the ability is still on cooldown for this many ticks: the press before it was refused. */
	void refused(long now, long remaining) {
		pendingAt = -1;
		end = now + remaining;
		if (start > end) start = now;
	}

	/** Runs every tick: a waiting use that the server never refused starts the timer from its press. */
	Outcome tick(long now) {
		if (pendingAt < 0 || now - pendingAt < GRACE_TICKS) return null;
		start(pendingAt, pendingLength);
		pendingAt = -1;
		return Outcome.STARTED;
	}

	private void start(long at, long length) {
		start = at;
		end = at + length;
	}

	/** Ticks until ready, or 0. */
	long left(long now) {
		return Math.max(0, end - now);
	}

	boolean running(long now) {
		return now < end;
	}

	/** Ticks since the timer ran out; large when it never ran. */
	long idle(long now) {
		return end == NEVER ? Long.MAX_VALUE : now - end;
	}

	/** What a use did: started the timer, is waiting for a refusal, or was the same use reported twice. */
	enum Outcome { STARTED, WAITING, REPEAT }
}
