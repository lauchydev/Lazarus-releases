package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The online roster as data: every online player with the reasons staff have to look at them (a Flag
 * Dashboard flag, what the anticheat said, a captcha, an open report, a banned alt, a watch-list entry), the flagged ones first with the
 * most recent reason on top, everyone else by rank then name. No Minecraft types, so it's tested on its own.
 */
public final class Roster {
	/**
	 * Where a reason comes from: the Flag Dashboard, the anticheat's priority score, setbacks and detections (a check it
	 * caught the player on), a captcha, a report, a banned alt, the watch list.
	 */
	public enum Source {DASHBOARD, PRIORITY, SETBACK, DETECTION, CAPTCHA, REPORT, ALT, WATCH}

	/** One reason to look at a player: where it's from, its chip's label, the line the panel shows, when, and whether it went badly (a failed captcha). */
	public record Reason(Source source, String chip, String text, long at, boolean bad) {
	}

	/** A labelled chip beside a flagged player's name: "MACRO", "REPORT ×2", "CAPTCHA" (bad when the newest failed). */
	public record Chip(String label, Source source, boolean bad) {
	}

	/** An online player as the game knows them: their real name, the nick they go by ("" when none) and their rank ("" when unknown). */
	public record Online(String name, String nick, String rank) {
	}

	/**
	 * A row of the roster: the player, every reason newest first, the chips, their Flag score (0 when nothing scores
	 * them) and the client they last joined with ("" when unknown).
	 */
	public record Row(String name, String nick, String rank, List<Reason> reasons, List<Chip> chips, double score, String brand) {
		/** Whether anything flags this player. */
		public boolean flagged() {
			return !reasons.isEmpty();
		}

		/** When the newest reason came, 0 when there's none. */
		public long newest() {
			return reasons.isEmpty() ? 0 : reasons.getFirst().at();
		}
	}

	/** Dragsim's rank order, highest first; a rank not listed sorts after these, and no rank (default) last. */
	private static final List<String> RANKS = List.of("OWNER", "MANAGER", "ADMIN", "MOD", "MODERATOR", "HELPER", "WHALE", "PATRICK", "DRAGON", "MVP+", "MVP", "VIP+", "VIP");
	/** One bracketed tag at the start of a tab list name, "[ADMIN] ". */
	private static final Pattern TAG = Pattern.compile("\\G\\s*\\[([^\\]]+)]");
	/** The tag a vanished staff member's tab list name starts with, "[V]": a state, not a rank. */
	private static final String VANISHED = "V";

	private Roster() {
	}

	/**
	 * Where the rank tag starts in a tab list name's text (colour codes already left out): the "[" of the last
	 * tag before the name, so "[V] [ADMIN] Lauchy" gives the "[" of "[ADMIN]". Where the name itself starts
	 * when there's no rank tag, and 0 for an empty name.
	 */
	public static int tagStart(String shown) {
		Matcher m = TAG.matcher(shown);
		int start = -1;
		while (m.find()) if (!m.group(1).trim().equals(VANISHED)) start = m.start() + m.group().indexOf('[');
		if (start >= 0) return start;
		// No rank: skip a vanish tag and the spaces before the name.
		int end = 0;
		m.reset();
		while (m.find()) end = m.end();
		while (end < shown.length() && Character.isWhitespace(shown.charAt(end))) end++;
		return end < shown.length() ? end : 0;
	}

	/**
	 * Where the rank tag ends (just after its "]") in a tab list name's text, or {@link #tagStart} when there's
	 * no rank tag.
	 */
	public static int tagEnd(String shown) {
		int start = tagStart(shown);
		if (start >= shown.length() || shown.charAt(start) != '[') return start;
		int close = shown.indexOf(']', start);
		return close < 0 ? start : close + 1;
	}

	/** The rank in front of a tab list name ("[ADMIN] Lauchy" and "[V] [ADMIN] Lauchy" give "ADMIN", "[MVP_PLUS]" gives "MVP+"), or "" when there is none. */
	public static String rankOf(String display) {
		String shown = display.replaceAll("§.", "");
		int start = tagStart(shown), end = tagEnd(shown);
		if (end <= start + 1) return "";
		return shown.substring(start + 1, end - 1).trim().toUpperCase(Locale.ROOT).replace("_PLUS", "+").replace(' ', '_');
	}

	/** How far down the order a rank is: unknown ranks follow the known ones, and no rank comes last. */
	static int rankOrder(String rank) {
		if (rank.isEmpty()) return RANKS.size() + 1;
		int i = RANKS.indexOf(rank);
		return i < 0 ? RANKS.size() : i;
	}

