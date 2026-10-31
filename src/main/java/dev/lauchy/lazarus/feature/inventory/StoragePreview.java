package dev.lauchy.lazarus.feature.inventory;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Shows a storage page's contents when you hover it in the Storage menu's own view (the overview's
 * Vanilla view), from the pages {@link StorageCache} remembers. {@code /lazarus find <item>} searches
 * all remembered pages, and matching pages glow in the Storage menu.
 */
public final class StoragePreview extends Feature {
	private static final int ENDER = StoragePages.ENDER;

	private final IntSetting delay = add(new IntSetting("delay", "Hover delay", "How long to hover before the preview opens.", 150, 0, 1000, "ms"));
	private final BoolSetting counts = add(new BoolSetting("counts", "Stack sizes", "Show the count on each stack.", true));

	/** What {@code /lazarus find} last searched for, lower case; empty when nothing is searched. */
	private static String search = "";
	private int hoverPage = -1;
	private long hoverSince;

	public StoragePreview() {
		super("storagePreview", "Storage Preview", "Hover a storage page to see inside it; /lazarus find searches them.", Category.INVENTORY, true);
	}

	/** What {@code /lazarus find} last searched for, so the overview's search box can start with it. */
	public static String search() {
		return search;
	}

	@Override
	public void init() {
		StorageCache.use(this::enabled);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				if (enabled() && StoragePages.isRoot(Text.plain(cs.getTitle())) && !StorageOverview.showing(cs)) drawRoot(g, cs, mx, my);
			});
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("find")
				.then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> {
					find(StringArgumentType.getString(c, "item"));
					return 1;
				}))));
	}

	private void drawRoot(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int mx, int my) {
		Font font = Minecraft.getInstance().font;
		g.nextStratum();
		if (!search.isEmpty()) {
			for (Slot s : screen.getMenu().slots) {
				int page = slotPage(s);
				if (page >= 0 && contains(page, search)) Draw.outline(g, screen.leftPos + s.x - 1, screen.topPos + s.y - 1, 18, 18, Theme.GOOD);
			}
		}
		int page = slotPage(screen.hoveredSlot);
		long now = Util.getMillis();
		if (page != hoverPage) {
			hoverPage = page;
			hoverSince = now;
		}
		if (page < 0 || now - hoverSince < delay.get()) return;
		StorageCache.Page cached = StorageCache.pages().get(page);
		List<ItemStack> items = cached == null ? null : cached.items();
		String title = page == ENDER ? "Ender Chest" : "Storage Page " + page;
		int cols = 9, rows = items == null ? 1 : Math.max(1, (items.size() + 8) / 9);
		int w = cols * 18 + 8, h = rows * 18 + 18;
		int x = Math.min(mx + 12, g.guiWidth() - w - 2), y = Math.clamp(my - h / 2, 2, Math.max(2, g.guiHeight() - h - 2));
		float in = Anim.since(hoverSince + delay.get(), 160);
		y += Math.round((1f - in) * 4);
		Draw.opacity = in;
		try {
			Draw.round(g, x, y, w, h, Theme.PANEL);
			Draw.round(g, x + 5, y + 6, 3, 3, Theme.ACCENT);
			Draw.text(g, font, title, x + 11, y + 4, Theme.TEXT);
			if (items == null) {
				Draw.text(g, font, "Open it once to remember it.", x + 4, y + 16, Theme.MUTED);
				return;
			}
			Draw.slotGrid(g, x + 4, y + 15, cols, rows, 0x30FFFFFF);
			for (int i = 0; i < items.size(); i++) {
				ItemStack st = items.get(i);
				int cx = x + 4 + (i % 9) * 18, cy = y + 15 + (i / 9) * 18;
				Draw.rect(g, cx, cy, 16, 16, 0x10FFFFFF);
				if (st.isEmpty()) continue;
				if (!search.isEmpty() && matches(st, search)) Draw.round(g, cx, cy, 16, 16, 0x6055FF55);
				g.item(st, cx, cy);
				if (counts.on()) g.itemDecorations(font, st, cx, cy);
			}
		} finally {
			Draw.opacity = 1f;
		}
	}

	private static int slotPage(Slot slot) {
		if (slot == null || slot.container instanceof Inventory || slot.getItem().isEmpty()) return -1;
		String name = Items.name(slot.getItem());
		int page = StoragePages.pageOfButton(name);
		if (page >= 0) return page;
		return name.equals("Ender Chest") ? ENDER : -1;
	}

	private boolean contains(int page, String q) {
		StorageCache.Page cached = StorageCache.pages().get(page);
		return cached != null && cached.items().stream().anyMatch(s -> matches(s, q));
	}

	static boolean matches(ItemStack s, String q) {
		return !s.isEmpty() && Items.name(s).toLowerCase(Locale.ROOT).contains(q);
	}

	private void find(String query) {
		search = query.trim().toLowerCase(Locale.ROOT);
		if (StorageCache.pages().isEmpty()) {
			Message.lazarus().info("No storage pages remembered yet. Open each page once.").send();
			return;
		}
		int total = 0;
		Message m = Message.lazarus();
		for (var e : StorageCache.pages().entrySet()) {
			int n = 0;
			for (ItemStack s : e.getValue().items()) if (matches(s, search)) n += s.getCount();
			if (n == 0) continue;
			if (total > 0) m.line();
			total += n;
			m.highlight(e.getKey() == ENDER ? "Ender Chest" : "Page " + e.getKey()).info(": ").success(n + "x");
		}
		if (total == 0) m.info("Nothing matching \"" + query + "\" in remembered storage.");
		else m.line().info("Matching pages glow in ").highlight("/storage").info(".");
		m.send();
	}
}
