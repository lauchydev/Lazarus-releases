package dev.lauchy.lazarus.ui;

import dev.lauchy.lazarus.core.Categories;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Favourites;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Features;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.Menu;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.feature.cosmetic.Capes;
import dev.lauchy.lazarus.feature.general.UpdateNotice;
import dev.lauchy.lazarus.hud.HudEditorScreen;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.Setting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The Lazarus menu: categories on the left, features on the right, each expandable to its
 * settings. Starred features gather under Favourites at the top of the sidebar. Each
 * category's page opens with a master switch for everything in it. Typing
 * anywhere searches every feature. Lazarus+ has its own page at the top, and a Halo plus
 * beside the title while it's active. Drawn immediate-mode: each frame
 * lays out the rows and records their click boxes, so there is no widget state to sync.
 */
public final class SettingsScreen extends Screen {
	private static final int ROW_H = 24, SETTING_H = 16, SIDEBAR_W = 92;
	/** The height of each further line of an expanded feature's description. */
	private static final int DESC_H = 10;
	/** The blank space in the sidebar above the Lazarus+ section. */
	private static final int SIDEBAR_GAP = 8;

	private static Category lastCategory = Category.END;
	/** Whether the Favourites page is showing; it opens there when anything is starred. */
	private static Boolean lastFavourites;
	private static final Set<String> EXPANDED = new HashSet<>();

	private record Hit(int x, int y, int w, int h, IntConsumer action) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private Category category = lastCategory;
	private boolean favourites = lastFavourites != null ? lastFavourites && !Favourites.isEmpty() : !Favourites.isEmpty();
	private String search = "";
	private double scroll;
	private int contentHeight, viewHeight;
	private KeySetting listening;
	/** The key that just finished a bind also arrives as a typed letter; it mustn't start a search. */
	private boolean swallowChar;
	private TextSetting editing;
	private IntSetting sliding;
	private int slideX, slideW;

	private long openedAt = Util.getMillis();
	/** The Lazarus+ welcome the title's + last rose in for: the 700ms moment plays once per purchase. */
	private static long risenFor;
	private long riseAt;
	/** Why the Lazarus+ page was opened for you (a locked switch was clicked), and when. */
	private String nudge;
	private long nudgeAt, openingAt;
	private final Anim.Smooth shownScroll = new Anim.Smooth(18f, 0f);
	private final Anim.Smooth selector = new Anim.Smooth(16f, -1f);
	private final Anim.Group<Object> hover = new Anim.Group<>(14f);
	private final Anim.Group<Object> switches = new Anim.Group<>(16f);
	private final Anim.Group<Object> expand = new Anim.Group<>(14f);
	private final Anim.Group<Object> sliders = new Anim.Group<>(20f);

	public SettingsScreen() {
		super(Component.literal("Lazarus"));
	}