	/**
	 * Every online player as a row. A reason counts for a player under their real name or their nick (a report
	 * may name the nick). Flagged rows come first, the highest Flag score first (those without one after, the newest
	 * reason first), then by name; the rest follow by rank, then name.
	 */
	public static List<Row> build(List<Online> online, StaffFlags.State shared, long now) {
		Map<String, List<Reason>> reasons = reasons(shared, now);
		List<Row> flagged = new ArrayList<>(), rest = new ArrayList<>();
		for (Online o : online) {
			Row row = row(o, reasons, shared);
			(row.flagged() ? flagged : rest).add(row);
		}
		flagged.sort(Comparator.comparingDouble(Row::score).reversed().thenComparing(Comparator.comparingLong(Row::newest).reversed())
				.thenComparing(r -> key(r.name())));
		rest.sort(Comparator.comparingInt((Row r) -> rankOrder(r.rank())).thenComparing(r -> key(r.name())));
		List<Row> out = new ArrayList<>(flagged);
		out.addAll(rest);
		return out;
	}

	/** One player's row, whether online or only looked up: their reasons under their name or nick, the chips, score and client. */
	public static Row row(Online o, StaffFlags.State shared, long now) {
		return row(o, reasons(shared, now), shared);
	}

	private static Row row(Online o, Map<String, List<Reason>> reasons, StaffFlags.State shared) {
		boolean nicked = !o.nick().isEmpty() && !o.nick().equalsIgnoreCase(o.name());
		List<Reason> mine = new ArrayList<>(reasons.getOrDefault(key(o.name()), List.of()));
		if (nicked) mine.addAll(reasons.getOrDefault(key(o.nick()), List.of()));
		mine.sort(Comparator.comparingLong(Reason::at).reversed());
		double score = Math.max(score(shared, o.name()), nicked ? score(shared, o.nick()) : 0);
		String brand = brand(shared, o.name());
		if (brand.isEmpty() && nicked) brand = brand(shared, o.nick());
		return new Row(o.name(), o.nick(), o.rank(), List.copyOf(mine), chips(mine), score, brand);
	}

	/**
	 * A player's Flag score: the anticheat's priority score when it was heard after the dashboard was last read,
	 * else the dashboard's (its highest for them); 0 when neither scores them.
	 */
	static double score(StaffFlags.State s, String name) {
		double board = 0;
		boolean onBoard = false;
		for (StaffFlags.Flag f : s.flags()) {
			if (f.name().equalsIgnoreCase(name)) {
				board = Math.max(board, f.score());
				onBoard = true;
			}
		}
		for (StaffFlags.Priority p : s.priorities()) {
			if (p.player().equalsIgnoreCase(name) && (!onBoard || p.at() > s.flagsAt())) return p.score();
		}
		return board;
	}

	/** The client a player last joined with, "" when unknown. */
	static String brand(StaffFlags.State s, String name) {
		for (StaffFlags.Brand b : s.brands()) if (b.player().equalsIgnoreCase(name)) return b.client();
		return "";
	}

	/** A score as the desk writes it: one decimal place at most, "67.5", "12". */
	public static String scoreText(double score) {
		return score == Math.rint(score) ? String.valueOf((long) score) : String.valueOf(Math.round(score * 10) / 10.0);
	}

	/** Every shared reason, by the lowercase name it's about. */
	private static Map<String, List<Reason>> reasons(StaffFlags.State s, long now) {
		Map<String, List<Reason>> out = new LinkedHashMap<>();
		for (StaffFlags.Flag f : s.flags()) {
			add(out, f.name(), new Reason(Source.DASHBOARD, f.type(), "Flagged for " + words(f.type()) + (f.detector().isEmpty() ? "" : " (" + f.detector() + ")")
					+ ", " + ago(now - f.since()), f.since(), false));
		}
		for (StaffFlags.Priority p : s.priorities()) {
			add(out, p.player(), new Reason(Source.PRIORITY, "SCORE " + scoreText(p.score()), "Priority score " + scoreText(p.score()) + ", " + ago(now - p.at()), p.at(), false));
		}
		for (StaffFlags.Setback b : s.setbacks()) {
			add(out, b.player(), new Reason(Source.SETBACK, b.count() > 1 ? "SETBACK ×" + b.count() : "SETBACK",
					"Position set back" + (b.count() > 1 ? " ×" + b.count() : "") + ", last " + ago(now - b.at()) + (b.place().isEmpty() ? "" : " at " + b.place()), b.at(), false));
		}
		// The last hour's detections, one reason per check: "Caught on click rate ×3, CPS up to 20, last 4 min ago".
		Map<String, List<StaffFlags.Detection>> heard = new LinkedHashMap<>();
		for (StaffFlags.Detection d : s.detections()) heard.computeIfAbsent(key(d.player()), k -> new ArrayList<>()).add(d);
		for (List<StaffFlags.Detection> his : heard.values()) {
			String name = his.getFirst().player();
			for (FlagAlerts.Check c : FlagAlerts.summarise(List.of(), 0, his, name, "", now)) {
				String counted = c.count() > 1 ? " ×" + c.count() : "";
				add(out, name, new Reason(Source.DETECTION, c.name().toUpperCase(Locale.ROOT) + counted, "Caught on " + c.name().toLowerCase(Locale.ROOT) + counted
						+ (c.peak().isEmpty() ? "" : ", " + c.peak()) + ", last " + ago(now - c.newest()), c.newest(), false));
			}
		}
		for (StaffFlags.Captcha c : s.captchas()) {
			add(out, c.player(), new Reason(Source.CAPTCHA, "CAPTCHA", (c.passed() ? "Passed" : "Failed") + " a captcha from " + c.by()
					+ (c.seconds() >= 0 ? " in " + c.seconds() + "s" : "") + ", " + ago(now - c.at()), c.at(), !c.passed()));
		}
		for (StaffFlags.Report r : s.reports()) {
			add(out, r.target(), new Reason(Source.REPORT, "REPORT", "Reported by " + r.reporter() + (r.reason().isEmpty() ? "" : ": " + r.reason())
					+ ", " + ago(now - r.at()), r.at(), false));
		}
		for (StaffFlags.Alt a : s.alts()) {
			// The banned account by name when the check said which ("Linked to OtherAlt (banned)"), else the group.
			List<String> banned = AltPages.banned(a.others());
			String text = banned.isEmpty() ? a.why() + (a.others().isEmpty() ? "" : ": " + a.others())
					: "Linked to " + String.join(", ", banned) + AltPages.BANNED;
			add(out, a.name(), new Reason(Source.ALT, "ALT", text, a.since(), false));
		}
		for (StaffFlags.Watch w : s.watch()) {
			add(out, w.name(), new Reason(Source.WATCH, "WATCH", "Watched" + (w.by().isEmpty() ? "" : " by " + w.by()) + (w.note().isEmpty() ? "" : ": " + w.note()),
					w.at(), false));
		}
		return out;
	}

