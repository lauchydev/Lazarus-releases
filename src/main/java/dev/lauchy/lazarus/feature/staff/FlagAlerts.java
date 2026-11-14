package dev.lauchy.lazarus.feature.staff;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A flagged player's own page on the server's Flag Dashboard ("Flags: ChillGuy87964"), read into its alerts, and
 * their last hour summed up check by check: what the anticheat keeps catching them doing, how often, and how far
 * ("Click rate ×12, CPS up to 20"). An alert on the page reads, in its item's name and lore:
 * <pre>
 * ANTICHEAT
 * Awaiting manual review
 * Score contribution: 1.99
 * Source: DragSim AC
 * Server: hub-1
 * Time: 11-07 11:25:22 UTC
 *
 * Click rate; coalesced=1; ping=212ms; TPS=20.00; CPS=20.0; window=2.0s;
 *  main-hand swings=40; attacks=0; independent streams, not summed
 * </pre>
 * The detections staff chat hears ({@link StaffFlags.Detection}) join in, so a player not yet opened still shows
 * what they're caught on. No Minecraft types, so it's tested on its own.
 */
public final class FlagAlerts {
	/** How far back a summary looks. */
	public static final long WINDOW_MS = 60 * 60_000L;

	/** One alert: its type ("ANTICHEAT"), state ("Awaiting manual review"), what it adds to the score, its source, server, when (0 unknown) and evidence. */
	public record Alert(String type, String status, double score, String source, String server, long at, String detail) {
		/** The check the evidence names first ("Click rate"), else the type as words ("Macro"). */
		public String check() {
			String first = detail.split(";", 2)[0].trim();
			return first.isEmpty() || first.contains("=") ? Roster.words(type) : first;
		}
	}

	/** One measure in an alert's evidence: "CPS=20.0" is CPS 20 with no unit, "ping=212ms" is ping 212 ms. */
	public record Metric(String name, double value, String unit) {
	}

	/** The lowest and highest a measure was across a check's alerts. */
	public record Range(String name, double min, double max, String unit) {
		/** "CPS 10.5–20", "ping 212 ms". */
		public String text() {
			String span = min == max ? number(max) : number(min) + "–" + number(max);
			return name + " " + span + (unit.isEmpty() ? "" : " " + unit);
		}
	}

	/**
	 * One check summed up: its name, how many alerts, what they added to the score (0 when only chat heard them), the
	 * measure that says most about it (null when there's none), every measure's range, the servers it happened on,
	 * and the newest alert's time and evidence.
	 */
	public record Check(String name, int count, double score, Range headline, List<Range> ranges, List<String> servers, long newest, String latest) {
		/** "Click rate ×12". */
		public String counted() {
			return count > 1 ? name + " ×" + count : name;
		}

		/** "CPS up to 20", or "" when no measure says. */
		public String peak() {
			return headline == null ? "" : headline.name() + " up to " + number(headline.max()) + (headline.unit().isEmpty() ? "" : " " + headline.unit());
		}
	}

