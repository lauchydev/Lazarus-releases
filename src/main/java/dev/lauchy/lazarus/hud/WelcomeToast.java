package dev.lauchy.lazarus.hud;

import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * The card in the top right when you join, where the game puts its advancement toasts, so it isn't lost in the
 * server's join lines: how to open the menu, or that Lazarus is blocked for this account. Shown once a game start,
 * with the chat welcome.
 */
public final class WelcomeToast implements Toast {
	/** How long the card stays. */
	private static final long SHOW_MS = 7000;

	private final String title, line;
	private Visibility visibility = Visibility.SHOW;

	private WelcomeToast(String title, String line) {
		this.title = title;
		this.line = line;
	}

	/** Shows the card: how to open the menu, or, for a blocked account, that Lazarus doesn't run. */
	public static void show(Minecraft mc, boolean open, String name, String menuKey) {
		WelcomeToast toast = open
				? new WelcomeToast("Welcome back, " + name, "§7" + menuKey + " opens the menu")
				: new WelcomeToast("Lazarus is blocked", "§7for this account");
		mc.getToastManager().addToast(toast);
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long shownFor) {
		visibility = shownFor < SHOW_MS ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public int width() {
		Font font = Minecraft.getInstance().font;
		return Math.max(DEFAULT_WIDTH, Math.max(font.width("§l" + title), font.width(line)) + 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long shownFor) {
		int w = width(), h = height();
		Draw.round(g, 0, 0, w, h, Theme.PANEL);
		Draw.rect(g, 0, 4, 2, h - 8, Theme.ACCENT); // the brand's violet edge, as its panels carry
		Draw.text(g, font, "§l" + title, 10, 7, Theme.TEXT);
		Draw.text(g, font, line, 10, 18, Theme.TEXT);
	}
}