	/** Files a reason under the player it's about. */
	private static void add(Map<String, List<Reason>> out, String name, Reason r) {
		out.computeIfAbsent(key(name), k -> new ArrayList<>()).add(r);
	}

	/**
	 * The chips for a player's reasons, newest first: one per dashboard flag type and per check the anticheat caught
	 * them on ("CLICK RATE ×3"), then SCORE and SETBACK from the anticheat, REPORT (with a count when there's more than
	 * one), CAPTCHA (bad when the newest check failed), ALT and WATCH.
	 */
	static List<Chip> chips(List<Reason> newestFirst) {
		List<Chip> out = new ArrayList<>();
		for (Source s : new Source[]{Source.DASHBOARD, Source.DETECTION}) {
			for (Reason r : newestFirst) {
				if (r.source() == s && out.stream().noneMatch(c -> c.label().equals(r.chip()))) out.add(new Chip(r.chip(), s, false));
			}
		}
		for (Source s : new Source[]{Source.PRIORITY, Source.SETBACK}) {
			newestFirst.stream().filter(r -> r.source() == s).findFirst().ifPresent(r -> out.add(new Chip(r.chip(), s, false)));
		}
		long reports = newestFirst.stream().filter(r -> r.source() == Source.REPORT).count();
		if (reports > 0) out.add(new Chip(reports == 1 ? "REPORT" : "REPORT ×" + reports, Source.REPORT, false));
		newestFirst.stream().filter(r -> r.source() == Source.CAPTCHA).findFirst().ifPresent(c -> out.add(new Chip("CAPTCHA", Source.CAPTCHA, c.bad())));
		if (newestFirst.stream().anyMatch(r -> r.source() == Source.ALT)) out.add(new Chip("ALT", Source.ALT, false));
		if (newestFirst.stream().anyMatch(r -> r.source() == Source.WATCH)) out.add(new Chip("WATCH", Source.WATCH, false));
		return List.copyOf(out);
	}

	/** A flag type as words: "MACRO" gives "Macro", "AUTO_CLICK" gives "Auto click". */
	static String words(String type) {
		String w = type.toLowerCase(Locale.ROOT).replace('_', ' ');
		return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
	}

	/** A name as the roster keys it, in lower case. */
	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * The player to select after moving {@code by} rows from {@code selected} (up is negative), staying within
	 * the list; the first row when nothing in it is selected, and "" when the list is empty.
	 */
	public static String move(List<Row> rows, String selected, int by) {
		if (rows.isEmpty()) return "";
		int at = -1;
		for (int i = 0; i < rows.size(); i++) if (rows.get(i).name().equalsIgnoreCase(selected)) at = i;
		if (at < 0) return rows.getFirst().name();
		return rows.get(Math.clamp(at + by, 0, rows.size() - 1)).name();
	}

	/** How long ago something happened, as the roster says it: "just now", "4 min ago", "2 h ago", "3 d ago". */
	public static String ago(long ms) {
		long min = Math.max(0, ms) / 60_000L;
		if (min < 1) return "just now";
		if (min < 60) return min + " min ago";
		return min < 48 * 60 ? min / 60 + " h ago" : min / (24 * 60) + " d ago";
	}

	/** The rows whose name or nick contains the search text, ignoring case; an empty search keeps everyone. */
	public static List<Row> filter(List<Row> rows, String search) {
		String q = search.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return rows;
		return rows.stream().filter(r -> key(r.name()).contains(q) || key(r.nick()).contains(q)).toList();
	}
}
