package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.mixin.ContainerLabelsAccessor;
import dev.lauchy.lazarus.mixin.SlotAccessor;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces the Storage menu (and each storage page and the ender chest) with one view of every page you have,
 * from what {@link StorageCache} remembers. The window that's really open sits among them as a live card, so you
 * can move items in and out of it as usual; clicking another page opens it in place, the held item still on the cursor.
 */
public final class StorageOverview extends Feature {
	/** The one instance, for the mixins that draw it and hide the chest underneath. */
	public static StorageOverview INSTANCE;

	/** Where a slot goes when its card isn't on screen: far enough away that nothing can hover or click it. */
	private static final int AWAY = -10000;
	/** How long each window on the way to a page (the Storage menu, then the page) has to arrive before the click is given up. */
	private static final long REQUEST_MS = 12000;

	private final IntSetting backdrop = add(new IntSetting("backdrop", "Backdrop", "How dark the world is behind the overview.", 80, 0, 100, "%"));
	private final IntSetting size = add(new IntSetting("size", "Size", "How big the pages are drawn, against the game's own slot size.", 125, 100, 200, "%"));
	private final BoolSetting move = add(new BoolSetting("move", "Move by clicking", "Clicking a slot on a page that isn't open opens it behind the overview and makes the click there.", true));

	/** Whether you switched to the server's own view; kept until you switch back. */
	private static boolean vanilla;
	/** Set once the chest panel hook runs. With Endsight or under Lunar Client it never does; the overview then draws later, over the panel. */
	public static boolean panelHook;
	/** The first card row on screen. */
	private static int scroll;
	/** What the search box holds. */
	private static String query = "";
	private static boolean typing;

	/** The storage window the overview is laid over, and where its slots and labels were before it moved them. */
	private AbstractContainerScreen<?> screen;
	private final Map<Slot, int[]> home = new HashMap<>();
	private int[] labels;
	private StoragePages.Layout layout;
	private List<Integer> cards = List.of();
	private long shownAt, closedAt;
	private boolean eatRelease;
	private Request request;
	private final Anim.Group<Integer> glow = new Anim.Group<>(16f);

	/**
	 * A page you asked to open, the item being carried there if any, and the click to make in it once it's open:
	 * which of its slots ({@code index}, -1 for none) and with which button.
	 */
	private static final class Request {
		final int page;
		final ItemStack item;
		final int inventorySlot;
		int index = -1, button;
		long started = Util.getMillis();
		final Object level = Minecraft.getInstance().level;
		Object clickedRoot, seen;
		int waited;

		Request(int page, ItemStack item, int inventorySlot) {
			this.page = page;
			this.item = item;
			this.inventorySlot = inventorySlot;
		}
	}

	public StorageOverview() {
		super("storageOverview", "Storage Overview", "/storage shows every page and your ender chest at once, and you can move items between any of their slots without leaving it.", Category.INVENTORY, true);
	}

	/** Whether the overview is drawn over this window right now. */
	public static boolean showing(AbstractContainerScreen<?> s) {
		return INSTANCE != null && INSTANCE.enabled() && !vanilla && INSTANCE.screen == s && s != null;
	}

