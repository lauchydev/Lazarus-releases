package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's answers to staff commands, read into what the staff desk shows (docs/dragsim/staff-replies.md):
 * punishment lists (history, warnings, bans, mutes, blame) as entries, {@code Key: value} answers (lookup,
 * account history), the anticheat's {@code key=value} line, alt lists, and what an action said, as one tidy
 * line. Lines come as plain text. No Minecraft types, so it's tested on its own.
 */
final class StaffReplies {
	/** Where a punishment stands: in force (with the time left), undone by someone, or over at once (a kick). */
	enum State {ACTIVE, UNDONE, ENDED}

	/** One punishment: its case number, kind (BAN, IPBAN, MUTE, WARN, KICK), who, by whom, when, why, and where it stands. */
	record Entry(int id, String kind, String player, String by, String date, String reason, State state, String left, String undoneBy) {
		/** Whether it's in force now. */
		boolean active() {
			return state == State.ACTIVE;
		}
	}

	/** "#290 MUTE TestAlt by Lauchy on Nov 7, 2026 05:01 - Lazarus test (active, 59m left)". */
	private static final Pattern ENTRY = Pattern.compile("^#(\\d+)\\s+([A-Z]+)\\s+(\\w{1,16})\\s+by\\s+(\\w{1,16})\\s+on\\s+(.+?)\\s+-\\s+(.*?)(?:\\s*\\(([^()]*)\\))?$");
	/** What a state in brackets starts with; other brackets at the end are part of the reason ("Spam (again)"). */
	private static final Pattern STATE = Pattern.compile("(?i)^(active|undone|expired|ended|permanent|removed|revoked)\\b.*");
	/** "-------- History of TestAlt (page 1) --------". */
	private static final Pattern HEADER = Pattern.compile("^-{3,}\\s*(.+?)\\s*-{3,}$");
	/** Chat stacking's count on a repeated line: " (x2)". */
	private static final Pattern STACKED = Pattern.compile("\\s*\\(x\\d+\\)$");
	/** A rule of dashes or bars, the lookup's frame. */
	private static final Pattern RULE = Pattern.compile("^[-▬=_]{5,}$");
	/** The keys /lookup answers with, and /accounthistory's. */
	private static final List<String> FIELDS = List.of("Lookup", "Rank", "UUID", "IP", "Balance", "Health", "Mana", "Names", "Addresses");
	private static final Pattern FIELD = Pattern.compile("^(" + String.join("|", FIELDS) + "):\\s*(.*)$");
	/** "[DragSim AC] TestAlt checks=176; vl=0; transaction=219ms; …". */
	private static final Pattern AC = Pattern.compile("^\\[DragSim AC]\\s+(\\w{1,16})\\s+(.*)$");
	private static final Pattern DONE = Pattern.compile("^You (warned|muted|banned|kicked) (\\w{1,16})(?: for (\\S+))?\\. Reason: (.*?)\\s*\\(#(\\d+)\\)$");
	private static final Pattern REMOVED = Pattern.compile("^Removed the (warning|mute|ban) on (\\w{1,16})\\.$");
	private static final Pattern CAPTCHA_SENT = Pattern.compile("^Sent a terminal check to (\\w{1,16})\\.\\s*They have (.+?)\\.?$");
	private static final Pattern TELEPORTED = Pattern.compile("^Teleported (\\w{1,16}) to (\\w{1,16})\\.$");

	private StaffReplies() {
	}

	/** A line as the readers take it: trimmed, without chat stacking's "(x2)". */
	static String clean(String line) {
		return STACKED.matcher(line.strip()).replaceFirst("").strip();
	}

	/** A punishment entry, or null when the line isn't one. */
	static Entry entry(String line) {
		Matcher m = ENTRY.matcher(clean(line));
		if (!m.matches()) return null;
		String reason = m.group(6).trim();
		// An entry still in force may come with no state at all: /warns lists an active warning that way.
		if (m.group(7) == null || !STATE.matcher(m.group(7).trim()).matches()) {
			if (m.group(7) != null) reason = (reason + " (" + m.group(7).trim() + ")").trim();
			return new Entry(Integer.parseInt(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5).trim(), reason, State.ACTIVE, "", "");
		}
		String s = m.group(7).trim();
		String lower = s.toLowerCase(Locale.ROOT);
		State state;
		String left = "", undoneBy = "";
		if (lower.startsWith("active")) {
			state = State.ACTIVE;
			left = s.replaceFirst("(?i)^active,?\\s*", "").replaceFirst("(?i)\\s*left$", "").trim();
		} else if (lower.startsWith("undone by")) {
			state = State.UNDONE;
			undoneBy = s.substring("undone by".length()).trim();
		} else if (lower.equals("undone") && m.group(2).equals("KICK")) {
			state = State.ENDED;
		} else {
			state = State.UNDONE;
		}
		return new Entry(Integer.parseInt(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5).trim(), reason, state, left, undoneBy);
	}

