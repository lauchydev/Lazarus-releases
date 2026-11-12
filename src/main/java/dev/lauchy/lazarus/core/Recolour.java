package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restyling words inside a chat line the server sent, keeping every other style, click and hover as it was. */
public final class Recolour {
	private Recolour() {
	}

	/**
	 * The same message with each match of {@code words} after the first colon (so a sender's own name
	 * is never touched) restyled by {@code style}, which gets the matched word and its current style.
	 */
	public static Component afterColon(Component message, Pattern words, Function<String, Function<Style, Style>> style) {
		return words(message, words, style, true);
	}

	/** The same message with each match of {@code words} anywhere in it restyled by {@code style}. */
	public static Component everywhere(Component message, Pattern words, Function<String, Function<Style, Style>> style) {
		return words(message, words, style, false);
	}

	private static Component words(Component message, Pattern words, Function<String, Function<Style, Style>> style, boolean afterColon) {
		MutableComponent out = Component.empty();
		boolean[] pastColon = {!afterColon};
		message.visit((s, text) -> {
			int from = 0;
			if (!pastColon[0]) {
				int c = text.indexOf(':');
				if (c < 0) {
					out.append(Component.literal(text).withStyle(s));
					return Optional.empty();
				}
				pastColon[0] = true;
				from = c + 1;
				out.append(Component.literal(text.substring(0, from)).withStyle(s));
			}
			Matcher m = words.matcher(text);
			m.region(from, text.length());
			int at = from;
			while (m.find()) {
				if (m.start() > at) out.append(Component.literal(text.substring(at, m.start())).withStyle(s));
				out.append(Component.literal(m.group()).withStyle(style.apply(m.group()).apply(s)));
				at = m.end();
			}
			if (at < text.length()) out.append(Component.literal(text.substring(at)).withStyle(s));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/**
	 * The same message with the sender's name (the first whole-word {@code name} before the first colon, any
	 * case) replaced by what {@code replace} makes of it, given the name as the server styled it. The name is
	 * looked for in the line's text as a whole, so a gradient name the server sends one letter at a time is
	 * found too, and handed over with each letter's own style. The message comes back untouched when the
	 * name isn't there.
	 */
	public static Component name(Component message, String name, UnaryOperator<Component> replace) {
		List<String> texts = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		message.visit((s, text) -> {
			texts.add(text);
			styles.add(s);
			return Optional.empty();
		}, Style.EMPTY);
		String all = String.join("", texts);
		int colon = all.indexOf(':');
		int at = wholeWord(all.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT), colon < 0 ? all.length() : colon);
		if (at < 0) return message;
		int end = at + name.length();
		MutableComponent out = Component.empty(), found = Component.empty(), after = Component.empty();
		// The name's pieces: one for a plainly styled name, one per letter for a gradient one, each with its own style.
		List<Component> namePieces = new ArrayList<>();
		int pos = 0;
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			Style s = styles.get(i);
			int from = pos, to = pos + text.length();
			pos = to;
			if (text.isEmpty()) continue;
			if (from < at) out.append(Component.literal(text.substring(0, Math.min(to, at) - from)).withStyle(s));
			int n0 = Math.max(from, at), n1 = Math.min(to, end);
			if (n1 > n0) namePieces.add(Component.literal(text.substring(n0 - from, n1 - from)).withStyle(s));
			if (to > end) after.append(Component.literal(text.substring(Math.max(from, end) - from)).withStyle(s));
		}
		for (Component piece : namePieces) found.append(piece);
		out.append(replace.apply(namePieces.size() == 1 ? namePieces.getFirst() : found));
		if (!after.getSiblings().isEmpty()) out.append(after);
		return out;
	}

	/** The least brightness (0-255, by eye) text keeps on a dark tooltip: Minecraft's own dark grey, §8. */
	static final int READABLE = 0x55;

	/**
	 * The same line with any text too dark to read on a tooltip (a black dye's name, "§0") lifted
	 * towards white until it is as bright as §8 grey, keeping its hue. The line comes back
	 * untouched when nothing in it is that dark.
	 */
	public static Component readable(Component line) {
		boolean[] dark = {false};
		line.visit((s, text) -> {
			if (!text.isEmpty() && (s.getColor() != null && readable(s.getColor().getValue()) != s.getColor().getValue() || text.contains("§0"))) dark[0] = true;
			return Optional.empty();
		}, Style.EMPTY);
		if (!dark[0]) return line;
		MutableComponent out = Component.empty();
		line.visit((s, text) -> {
			Style st = s.getColor() == null ? s : s.withColor(TextColor.fromRgb(readable(s.getColor().getValue())));
			out.append(Component.literal(text.replace("§0", "§8")).setStyle(st));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** An RGB colour lifted towards white until it is as bright as {@link #READABLE}; brighter ones are kept. */
	public static int readable(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		double bright = 0.299 * r + 0.587 * g + 0.114 * b;
		if (bright >= READABLE) return rgb & 0xFFFFFF;
		double t = (READABLE - bright) / (255 - bright);
		r += (int) Math.round((255 - r) * t);
		g += (int) Math.round((255 - g) * t);
		b += (int) Math.round((255 - b) * t);
		return r << 16 | g << 8 | b;
	}

	/** Where {@code word} sits in {@code text} as a whole word, starting before {@code end}, or -1. */
	private static int wholeWord(String text, String word, int end) {
		int from = 0;
		while (from < end) {
			int at = text.indexOf(word, from);
			if (at < 0 || at >= end) return -1;
			int after = at + word.length();
			boolean startOk = at == 0 || !wordChar(text.charAt(at - 1));
			boolean endOk = after >= text.length() || !wordChar(text.charAt(after));
			if (startOk && endOk) return at;
			from = at + 1;
		}
		return -1;
	}

	private static boolean wordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}
