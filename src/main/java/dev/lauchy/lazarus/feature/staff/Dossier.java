package dev.lauchy.lazarus.feature.staff;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the staff desk knows about one player from the server's commands: the records it asks for on its own
 * (history, then warnings, then alts: the server answers one command at a time) and the lookups asked for by
 * hand. Each is waiting, being asked, answered, or unanswered. No Minecraft types, so it's tested on its own.
 */
final class Dossier {
	/** Where one command's answer stands. */
	enum Status {WAITING, ASKING, ANSWERED, NO_ANSWER}

	/** The records asked for on opening, in the order they're asked. */
	static final List<StaffActions> RECORDS = List.of(StaffActions.HISTORY, StaffActions.WARNS, StaffActions.ALTS);

	final String player;
	private final Map<StaffActions, Status> status = new EnumMap<>(StaffActions.class);
	private final Map<StaffActions, ServerReply> replies = new EnumMap<>(StaffActions.class);
	private final Map<StaffActions, Long> askedAt = new EnumMap<>(StaffActions.class);

	Dossier(String player) {
		this.player = player;
		for (StaffActions a : RECORDS) status.put(a, Status.WAITING);
	}

	/** What the Account tab asks for when it's opened, in turn: the lookup, the account history, the anticheat's view. */
	static final List<StaffActions> ACCOUNT = List.of(StaffActions.LOOKUP, StaffActions.ACCOUNT_HISTORY, StaffActions.AC_INFO);

	/** The next record to ask for, or null when every one has been asked. */
	StaffActions next() {
		for (StaffActions a : RECORDS) if (status(a) == Status.WAITING) return a;
		return null;
	}

	/** The next of {@code order} not yet asked (nor answered), or null. */
	StaffActions next(List<StaffActions> order) {
		for (StaffActions a : order) if (!status.containsKey(a) || status(a) == Status.WAITING) return a;
		return null;
	}

	/** Whether anything is being asked right now. */
	boolean asking() {
		return status.containsValue(Status.ASKING);
	}

	Status status(StaffActions a) {
		return status.getOrDefault(a, Status.WAITING);
	}

	/** The answer to a command, or null before there is one. */
	ServerReply reply(StaffActions a) {
		return replies.get(a);
	}

	/** A command has been sent. */
	void asked(StaffActions a) {
		status.put(a, Status.ASKING);
		askedAt.put(a, net.minecraft.util.Util.getMillis());
	}

	/** When a command was last sent (game time in milliseconds), or now when it hasn't been. */
	long askedAt(StaffActions a) {
		return askedAt.getOrDefault(a, net.minecraft.util.Util.getMillis());
	}

	/** The server's answer came (an empty one counts as none). */
	void answered(StaffActions a, ServerReply reply) {
		replies.put(a, reply);
		status.put(a, reply.answered() ? Status.ANSWERED : Status.NO_ANSWER);
	}

	/** Ask again: a record goes back in the queue, a lookup is forgotten. */
	void retry(StaffActions a) {
		replies.remove(a);
		if (RECORDS.contains(a)) status.put(a, Status.WAITING);
		else status.remove(a);
	}
}