	/** Every entry in an answer, in the order sent. */
	static List<Entry> entries(List<String> lines) {
		List<Entry> out = new ArrayList<>();
		for (String l : lines) {
			Entry e = entry(l);
			if (e != null) out.add(e);
		}
		return out;
	}

	/** A list's title ("History of TestAlt (page 1)"), or null when the line isn't a header. */
	static String header(String line) {
		Matcher m = HEADER.matcher(clean(line));
		return m.matches() ? m.group(1) : null;
	}

	/** The {@code Key: value} lines of a /lookup or /accounthistory answer, in order. */
	static Map<String, String> fields(List<String> lines) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String l : lines) {
			Matcher m = FIELD.matcher(clean(l));
			if (m.matches()) out.put(m.group(1), m.group(2).trim());
		}
		return out;
	}

	/** The anticheat's {@code key=value} pairs for a player ("checks" → "176", "vl" → "0", "alerts" → "false"). */
	static Map<String, String> acInfo(List<String> lines) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String l : lines) {
			Matcher m = AC.matcher(clean(l));
			if (!m.matches()) continue;
			for (String pair : m.group(2).split(";")) {
				int eq = pair.indexOf('=');
				if (eq > 0) out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
			}
		}
		return out;
	}

	/** The names under an "Alts of …" header. */
	static List<String> alts(List<String> lines) {
		List<String> out = new ArrayList<>();
		boolean in = false;
		for (String l : lines) {
			String c = clean(l), h = header(c);
			if (h != null) {
				in = h.toLowerCase(Locale.ROOT).startsWith("alts of");
				continue;
			}
			if (in && c.matches("\\w{3,16}")) out.add(c);
			else if (in && !c.isEmpty()) in = false;
		}
		return out;
	}

	/** Whether a line belongs to a /lookup or /accounthistory answer: one of its keys, or the frame around it. */
	static boolean lookupLine(String line) {
		String c = clean(line);
		return FIELD.matcher(c).matches() || RULE.matcher(c).matches() || header(c) != null;
	}

	/** Whether a line is the server answering an action (not the "[Punish]" line every staff member sees). */
	static boolean outcomeLine(String line) {
		String c = clean(line);
		return DONE.matcher(c).matches() || REMOVED.matcher(c).matches() || CAPTCHA_SENT.matcher(c).matches() || TELEPORTED.matcher(c).matches()
				|| c.startsWith("You are now vanished") || c.startsWith("You are no longer vanished") || c.startsWith("Scanning ") || c.startsWith("Scan started")
				|| c.startsWith("You are now in the");
	}

	/** What an action's answer says, as one tidy line ("Muted TestAlt for 1h · Lazarus test · #290"); "" when it said nothing. */
	static String outcome(List<String> lines) {
		for (String l : lines) {
			String c = clean(l);
			Matcher m;
			if ((m = DONE.matcher(c)).matches()) {
				String verb = switch (m.group(1)) {
					case "warned" -> "Warned";
					case "muted" -> "Muted";
					case "banned" -> "Banned";
					default -> "Kicked";
				};
				return verb + " " + m.group(2) + (m.group(3) == null ? "" : " for " + m.group(3)) + " · " + m.group(4) + " · #" + m.group(5);
			}
			if ((m = REMOVED.matcher(c)).matches()) return "Removed the " + m.group(1) + " on " + m.group(2);
			if ((m = CAPTCHA_SENT.matcher(c)).matches()) return "Captcha sent to " + m.group(1) + " · " + m.group(2) + " to answer";
			if ((m = TELEPORTED.matcher(c)).matches()) return "Teleported " + m.group(1) + " to " + m.group(2);
			if (c.startsWith("You are now vanished")) return "You're vanished";
			if (c.startsWith("You are no longer vanished")) return "You're visible again";
			if (c.startsWith("Scan started") || c.startsWith("Scanning ")) return "Dupe scan started · results go to MOD+ chat";
		}
		return lines.isEmpty() ? "" : clean(lines.getFirst());
	}

	/**
	 * One /auditlog line: when ("11/07 00:01:38"), what happened (GIVE, CREATE, DESTROY, TRANSFER…), the item's id, how many
	 * (1 unless "x7"), who it went to or came from in a trade ("" otherwise) and why ("loot_drop", "trade"…).
	 */
	record Audit(String time, String action, String item, int count, String with, String source) {
		/** The item as a player reads it: "golden_dragon_pet_legendary" → "Golden Dragon Pet Legendary". */
		String itemName() {
			StringBuilder out = new StringBuilder();
			for (String w : item.split("_")) {
				if (w.isEmpty()) continue;
				if (!out.isEmpty()) out.append(' ');
				out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
			}
			return out.toString();
		}
	}

	/** "[11/07 00:01:38] TRANSFER golden_dragon_pet_legendary ↔ MaciekByr (trade)", "[11/07 00:07:54] DESTROY summoning_eye x7 (npc_sell)". */
	private static final Pattern AUDIT = Pattern.compile("^\\[(\\d{2}/\\d{2} \\d{2}:\\d{2}(?::\\d{2})?)]\\s+([A-Z_]+)\\s+(\\S+)(?:\\s+x(\\d+))?(?:\\s+[↔<>-]+\\s+(\\w{1,16}))?(?:\\s+\\(([^()]*)\\))?$");
	/** "50 transactions for Finnzius (last 7 days):". */
	private static final Pattern AUDIT_HEAD = Pattern.compile("^(\\d+) transactions? for (\\w{1,16})(?:\\s*\\((.+?)\\))?:?$");
	/** "No transactions found for TestAlt.". */
	private static final Pattern AUDIT_NONE = Pattern.compile("^No transactions found for (\\w{1,16})\\.?$");

	/** One audit log line, or null when the line isn't one. */
	static Audit audit(String line) {
		Matcher m = AUDIT.matcher(clean(line));
		if (!m.matches()) return null;
		return new Audit(m.group(1), m.group(2), m.group(3), m.group(4) == null ? 1 : Integer.parseInt(m.group(4)),
				m.group(5) == null ? "" : m.group(5), m.group(6) == null ? "" : m.group(6));
	}

	/** Every transaction in an audit log answer, in the order sent. */
	static List<Audit> audits(List<String> lines) {
		List<Audit> out = new ArrayList<>();
		for (String l : lines) {
			Audit a = audit(l);
			if (a != null) out.add(a);
		}
		return out;
	}

	/** The span an audit log answer covers ("last 7 days"), "" when it didn't say. */
	static String auditSpan(List<String> lines) {
		for (String l : lines) {
			Matcher m = AUDIT_HEAD.matcher(clean(l));
			if (m.matches()) return m.group(3) == null ? "" : m.group(3);
		}
		return "";
	}

	/** How many transactions the answer says there are ("50 transactions for …"), or 0 when it didn't say. */
	static int auditCount(List<String> lines) {
		for (String l : lines) {
			Matcher m = AUDIT_HEAD.matcher(clean(l));
			if (m.matches()) return Integer.parseInt(m.group(1));
		}
		return 0;
	}

	/** Whether the server said the player has no transactions. */
	static boolean auditEmpty(List<String> lines) {
		return lines.stream().anyMatch(l -> AUDIT_NONE.matcher(clean(l)).matches());
	}

	/** Whether a line belongs to an /auditlog answer: its header, its "none found", or a transaction. */
	static boolean auditLine(String line) {
		String c = clean(line);
		return AUDIT_HEAD.matcher(c).matches() || AUDIT_NONE.matcher(c).matches() || AUDIT.matcher(c).matches();
	}

	/** "greenum: no activity tracked" (/captcha status) as the desk says it; any other line as sent. */
	static String captchaStatus(String line) {
		String c = clean(line);
		Matcher m = CAPTCHA_NONE.matcher(c);
		return m.matches() ? "No captcha on record for " + m.group(1) + "." : c;
	}

	private static final Pattern CAPTCHA_NONE = Pattern.compile("(?i)^(\\w{1,16}):\\s*no activity tracked\\.?$");

	/** A date as the desk shows it: "Nov 7, 2026 05:01" → "Nov 7, 05:01" (the year only when it isn't this one). */
	static String shortDate(String date, int thisYear) {
		return date.replaceFirst(",\\s*" + thisYear + "\\s+", ", ");
	}
}
