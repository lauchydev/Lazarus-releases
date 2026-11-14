package dev.lauchy.lazarus.hud;

import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * A card in the top right corner for a moment worth marking, the way the game shows an advancement: a gold plus,
 * "Lazarus+" over one line saying what happened. It slides in from the edge with the game's own advancement
 * sound, stays five seconds and fades. Used once, when Lazarus+ arrives on this account.
 */
public final class Achievement {
	private static final long SHOW_MS = 5000, IN_MS = 240, OUT_MS = 240;
	/** The card's size, the game's own advancement card's. */
	private static final int W = 160, H = 32;
	private static String line;
	private static long shownAt;

	private Achievement() {
	}

	/** Shows the card for Lazarus+ arriving, with the game's advancement sound. */
	public static void plusUnlocked() {
		line = "Features unlocked";
		shownAt = Util.getMillis();
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f));
	}

	static void draw(GuiGraphicsExtractor g) {
		if (line == null) return;
		long age = Util.getMillis() - shownAt;
		if (age > SHOW_MS) {
			line = null;
			return;
		}
		float in = Anim.easeOut(age / (float) IN_MS);
		float out = age > SHOW_MS - OUT_MS ? 1f - (age - (SHOW_MS - OUT_MS)) / (float) OUT_MS : 1f;
		var font = Minecraft.getInstance().font;
		// It comes in from the right edge, as the game's own does.
		int x = g.guiWidth() - W - 6 + Math.round((1f - in) * (W + 6)), y = 6;
		Draw.opacity = Math.min(1f, out);
		try {
			Draw.round(g, x, y, W, H, Theme.PANEL);
			Draw.outline(g, x, y, W, H, Draw.alpha(Theme.HALO, 0.5f));
			// The gold plus, on the 16-unit grid.
			Draw.rect(g, x + 14, y + 9, 4, 14, Theme.HALO);
			Draw.rect(g, x + 9, y + 14, 14, 4, Theme.HALO);
			Draw.text(g, font, "Lazarus", x + 32, y + 7, Theme.ACCENT);
			Draw.text(g, font, "+", x + 32 + font.width("Lazarus"), y + 7, Theme.HALO);
			Draw.text(g, font, line, x + 32, y + 18, Theme.TEXT);
		} finally {
			Draw.opacity = 1f;
		}
	}
}