	/** The page's title, "Flags: ChillGuy87964". */
	private static final Pattern TITLE = Pattern.compile("^Flags:\\s*(\\w{1,16})\\s*$", Pattern.CASE_INSENSITIVE);
	/** A "Label: value" lore line. */
	private static final Pattern FIELD = Pattern.compile("^(Score contribution|Source|Server|Time):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	/** "11-07 11:25:22 UTC", or with its year, "2026-11-07 11:25:22 UTC". */
	private static final Pattern TIME = Pattern.compile("^(?:(\\d{4})-)?(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\s*UTC)?$", Pattern.CASE_INSENSITIVE);
	/** One measure: a name ("main-hand swings"), a number, and a unit stuck to it ("212ms"). */
	private static final Pattern METRIC = Pattern.compile("^([A-Za-z][\\w -]*?)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)\\s*([A-Za-z%]*)$");
	/** A line telling you what clicking the item does ("Click to mark reviewed", "Right-click: dismiss"), which isn't evidence; "Click rate" is a check. */
	private static final Pattern HINT = Pattern.compile("^(?:(?:left|right|shift|middle)[- ]?)?click\\s*(?:to\\b|here\\b|[:»►➜→]).*", Pattern.CASE_INSENSITIVE);
	/** Measures that describe the server or the sample rather than the player: never the one a check is summed up by. */
	private static final Set<String> CONTEXT = Set.of("coalesced", "ping", "tps", "window", "vl");

	private FlagAlerts() {
	}

	/** The player whose page a window with this plain title is ("Flags: ChillGuy87964"), or null when it isn't one. */
	public static String player(String title) {
		Matcher m = TITLE.matcher(title.trim());
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * One item on the page, read from its plain name and lore, or null when it isn't an alert (a button, filler).
	 * The evidence is everything after the first blank lore line, its wrapped lines joined back.
	 */
	public static Alert read(String name, List<String> lore, long now) {
		String status = "", source = "", server = "";
		double score = 0;
		long at = 0;
		boolean alert = false, evidence = false;
		StringBuilder detail = new StringBuilder();
		for (String raw : lore) {
			String line = raw.strip();
			if (evidence) {
				if (line.isEmpty() || HINT.matcher(line).matches()) continue;
				// A wrapped line that went on after a space joins straight on; one cut where there was none gets its space back.
				if (!detail.isEmpty() && !Character.isWhitespace(raw.charAt(0)) && detail.charAt(detail.length() - 1) != ' ') detail.append(' ');
				detail.append(detail.isEmpty() ? line : raw.stripTrailing());
				continue;
			}
			if (line.isEmpty()) {
				evidence = alert;
				continue;
			}
			Matcher m = FIELD.matcher(line);
			if (m.matches()) {
				alert = true;
				String v = m.group(2).trim();
				switch (m.group(1).toLowerCase(Locale.ROOT)) {
					case "score contribution" -> score = parse(v);
					case "source" -> source = v;
					case "server" -> server = v;
					default -> at = time(v, now);
				}
			} else if (status.isEmpty() && !HINT.matcher(line).matches()) {
				status = line;
			}
		}
		if (!alert) return null;
		return new Alert(name.trim(), status, score, source, server, at, detail.toString().replaceAll("\\s+", " ").trim());
	}

	/** "11-07 11:25:22 UTC" as ms, in the year that puts it no later than a day after {@code now}; 0 when it isn't a time. */
	public static long time(String text, long now) {
		Matcher m = TIME.matcher(text.trim());
		if (!m.matches()) return 0;
		try {
			int year = m.group(1) != null ? Integer.parseInt(m.group(1)) : ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneOffset.UTC).getYear();
			LocalDateTime t = LocalDateTime.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
					m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
			long ms = t.toInstant(ZoneOffset.UTC).toEpochMilli();
			// The page leaves the year out: a time "in the future" is last year's (read on New Year's Day).
			if (m.group(1) == null && ms > now + 24 * 3600_000L) ms = t.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli();
			return ms;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Every measure in an alert's evidence, in order: "CPS=20.0; ping=212ms" gives CPS 20 and ping 212 ms. */
	public static List<Metric> metrics(String detail) {
		List<Metric> out = new ArrayList<>();
		for (String part : detail.split(";")) {
			Matcher m = METRIC.matcher(part.trim());
			if (m.matches()) out.add(new Metric(m.group(1).trim(), Double.parseDouble(m.group(2)), m.group(3)));
		}
		return out;
	}

	/**
	 * The last hour for {@code player} (or their nick, {@code alsoKnownAs}, "" for none) summed up by check, the most
	 * alerts first: the page's alerts as read at {@code readAt}, and what staff chat heard since (all of the last hour
	 * when the page hasn't been read, {@code readAt} 0), since the chat lines up to the read are on the page already.
	 */
	public static List<Check> summarise(List<Alert> page, long readAt, List<StaffFlags.Detection> heard, String player, String alsoKnownAs, long now) {
		long from = now - WINDOW_MS, chatFrom = Math.max(from, readAt);
		List<Alert> all = new ArrayList<>();
		for (Alert a : page) if (a.at() > from) all.add(a);
		for (StaffFlags.Detection d : heard) {
			boolean his = d.player().equalsIgnoreCase(player) || !alsoKnownAs.isEmpty() && d.player().equalsIgnoreCase(alsoKnownAs);
			if (his && d.at() > chatFrom) all.add(new Alert("ANTICHEAT", "", 0, "DragSim AC", "", d.at(), d.check() + (d.detail().isEmpty() ? "" : "; " + d.detail())));
		}
		Map<String, List<Alert>> byCheck = new LinkedHashMap<>();
		for (Alert a : all) byCheck.computeIfAbsent(a.check().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(a);
		List<Check> out = new ArrayList<>();
		for (List<Alert> alerts : byCheck.values()) out.add(check(alerts));
		out.sort(Comparator.comparingInt(Check::count).reversed().thenComparing(Comparator.comparingLong(Check::newest).reversed()));
		return List.copyOf(out);
	}

	/** One check's alerts summed up. */
	private static Check check(List<Alert> alerts) {
		alerts.sort(Comparator.comparingLong(Alert::at).reversed());
		Alert newest = alerts.getFirst();
		Map<String, double[]> spans = new LinkedHashMap<>();
		Map<String, String> units = new LinkedHashMap<>(), names = new LinkedHashMap<>();
		List<String> servers = new ArrayList<>();
		double score = 0;
		for (Alert a : alerts) {
			score += a.score();
			if (!a.server().isEmpty() && !servers.contains(a.server())) servers.add(a.server());
			for (Metric m : metrics(a.detail())) {
				String key = m.name().toLowerCase(Locale.ROOT);
				double[] span = spans.computeIfAbsent(key, k -> new double[]{m.value(), m.value()});
				span[0] = Math.min(span[0], m.value());
				span[1] = Math.max(span[1], m.value());
				units.putIfAbsent(key, m.unit());
				names.putIfAbsent(key, m.name());
			}
		}
		// The player's own measures first, the telling one leading, as the evidence gives them; then the sample's (ping, TPS).
		List<Range> ranges = new ArrayList<>(), context = new ArrayList<>();
		for (var e : spans.entrySet()) {
			Range r = new Range(names.get(e.getKey()), e.getValue()[0], e.getValue()[1], units.get(e.getKey()));
			(CONTEXT.contains(e.getKey()) ? context : ranges).add(r);
		}
		Range headline = ranges.isEmpty() ? null : ranges.getFirst();
		ranges.addAll(context);
		return new Check(newest.check(), alerts.size(), Math.round(score * 100) / 100.0, headline, List.copyOf(ranges), List.copyOf(servers), newest.at(), newest.detail());
	}

	/** The checks as one line, "Click rate ×12, Reach ×3", the most alerts first; "" when there are none. */
	public static String line(List<Check> checks) {
		return String.join(", ", checks.stream().map(Check::counted).toList());
	}

	/** How many alerts the checks hold between them. */
	public static int total(List<Check> checks) {
		return checks.stream().mapToInt(Check::count).sum();
	}

	/** A number as the desk writes a measure: "20", "14.5", "2.25". */
	static String number(double v) {
		if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
		String s = String.valueOf(Math.round(v * 100) / 100.0);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static double parse(String v) {
		Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(v);
		return m.find() ? Double.parseDouble(m.group()) : 0;
	}
}
