package dev.lauchy.lazarus.core;

/**
 * Whether Lazarus+ is unlocked, and why, from what the Lazarus server last signed for this
 * account. Pure: nothing here touches the game, files or the network, and every question takes
 * the time, so tests can pass in any clock.
 */
public final class Membership {
	/** A saved receipt counts for this many days after the server was last reached. */
	public static final int FRESH_DAYS = 7;
	/** The server sells a pass for an account only once this many days or fewer are left on it. */
	public static final int EXTEND_WITHIN_DAYS = 60;
	private static final long DAY = 86_400_000L;

	/** The last receipt the server signed for this account; null when it never sent one. */
	private volatile Receipt last;
	/** When the server last answered, in ms; 0 when it never did. */
	private volatile long contact;
	/** The latest time the mod saw on the computer's clock, in ms, so a clock set back shows up. */
	private volatile long seen;

	/**
	 * A membership receipt the server signed for this account: when Lazarus+ ends (or ended), in ms; 0 when it never had it.
	 * Frozen while a chargeback is open on one of its payments.
	 */
	public record Receipt(long until, boolean frozen) {
		/** A receipt for a pass that isn't frozen. */
		public Receipt(long until) {
			this(until, false);
		}
	}

	/** Why Lazarus+ is unlocked or locked. */
	public enum Reason {
		/** The pass hasn't reached its end date. */
		ACTIVE,
		/** The pass reached its end date. */
		ENDED,
		/** This account never had Lazarus+, or the server never signed a receipt for it. */
		NEVER_HAD,
		/** A chargeback is open on one of the account's payments, so the pass is paused until it closes. */
		FROZEN,
		/** The mod hasn't reached the server (by an answer, the live connection or a check-in) in over {@link #FRESH_DAYS} days, so the saved receipt no longer counts. */
		OUT_OF_TOUCH,
		/** The computer's clock is earlier than the latest time the mod saw, so the end date can't be trusted until the server answers. */
		CLOCK_BACK
	}

	/** What the mod does about Lazarus+ right now. */
	public record Decision(boolean unlocked, Reason reason) {
	}

	/** The server answered with a signed receipt at {@code now}: its word replaces whatever the clock said before. */
	public void heard(Receipt receipt, long now) {
		last = receipt;
		contact = now;
		seen = now;
	}

	/**
	 * The server was reached at {@code now} with no news about Lazarus+ (the live connection answering a ping,
	 * or telling of others): the receipt from before still stands, and counts as heard from now, as
	 * {@link #heard} does, so a game connected for weeks without a change never pauses Plus.
	 */
	public void reached(long now) {
		contact = now;
		seen = now;
	}

	/** The server couldn't be reached, so the receipt saved from an earlier answer stands in, with when the server was last reached and the latest time the mod saw. */
	public void restored(Receipt receipt, long contact, long seen) {
		last = receipt;
		this.contact = contact;
		this.seen = seen;
	}

	/** The computer's clock read {@code now}; the latest time seen only ever moves forward. */
	public void saw(long now) {
		if (now > seen) seen = now;
	}

	/** When the server last answered, in ms; 0 when it never did. */
	public long contact() {
		return contact;
	}

	/** The latest time the mod saw on the computer's clock, in ms. */
	public long seen() {
		return seen;
	}

	/** When Lazarus+ ends (or ended), in ms; 0 when this account never had it or no receipt came. */
	public long until() {
		Receipt r = last;
		return r == null ? 0 : r.until();
	}

	/**
	 * Whether a pass running at {@code now} has more than {@link #EXTEND_WITHIN_DAYS} days left, the one reason to
	 * refuse a purchase the mod can tell the player. A frozen pass or none at all is never refused for this.
	 */
	public boolean tooMuchLeft(long now) {
		Receipt r = last;
		return r != null && !r.frozen() && r.until() - now > EXTEND_WITHIN_DAYS * DAY;
	}

	/** Lazarus+ at {@code now}. */
	public Decision decide(long now) {
		Receipt r = last;
		if (r == null) return new Decision(false, Reason.NEVER_HAD);
		if (r.frozen()) return new Decision(false, Reason.FROZEN);
		if (r.until() <= 0) return new Decision(false, Reason.NEVER_HAD);
		if (r.until() <= now) return new Decision(false, Reason.ENDED);
		if (now < seen) return new Decision(false, Reason.CLOCK_BACK);
		if (now - contact > FRESH_DAYS * DAY) return new Decision(false, Reason.OUT_OF_TOUCH);
		return new Decision(true, Reason.ACTIVE);
	}
}
