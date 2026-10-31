package dev.lauchy.lazarus.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Small drawing helpers on top of the vanilla GUI extractor. Everything drawn through
 * here is multiplied by {@link #opacity}, which is how whole screens and readouts fade.
 */
public final class Draw {
	/** Applied to every colour drawn through these helpers; set it, draw, then put it back. */
	public static float opacity = 1f;
	/** The lines of the last {@link #panel} drawn, so the HUD can fade a readout out after it ends. */
	public static List<String> lastPanel;

	private Draw() {
	}

	/** {@code color} with the current {@link #opacity} applied. */
	public static int fade(int color) {
		if (opacity >= 1f) return color;
		return (Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f)) << 24) | (color & 0xFFFFFF);
	}

	public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		g.fill(x, y, x + w, y + h, fade(color));
	}

	/** A rectangle with its corner pixels left out, which reads as softly rounded at GUI scale. */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w < 3 || h < 3) {
			rect(g, x, y, w, h, color);
			return;
		}
		int c = fade(color);
		g.fill(x + 1, y, x + w - 1, y + h, c);
		g.fill(x, y + 1, x + 1, y + h - 1, c);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
	}

	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		int c = fade(color);
		g.fill(x + 1, y, x + w - 1, y + 1, c);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
		g.fill(x, y + 1, x + 1, y + h - 1, c);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
	}

	/**
	 * Hairlines between and around 18-pixel inventory slots, like a real inventory. (x, y)
	 * is where the first item draws; the lines sit in the gaps one pixel outside each item.
	 */
	public static void slotGrid(GuiGraphicsExtractor g, int x, int y, int cols, int rows, int color) {
		int w = cols * 18, h = rows * 18;
		for (int c = 0; c <= cols; c++) rect(g, x - 1 + c * 18, y - 1, 1, h + 1, color);
		for (int r = 0; r <= rows; r++) rect(g, x - 1, y - 1 + r * 18, w + 1, 1, color);
	}

	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		int c = fade(color);
		if ((c >>> 24) > 8) g.text(font, s, x, y, c, true); // nearly clear text would flash fully opaque
	}

	public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
		text(g, font, s, right - font.width(s), y, color);
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
		text(g, font, s, cx - font.width(s) / 2, y, color);
	}

	/** An on/off pill switch, 18x9; {@code t} runs from 0 (off) to 1 (on) so the knob can slide. */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, float t) {
		round(g, x, y, 18, 9, Anim.lerpColor(Theme.OFF, Theme.ACCENT, t));
		int kx = Math.round(x + 1 + 9 * t);
		round(g, kx, y + 1, 8, 7, Anim.lerpColor(0xFFB8B8C4, 0xFFFFFFFF, t));
	}

	public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
		toggle(g, x, y, on ? 1f : 0f);
	}

	/**
	 * A HUD panel of text lines (colour codes allowed) on a soft translucent backdrop.
	 * @return {width, height}
	 */
	public static int[] panel(GuiGraphicsExtractor g, Font font, List<String> lines) {
		int w = 0;
		for (String l : lines) {
			int tab = l.indexOf('\t');
			// A tab splits a row: the part after it sits against the right edge, a gap clear of the left part.
			w = Math.max(w, tab < 0 ? font.width(l) : font.width(l.substring(0, tab)) + TAB_GAP + font.width(l.substring(tab + 1)));
		}
		int h = lines.size() * (font.lineHeight + 1) + 7;
		w += 10;
		if (g != null) {
			lastPanel = lines;
			round(g, 0, 0, w, h, Theme.HUD_BG);
			int y = 4;
			for (String l : lines) {
				int tab = l.indexOf('\t');
				if (tab < 0) text(g, font, l, 5, y, Theme.TEXT);
				else {
					text(g, font, l.substring(0, tab), 5, y, Theme.TEXT);
					String right = l.substring(tab + 1);
					text(g, font, right, w - 5 - font.width(right), y, Theme.TEXT);
				}
				y += font.lineHeight + 1;
			}
		}
		return new int[]{w, h};
	}

	/** The least room between a row's left part and its right-aligned part, in pixels. */
	private static final int TAB_GAP = 12;

	/** {@code lines} with any wider than {@code width} pixels wrapped at spaces onto indented lines that keep their colour. */
	public static List<String> wrap(Font font, List<String> lines, int width) {
		List<String> out = new ArrayList<>();
		for (String l : lines) out.addAll(wrap(font::width, l, width));
		return out;
	}

	/**
	 * One line wrapped at spaces so no piece is wider than {@code width} by {@code measure}; a word too
	 * wide on its own is cut. Each later piece is indented two spaces and starts with the colour and
	 * bold in force where the line broke, so a wrapped name stays in its colour.
	 */
	public static List<String> wrap(ToIntFunction<String> measure, String line, int width) {
		List<String> out = new ArrayList<>();
		if (measure.applyAsInt(line) <= width) {
			out.add(line);
			return out;
		}
		String indent = "  ";
		String current = "";
		boolean fresh = true; // nothing but the indent and codes on this line yet
		for (String word : line.split(" ")) {
			String rest = word;
			while (true) {
				String tryLine = fresh ? current + rest : current + " " + rest;
				if (measure.applyAsInt(tryLine) <= width) {
					current = tryLine;
					fresh = false;
					break;
				}
				if (!fresh) {
					out.add(current);
					current = indent + codesInForce(current);
					fresh = true;
					continue;
				}
				// A single word wider than the panel: cut it where it fits.
				int cut = rest.length();
				while (cut > 1 && measure.applyAsInt(current + rest.substring(0, cut)) > width) cut--;
				out.add(current + rest.substring(0, cut));
				current = indent + codesInForce(current + rest.substring(0, cut));
				rest = rest.substring(cut);
				if (rest.isEmpty()) break;
			}
		}
		if (!fresh) out.add(current);
		return out;
	}

	/** The § colour and bold codes a line ends under, as text to start the next line with. */
	static String codesInForce(String s) {
		String colour = "";
		boolean bold = false;
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) != '§') continue;
			char c = Character.toLowerCase(s.charAt(++i));
			if (c == 'l') bold = true;
			else if (c == 'r') {
				colour = "";
				bold = false;
			} else if ("0123456789abcdef".indexOf(c) >= 0) {
				colour = "§" + c;
				bold = false;
			}
		}
		return colour + (bold ? "§l" : "");
	}

	public static int alpha(int color, float a) {
		return ((int) (Math.clamp(a, 0f, 1f) * 255) << 24) | (color & 0xFFFFFF);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
