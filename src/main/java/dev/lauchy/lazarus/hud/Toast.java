package dev.lauchy.lazarus.hud;

import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * A short bottom-right note, e.g. what a keybind just switched. One at a time: it rises
 * into place (the brand's signature move, a short upward drift with a fade, over 240ms), a
 * hairline under it runs down the time left, then it fades away.
 */
public final class Toast {
	private static final long SHOW_MS = 2000, IN_MS = 240, OUT_MS = 240;
	/** How far the toast rises as it appears: two grid units. */
	private static final int RISE = 8;
	private static String message;
	private static int color;
	private static long shownAt;

	private Toast() {
	}

	public static void show(String text, int accent) {
		// A new toast replacing one still on screen swaps its text without rising in again.
		boolean showing = message != null && Util.getMillis() - shownAt < SHOW_MS - OUT_MS;
		message = text;
		color = accent;
		shownAt = showing ? Util.getMillis() - IN_MS : Util.getMillis();
	}

	public static void toggled(String feature, boolean on) {
		show(feature + (on ? " §aon" : " §coff"), on ? Theme.GOOD : Theme.BAD);
	}

	static void draw(GuiGraphicsExtractor g) {
		if (message == null) return;
		long age = Util.getMillis() - shownAt;
		if (age > SHOW_MS) {
			message = null;
			return;
		}
		float in = Anim.easeOut(age / (float) IN_MS);
		float out = age > SHOW_MS - OUT_MS ? 1f - (age - (SHOW_MS - OUT_MS)) / (float) OUT_MS : 1f;
		var font = Minecraft.getInstance().font;
		int w = font.width(message) + 18, h = 18;
		int x = g.guiWidth() - w - 6, y = g.guiHeight() - h - 6 + Math.round((1f - in) * RISE) - Math.round((1f - out) * 4);
		Draw.opacity = Math.min(in, out);
		try {
			Draw.round(g, x, y, w, h, Theme.PANEL);
			Draw.round(g, x + 6, y + 7, 4, 4, color);
			Draw.text(g, font, message, x + 14, y + 5, Theme.TEXT);
			float left = 1f - age / (float) SHOW_MS;
			Draw.rect(g, x + 1, y + h - 1, Math.round((w - 2) * left), 1, Draw.alpha(color, 0.6f));
		} finally {
			Draw.opacity = 1f;
		}
	}
}
