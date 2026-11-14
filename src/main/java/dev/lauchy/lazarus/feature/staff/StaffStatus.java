package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the server's own lines say about you as staff, for the status panel:
 * <ul>
 * <li>"You are now vanished. Other players cannot see you." / "You are no longer vanished." — a {@link Vanished}, as is
 * "You are vanished (persisted from last session)." on arriving in a lobby;</li>
 * <li>"You are now nicked as [DRAGON] GhostRogue56! Use /nick reset to go back." — a {@link Nicked}, and a nick reset
 * a {@link Nicked} with no nick;</li>
 * <li>"You are now in the STAFF channel" — a {@link Channel}.</li>
 * </ul>
 * A player can't say any of these: each starts the line. No Minecraft types, so it's tested on its own.
 */
public final class StaffStatus {
	/** What one line says. */
	public sealed interface Change permits Vanished, Nicked, Channel {
	}

	/** Whether you're vanished now. */
	public record Vanished(boolean on) implements Change {
	}

	/** The nick you go by now and the rank it shows ("" for both when you've gone back to your own name). */
	public record Nicked(String nick, String rank) implements Change {
	}

	/** The chat channel what you type goes to now, as the server names it ("STAFF"). */
	public record Channel(String name) implements Change {
	}

	/** Vanishing, or arriving vanished: "You are vanished (persisted from last session)." */
	private static final Pattern VANISHED = Pattern.compile("^You are (?:now )?vanished\\b.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern VISIBLE = Pattern.compile("^You are no longer vanished\\b.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern NICKED = Pattern.compile("^You are now nicked as (?:\\[([^\\]]+)]\\s*)?(\\w{1,16})\\b.*", Pattern.CASE_INSENSITIVE);
	/** The server's words for going back to your own name aren't on record yet: any of the usual ones. */
	private static final Pattern UNNICKED = Pattern.compile("^(?:You are no longer nicked|You are now unnicked|(?:Your )?nick(?:name)? (?:has been |was )?(?:reset|removed|cleared))\\b.*",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CHANNEL = Pattern.compile("^You are now in the (\\w{1,20}) channel\\b.*", Pattern.CASE_INSENSITIVE);
	/** Chat stacking's " (x2)" after a repeated line. */
	private static final Pattern STACKED = Pattern.compile("\\s*\\(x\\d+\\)$");

	private StaffStatus() {
	}

	/** What a plain chat line says about you, or null when it says nothing the panel shows. */
	public static Change read(String line) {
		String l = STACKED.matcher(line.trim()).replaceFirst("");
		Matcher m;
		if (VANISHED.matcher(l).matches()) return new Vanished(true);
		if (VISIBLE.matcher(l).matches()) return new Vanished(false);
		if ((m = NICKED.matcher(l)).matches()) return new Nicked(m.group(2), m.group(1) == null ? "" : m.group(1).trim().toUpperCase(Locale.ROOT));
		if (UNNICKED.matcher(l).matches()) return new Nicked("", "");
		if ((m = CHANNEL.matcher(l)).matches()) return new Channel(m.group(1).toUpperCase(Locale.ROOT));
		return null;
	}

	/** One server's line of {@code /glist all}: "[end] (91): Cerberus_VN, Leenoox, …". */
	private static final Pattern GLIST = Pattern.compile("^\\[([\\w-]{1,20})]\\s*\\(\\d+\\):\\s*(.*)$");
	/** The line that ends {@code /glist all}: "165 players are currently connected to the proxy." */
	private static final Pattern GLIST_END = Pattern.compile("^\\d+ players? (?:is|are) currently connected to the proxy\\.?$");

	/** A name in {@code /glist all}, with any mark after it ("Quan976 ⚠"). */
	private static final Pattern MARKED_NAME = Pattern.compile("^(\\w{1,16})(?:\\s+[^\\w\\s].*)?$");

	/** The players {@code /glist all} names on one server. */
	public record Server(String name, List<String> players) {
	}

	/** The server and players one line of {@code /glist all} names, or null when it isn't one of its lines. */
	public static Server glist(String line) {
		Matcher m = GLIST.matcher(line.trim());
		if (!m.matches()) return null;
		List<String> players = new ArrayList<>();
		for (String p : m.group(2).split(",")) {
			Matcher n = MARKED_NAME.matcher(p.trim());
			if (n.matches()) players.add(n.group(1));
		}
		return new Server(m.group(1).toLowerCase(Locale.ROOT), List.copyOf(players));
	}

	/** Whether a line is any of {@code /glist all}'s answer, a server's or the total, so a quiet ask keeps it out of chat. */
	public static boolean isGlist(String line) {
		return GLIST.matcher(line.trim()).matches() || GLIST_END.matcher(line.trim()).matches();
	}

	/** A tab list row that is a player: tags, then the name, then nothing or marks ("⚠", "✎"), never more words. */
	private static final Pattern ROW = Pattern.compile("^(?:\\[[^\\]]{1,20}]\\s*)*(\\w{1,16})(?:\\s+[^\\w\\s].*)?$");

	/**
	 * The player a tab list row shows ("[V] [ADMIN] Lauchy" gives Lauchy, "petseklapsas ✎" petseklapsas), or null for
	 * a row that isn't one ("Area: The End", "Strength 571", "Active Boosters (1)"). With many online the server pages
	 * the list itself, its rows not the players' own entries, so a row's text is how to tell who it is.
	 */
	public static String nameIn(String shown) {
		Matcher m = ROW.matcher(shown.trim());
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * Whether a tab list entry showing a player is a row of the server's pages, which shows the [V] when they're
	 * vanished: an entry for a profile other than the player it shows ({@code otherProfile}), or the player shown by
	 * more than one entry at once (their row and their own entry, which never carries the [V]).
	 */
	public static boolean pageRow(boolean otherProfile, int entriesForPlayer) {
		return otherProfile || entriesForPlayer > 1;
	}

	/** Whether a tab list name (colour codes left out) has the vanished tag in front: "[V] [ADMIN] Lauchy". */
	public static boolean vanishTag(String shown) {
		return shown.trim().startsWith("[V]");
	}

	/** How long before arriving in a lobby a line saying you're vanished still speaks for it. */
	public static final long ARRIVAL_MS = 5000;

	/**
	 * Whether you're vanished on arriving in a lobby at {@code now}: no, unless the server said you are (arriving, "You
	 * are vanished (persisted from last session)", comes just before the game notices) in the moments before;
	 * {@code saidAt} is when a line last said you're vanished, 0 for never.
	 */
	public static boolean vanishedOnArrival(long saidAt, long now) {
		return saidAt > 0 && now - saidAt <= ARRIVAL_MS;
	}

	/**
	 * Staff online: seen on the tab list lately, or named by {@code /glist all} on any server. With many online the tab
	 * list shows them a page at a time, and a name's rank tag (and vanished tag) only while its page is up, so a staff
	 * member's vanish is as their page last showed it, and someone counts until they've been gone a while: from the tab
	 * list for {@link #GONE_MS}, from the last {@code /glist all} for {@link #LISTED_MS}.
	 */
	public static final class Seen {
		/** How long someone gone from the tab list still counts. */
		public static final long GONE_MS = 30_000;
		/** How long someone {@code /glist all} named still counts without being named again (it's asked once a minute). */
		public static final long LISTED_MS = 150_000;

		/**
		 * One staff member: their real name, rank, the nick they show as ("" none), whether vanished when last shown,
		 * when last on the tab list (0 never), the server {@code /glist all} last named them on ("" never), and when.
		 */
		public record Member(String name, String rank, String as, boolean vanished, long at, String server, long listedAt) {
			/** Whether they're on your tab list now, rather than only named on a server by {@code /glist all}. */
			public boolean here(long now) {
				return at > 0 && now - at <= GONE_MS;
			}
		}

		private final java.util.Map<String, Member> seen = new java.util.HashMap<>();

		/** Notes a staff member on the tab list at {@code now}; {@code vanished} is null when their page isn't up to say. */
		public void saw(String name, String rank, String as, Boolean vanished, long now) {
			String key = name.toLowerCase(Locale.ROOT);
			Member old = seen.get(key);
			seen.put(key, new Member(name, rank, as, vanished != null ? vanished : old != null && old.vanished(), now,
					old == null ? "" : old.server(), old == null ? 0 : old.listedAt()));
		}

		/** Notes a staff member {@code /glist all} named on {@code server} at {@code now}. */
		public void listed(String name, String rank, String as, String server, long now) {
			String key = name.toLowerCase(Locale.ROOT);
			Member old = seen.get(key);
			seen.put(key, old == null ? new Member(name, rank, as, false, 0, server, now) : new Member(old.name(), rank, old.as().isEmpty() ? as : old.as(),
					old.vanished(), old.at(), server, now));
		}

		/** Forgets everyone gone from the tab list and from {@code /glist all} a while at {@code now}. */
		public void forget(long now) {
			seen.values().removeIf(m -> (m.at() == 0 || now - m.at() > GONE_MS) && (m.listedAt() == 0 || now - m.listedAt() > LISTED_MS));
		}

		/** How many count now. */
		public int count() {
			return seen.size();
		}

		/** How many of them were vanished when last shown. */
		public int vanished() {
			return (int) seen.values().stream().filter(Member::vanished).count();
		}

		/** Everyone counted, the highest rank first, then by name. */
		public java.util.List<Member> members() {
			return seen.values().stream().sorted(java.util.Comparator.comparingInt((Member m) -> Roster.rankOrder(m.rank()))
					.thenComparing(m -> m.name().toLowerCase(Locale.ROOT))).toList();
		}

		/** The tab list forgotten (another server's is another set of staff); who {@code /glist all} named stands. */
		public void leftServer() {
			seen.values().removeIf(m -> m.listedAt() == 0);
			seen.replaceAll((k, m) -> new Member(m.name(), m.rank(), m.as(), m.vanished(), 0, m.server(), m.listedAt()));
		}
	}

	/** A channel as the panel says it: "STAFF" gives "Staff". */
	public static String channelName(String channel) {
		return channel.isEmpty() ? "" : channel.charAt(0) + channel.substring(1).toLowerCase(Locale.ROOT);
	}
}