	@Override
	public void init() {
		INSTANCE = this;
		StorageCache.use(this::enabled);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, s, w, h) -> {
			if (!(s instanceof AbstractContainerScreen<?> cs) || !storageWindow(cs)) return;
			boolean fresh = screen != cs; // init runs again on every resize
			if (fresh) {
				home.clear();
				labels = null;
				typing = false;
				// Fade in coming from the world, not when one page hands over to the next.
				if (Util.getMillis() - closedAt > 400) {
					shownAt = Util.getMillis();
					if (!StoragePreview.search().isEmpty()) query = StoragePreview.search();
				}
			}
			screen = cs;
			place(cs, fresh);
			ScreenEvents.afterTick(s).register(x -> place(cs, false));
			ScreenEvents.remove(s).register(x -> {
				if (screen != cs) return;
				screen = null;
				home.clear();
				labels = null;
				closedAt = Util.getMillis();
			});
			ScreenEvents.afterExtract(s).register((x, g, mx, my, d) -> {
				if (enabled() && vanilla) overviewButton(cs, g, mx, my);
				else if (showing(cs)) carriedGhost(g, mx, my);
			});
			ScreenMouseEvents.allowMouseClick(s).register((x, click) -> !click(cs, click.x(), click.y(), click.button()));
			ScreenMouseEvents.allowMouseRelease(s).register((x, click) -> {
				boolean eat = eatRelease;
				eatRelease = false;
				return !eat;
			});
			ScreenMouseEvents.allowMouseScroll(s).register((x, mx, my, hx, vy) -> !scrolled(cs, vy));
			ScreenKeyboardEvents.allowKeyPress(s).register((x, key) -> !keyPressed(cs, key));
			ScreenKeyboardEvents.allowCharType(s).register((x, ch) -> !charTyped(cs, ch));
		});
	}

	private static boolean storageWindow(AbstractContainerScreen<?> cs) {
		String title = Text.plain(cs.getTitle());
		return StoragePages.isRoot(title) || StoragePages.pageOfWindow(title) >= 0;
	}

	/** The page this window shows, or -1 for the Storage menu itself. */
	private static int activePage(AbstractContainerScreen<?> cs) {
		return StoragePages.pageOfWindow(Text.plain(cs.getTitle()));
	}

	/** Every card in order: the ender chest, then each page up to the highest one known. */
	private static List<Integer> cards(int active) {
		int last = Math.max(StorageCache.count(), active);
		for (int p : StorageCache.pages().keySet()) last = Math.max(last, p);
		List<Integer> out = new ArrayList<>();
		for (int p = StoragePages.ENDER; p <= Math.min(last, StoragePages.MAX_PAGES); p++) out.add(p);
		return out;
	}

	/** Rows of slots a card shows: five, as a page has, or more when the ender chest is bigger. */
	private static int rows(AbstractContainerScreen<?> cs, int active) {
		int rows = 5;
		for (StorageCache.Page p : StorageCache.pages().values()) rows = Math.max(rows, p.rows());
		if (active >= 0) rows = Math.max(rows, (StorageCache.content(cs, active).size() + 8) / 9);
		return Math.min(6, rows);
	}

	// ---- Laying the real slots onto the cards ----

	/**
	 * Moves the window's slots onto the overview: the open page's onto its card, yours onto the Inventory card,
	 * the server's buttons out of sight. Vanilla then draws, hovers and clicks them right where they are.
	 */
	private void place(AbstractContainerScreen<?> cs, boolean reveal) {
		if (cs != screen) return;
		if (!showing(cs)) {
			restore(cs);
			return;
		}
		ContainerLabelsAccessor l = (ContainerLabelsAccessor) cs;
		if (labels == null) labels = new int[]{l.lazarus$titleX(), l.lazarus$titleY(), l.lazarus$inventoryX(), l.lazarus$inventoryY(), cs.imageWidth, cs.imageHeight};
		l.lazarus$setTitleX(AWAY);
		l.lazarus$setTitleY(AWAY);
		l.lazarus$setInventoryX(AWAY);
		l.lazarus$setInventoryY(AWAY);
		cs.leftPos = 0;
		cs.topPos = 0;
		// The window counts any click outside its own box as a click outside (slot -999), which drops the click on
		// a slot the overview moved there. Stretched over the whole screen, nothing is outside it.
		l.lazarus$setImageWidth(cs.width);
		l.lazarus$setImageHeight(cs.height);

		int active = activePage(cs);
		cards = cards(active);
		float sc = scale();
		layout = StoragePages.layout(Math.round(cs.width / sc), Math.round(cs.height / sc), cards.size(), rows(cs, active));
		int at = cards.indexOf(active);
		if (reveal && at >= 0) scroll = layout.reveal(at, scroll);
		scroll = Math.clamp(scroll, 0, layout.maxScroll());
		List<Slot> content = active >= 0 ? StorageCache.content(cs, active) : List.of();
		boolean onScreen = at >= 0 && layout.visible(at, scroll);
		for (Slot s : cs.getMenu().slots) {
			home.putIfAbsent(s, new int[]{s.x, s.y});
			int x = AWAY, y = AWAY;
			if (s.container instanceof Inventory) {
				int i = s.getContainerSlot();
				// The slot an item is parked in on its way to another page stays hidden: the item is drawn on the cursor instead.
				if (i < 36 && !(request != null && !request.item.isEmpty() && i == request.inventorySlot)) {
					x = layout.left() + 2 + (i % 9) * StoragePages.CELL;
					y = StoragePages.TOP + StoragePages.CARD_HEAD + 1 + (i < 9 ? 3 * StoragePages.CELL + 4 : (i / 9 - 1) * StoragePages.CELL);
				}
			} else if (onScreen) {
				int i = content.indexOf(s);
				if (i >= 0) {
					x = layout.x(at % layout.cols()) + 2 + (i % 9) * StoragePages.CELL;
					y = layout.y(at / layout.cols(), scroll) + StoragePages.CARD_HEAD + 1 + (i / 9) * StoragePages.CELL;
				}
			}
			if (x != AWAY) {
				x = Math.round(x * sc);
				y = Math.round(y * sc);
			}
			if (s.x != x) ((SlotAccessor) s).lazarus$setX(x);
			if (s.y != y) ((SlotAccessor) s).lazarus$setY(y);
		}
	}

	/** Puts the window back the way the server laid it out, for the Vanilla view. */
	private void restore(AbstractContainerScreen<?> cs) {
		if (home.isEmpty()) return;
		for (var e : home.entrySet()) {
			((SlotAccessor) e.getKey()).lazarus$setX(e.getValue()[0]);
			((SlotAccessor) e.getKey()).lazarus$setY(e.getValue()[1]);
		}
		home.clear();
		if (labels != null) {
			ContainerLabelsAccessor l = (ContainerLabelsAccessor) cs;
			l.lazarus$setTitleX(labels[0]);
			l.lazarus$setTitleY(labels[1]);
			l.lazarus$setInventoryX(labels[2]);
			l.lazarus$setInventoryY(labels[3]);
			l.lazarus$setImageWidth(labels[4]);
			l.lazarus$setImageHeight(labels[5]);
			labels = null;
		}
		cs.leftPos = (cs.width - cs.imageWidth) / 2;
		cs.topPos = (cs.height - cs.imageHeight) / 2;
	}

	/** How much bigger than the game's own slots the cards are drawn. */
	public float scale() {
		return size.get() / 100f;
	}

	/** Scales a live slot's item around its own corner while the overview shows, so it matches the cards. */
	public void scaleSlot(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Slot slot, boolean begin) {
		if (!showing(cs) || slot.x == AWAY) return;
		var pose = g.pose();
		if (!begin) {
			pose.popMatrix();
			return;
		}
		float sc = scale();
		pose.pushMatrix();
		pose.translate(slot.x, slot.y);
		pose.scale(sc, sc);
		pose.translate(-slot.x, -slot.y);
	}

	// ---- Drawing ----

	/** An item on its way to another page, drawn on the cursor as if it had never left it. */
	private void carriedGhost(GuiGraphicsExtractor g, int mx, int my) {
		Request r = request;
		if (r == null || r.item.isEmpty()) return;
		g.nextStratum();
		g.item(r.item, mx - 8, my - 8);
		g.itemDecorations(Minecraft.getInstance().font, r.item, mx - 8, my - 8);
	}

	/** Draws the overview under the window's slots: from the screen's background, or just after it ({@code late}) when the chest panel can't be hidden. */
	public void backdrop(Screen s, GuiGraphicsExtractor g, int mx, int my, boolean late) {
		if (late == panelHook || !(s instanceof AbstractContainerScreen<?> cs) || !showing(cs) || layout == null) return;
		Draw.opacity = Anim.since(shownAt, 220);
		try {
			draw(cs, g, mx, my);
		} finally {
			Draw.opacity = 1f;
		}
	}

	private void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
		Font font = Minecraft.getInstance().font;
		Draw.rect(g, 0, 0, cs.width, cs.height, Draw.alpha(0x0C0C10, backdrop.get() / 100f));
		float sc = scale();
		int left = Math.round(layout.left() * sc), right = cs.width - left;

		// Header: the name, the way back to the server's view, the search box.
		Draw.round(g, left + 1, 15, 3, 3, Theme.ACCENT);
		Draw.text(g, font, "Storage", left + 8, 12, Theme.TEXT);
		int sx = searchX(cs), sw = right - sx;
		boolean searchHover = Draw.inside(mx, my, sx, 8, sw, 16);
		Draw.round(g, sx, 8, sw, 16, Theme.FIELD);
		if (typing || searchHover) Draw.outline(g, sx, 8, sw, 16, typing ? Theme.ACCENT : Theme.LINE);
		boolean caret = typing && Util.getMillis() / 500 % 2 == 0;
		Draw.text(g, font, query.isEmpty() && !typing ? "Search items" : query + (caret ? "_" : ""), sx + 6, 12, query.isEmpty() && !typing ? Theme.FAINT : Theme.TEXT);
		String vanillaLabel = "Vanilla view";
		int vx = sx - 12 - font.width(vanillaLabel);
		boolean vHover = Draw.inside(mx, my, vx - 3, 8, font.width(vanillaLabel) + 6, 16);
		Draw.text(g, font, vanillaLabel, vx, 12, vHover ? Theme.ACCENT_HOVER : Theme.MUTED);
		Draw.rect(g, left, StoragePages.TOP - 12, right - left, 1, Theme.LINE);

		String q = query.trim().toLowerCase(Locale.ROOT);
		ItemStack carried = cs.getMenu().getCarried();
		int active = activePage(cs);
		Component tip = null;
		List<Component> cardTip = null;
		ItemStack itemTip = ItemStack.EMPTY;

		// The cards are laid out at the game's slot size and drawn scaled up, the mouse scaled down to match.
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(sc, sc);
		int smx = Math.round(mx / sc), smy = Math.round(my / sc);
		left = layout.left();

		// Your inventory: its real slots sit on this card.
		int iy = StoragePages.TOP;
		Draw.text(g, font, "Inventory", left + 2, iy + 3, Theme.TEXT);
		Draw.slotGrid(g, left + 2, iy + StoragePages.CARD_HEAD + 1, 9, 3, 0x30FFFFFF);
		Draw.slotGrid(g, left + 2, iy + StoragePages.CARD_HEAD + 1 + 3 * StoragePages.CELL + 4, 9, 1, 0x30FFFFFF);
		if (move.on()) {
			int hy = iy + StoragePages.CARD_HEAD + 4 * StoragePages.CELL + 12;
			for (String line : Draw.wrap(font, List.of("Holding an item, click another page to open it with the item still on your cursor, or one of its slots to drop it there."), StoragePages.CARD_W - 4)) {
				Draw.text(g, font, line.strip(), left + 2, hy, Theme.FAINT);
				hy += font.lineHeight + 1;
			}
		}

		for (int i = scroll * layout.cols(); i < cards.size() && layout.visible(i, scroll); i++) {
			int page = cards.get(i);
			int x = layout.x(i % layout.cols()), y = layout.y(i / layout.cols(), scroll);
			boolean isActive = page == active;
			boolean hovered = Draw.inside(smx, smy, x - 3, y - 2, StoragePages.CARD_W + 6, layout.cardH() + 4);
			boolean target = request != null && request.page == page;
			float h = glow.to(page, hovered || target);
			StorageCache.Page cached = StorageCache.pages().get(page);
			boolean locked = StorageCache.locked(page);

			if (isActive) Draw.round(g, x - 3, y - 2, StoragePages.CARD_W + 6, layout.cardH() + 4, Theme.ACCENT_SOFT);
			else if (h > 0.01f) Draw.round(g, x - 3, y - 2, StoragePages.CARD_W + 6, layout.cardH() + 4, Draw.alpha(0xFFFFFF, 0.05f * h));
			if (isActive) Draw.outline(g, x - 3, y - 2, StoragePages.CARD_W + 6, layout.cardH() + 4, Draw.alpha(Theme.ACCENT, 0.7f));

			int found = 0;
			List<Slot> live = isActive ? StorageCache.content(cs, page) : List.of();
			if (!q.isEmpty()) {
				if (isActive) {
					for (Slot s : live) if (StoragePreview.matches(s.getItem(), q)) found++;
				} else if (cached != null) {
					for (ItemStack st : cached.items()) if (StoragePreview.matches(st, q)) found++;
				}
			}
			int titleColour = isActive ? Theme.ACCENT : Anim.lerpColor(Theme.TEXT, Theme.ACCENT_HOVER, h);
			Draw.text(g, font, StoragePages.label(page), x + 2, y + 3, titleColour);
			String status;
			int statusColour = Theme.MUTED;
			if (target) {
				status = "Opening";
				statusColour = Theme.ACCENT; // the brand guide keeps motion from looping, so no pulse
			} else if (found > 0) {
				status = found + " found";
				statusColour = Theme.ACCENT;
			} else if (!carried.isEmpty() && hovered && !isActive && !locked && move.on()) {
				status = "Open here";
				statusColour = Theme.ACCENT_HOVER;
			} else if (locked) status = "Locked";
			else if (isActive) status = "Open";
			else if (cached == null) status = "Not visited";
			else status = "";
			if (isActive && status.equals("Open")) statusColour = Theme.ACCENT;
			Draw.textRight(g, font, status, x + StoragePages.CARD_W - 2, y + 3, statusColour);

			int fits = (layout.cardH() - StoragePages.CARD_HEAD - 1) / StoragePages.CELL;
			int rows = Math.min(fits, isActive ? Math.max(1, (live.size() + 8) / 9) : cached != null ? cached.rows() : fits);
			int gx = x + 2, gy = y + StoragePages.CARD_HEAD + 1;
			Draw.slotGrid(g, gx, gy, 9, rows, isActive ? Draw.alpha(Theme.ACCENT, 0.35f) : 0x30FFFFFF);
			if (isActive || cached == null) {
				if (hovered && !isActive) cardTip = cardTip(page, cached, locked, carried);
				continue;
			}
			boolean overItem = false;
			int hoverCell = cellAt(gx, gy, rows, smx, smy);
			if (hoverCell >= 0) Draw.rect(g, gx + hoverCell % 9 * StoragePages.CELL, gy + hoverCell / 9 * StoragePages.CELL, 16, 16, 0x50FFFFFF);
			for (int n = 0; n < Math.min(cached.items().size(), rows * 9); n++) {
				ItemStack st = cached.at(n);
				int cx = gx + (n % 9) * StoragePages.CELL, cy = gy + (n / 9) * StoragePages.CELL;
				if (st.isEmpty()) continue;
				boolean match = !q.isEmpty() && StoragePreview.matches(st, q);
				if (match) {
					Draw.rect(g, cx - 1, cy - 1, 18, 18, Theme.ACCENT_SOFT);
					Draw.outline(g, cx - 1, cy - 1, 18, 18, Theme.ACCENT);
				}
				g.item(st, cx, cy);
				g.itemDecorations(font, st, cx, cy);
				if (RarityLines.INSTANCE != null) RarityLines.INSTANCE.slotMark(g, st, cx, cy); // the same mark the open page's slots get
				if (!q.isEmpty() && !match) Draw.rect(g, cx, cy, 16, 16, 0xB00C0C10);
				if (Draw.inside(smx, smy, cx - 1, cy - 1, 18, 18) && carried.isEmpty()) {
					itemTip = st;
					overItem = true;
				}
			}
			if (hovered && !overItem) cardTip = cardTip(page, cached, locked, carried);
		}
		pose.popMatrix();

		// Footer: which cards are showing, with arrows when there are more.
		if (layout.maxScroll() > 0) {
			int first = scroll * layout.cols() + 1, last = Math.min(cards.size(), (scroll + layout.visibleRows()) * layout.cols());
			String range = first + "-" + last + " of " + cards.size();
			int fy = cs.height - 18, cx = cs.width / 2;
			Draw.textCentered(g, font, range, cx, fy, Theme.MUTED);
			int half = font.width(range) / 2 + 14;
			Draw.textCentered(g, font, "<", cx - half, fy, scroll > 0 ? arrowColour(mx, my, cx - half, fy) : Theme.OFF);
			Draw.textCentered(g, font, ">", cx + half, fy, scroll < layout.maxScroll() ? arrowColour(mx, my, cx + half, fy) : Theme.OFF);
		}

		if (!itemTip.isEmpty()) g.setTooltipForNextFrame(font, itemTip, mx, my);
		else if (cardTip != null) g.setTooltipForNextFrame(font, cardTip, Optional.empty(), mx, my);
		else if (vHover) tip = Component.literal("Show the server's own Storage menu");
		if (tip != null) g.setTooltipForNextFrame(font, tip, mx, my);
	}

	private static int arrowColour(int mx, int my, int cx, int fy) {
		return Draw.inside(mx, my, cx - 8, fy - 4, 16, 16) ? Theme.ACCENT_HOVER : Theme.TEXT;
	}

	private List<Component> cardTip(int page, StorageCache.Page cached, boolean locked, ItemStack carried) {
		List<Component> tip = new ArrayList<>();
		String name = StoragePages.label(page);
		if (locked) tip.add(Component.literal(name + " is locked"));
		else if (!carried.isEmpty() && move.on()) tip.add(Component.literal("Open " + name + " holding " + Items.name(carried)));
		else tip.add(Component.literal("Open " + name));
		tip.add(Component.literal(cached == null ? "Not visited yet" : "Last seen " + StoragePages.age(System.currentTimeMillis() - cached.seenAt())).withColor(Theme.MUTED & 0xFFFFFF));
		return tip;
	}

	private int searchX(AbstractContainerScreen<?> cs) {
		return cs.width - Math.round(layout.left() * scale()) - Math.min(160, cs.width / 4);
	}

	/** Search matches on the open page's and your inventory's own slots are outlined and the rest dimmed, like on the other cards. */
	public void slotMark(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Slot slot) {
		if (!showing(cs) || slot.x == AWAY || slot.getItem().isEmpty()) return;
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return;
		if (StoragePreview.matches(slot.getItem(), q)) Draw.outline(g, slot.x - 1, slot.y - 1, 18, 18, Theme.ACCENT);
		else Draw.rect(g, slot.x, slot.y, 16, 16, 0xB00C0C10);
	}

	/** In the Vanilla view, the one way back to the overview. */
	private void overviewButton(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
		Font font = Minecraft.getInstance().font;
		g.nextStratum();
		String label = "Overview";
		int x = cs.width - 12 - font.width(label);
		Draw.text(g, font, label, x, 12, Draw.inside(mx, my, x - 3, 8, font.width(label) + 6, 16) ? Theme.ACCENT_HOVER : Theme.MUTED);
	}

	// ---- Input ----

	/** @return true when the overview used the click, so the window underneath never sees it */
	private boolean click(AbstractContainerScreen<?> cs, double mx, double my, int button) {
		if (!enabled() || cs != screen) return false;
		Font font = Minecraft.getInstance().font;
		if (vanilla) {
			int x = cs.width - 12 - font.width("Overview");
			if (button != 0 || !Draw.inside(mx, my, x - 3, 8, font.width("Overview") + 6, 16)) return false;
			vanilla = false;
			shownAt = Util.getMillis();
			place(cs, true);
			eatRelease = true;
			return true;
		}
		if (layout == null) return false;
		for (Slot s : cs.getMenu().slots) {
			if (s.x != AWAY && Draw.inside(mx, my, s.x - 1, s.y - 1, Math.round(18 * scale()), Math.round(18 * scale()))) {
				typing = false;
				return false; // a real slot: the window handles it as usual
			}
		}
		// Anywhere else would count as clicking outside the window, which throws a held item on the floor.
		eatRelease = true;
		if (button > 1) return true;
		if (button == 1) {
			// A right click on another page's slot drops one of the held stack there, as it would in that page.
			int[] hit = cellHit(mx / scale(), my / scale());
			if (hit != null) slotClicked(cs, cards.get(hit[0]), hit[1], 1);
			return true;
		}
		int sx = searchX(cs);
		typing = Draw.inside(mx, my, sx, 8, cs.width - Math.round(layout.left() * scale()) - sx, 16);
		if (typing) return true;
		int vx = sx - 12 - font.width("Vanilla view");
		if (Draw.inside(mx, my, vx - 3, 8, font.width("Vanilla view") + 6, 16)) {
			vanilla = true;
			request = null;
			restore(cs);
			return true;
		}
		if (layout.maxScroll() > 0) {
			String range = (scroll * layout.cols() + 1) + "-" + Math.min(cards.size(), (scroll + layout.visibleRows()) * layout.cols()) + " of " + cards.size();
			int fy = cs.height - 18, cx = cs.width / 2, half = font.width(range) / 2 + 14;
			if (Draw.inside(mx, my, cx - half - 8, fy - 4, 16, 16)) return scrolled(cs, 1);
			if (Draw.inside(mx, my, cx + half - 8, fy - 4, 16, 16)) return scrolled(cs, -1);
		}
		double lmx = mx / scale(), lmy = my / scale(); // the cards are laid out before scaling
		for (int i = scroll * layout.cols(); i < cards.size() && layout.visible(i, scroll); i++) {
			int x = layout.x(i % layout.cols()), y = layout.y(i / layout.cols(), scroll);
			if (Draw.inside(lmx, lmy, x - 3, y - 2, StoragePages.CARD_W + 6, layout.cardH() + 4)) {
				int[] hit = cellHit(lmx, lmy);
				if (hit != null) slotClicked(cs, cards.get(i), hit[1], 0);
				else cardClicked(cs, cards.get(i));
				return true;
			}
		}
		return true;
	}

	/** The slot under the mouse on a card's grid, counted from its top left, or -1. */
	private static int cellAt(int gx, int gy, int rows, double mx, double my) {
		if (mx < gx - 1 || my < gy - 1) return -1;
		int col = (int) (mx - gx + 1) / StoragePages.CELL, row = (int) (my - gy + 1) / StoragePages.CELL;
		return col < 9 && row < rows ? row * 9 + col : -1;
	}

	/** The card (by index in {@link #cards}) and slot under the mouse, on any page that isn't the open one. */
	private int[] cellHit(double mx, double my) {
		if (layout == null || screen == null) return null;
		int active = activePage(screen);
		int fits = (layout.cardH() - StoragePages.CARD_HEAD - 1) / StoragePages.CELL;
		for (int i = scroll * layout.cols(); i < cards.size() && layout.visible(i, scroll); i++) {
			if (cards.get(i) == active) continue;
			StorageCache.Page cached = StorageCache.pages().get(cards.get(i));
			int rows = cached == null ? fits : Math.min(fits, cached.rows());
			int cell = cellAt(layout.x(i % layout.cols()) + 2, layout.y(i / layout.cols(), scroll) + StoragePages.CARD_HEAD + 1, rows, mx, my);
			if (cell >= 0) return new int[]{i, cell};
		}
		return null;
	}

	/**
	 * A click on one slot of a page that isn't open: Lazarus opens that page behind the overview and, when an item
	 * is on the cursor, drops it into that very slot, so you can put an item anywhere on any page without leaving this view.
	 */
	private void slotClicked(AbstractContainerScreen<?> cs, int page, int index, int button) {
		cardClicked(cs, page);
		// With nothing on the cursor the click only opens the page, so a click on a card never surprises you with an item in hand.
		if (request == null || request.page != page || request.item.isEmpty()) return;
		request.index = index;
		request.button = button;
	}

	/** Opens a page, carrying the held item along when there is one. */
	private void cardClicked(AbstractContainerScreen<?> cs, int page) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || page == activePage(cs)) return;
		// A click while an item is already on its way waits for that to finish; any other click starts over.
		if (request != null && !request.item.isEmpty()) return;
		String name = StoragePages.label(page);
		if (StorageCache.locked(page)) {
			Toast.show(name + " is locked.", Theme.WARN);
			return;
		}
		ItemStack carried = cs.getMenu().getCarried();
		if (carried.isEmpty()) {
			request = new Request(page, ItemStack.EMPTY, -1);
		} else {
			if (!move.on()) return;
			Slot free = freeSlot(cs);
			if (free == null) {
				Toast.show("Your inventory is full.", Theme.WARN);
				return;
			}
			// Set it down in your inventory first: closing a window with an item on the cursor is how items get lost.
			ItemStack item = carried.copy();
			mc.gameMode.handleContainerInput(cs.getMenu().containerId, free.index, 0, ContainerInput.PICKUP, mc.player);
			request = new Request(page, item, free.getContainerSlot());
		}
		if (page == StoragePages.ENDER) mc.player.connection.sendCommand("ec");
		else if (activePage(cs) < 0) clickRoot(cs, request);
		else mc.player.connection.sendCommand("storage");
	}

	/** An empty slot in your inventory, main rows before the hotbar. */
	private static Slot freeSlot(AbstractContainerScreen<?> cs) {
		Slot hotbar = null;
		for (Slot s : cs.getMenu().slots) {
			if (!(s.container instanceof Inventory) || !s.getItem().isEmpty() || s.getContainerSlot() >= 36) continue;
			if (s.getContainerSlot() >= 9) return s;
			if (hotbar == null) hotbar = s;
		}
		return hotbar;
	}

	private static Slot inventorySlot(AbstractContainerScreen<?> cs, int containerSlot) {
		for (Slot s : cs.getMenu().slots) if (s.container instanceof Inventory && s.getContainerSlot() == containerSlot) return s;
		return null;
	}

	/** In the Storage menu, clicks the page's own button, once it has arrived. */
	private void clickRoot(AbstractContainerScreen<?> cs, Request r) {
		Minecraft mc = Minecraft.getInstance();
		if (r.clickedRoot == cs || mc.player == null || mc.gameMode == null) return;
		for (Slot s : cs.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			if (StoragePages.pageOfButton(Items.name(s.getItem())) != r.page) continue;
			if (StorageCache.locked(r.page)) {
				Toast.show(StoragePages.label(r.page) + " is locked.", Theme.WARN);
				request = null;
				return;
			}
			r.clickedRoot = cs;
			mc.gameMode.handleContainerInput(cs.getMenu().containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
			return;
		}
	}

	/** Walks a request along: Storage menu, then the page, then the item shift-clicked in. */
	private void tick(Minecraft mc) {
		Request r = request;
		if (r == null) return;
		if (mc.player == null || mc.gameMode == null || mc.level != r.level) {
			request = null;
			return;
		}
		if (mc.screen != r.seen) { // each window on the way gets its own time to arrive
			r.seen = mc.screen;
			r.started = Util.getMillis();
		}
		if (Util.getMillis() - r.started > REQUEST_MS) {
			String name = StoragePages.label(r.page);
			Toast.show(r.item.isEmpty() ? "Couldn't open " + name + "." : "Couldn't open " + name + ". Your " + Items.name(r.item) + " is in your inventory.", Theme.WARN);
			request = null;
			return;
		}
		if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
		String title = Text.plain(cs.getTitle());
		if (StoragePages.isRoot(title)) {
			if (r.page != StoragePages.ENDER) clickRoot(cs, r);
			return;
		}
		if (StoragePages.pageOfWindow(title) != r.page) return;
		if (r.item.isEmpty()) {
			request = null; // the page is open, which is all the click asked for
			return;
		}
		// The page is open: give the server a moment to send its contents, then put the item back on the cursor.
		if (!StorageCache.settled(cs) || ++r.waited < 3) return;
		List<Slot> content = StorageCache.content(cs, r.page);
		Slot into = r.index >= 0 && r.index < content.size() ? content.get(r.index) : null;
		Slot slot = inventorySlot(cs, r.inventorySlot);
		if (slot != null && ItemStack.isSameItemSameComponents(slot.getItem(), r.item)) {
			// Back on the cursor, so you place it on this page yourself; or, when you clicked one of its slots, straight into that slot.
			mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
			if (into != null) mc.gameMode.handleContainerInput(cs.getMenu().containerId, into.index, r.button, ContainerInput.PICKUP, mc.player);
		}
		request = null; // if it isn't there any more, you moved it yourself in the meantime
	}

	private boolean scrolled(AbstractContainerScreen<?> cs, double dy) {
		if (!showing(cs) || layout == null) return false;
		if (dy != 0) {
			scroll = Math.clamp(scroll + (dy > 0 ? -1 : 1), 0, layout.maxScroll());
			place(cs, false);
		}
		return true;
	}

	/** @return true when the search box took the key */
	private boolean keyPressed(AbstractContainerScreen<?> cs, KeyEvent key) {
		if (!showing(cs)) return false;
		if (key.key() == 266 || key.key() == 267) return scrolled(cs, key.key() == 266 ? 1 : -1); // page up, page down
		if (!typing) return false;
		if (key.key() == 256) typing = false; // escape leaves the box before it closes the window
		else if (key.key() == 259 && !query.isEmpty()) query = query.substring(0, query.length() - 1); // backspace
		else if (key.key() == 257 || key.key() == 335) typing = false; // enter
		return true;
	}

	/** @return true when the search box took the character */
	private boolean charTyped(AbstractContainerScreen<?> cs, CharacterEvent ch) {
		if (!showing(cs) || !typing) return false;
		String typed = ch.codepointAsString();
		if (query.length() < 32 && (!typed.isBlank() || !query.isEmpty() && typed.equals(" "))) query += typed;
		return true;
	}
}
