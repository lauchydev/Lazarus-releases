package dev.lauchy.lazarus.feature.profile;

import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What came of the last staff action on a profile: the server's reply to its command,
 * caught from chat for a few seconds after sending. {@link ProfileScreen} shows it as a
 * card; with the profile closed (a tunnel moves you, say) the reply comes up as a toast.
 */
final class StaffOutcome {
	/** How long the server has to answer, and how long the card stays once it has. */
	static final long WAIT_MS = 4000, SHOW_MS = 4500;
	private static final int MAX_LINES = 4;
	private static final Pattern FAILED = Pattern.compile(
			"(?i)\\b(not|no|invalid|unknown|cannot|can't|couldn't|failed|error|offline|usage|permission|already)\\b");

	record Shown(String label, String detail, List<String> lines, long sentAt, long lastAt, boolean waits, boolean bad) {
		boolean waiting() {
			return waits && lines.isEmpty() && Util.getMillis() - sentAt < WAIT_MS;
		}

		boolean failed() {
			if (!waits) return bad;
			if (lines.isEmpty()) return !waiting();
			for (String l : lines) if (FAILED.matcher(l).find()) return true;
			return false;
		}

		int color() {
			return waiting() ? Theme.ACCENT : failed() ? Theme.BAD : Theme.GOOD;
		}

		/** When the card should be fully gone. */
		long until() {
			long settled = waits ? (lines.isEmpty() ? sentAt + WAIT_MS : lastAt) : sentAt;
			return settled + SHOW_MS;
		}
	}

	private static Shown current;
	private static String target = "";

	private StaffOutcome() {
	}

	static void init() {
		Chat.listen(line -> {
			Shown s = current;
			if (s == null || !s.waits || Util.getMillis() - s.sentAt > WAIT_MS || s.lines.size() >= MAX_LINES) return;
			String text = line.text().trim();
			if (text.isEmpty()) return;
			// Player chat landing in the window isn't the reply, unless it's about the target.
			if (Chat.fromPlayer(text) && !text.toLowerCase(Locale.ROOT).contains(target)) return;
			List<String> lines = new ArrayList<>(s.lines);
			lines.add(text);
			current = new Shown(s.label, s.detail, List.copyOf(lines), s.sentAt, Util.getMillis(), true, false);
			if (!(Minecraft.getInstance().screen instanceof ProfileScreen)) {
				Toast.show("§7" + s.label + ": §f" + text, current.color());
			}
		});
	}

	/** A command was sent for {@code player}; its reply is caught from chat. */
	static void sent(String label, String command, String player) {
		target = player.toLowerCase(Locale.ROOT);
		long now = Util.getMillis();
		current = new Shown(label, "/" + command, List.of(), now, now, true, false);
	}

	/** An action done here with nothing to wait for, such as a copy. */
	static void done(String label, String detail) {
		long now = Util.getMillis();
		current = new Shown(label, detail, List.of(), now, now, false, false);
	}

	/** An action that couldn't be sent, and why. */
	static void failed(String label, String why) {
		long now = Util.getMillis();
		current = new Shown(label, "", List.of(why), now, now, false, true);
	}

	/** The outcome to show now, or null once it has faded. */
	static Shown shown() {
		Shown s = current;
		return s != null && Util.getMillis() < s.until() ? s : null;
	}
}
