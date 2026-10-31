package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Chat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A server command sent quietly, with its answer caught from chat: the lines the server sends back
 * over the next few seconds that look like the answer (by the asker's test, or because they name the
 * target) are gathered, kept out of chat when asked, and handed over once they stop arriving. A player
 * talking is never the answer, so chat is never swallowed. One ask at a time: a new one hands the old
 * one what it has so far.
 */
final class Reply {
	/** How long the server has to start answering, how long a pause ends the answer, and how much is kept. */
	static final long WAIT_MS = 4000, SETTLE_MS = 700;
	private static final int MAX_LINES = 60;

	private static final class Ask {
		final String command, target;
		final boolean hide;
		final Predicate<String> ours;
		final Consumer<List<String>> done;
		final List<String> lines = new ArrayList<>();
		final long sentAt = Util.getMillis();
		long lastAt;

		Ask(String command, String target, boolean hide, Predicate<String> ours, Consumer<List<String>> done) {
			this.command = command;
			this.target = target.toLowerCase(Locale.ROOT);
			this.hide = hide;
			this.ours = ours;
			this.done = done;
		}

		/** Whether a server line belongs to this answer. */
		boolean wants(String text) {
			return ours.test(text) || text.toLowerCase(Locale.ROOT).contains(target);
		}

		boolean over(long now) {
			return lines.size() >= MAX_LINES || (lines.isEmpty() ? now - sentAt > WAIT_MS : now - lastAt > SETTLE_MS);
		}
	}

	private static Ask current;
	private static boolean ready;

	private Reply() {
	}

	/** Hooks chat once, the first time an ask is made. */
	private static void init() {
		if (ready) return;
		ready = true;
		Chat.hide(line -> {
			Ask a = current;
			if (a == null) return false;
			if (a.over(Util.getMillis())) return false;
			// The answer may come as one message per line or one message with line breaks.
			boolean ours = false;
			for (String part : line.text().split("\n")) {
				String text = part.trim();
				if (text.isEmpty()) continue;
				if (Chat.speaker(text) != null || !a.wants(text)) continue;
				a.lines.add(text);
				a.lastAt = Util.getMillis();
				ours = true;
			}
			return ours && a.hide;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Ask a = current;
			if (a != null && a.over(Util.getMillis())) finish();
		});
	}

	/**
	 * Sends {@code command} (without its slash) about {@code target} and later runs {@code done} on the
	 * game thread with the lines the server answered, which is empty when it said nothing in time.
	 * With {@code hide} the answer never shows in chat.
	 */
	static void ask(String command, String target, boolean hide, Consumer<List<String>> done) {
		ask(command, target, hide, text -> false, done);
	}

	/** As {@link #ask(String, String, boolean, Consumer)}, with {@code ours} saying which server lines are the answer besides those naming the target. */
	static void ask(String command, String target, boolean hide, Predicate<String> ours, Consumer<List<String>> done) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		init();
		finish();
		current = new Ask(command, target, hide, ours, done);
		mc.player.connection.sendCommand(command);
	}

	/** Whether an ask is still waiting on the server. */
	static boolean busy() {
		return current != null;
	}

	private static void finish() {
		Ask a = current;
		if (a == null) return;
		current = null;
		a.done.accept(List.copyOf(a.lines));
	}
}