	@Override
	public void added() {
		super.added();
		openedAt = Util.getMillis(); // coming back from the HUD editor plays it again
		// A Staff page left open by someone who no longer counts as staff (or isn't yet, this game start) isn't in the sidebar.
		if (category.staffSection() && !Staff.is()) category = lastCategory = Category.END;
		if (Plus.welcomedAt() > risenFor) {
			risenFor = Plus.welcomedAt();
			riseAt = openedAt;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		hits.clear();
		float open = Anim.since(openedAt, 240);
		Draw.opacity = open;
		try {
			draw(g, mouseX, mouseY, Math.round((1f - open) * 10));
		} finally {
			Draw.opacity = 1f;
		}
	}

	private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, int rise) {
		int pw = Math.min(460, width - 20), ph = Math.min(320, height - 20);
		int px = (width - pw) / 2, py = (height - ph) / 2 + rise;

		Draw.round(g, px, py, pw, ph, Theme.PANEL);
		Draw.rect(g, px + SIDEBAR_W, py + 8, 1, ph - 16, Theme.LINE);
		Draw.text(g, font, "§lLazarus", px + 12, py + 11, Theme.TEXT);
		int tx = px + 12 + font.width("§lLazarus");
		if (Plus.active()) {
			// The brand's "rise": a short drift up as it fades in, once, the first time the menu opens after buying.
			float r = riseAt == 0 ? 1f : Anim.since(riseAt, 700);
			float before = Draw.opacity;
			Draw.opacity *= r;
			Draw.text(g, font, "§l+", tx, py + 11 + Math.round((1f - r) * 4), Theme.HALO);
			Draw.opacity = before;
			if (Draw.inside(mouseX, mouseY, px + 12, py + 9, tx + font.width("§l+") - px - 12, 12)) {
				g.setTooltipForNextFrame(font, Component.literal("Lazarus+ · " + Plus.leftText() + " left"), mouseX, mouseY);
			}
		} else {
			Draw.round(g, tx + 1, py + 17, 3, 3, Theme.ACCENT);
		}

		int listTop = py + 30;
		List<Category> cats = Menu.sidebar(Plus.unlocked(), Staff.is());
		// Row indexes (counting Favourites as 0) of the categories below a gap; the selector slides over each gap too.
		List<Integer> gapRows = new ArrayList<>();
		for (Category c : cats) if (Menu.gapBefore(c)) gapRows.add(cats.indexOf(c) + 1);
		// Row 0 is Favourites, then one row per category.
		int selIndex = !search.isEmpty() ? -1 : favourites ? 0 : cats.indexOf(category) + 1;
		if (selector.get() < 0 && selIndex >= 0) selector.snap(selIndex);
		float sel = selector.to(selIndex >= 0 ? selIndex : selector.get());
		float selShown = hover.to("selector", selIndex >= 0);
		if (selShown > 0f) {
			float before = Draw.opacity;
			Draw.opacity *= selShown;
			float gaps = 0f;
			for (int gapRow : gapRows) gaps += Math.clamp(sel - gapRow + 1, 0f, 1f);
			Draw.round(g, px + 6, Math.round(listTop + sel * 17 + gaps * SIDEBAR_GAP), SIDEBAR_W - 12, 16, Theme.ACCENT_SOFT);
			Draw.opacity = before;
		}
		int cy = listTop;
		{
			float h = hover.to("favourites", selIndex == 0 || Draw.inside(mouseX, mouseY, px, cy, SIDEBAR_W, 16));
			Draw.text(g, font, "★ Favourites", px + 14 + Math.round(h * 2), cy + 4, Anim.lerpColor(Theme.MUTED, Theme.TEXT, h));
			hits.add(new Hit(px, cy, SIDEBAR_W, 16, b -> {
				favourites = lastFavourites = true;
				search = "";
				resetScroll();
			}));
			cy += 17;
		}
		for (Category c : cats) {
			// A blank gap, with no heading, sets the Lazarus+ section apart from the normal categories, and Staff from both.
			if (Menu.gapBefore(c)) cy += SIDEBAR_GAP;
			boolean on = selIndex == cats.indexOf(c) + 1;
			float h = hover.to(c, on || Draw.inside(mouseX, mouseY, px, cy, SIDEBAR_W, 16));
			// A category switched off reads faint in the sidebar.
			int base = Categories.on(c) ? Theme.MUTED : Theme.FAINT;
			int color = Anim.lerpColor(base, Categories.on(c) ? Theme.TEXT : Theme.MUTED, h);
			if (c == Category.PLUS) plusName(g, "Lazarus", px + 14 + Math.round(h * 2), cy + 4, color);
			else Draw.text(g, font, c.title, px + 14 + Math.round(h * 2), cy + 4, color);
			hits.add(new Hit(px, cy, SIDEBAR_W, 16, b -> {
				category = lastCategory = c;
				nudge = null;
				favourites = lastFavourites = false;
				search = "";
				resetScroll();
			}));
			cy += 17;
		}
		int by = py + ph - 26, bw = SIDEBAR_W - 16;
		updates(g, px + 8, by - 34, bw, mouseX, mouseY);
		float hh = hover.to("moveHud", Draw.inside(mouseX, mouseY, px + 8, by, bw, 16));
		Draw.round(g, px + 8, by, bw, 16, Anim.lerpColor(Theme.ACCENT_SOFT, Theme.ACCENT, hh));
		Draw.textCentered(g, font, "Move HUD", px + 8 + bw / 2, by + 4, Theme.TEXT);
		hits.add(new Hit(px + 8, by, bw, 16, b -> minecraft.setScreen(new HudEditorScreen(this))));

		int cx = px + SIDEBAR_W + 10, cw = pw - SIDEBAR_W - 20;
		Draw.round(g, cx, py + 8, cw, 16, Theme.FIELD);
		boolean caret = Util.getMillis() / 500 % 2 == 0;
		Draw.text(g, font, search.isEmpty() ? "Type to search" : search + (caret ? "§7_" : ""), cx + 6, py + 12,
				search.isEmpty() ? Theme.FAINT : Theme.TEXT);

		int top = py + 30, bottom = py + ph - 6;
		viewHeight = bottom - top;
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - viewHeight));
		float sc = shownScroll.to((float) scroll);
		g.enableScissor(cx, top, cx + cw, bottom);
		int y = top - Math.round(sc), start = y;
		List<Feature> shown = shownFeatures();
		boolean capePage = search.isEmpty() && !favourites && category == Category.CAPES;
		boolean plusPage = search.isEmpty() && !favourites && category == Category.PLUS;
		// Without Lazarus+, a locked switch or cape shows the Lazarus+ card at the top of the page it belongs to.
		if (!plusPage && nudge != null && search.isEmpty() && !favourites) y = plusCard(g, cx, y, cw, mouseX, mouseY, top, bottom) + 6;
		if (search.isEmpty() && !favourites && !capePage && !plusPage) y = categoryRow(g, category, cx, y, cw, mouseX, mouseY, top, bottom);
		if (plusPage) y = plusCard(g, cx, y, cw, mouseX, mouseY, top, bottom);
		if (shown.isEmpty() && !plusPage) Draw.text(g, font, search.isEmpty() && favourites
				? "Star a feature (☆ beside its switch) to keep it here." : "No features match.", cx + 4, y + 6, Theme.MUTED);
		for (Feature f : shown) {
			y = featureRow(g, f, cx, y, cw, mouseX, mouseY, top, bottom);
		}
		if (capePage) y = capeGrid(g, cx, y, cw, mouseX, mouseY, top, bottom);
		if (plusPage) y = plusFooter(g, cx, y, cw, mouseX, mouseY, top, bottom);
		g.disableScissor();
		contentHeight = y - start;
		if (contentHeight > viewHeight) {
			int barH = Math.max(16, viewHeight * viewHeight / contentHeight);
			int barY = top + Math.round((viewHeight - barH) * (sc / (contentHeight - viewHeight)));
			Draw.round(g, cx + cw + 4, barY, 2, barH, Theme.LINE);
		}
	}

	/** The version this is and a button that looks for a newer one, then installs it. */
	private void updates(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		UpdateNotice u = UpdateNotice.INSTANCE;
		UpdateNotice.Status st = u == null ? UpdateNotice.Status.DEV : u.status();
		Draw.textCentered(g, font, "v" + UpdateNotice.version(), x + w / 2, y, Theme.FAINT);
		String label = switch (st) {
			case IDLE, FAILED -> st == UpdateNotice.Status.FAILED ? "Try again" : "Check updates";
			case CHECKING -> "Checking" + ".".repeat((int) (Util.getMillis() / 300 % 4));
			case CURRENT -> "Up to date ✔";
			case AVAILABLE -> "Install update";
			case DOWNLOADING -> "Downloading…";
			case DOWNLOADED -> "Restart to apply";
			case DEV -> "Dev build";
		};
		boolean clickable = st == UpdateNotice.Status.IDLE || st == UpdateNotice.Status.FAILED
				|| st == UpdateNotice.Status.CURRENT || st == UpdateNotice.Status.AVAILABLE;
		boolean over = Draw.inside(mx, my, x, y + 12, w, 16);
		float h = hover.to("updates", clickable && over);
		int bg = st == UpdateNotice.Status.AVAILABLE ? Anim.lerpColor(Theme.ACCENT_SOFT, Theme.ACCENT, 0.5f + h * 0.5f)
				: Anim.lerpColor(Theme.FIELD, Theme.ACCENT_SOFT, h);
		Draw.round(g, x, y + 12, w, 16, bg);
		Draw.textCentered(g, font, label, x + w / 2, y + 16, clickable ? Theme.TEXT : Theme.MUTED);
		if (over && u != null && u.latest() != null && st != UpdateNotice.Status.CHECKING) {
			g.setTooltipForNextFrame(font, Component.literal("Newest build: " + u.latest()), mx, my);
		}
		if (clickable && u != null) hits.add(new Hit(x, y + 12, w, 16, b -> {
			if (u.status() == UpdateNotice.Status.AVAILABLE) u.install();
			else u.checkNow();
		}));
	}

	/** The category's master switch, above its features. Their own switches are kept as they are. */
	private int categoryRow(GuiGraphicsExtractor g, Category c, int x, int y, int w, int mx, int my, int top, int bottom) {
		boolean on = Categories.on(c);
		Draw.round(g, x, y + 2, w, 18, Theme.FIELD);
		Draw.text(g, font, "All " + c.title + " features", x + 8, y + 7, on ? Theme.TEXT : Theme.MUTED);
		if (!on) Draw.textRight(g, font, "off", x + w - 32, y + 7, Theme.FAINT);
		Draw.toggle(g, x + w - 24, y + 7, switches.to(c, on));
		clip(x, y + 2, w, 18, top, bottom, b -> Categories.toggle(c));
		return y + 24;
	}

	/** "Lazarus+" as the brand writes it: the name in {@code color}, the plus in Halo. */
	private void plusName(GuiGraphicsExtractor g, String name, int x, int y, int color) {
		Draw.text(g, font, name, x, y, color);
		Draw.text(g, font, name.startsWith("§l") ? "§l+" : "+", x + font.width(name), y, Theme.HALO);
	}

	/** The Lazarus+ page's card: where your pass stands, with the buttons to buy, extend or manage it. */
	private int plusCard(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, int top, int bottom) {
		if (nudge != null) {
			float n = Anim.since(nudgeAt, 240), before = Draw.opacity;
			Draw.opacity *= n;
			Draw.text(g, font, nudge, x + 4, y + 6 + Math.round((1f - n) * 4), Theme.TEXT);
			Draw.opacity = before;
			y += 18;
		}
		boolean active = Plus.active(), paused = Plus.paused(), frozen = Plus.frozen(), had = Plus.until() > 0;
		int ch = active ? 60 : 54;
		Draw.round(g, x, y + 2, w, ch, Theme.FIELD);
		plusName(g, "§lLazarus", x + 8, y + 9, Theme.TEXT);
		Draw.textRight(g, font, active ? "Active" : paused || frozen ? "Paused" : had ? "Ended" : "Not active", x + w - 8, y + 9,
				active ? Theme.GOOD : Theme.MUTED);
		int ty = y + 22;
		if (frozen) {
			// A chargeback is open: the server keeps the time left until it closes.
			Draw.text(g, font, fit("Paused while a payment dispute is open.", w - 16), x + 8, ty, Theme.MUTED);
			ty += 14;
		} else if (paused) {
			// Offline too long or the clock went back: the pass still runs to its end date, so say why it's off.
			Draw.text(g, font, fit("Off until the Lazarus server answers. Ends " + Plus.endDate() + ".", w - 16), x + 8, ty, Theme.MUTED);
			ty += 14;
		} else if (active) {
			Draw.text(g, font, "§f" + Plus.leftText() + "§7 left. Ends " + Plus.endDate() + ".", x + 8, ty, Theme.MUTED);
			Draw.rect(g, x + 8, ty + 12, w - 16, 2, Theme.OFF);
			Draw.rect(g, x + 8, ty + 12, Math.round((w - 16) * Plus.leftShare()), 2, Theme.ACCENT);
			ty += 18;
		} else {
			Draw.text(g, font, fit(had ? "Ended " + Plus.endDate() + ". Your settings are saved for when you're back."
					: Plus.PRICE + " for " + Plus.DAYS + " days. One payment, never renews.", w - 16), x + 8, ty, Theme.MUTED);
			ty += 14;
		}
		int bx = x + 8;
		if (!active || Plus.canExtend()) {
			String label = Util.getMillis() - openingAt < 3000 ? "Opening your browser…" : active || paused ? "Extend " + Plus.DAYS + " days" : "Get Lazarus+";
			bx += button(g, label, bx, ty, true, mx, my, top, bottom, () -> {
				openingAt = Util.getMillis();
				Plus.upgrade();
			}) + 4;
		}
		if (had) button(g, "Manage", bx, ty, false, mx, my, top, bottom, Plus::manage);
		y += ch + 10;
		// The same list as the site and the Discord, which keeps individual Plus features unnamed;
		// each one carries the gold + where it lives in the menu.
		Draw.text(g, font, "§lIncluded", x + 4, y, Theme.MUTED);
		y += 14;
		String[] perks = {"All " + Capes.ALL.size() + " capes, seen by every Lazarus user", "Exclusive features and cosmetics",
				"New Plus features first", "The gold plus beside your name in the tab list"};
		for (String perk : perks) {
			Draw.text(g, font, "+", x + 6, y, Theme.HALO);
			Draw.text(g, font, fit(perk, w - 20), x + 16, y, Theme.TEXT);
			y += 14;
		}
		return y;
	}

	/** Under the Lazarus+ features: the terms, and the line the Minecraft usage guidelines ask for. */
	private int plusFooter(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, int top, int bottom) {
		y += 8;
		int lx = x + 4;
		for (String[] link : new String[][]{{"Terms", "/terms"}, {"Refunds", "/refunds"}, {"Privacy", "/privacy"}, {"Help", "/contact"}}) {
			int lw = font.width(link[0]);
			float h = hover.to("link:" + link[1], my >= top && my < bottom && Draw.inside(mx, my, lx - 1, y - 2, lw + 2, 12));
			Draw.text(g, font, link[0], lx, y, Anim.lerpColor(Theme.ACCENT, Theme.ACCENT_HOVER, h));
			String url = Plus.SITE + link[1];
			clip(lx - 1, y - 2, lw + 2, 12, top, bottom, b -> Util.getPlatform().openUri(URI.create(url)));
			lx += lw + 10;
		}
		y += 14;
		Draw.text(g, font, fit("Not an official Minecraft product. Not approved by or", w - 8), x + 4, y, Theme.MUTED);
		Draw.text(g, font, fit("associated with Mojang or Microsoft.", w - 8), x + 4, y + 10, Theme.MUTED);
		return y + 24;
	}

	/** A small button: the main one Violet (Lilac under the mouse), the rest quiet. Returns its width. */
	private int button(GuiGraphicsExtractor g, String label, int x, int y, boolean main, int mx, int my, int top, int bottom, Runnable action) {
		int bw = font.width(label) + 14;
		float h = hover.to("button:" + main, my >= top && my < bottom && Draw.inside(mx, my, x, y, bw, 14));
		Draw.round(g, x, y, bw, 14, main ? Anim.lerpColor(Theme.ACCENT, Theme.ACCENT_HOVER, h) : Anim.lerpColor(Theme.OFF, Theme.ACCENT_SOFT, h));
		Draw.textCentered(g, font, label, x + bw / 2, y + 3, Theme.TEXT);
		clip(x, y, bw, 14, top, bottom, b -> action.run());
		return bw;
	}

	/** A Lazarus+ switch or cape clicked without Lazarus+: its home page, with the Lazarus+ card on top saying why. */
	private void needPlus(String what, Category home) {
		nudge = what + (what.endsWith("s") ? " are" : " is") + " part of Lazarus+.";
		nudgeAt = Util.getMillis();
		category = lastCategory = home;
		favourites = lastFavourites = false;
		search = "";
		resetScroll();
	}

	/** The Capes page: every official cape as a tile showing its front; click one to wear it, again to take it off. */
	private int capeGrid(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, int top, int bottom) {
		Capes capes = Capes.INSTANCE;
		if (capes == null) return y;
		boolean member = Plus.unlocked();
		Capes.Cape worn = member ? Capes.byKey(capes.selected()) : null;
		y += 6;
		if (!member) {
			// Browsing stays open to everyone; wearing one is Lazarus+.
			Draw.round(g, x, y - 4, w, 22, Theme.FIELD);
			Draw.text(g, font, "Wearing a cape is part of ", x + 8, y + 3, Theme.TEXT);
			plusName(g, "Lazarus", x + 8 + font.width("Wearing a cape is part of "), y + 3, Theme.TEXT);
			String label = Util.getMillis() - openingAt < 3000 ? "Opening…" : "Get Lazarus+";
			button(g, label, x + w - font.width(label) - 18, y, true, mx, my, top, bottom, () -> {
				openingAt = Util.getMillis();
				Plus.upgrade();
			});
			y += 26;
		} else Draw.text(g, font, worn == null ? "§7Wearing your own cape" : "§7Wearing §f" + worn.name(), x + 4, y, Theme.TEXT);
		if (worn != null) {
			String label = "Take off";
			int bw = font.width(label) + 12, bx = x + w - bw;
			float h = hover.to("capeOff", my >= top && my < bottom && Draw.inside(mx, my, bx, y - 3, bw, 14));
			Draw.round(g, bx, y - 3, bw, 14, Anim.lerpColor(Theme.FIELD, Theme.ACCENT_SOFT, h));
			Draw.textCentered(g, font, label, bx + bw / 2, y, Theme.TEXT);
			clip(bx, y - 3, bw, 14, top, bottom, b -> capes.select(""));
		}
		if (member) {
			y += 12;
			Draw.text(g, font, (LazarusServer.configured() ? "Lazarus users see it too." : "Only you see it for now.")
					+ " Needs Cape on in Skin Customization.", x + 4, y, Theme.FAINT);
			y += 14;
		}
		int cols = Math.max(1, (w + 4) / 56), tw = (w - (cols - 1) * 4) / cols, th = 68;
		String group = "";
		int col = 0;
		for (Capes.Cape c : Capes.ALL) {
			if (!c.group().equals(group)) {
				if (col > 0) y += th + 4;
				col = 0;
				group = c.group();
				y += 4;
				Draw.text(g, font, "§l" + group, x + 4, y, Theme.MUTED);
				y += 13;
			}
			int tx = x + col * (tw + 4);
			boolean visible = y + th > top && y < bottom;
			boolean wearing = c == worn;
			float h = hover.to("cape:" + c.key(), visible && my >= top && my < bottom && Draw.inside(mx, my, tx, y, tw, th));
			float on = switches.to("cape:" + c.key(), wearing);
			if (visible) {
				Draw.round(g, tx, y, tw, th, Anim.lerpColor(Theme.FIELD, Theme.ACCENT_SOFT, Math.max(on, h * 0.6f)));
				if (on > 0f) Draw.outline(g, tx, y, tw, th, Draw.alpha(Theme.ACCENT, on));
				int lift = Math.round(h * 2);
				switch (capes.state(c)) {
					case READY -> {
						var pose = g.pose();
						pose.pushMatrix();
						pose.translate(tx + tw / 2f - 15, y + 5 - lift);
						pose.scale(3f, 3f);
						g.blit(RenderPipelines.GUI_TEXTURED, Capes.id(c), 0, 0, 1f, 1f, 10, 16, 64, 32);
						pose.popMatrix();
					}
					case FAILED -> Draw.textCentered(g, font, "✕", tx + tw / 2, y + 25, Theme.FAINT);
					default -> Draw.textCentered(g, font, ".".repeat((int) (Util.getMillis() / 300 % 4)), tx + tw / 2, y + 25, Theme.FAINT);
				}
				Draw.textCentered(g, font, fit(c.name(), tw - 4), tx + tw / 2, y + th - 11, Anim.lerpColor(Theme.MUTED, Theme.TEXT, Math.max(h, on)));
				clip(tx, y, tw, th, top, bottom, b -> {
					if (member) capes.select(wearing ? "" : c.key());
					else needPlus("Capes", Category.CAPES);
				});
			}
			if (++col == cols) {
				col = 0;
				y += th + 4;
			}
		}
		if (col > 0) y += th + 4;
		return y + 4;
	}

	private void resetScroll() {
		scroll = 0;
		shownScroll.snap(0);
	}

	private List<Feature> shownFeatures() {
		if (search.isEmpty()) return favourites ? Favourites.features() : Menu.listed(Features.all(), category, Plus.unlocked(), f -> f.category, Feature::plus);
		String q = search.toLowerCase(Locale.ROOT);
		return Menu.search(Features.all(), Plus.unlocked(), Staff.is(), f -> f.category, Feature::plus, f -> f.name.toLowerCase(Locale.ROOT).contains(q)
				|| f.description.toLowerCase(Locale.ROOT).contains(q)
				|| f.settings().stream().anyMatch(s -> s.name.toLowerCase(Locale.ROOT).contains(q)));
	}

	private int featureRow(GuiGraphicsExtractor g, Feature f, int x, int y, int w, int mx, int my, int top, int bottom) {
		boolean visible = y + ROW_H > top && y < bottom;
		float h = hover.to(f, visible && my >= top && my < bottom && Draw.inside(mx, my, x, y, w, ROW_H));
		if (h > 0f) Draw.round(g, x, y, w, ROW_H, Draw.alpha(Theme.ROW_HOVER, h * 0.08f));
		boolean open = EXPANDED.contains(f.id);
		float e = expand.to(f.id, open);
		Draw.text(g, font, open ? "▾" : "▸", x + 4, y + 3, Anim.lerpColor(Theme.FAINT, Theme.ACCENT, e));
		// Everything in a category that's switched off is shown greyed, its switches kept.
		Draw.text(g, font, f.name, x + 13, y + 3, Categories.on(f.category) ? Theme.TEXT : Theme.MUTED);
		int badgeX = x + 17 + font.width(f.name);
		if (f.plus()) {
			// The Lazarus+ mark on its features: the plus alone, in Halo.
			int bx = x + 14 + font.width(f.name);
			badgeX = bx + font.width("+") + 4;
			Draw.text(g, font, "+", bx, y + 3, Theme.HALO);
			if (visible && my >= top && my < bottom && Draw.inside(mx, my, bx - 1, y + 2, 8, 10) && Draw.opacity >= 1f) {
				String tip = f.locked() ? "Part of Lazarus+" : "Part of Lazarus+, which you have";
				g.setTooltipForNextFrame(font, Component.literal(tip), mx, my);
			}
		}
		if (f.bannable()) {
			// A red pill, kept apart from the Halo plus so the two never read as one mark.
			int bw = font.width("BANNABLE") + 6;
			Draw.round(g, badgeX, y + 2, bw, 11, Draw.alpha(Theme.BAD, 0.18f));
			Draw.text(g, font, "BANNABLE", badgeX + 3, y + 4, Theme.BAD);
		}
		// Collapsed, a long description is cut with an ellipsis; expanded, it wraps and the rest sits above the settings.
		List<String> desc = describe(f.description, w - 58);
		Draw.text(g, font, open && desc.size() > 1 ? desc.getFirst() : fit(f.description, w - 58), x + 13, y + 13, Theme.MUTED);
		float shade = Draw.opacity;
		boolean locked = f.locked();
		// Locked reads as off and faded; the feature's own switch is kept for when Lazarus+ is back.
		if (!Categories.on(f.category) || locked) Draw.opacity *= 0.4f;
		Draw.toggle(g, x + w - 24, y + 7, switches.to(f, f.enabledSetting().on() && !locked));
		Draw.opacity = shade;
		boolean starred = Favourites.has(f);
		float star = hover.to("star:" + f.id, visible && my >= top && my < bottom && Draw.inside(mx, my, x + w - 42, y, 14, ROW_H));
		// The star stays faint until the row is hovered, so the list isn't a column of outlines.
		int starColor = starred ? Theme.ACCENT : Draw.alpha(Anim.lerpColor(Theme.FAINT, Theme.TEXT, star), Math.max(h, star));
		Draw.text(g, font, starred ? "★" : "☆", x + w - 39, y + 8, starColor);
		if (visible) {
			clip(x + w - 28, y, 28, ROW_H, top, bottom, b -> {
				if (f.locked()) needPlus(f.name, f.category.plusSection() ? f.category : Category.PLUS);
				else f.setEnabled(!f.enabledSetting().on());
			});
			clip(x + w - 42, y, 14, ROW_H, top, bottom, b -> Favourites.toggle(f));
			clip(x, y, w - 42, ROW_H, top, bottom, b -> {
				if (!EXPANDED.remove(f.id)) EXPANDED.add(f.id);
			});
		}
		y += ROW_H;
		if (e > 0f) {
			int more = (desc.size() - 1) * DESC_H;
			int full = more + (f.settings().size() + 1) * SETTING_H + 4;
			int shownH = Math.round(full * e);
			if (shownH > 0) {
				// Settings slide open under the row: drawn in full, clipped to the part that has opened.
				int limit = Math.min(bottom, y + shownH), from = Math.max(top, y);
				g.enableScissor(x, y, x + w, y + shownH);
				float before = Draw.opacity;
				Draw.opacity *= e;
				int sy = y - (full - shownH) / 2;
				for (int i = 1; i < desc.size(); i++) Draw.text(g, font, desc.get(i), x + 13, sy + (i - 1) * DESC_H, Theme.MUTED);
				sy += more;
				for (Setting<?> s : f.settings()) sy = settingRow(g, s, x + 12, sy, w - 12, mx, my, from, limit);
				settingRow(g, f.toggleKey(), x + 12, sy, w - 12, mx, my, from, limit);
				Draw.opacity = before;
				g.disableScissor();
				y += shownH;
			}
		}
		Draw.rect(g, x + 4, y, w - 8, 1, Theme.LINE);
		return y + 1;
	}

	private int settingRow(GuiGraphicsExtractor g, Setting<?> s, int x, int y, int w, int mx, int my, int top, int bottom) {
		boolean hovered = my >= top && my < bottom && Draw.inside(mx, my, x, y, w, SETTING_H);
		float h = hover.to(s, hovered);
		if (h > 0f) Draw.round(g, x, y, w, SETTING_H, Draw.alpha(Theme.ROW_HOVER, h * 0.06f));
		Draw.text(g, font, s.name, x + 4, y + 4, Anim.lerpColor(Theme.MUTED, Theme.TEXT, Math.max(h, 0.6f)));
		int right = x + w - 6;
		switch (s) {
			case BoolSetting b -> {
				Draw.toggle(g, right - 18, y + 4, switches.to(b, b.on()));
				clip(x, y, w, SETTING_H, top, bottom, btn -> b.toggle());
			}
			case IntSetting i -> {
				int sw = 90, sx = right - sw;
				float t = sliders.to(i, (float) (i.get() - i.min) / Math.max(1, i.max - i.min));
				int fill = Math.round(sw * t);
				Draw.rect(g, sx, y + 7, sw, 2, Theme.OFF);
				Draw.rect(g, sx, y + 7, fill, 2, Theme.ACCENT);
				Draw.round(g, sx + fill - 3, y + 4, 6, 8, i == sliding ? Theme.ACCENT : 0xFFFFFFFF);
				Draw.textRight(g, font, i.display(), sx - 8, y + 4, Theme.MUTED);
				clip(sx - 3, y, sw + 6, SETTING_H, top, bottom, btn -> {
					sliding = i;
					slideX = sx;
					slideW = sw;
				});
			}
			case ChoiceSetting<?> c -> {
				Draw.textRight(g, font, "§8‹ §r" + c.display() + " §8›", right, y + 4, Theme.ACCENT);
				clip(x, y, w, SETTING_H, top, bottom, btn -> c.cycle(btn == 1 ? -1 : 1));
			}
			case ColorSetting c -> {
				Draw.round(g, right - 16, y + 3, 16, 10, 0x30FFFFFF);
				Draw.round(g, right - 15, y + 4, 14, 8, c.argb() | 0xFF000000);
				clip(x, y, w, SETTING_H, top, bottom, btn -> c.cycle(btn == 1 ? -1 : 1));
			}
			case KeySetting k -> {
				String label = k == listening ? "§epress a key or mouse button" : Keys.name(k);
				int kw = font.width(label) + 10;
				Draw.round(g, right - kw, y + 2, kw, 12, k == listening ? Theme.ACCENT_SOFT : Theme.FIELD);
				Draw.textRight(g, font, label, right - 5, y + 4, Theme.ACCENT);
				clip(x, y, w, SETTING_H, top, bottom, btn -> {
					if (btn == 1) k.set(KeySetting.NONE);
					else listening = k;
				});
			}
			case TextSetting t -> {
				int bw = Math.min(150, w / 2), bx = right - bw;
				Draw.round(g, bx, y + 2, bw, 12, Theme.FIELD);
				if (t == editing) Draw.rect(g, bx + 2, y + 13, bw - 4, 1, Theme.ACCENT);
				boolean caret = Util.getMillis() / 500 % 2 == 0;
				String shown = t.get().isEmpty() && t != editing ? "§8empty" : t.get() + (t == editing && caret ? "_" : "");
				while (font.width(shown) > bw - 6 && shown.length() > 1) shown = shown.substring(1);
				Draw.text(g, font, shown, bx + 3, y + 4, Theme.TEXT);
				clip(bx, y, bw, SETTING_H, top, bottom, btn -> {
					if (btn == 1) t.set("");
					editing = t;
				});
			}
			default -> {
			}
		}
		if (hovered && !s.description.isEmpty() && Draw.opacity >= 1f) {
			g.setTooltipForNextFrame(font, Component.literal(s.description), mx, my);
		}
		return y + SETTING_H;
	}

	/** Records a click box, trimmed to the scrolled list so hidden rows can't be clicked. */
	private void clip(int x, int y, int w, int h, int top, int bottom, IntConsumer action) {
		int y0 = Math.max(y, top), y1 = Math.min(y + h, bottom);
		if (y1 > y0) hits.add(new Hit(x, y0, w, y1 - y0, action));
	}

	/** A feature's description wrapped at spaces to {@code width}, without the indent chat lines get. */
	private List<String> describe(String text, int width) {
		return Draw.wrap(font::width, text, width).stream().map(String::strip).toList();
	}

	private String fit(String s, int width) {
		if (font.width(s) <= width) return s;
		while (!s.isEmpty() && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null && event.button() >= 2) {
			// The middle and side buttons bind like keys; left and right are for the menu itself.
			listening.set(KeySetting.of(KeySetting.mouse(event.button()), Keys.held()));
			listening = null;
			return true;
		}
		listening = null;
		editing = null;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Draw.inside(event.x(), event.y(), h.x, h.y, h.w, h.h)) {
				h.action.accept(event.button());
				if (sliding != null) slide(event.x());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (sliding != null) {
			slide(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	private void slide(double mouseX) {
		double t = Math.clamp((mouseX - slideX) / slideW, 0, 1);
		sliding.set((int) Math.round(sliding.min + t * (sliding.max - sliding.min)));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		sliding = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll -= dy * 24;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		swallowChar = false;
		if (listening != null) {
			// Holding Ctrl, Alt or Shift while pressing the key binds the combination, e.g. Alt + W.
			// A modifier going down keeps listening for the key that goes with it.
			int key = event.key();
			if (Keys.isModifier(key)) return true;
			listening.set(key == 256 ? KeySetting.NONE : KeySetting.of(key, event.modifiers()));
			listening = null;
			swallowChar = true;
			return true;
		}
		if (editing != null) {
			if (event.key() == 259 && !editing.get().isEmpty()) editing.set(editing.get().substring(0, editing.get().length() - 1));
			if (event.key() == 256 || event.key() == 257) editing = null; // escape or enter
			return true;
		}
		if (event.key() == 259 && !search.isEmpty()) { // backspace
			search = search.substring(0, search.length() - 1);
			return true;
		}
		if (event.key() == 256 && !search.isEmpty()) {
			search = "";
			return true;
		}
		return super.keyPressed(event);
	}

	/** A modifier pressed and let go on its own binds that modifier by itself. */
	@Override
	public boolean keyReleased(KeyEvent event) {
		if (listening != null && Keys.isModifier(event.key())) {
			listening.set(KeySetting.of(event.key(), 0));
			listening = null;
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (listening != null || swallowChar) {
			swallowChar = false;
			return true;
		}
		String typed = event.codepointAsString();
		if (editing != null) {
			if (editing.get().length() < editing.maxLength) editing.set(editing.get() + typed);
			return true;
		}
		if (search.length() < 32 && !typed.isBlank() || !search.isEmpty() && typed.equals(" ")) {
			search += typed;
			resetScroll();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		Config.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
