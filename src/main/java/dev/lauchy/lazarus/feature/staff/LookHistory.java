package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A player's punishment history at a glance: look at them and press the key, and the server's
 * {@code /history} answer comes back as one card with a Punish button, instead of loose lines. An
 * answer is kept for a while so pressing again on the same player is instant.
 */
public final class LookHistory extends Feature {
	static LookHistory INSTANCE;

	private record Cached(List<String> lines, long at) {
	}

	/** One punishment from the server's list: "#205 BAN name by Porier on Oct 28, 2026 02:15 - No reason specified (expired)". */
	public record Entry(int id, String kind, String by, String date, String reason, String state) {
		/** The card row: the kind in its colour, the date, the reason, and the state greyed. */
		String row() {
			String colour = switch (kind) {
				case "BAN" -> "§c";
				case "MUTE" -> "§e";
				case "KICK" -> "§6";
				case "WARN" -> "§5";
				default -> "§f";
			};
			return colour + kind + " §7" + date + " §f" + reason + (state.isEmpty() ? "" : " §8(" + state + ")") + " §8by " + by;
		}
	}

	private static final Pattern HEADER = Pattern.compile("^[^A-Za-z]*History of (\\w{1,16})\\s*\\(page (\\d+)\\)[^A-Za-z]*$");
	private static final Pattern ENTRY = Pattern.compile("^#(\\d+)\\s+(\\w+)\\s+\\w{1,16}\\s+by\\s+(\\w{1,16})\\s+on\\s+(.+?)\\s+-\\s+(.*?)(?:\\s+\\(([^()]*)\\))?$");

	private final IntSetting keep = add(new IntSetting("keep", "Remember for", "How long an answer is kept before the server is asked again.", 10, 1, 60, "min"));

	private final Map<String, Cached> cache = new HashMap<>();

	public LookHistory() {
		super("lookHistory", "History on Look", "A key shows the punishment history of the player you're looking at, as one card.", Category.STAFF, true);
		INSTANCE = this;
	}

	/** Whether a server line is part of a history answer: its header, an entry, or a page footer. */
	static boolean looksLike(String text) {
		return HEADER.matcher(text).matches() || ENTRY.matcher(text).matches() || text.startsWith("#");
	}

	/**
	 * A player's history for another feature: the remembered answer when it's fresh, else the server's
	 * {@code /history} asked quietly; {@code then} gets the lines (empty when the server stayed quiet).
	 */
	public static void lookup(String player, java.util.function.Consumer<List<String>> then) {
		LookHistory f = INSTANCE;
		String key = player.toLowerCase(Locale.ROOT);
		Cached c = f == null ? null : f.cache.get(key);
		long keep = f == null ? 10 : f.keep.get();
		if (c != null && Util.getMillis() - c.at() < keep * 60_000L) {
			then.accept(c.lines());
			return;
		}
		Reply.ask("history " + player, player, true, LookHistory::looksLike, lines -> {
			if (f != null && !lines.isEmpty()) f.cache.put(key, new Cached(lines, Util.getMillis()));
			then.accept(lines);
		});
	}

	@Override
	public void init() {
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("history")
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					show(StringArgumentType.getString(c, "player"));
					return 1;
				}))));
	}

	/** The player's history, from the last answer when it's recent enough, else asked for again. */
	private void show(String player) {
		Cached c = cache.get(player.toLowerCase(Locale.ROOT));
		if (c != null && Util.getMillis() - c.at() < keep.get() * 60_000L) {
			card(player, c.lines(), true).send();
			return;
		}
		if (Reply.busy()) {
			Toast.show("Still waiting on the server", Theme.MUTED);
			return;
		}
		Toast.show("§7History: §f" + player, Theme.ACCENT);
		Reply.ask("history " + player, player, true, LookHistory::looksLike, lines -> {
			if (!lines.isEmpty()) cache.put(player.toLowerCase(Locale.ROOT), new Cached(lines, Util.getMillis()));
			card(player, lines, false).send();
		});
	}

	/** The punishments in the server's answer, in the order sent; empty when it isn't shaped like a history list. */
	public static List<Entry> parse(List<String> lines) {
		List<Entry> out = new ArrayList<>();
		for (String l : lines) {
			Matcher m = ENTRY.matcher(l.trim());
			if (!m.matches()) continue;
			out.add(new Entry(Integer.parseInt(m.group(1)), m.group(2).toUpperCase(Locale.ROOT), m.group(3), m.group(4), m.group(5), m.group(6) == null ? "" : m.group(6)));
		}
		return out;
	}

	/** Whether the answer has the "History of" header, so an empty list means a clean record rather than no answer. */
	static boolean isHistory(List<String> lines) {
		for (String l : lines) if (HEADER.matcher(l.trim()).matches()) return true;
		return false;
	}

	/** "2 bans · 4 mutes · 1 kick", the kinds in the order they first appear. */
	static String summary(List<Entry> entries) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Entry e : entries) counts.merge(e.kind(), 1, Integer::sum);
		List<String> parts = new ArrayList<>();
		counts.forEach((kind, n) -> parts.add(n + " " + kind.toLowerCase(Locale.ROOT) + (n == 1 ? "" : "s")));
		return String.join(" · ", parts);
	}

	/**
	 * The answer as one card: a count of each kind, then every punishment as a row (kind, date, reason,
	 * whether it expired or was undone), then Punish, Profile and Alts buttons. An answer that isn't a
	 * history list is shown as the server sent it.
	 */
	static Message card(String player, List<String> lines, boolean remembered) {
		Message m = Message.lazarus().heading("History · " + player).muted(remembered ? "  from a moment ago" : "");
		List<Entry> entries = parse(lines);
		if (lines.isEmpty()) m.line().warning("The server didn't answer in time.").muted("  It may show history another way.");
		else if (!entries.isEmpty()) {
			m.line().info(summary(entries));
			List<String> rows = new ArrayList<>();
			for (Entry e : entries) rows.add(e.row());
			m.rows(rows);
		} else if (isHistory(lines)) m.line().success("Clean record.");
		else for (String l : lines) m.line().info(l);
		m.line().danger("Staff", "Click for staff options on " + player, "/lazarus staff " + player).info("  ")
				.player(player).info("  ")
				.command("Alts", "Click to see " + player + "'s alts", "/lazarus alts " + player);
		return m;
	}
}
