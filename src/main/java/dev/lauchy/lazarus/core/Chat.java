package dev.lauchy.lazarus.core;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server chat lines for features. Every listener sees every line, including ones another
 * feature hides, so hiding a message never breaks a tracker that reads it.
 */
public final class Chat {
	/** A received line: the original component and its plain text. */
	public record Line(Component component, String text) {
	}

	@FunctionalInterface
	public interface Listener {
		void onLine(Line line);
	}

	/** Something a player said, as Dragsim shows it: "[RANK] Name: text", "Name: text", or "Staff > [RANK] Name: text" in staff chat. */
	public record Speaker(String name, String rank, String message, boolean staffChat) {
	}

	/** "Staff > [ADMIN] Name: hi", "[MVP+] Name: hi", "Name: hi"; a rank is anything in the brackets. */
	private static final Pattern SPEAKER = Pattern.compile("^(Staff > )?(?:\\[([^\\]]{1,20})\\]\\s+)?(\\w{1,16}): (.*)$");

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final List<Predicate<Line>> HIDERS = new ArrayList<>();

	private Chat() {
	}

	public static void init() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay) return true;
			Line line = new Line(message, Text.plain(message));
			for (Listener l : LISTENERS) {
				try {
					l.onLine(line);
				} catch (RuntimeException e) {
					dev.lauchy.lazarus.Lazarus.LOG.error("Chat listener failed on '{}'", line.text, e);
				}
			}
			// Every hider sees every line too: a hider also keeps state (a panel, a tally), so one hiding a
			// line must never stop another from reading it.
			boolean hide = false;
			for (Predicate<Line> h : HIDERS) {
				try {
					hide |= h.test(line);
				} catch (RuntimeException e) {
					dev.lauchy.lazarus.Lazarus.LOG.error("Chat hider failed on '{}'", line.text, e);
				}
			}
			return !hide;
		});
	}

	public static void listen(Listener listener) {
		LISTENERS.add(listener);
	}

	/** Hide a line from chat when the predicate is true. Listeners still see it. */
	public static void hide(Predicate<Line> hider) {
		HIDERS.add(hider);
	}

	/** Who said a line and what, or null when it isn't shaped like a player's chat line. */
	public static Speaker speaker(String text) {
		Matcher m = SPEAKER.matcher(text.trim());
		if (!m.matches()) return null;
		return new Speaker(m.group(3), m.group(2) == null ? "" : m.group(2), m.group(4), m.group(1) != null);
	}

	/** Whether a line could have been typed by a player: player chat always has a colon. */
	public static boolean fromPlayer(String text) {
		return text.indexOf(':') >= 0;
	}
}
