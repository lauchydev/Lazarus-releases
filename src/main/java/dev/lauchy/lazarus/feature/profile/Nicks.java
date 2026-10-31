package dev.lauchy.lazarus.feature.profile;

import dev.lauchy.lazarus.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nicked staff, read from the server's {@code /nicklist}: "Online Nicknames (2)" then one
 * "RealName → nick (server)" line each. It's asked quietly when a profile opens with staff
 * tools, and the answer is kept out of chat.
 */
public final class Nicks {
	private static final long WAIT_MS = 3000;
	private static final Pattern HEADER = Pattern.compile("^Online Nicknames\\s*\\(\\d+\\)$");
	/** The server's answer when nobody is nicked: a list of nobody. */
	private static final Pattern NOBODY = Pattern.compile("^No online players are nicked\\.?$");
	private static final Pattern ENTRY = Pattern.compile("^(\\w{1,16})\\s*(?:→|->)\\s*(\\w{1,16})(?:\\s*\\(.*\\))?$");

	/** When the server last answered, with a list or with "nobody is nicked". */
	private static long answeredAt;
	/** Nick by lowercase real name, from the last answer. */
	private static final Map<String, String> nicks = new HashMap<>();
	/** Real name as written, by nick as written. */
	private static final Map<String, String> realNames = new HashMap<>();
	private static long askedAt;
	/** How long /l waits for the answer before looking a name up. */
	private static final long ANSWER_MS = 700;

	private Nicks() {
	}

	static void init() {
		Chat.hide(line -> {
			if (Util.getMillis() - askedAt > WAIT_MS) return false;
			// The answer may come as one message per line or as one message with line breaks.
			boolean ours = false;
			for (String part : line.text().split("\n")) {
				String text = part.trim();
				if (text.isEmpty()) continue;
				if (HEADER.matcher(text).matches() || NOBODY.matcher(text).matches()) {
					nicks.clear(); // a fresh list (or a list of nobody): nobody missing from it is nicked any more
					realNames.clear();
					answeredAt = Util.getMillis();
					ours = true;
					continue;
				}
				Matcher m = ENTRY.matcher(text);
				if (!m.matches()) return false;
				nicks.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2));
				realNames.put(m.group(2), m.group(1));
				ours = true;
			}
			return ours;
		});
	}

	/** Asks the server for the list again. */
	public static void refresh() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		askedAt = Util.getMillis();
		mc.player.connection.sendCommand("nicklist");
	}

	/** Asks for the list again and runs {@code then} on the game thread once the answer has had time to arrive. */
	public static void refresh(Runnable then) {
		refresh();
		Minecraft mc = Minecraft.getInstance();
		java.util.concurrent.CompletableFuture.runAsync(() -> {
		}, java.util.concurrent.CompletableFuture.delayedExecutor(ANSWER_MS, java.util.concurrent.TimeUnit.MILLISECONDS)).thenRun(() -> mc.execute(then));
	}

	/** Milliseconds since the list was last asked for, or a very long time when it never was. */
	public static long sinceAsked() {
		return askedAt == 0 ? Long.MAX_VALUE : Util.getMillis() - askedAt;
	}

	/** Whether the server has ever answered, with a list or with "nobody is nicked". */
	public static boolean answered() {
		return answeredAt != 0;
	}

	/** Every nicked player as "Real → nick", for a listing. */
	public static java.util.List<String> all() {
		java.util.List<String> out = new java.util.ArrayList<>();
		realNames.forEach((nick, real) -> out.add(real + " → " + nick));
		java.util.Collections.sort(out);
		return out;
	}

	/** The real name behind a nick, or null when nobody goes by it. */
	public static String realName(String nick) {
		for (var e : realNames.entrySet()) if (e.getKey().equalsIgnoreCase(nick)) return e.getValue();
		return null;
	}

	/** The player's nick, or null when they aren't nicked (as far as the last list said). */
	public static String of(String player) {
		return nicks.get(player.toLowerCase(Locale.ROOT));
	}
}
