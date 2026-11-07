package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Chat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
	private static final int MAX_LINES = 200;

	private static final class Ask {
		final String command, target;
		final boolean hide, coloured;
		final Predicate<String> ours;
		final Consumer<List<String>> done;
		final long wait;
		final List<String> lines = new ArrayList<>();
		final long sentAt = Util.getMillis();
		long lastAt;

		Ask(String command, String target, boolean hide, boolean coloured, Predicate<String> ours, long wait, Consumer<List<String>> done) {
			this.command = command;
			this.target = target.toLowerCase(Locale.ROOT);
			this.hide = hide;
			this.coloured = coloured;
			this.ours = ours;
			this.wait = wait;
			this.done = done;
		}

		/** Whether a server line belongs to this answer. */
		boolean wants(String text) {
			return ours.test(text) || text.toLowerCase(Locale.ROOT).contains(target);
		}

		boolean over(long now) {
			return lines.size() >= MAX_LINES || (lines.isEmpty() ? now - sentAt > wait : now - lastAt > SETTLE_MS);
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
			for (String part : (a.coloured ? legacy(line.component()) : line.text()).split("\n")) {
				String text = a.coloured ? AltPages.plain(part).trim() : part.trim();
				if (text.isEmpty()) continue;
				// A player talking is never the answer, unless the asker knows the line's exact shape ("Rank: DRAGON" reads like chat).
				if (!a.wants(text) || Chat.speaker(text) != null && !a.ours.test(text)) continue;
				a.lines.add(a.coloured ? part.trim() : text);
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
		ask(command, target, hide, ours, WAIT_MS, done);
	}

	/** As above, waiting up to {@code waitMs} for the answer to start: some commands answer late (/lookup ~4 s, /auditlog ~30 s). */
	static void ask(String command, String target, boolean hide, Predicate<String> ours, long waitMs, Consumer<List<String>> done) {
		ask(command, target, hide, false, ours, waitMs, done);
	}

	/** As {@link #ask(String, String, boolean, Predicate, Consumer)}, the lines keeping their § colour codes; {@code ours} still sees them plain. */
	static void askColoured(String command, String target, boolean hide, Predicate<String> ours, Consumer<List<String>> done) {
		ask(command, target, hide, true, ours, WAIT_MS, done);
	}

	private static void ask(String command, String target, boolean hide, boolean coloured, Predicate<String> ours, long waitMs, Consumer<List<String>> done) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		init();
		finish();
		current = new Ask(command, target, hide, coloured, ours, waitMs, done);
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

	/** The text as § codes and words, whether the server styled it or wrote the codes in, so colours survive. */
	private static String legacy(Component text) {
		StringBuilder out = new StringBuilder();
		text.visit((style, part) -> {
			out.append(codes(style)).append(part);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private static String codes(Style style) {
		StringBuilder c = new StringBuilder();
		ChatFormatting f = style.getColor() == null ? null : ChatFormatting.getByName(style.getColor().serialize());
		if (f != null) c.append('§').append(f.getChar());
		else if (style.getColor() != null) c.append("§#").append(String.format("%06x", style.getColor().getValue() & 0xFFFFFF));
		if (style.isBold()) c.append("§l");
		if (style.isItalic()) c.append("§o");
		if (style.isUnderlined()) c.append("§n");
		if (style.isStrikethrough()) c.append("§m");
		if (style.isObfuscated()) c.append("§k");
		return c.toString();
	}
}
