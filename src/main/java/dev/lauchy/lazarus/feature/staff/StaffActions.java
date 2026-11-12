package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Gate;


/**
 * Everything the staff desk can do, as the server's Staff Command Center lists it (docs/dragsim/staff-commands.md):
 * each action's label, its server command ({@code %p} the player, {@code %me} you, {@code %d} the duration,
 * {@code %r} the reason, {@code %s} a server; empty for what Lazarus does itself), how it's entered, the rank it
 * needs and whether the player must be online. No Minecraft types, so it's tested on its own.
 */
enum StaffActions {
	BAN("Ban", "ban %p %d %r", Kind.PUNISH, Card.BAN, Gate.Tier.MOD, true, false),
	IP_BAN("IP ban", "ipban %p %d %r", Kind.PUNISH, Card.BAN, Gate.Tier.MOD, true, false),
	MUTE("Mute", "mute %p %d %r", Kind.PUNISH, Card.MUTE, Gate.Tier.MOD, true, false),
	IP_MUTE("IP mute", "ipmute %p %d %r", Kind.PUNISH, Card.MUTE, Gate.Tier.MOD, true, false),
	KICK("Kick", "kick %p %r", Kind.PUNISH, Card.REASON, Gate.Tier.MOD, true, true),
	WARN("Warn", "warn %p %r", Kind.PUNISH, Card.REASON, Gate.Tier.MOD, false, false),

	UNBAN("Unban", "unban %p", Kind.REVOKE, Card.CONFIRM, Gate.Tier.MOD, false, false),
	UNMUTE("Unmute", "unmute %p", Kind.REVOKE, Card.CONFIRM, Gate.Tier.MOD, false, false),
	UNWARN("Unwarn", "unwarn %p", Kind.REVOKE, Card.CONFIRM, Gate.Tier.MOD, false, false),

	CAPTCHA("Captcha", "captcha %p", Kind.INVESTIGATE, Card.NONE, Gate.Tier.MOD, false, true),
	WATCH("Watch", "", Kind.INVESTIGATE, Card.NONE, Gate.Tier.NONE, false, false),
	FLAG_DETAILS("Flag details", "", Kind.INVESTIGATE, Card.NONE, Gate.Tier.NONE, false, false),
	PROFILE("View profile", "", Kind.INVESTIGATE, Card.NONE, Gate.Tier.NONE, false, false),

	HISTORY("History", "history %p", Kind.RECORD, Card.NONE, Gate.Tier.MOD, false, false),
	WARNS("Warnings", "warns %p", Kind.RECORD, Card.NONE, Gate.Tier.MOD, false, false),
	ALTS("Alts", "alts %p", Kind.RECORD, Card.NONE, Gate.Tier.MOD, false, false),

	ACCOUNT_HISTORY("Account history", "accounthistory %p", Kind.LOOKUP, Card.NONE, Gate.Tier.MOD, false, false),
	LOOKUP("Lookup", "lookup %p", Kind.LOOKUP, Card.NONE, Gate.Tier.MOD, false, false),
	AC_INFO("AC info", "dragsimac info %p", Kind.LOOKUP, Card.NONE, Gate.Tier.MOD, false, false),
	CAPTCHA_STATUS("Captcha status", "captcha status %p", Kind.LOOKUP, Card.NONE, Gate.Tier.MOD, false, false),
	SCAN("Scan", "scan %p", Kind.LOOKUP, Card.NONE, Gate.Tier.MOD, false, true),
	AUDIT_LOG("Audit log", "auditlog %p", Kind.LOOKUP, Card.NONE, Gate.Tier.ADMIN, false, false),
	PROFILE_ADMIN("Profile admin", "profileadmin %p", Kind.LOOKUP, Card.NONE, Gate.Tier.ADMIN, false, false),

	GO_TO("Go to", "goto %p", Kind.TRAVEL, Card.NONE, Gate.Tier.MOD, false, true),
	BRING("Bring here", "tp %p %me", Kind.TRAVEL, Card.NONE, Gate.Tier.MOD, false, true),
	SEND("Send", "send %p %s", Kind.TRAVEL, Card.PICK_SERVER, Gate.Tier.ADMIN, false, true),

	VANISH("Vanish", "vanish", Kind.SELF, Card.NONE, Gate.Tier.MOD, false, false),
	LOCK_CHAT("Lock chat", "lockchat", Kind.SELF, Card.NONE, Gate.Tier.MOD, false, false),
	STAFF_CHAT("/sc", "", Kind.SELF, Card.NONE, Gate.Tier.MOD, false, false),
	HOP("Hop", "server %s", Kind.SELF, Card.PICK_SERVER, Gate.Tier.ADMIN, false, false);

	/**
	 * Where an action sits: punishments and their undoing, looking into a player (the buttons, the records the
	 * dossier loads, the lookups it fetches on demand), moving players, and what you do to yourself.
	 */
	enum Kind {PUNISH, REVOKE, INVESTIGATE, RECORD, LOOKUP, TRAVEL, SELF}

	/**
	 * How an action is entered: at once, a reason, the ban card (duration, reason, held to confirm), the mute card,
	 * a second click to confirm, or a server to pick.
	 */
	enum Card {NONE, REASON, BAN, MUTE, CONFIRM, PICK_SERVER}

	final String label, command;
	final Kind kind;
	final Card card;
	final Gate.Tier tier;
	/** Whether doing it handles every open report on the player (Glossary: Handled). */
	final boolean handlesReports;
	/** Whether the player must be online for it. */
	final boolean onlineOnly;

	StaffActions(String label, String command, Kind kind, Card card, Gate.Tier tier, boolean handlesReports, boolean onlineOnly) {
		this.label = label;
		this.command = command;
		this.kind = kind;
		this.card = card;
		this.tier = tier;
		this.handlesReports = handlesReports;
		this.onlineOnly = onlineOnly;
	}

	/** Whether it's a server command rather than something Lazarus does itself. */
	boolean server() {
		return !command.isEmpty();
	}

	/** The command for a player, with what the card asked filled in and spare spaces taken out; no leading slash. */
	static String fill(StaffActions a, String player, String me, String duration, String reason, String server) {
		return a.command.replace("%p", player).replace("%me", me).replace("%d", duration).replace("%r", reason).replace("%s", server)
				.replaceAll(" +", " ").trim();
	}

	/** Why the viewer can't use it right now ("MOD+", "ADMIN+", "offline"), or "" when they can. */
	static String blocked(StaffActions a, Gate.Tier viewer, boolean online) {
		if (!viewer.allows(a.tier)) return a.tier.tag();
		if (a.onlineOnly && !online) return "offline";
		return "";
	}
}
