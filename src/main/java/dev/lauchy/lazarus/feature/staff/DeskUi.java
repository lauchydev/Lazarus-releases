package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The staff desk's look (docs/dragsim/desk-design): a dark zinc ground, cards with a hairline border, small
 * badges, and buttons in a few kinds — solid red for the one destructive action, red outline for the lesser,
 * plain for the rest, dashed for the rarely used, purple for travel. Colours and sizes live here so the desk
 * and its ban dialog look like one thing.
 */
final class DeskUi {
	static final int CARD = 0xFF111114, BORDER = 0xFF27272A, BORDER_SOFT = 0xFF1F1F23, FIELD = 0xFF18181B, INSET = 0xFF0B0B0E;
	static final int TEXT = 0xFFFAFAFA, SUB = 0xFFD4D4D8, MUTED = 0xFFA1A1AA, FAINT = 0xFF71717A;
	static final int RED = 0xFFB91C1C, RED_HOT = 0xFFDC2626, RED_EDGE = 0xFF7F1D1D, RED_BG = 0xFF1F0F10, RED_TEXT = 0xFFFCA5A5, RED_SOFT = 0xFFFECACA;
	static final int VIOLET_BG = 0xFF2A1D42, VIOLET_EDGE = 0xFF3B2A5C, VIOLET_TEXT = 0xFFE9D5FF;
	/** A button's and a field's height. */
	static final int BTN_H = 14;

	/** How a button looks. */
	enum Kind {
		/** The one destructive action: solid red. */
		DANGER,
		/** A lesser punishment: red outline. */
		DANGER_OUTLINE,
		/** Most things. */
		PLAIN,
		/** Rarely used: a dashed outline, quiet text. */
		DASHED,
		/** Going somewhere: solid purple. */
		ACCENT,
		/** A second way of going somewhere: purple outline. */
		ACCENT_OUTLINE
	}

	private DeskUi() {
	}

	/** A card tinted to stand out (red for danger): fill and edge given. */
	static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
		Draw.round(g, x, y, w, h, fill);
		Draw.outline(g, x, y, w, h, edge);
	}

	/**
	 * A button of {@code kind}: label centred (or left with a hint on the right when {@code hint} is given),
	 * {@code h} hover from 0 to 1, faded when {@code enabled} is false.
	 */
	static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, String hint, Kind kind, float h, boolean enabled) {
		float before = Draw.opacity;
		if (!enabled) Draw.opacity *= 0.4f;
		int text;
		switch (kind) {
			case DANGER -> {
				Draw.round(g, x, y, w, BTN_H, Anim.lerpColor(RED, RED_HOT, h));
				text = 0xFFFFFFFF;
			}
			case DANGER_OUTLINE -> {
				Draw.round(g, x, y, w, BTN_H, Anim.lerpColor(RED_BG, 0xFF2A1214, h));
				Draw.outline(g, x, y, w, BTN_H, RED_EDGE);
				text = RED_SOFT;
			}
			case ACCENT -> {
				Draw.round(g, x, y, w, BTN_H, Anim.lerpColor(VIOLET_BG, 0xFF3A2860, h));
				text = VIOLET_TEXT;
			}
			case ACCENT_OUTLINE -> {
				Draw.round(g, x, y, w, BTN_H, Draw.alpha(VIOLET_BG, 0.5f * h));
				Draw.outline(g, x, y, w, BTN_H, VIOLET_EDGE);
				text = VIOLET_TEXT;
			}
			case DASHED -> {
				if (h > 0) Draw.round(g, x, y, w, BTN_H, Draw.alpha(0xFFFFFFFF, 0.04f * h));
				dashed(g, x, y, w, BTN_H, 0xFF3F3F46);
				text = Anim.lerpColor(MUTED, TEXT, h);
			}
			default -> {
				Draw.round(g, x, y, w, BTN_H, Anim.lerpColor(FIELD, 0xFF232328, h));
				Draw.outline(g, x, y, w, BTN_H, BORDER);
				text = TEXT;
			}
		}
		if (hint.isEmpty()) {
			textCentered(g, font, label, x + w / 2, y + 3, text);
		} else {
			text(g, font, label, x + 6, y + 3, text);
			text(g, font, hint, x + w - 6 - width(font, hint), y + 3, FAINT);
		}
		Draw.opacity = before;
	}

	/** A dashed one-pixel border. */
	static void dashed(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
		for (int i = 1; i < w - 1; i += 4) {
			Draw.rect(g, x + i, y, Math.min(2, w - 1 - i), 1, colour);
			Draw.rect(g, x + i, y + h - 1, Math.min(2, w - 1 - i), 1, colour);
		}
		for (int i = 1; i < h - 1; i += 4) {
			Draw.rect(g, x, y + i, 1, Math.min(2, h - 1 - i), colour);
			Draw.rect(g, x + w - 1, y + i, 1, Math.min(2, h - 1 - i), colour);
		}
	}

	/** Text cut to {@code w} pixels, with an ellipsis when it had to be cut. */
	static String fit(Font font, String text, int w) {
		if (width(font, text) <= w) return text;
		String t = text;
		while (t.length() > 1 && width(font, t + "…") > w) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	/** Desk text, in Inter ({@link DeskFont}) with no drop shadow, faded with {@link Draw#opacity}. */
	static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int colour) {
		int c = Draw.fade(colour);
		if ((c >>> 24) > 8) g.text(font, DeskFont.of(s), x, y, c, false);
	}

	static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int colour) {
		int c = Draw.fade(colour);
		if ((c >>> 24) > 8) g.text(font, DeskFont.of(s), x, y, c, false);
	}

	/** A line already split from desk text (so already in Inter). */
	static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int colour) {
		int c = Draw.fade(colour);
		if ((c >>> 24) > 8) g.text(font, s, x, y, c, false);
	}

	static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int colour) {
		text(g, font, s, cx - width(font, s) / 2, y, colour);
	}

	/** How wide desk text draws. */
	static int width(Font font, String s) {
		return font.width(DeskFont.of(s));
	}

	static int width(Font font, Component s) {
		return font.width(DeskFont.of(s));
	}

	static int width(Font font, FormattedCharSequence s) {
		return font.width(s);
	}
}
