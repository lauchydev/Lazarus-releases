package dev.lauchy.lazarus.feature.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.profile.ProfileItems;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A panel beside the Accessory Bag ({@code /acc}) and the crafting menu ({@code /craft}) of every
 * accessory you don't have yet, leaving out those on the way up to one you do ({@link AccessoryPaths}).
 * Each sits on a light slot when the ender chest and storage pages {@link StorageCache} remembers,
 * your inventory and your bag hold everything its recipe takes, and a dark one when they don't;
 * hovering it lists the recipe with how much of each part you have. What you own comes from every bag page you've opened,
 * everything you carry or store, and your profile on dragsim.co.
 */
public final class MissingAccessories extends Feature {
	/** "Accessory Bag", or "Accessory Bag (1/2)" when it has pages. */
	private static final Pattern BAG = Pattern.compile("^Accessory Bag(?: \\(\\d+/\\d+\\))?$");
	/** The crafting menu {@code /craft} opens, where the recipe card is handy while crafting. */
	private static final Pattern CRAFT = Pattern.compile("^(?:Craft Item|Crafting(?: Table)?)$");
	private static final int COLS = 6, CELL = 18, PAD = 4, HEAD = 15, GAP = 6;
	/** How often the list is worked out again while the bag is open, and how often dragsim.co is asked. */
	private static final long REBUILD_MS = 1000, SITE_MS = 120_000;

	/** One missing accessory: how it looks, its recipe (null if unknown) and its parts, and whether you can craft it. */
	private record Entry(String name, ItemStack stack, Recipes.Recipe recipe, Map<String, Integer> needs, boolean craftable) {
	}

	/** Accessories your profile on dragsim.co holds anywhere, by name. */
	private Set<String> siteOwned = Set.of();
	/** Accessories seen in the bag's pages this session, by name: on another page or in /craft, they're still yours. */
	private Set<String> bagOwned = Set.of();
	private long siteAt;
	private List<Entry> entries = List.of();
	/** What you have of each item by name, as of the last rebuild, for the hovered recipe. */
	private Map<String, Integer> haveCounts = Map.of();
	private long builtAt;
	private int scroll;
	/** The accessory whose recipe card is open, by name; null when none is. */
	private String pinned;
	/** How each recipe part looks, by name, made once. */
	private final Map<String, ItemStack> partLooks = new HashMap<>();

	/** The one instance, for the screen background hook. */
	public static MissingAccessories INSTANCE;

	public MissingAccessories() {
		super("missingAccessories", "Missing Accessories", "Lists the accessories you don't have beside the Accessory Bag and /craft, lit when you can craft them.", Category.INVENTORY, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		StorageCache.use(this::enabled);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			builtAt = 0;
			scroll = 0;
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hx, vy) -> !(showing(cs) && scrolled(cs, mx, my, vy)));
			// A click on the panel or the card is theirs: outside the window it would drop what the cursor holds.
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !(showing(cs) && clicked(cs, click.x(), click.y())));
		});
	}

	/**
	 * Draws the panel as the window starts drawing its contents (from {@code AbstractContainerScreenMixin}),
	 * before its slots and tooltips: a tooltip asked for after the screen has drawn never shows.
	 */
	public void backdrop(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
		if (showing(cs)) draw(g, cs, mx, my);
	}

	private boolean showing(AbstractContainerScreen<?> cs) {
		String title = Text.plain(cs.getTitle());
		return enabled() && (BAG.matcher(title).matches() || CRAFT.matcher(title).matches());
	}

	/** Works the list out again: what's missing, sorted craftable first, then by name. */
	private void rebuild(AbstractContainerScreen<?> cs) {
		Map<String, String> idOf = new HashMap<>();
		List<String> names = new ArrayList<>();
		for (String id : Catalog.accessories()) {
			Catalog.Item item = Catalog.item(id);
			if (item == null || idOf.putIfAbsent(item.name(), id) != null) continue;
			names.add(item.name());
		}
		Set<String> accessoryIds = new HashSet<>(idOf.values());
		Map<String, Integer> have = new HashMap<>();
		Set<String> owned = new HashSet<>(siteOwned);
		List<ItemStack> held = new ArrayList<>();
		for (Slot s : cs.getMenu().slots) held.add(s.getItem());
		StorageCache.pages().values().forEach(p -> held.addAll(p.items()));
		for (ItemStack st : held) {
			if (st.isEmpty()) continue;
			String id = Items.id(st);
			Catalog.Item def = id == null ? null : Catalog.item(id);
			String name = def != null ? def.name() : Items.name(st);
			have.merge(name, st.getCount(), Integer::sum);
			if (id != null && accessoryIds.contains(id)) owned.add(name);
		}
		if (BAG.matcher(Text.plain(cs.getTitle())).matches()) {
			Set<String> seen = new HashSet<>(bagOwned);
			seen.addAll(owned);
			bagOwned = Set.copyOf(seen);
		}
		owned.addAll(bagOwned);
		for (String name : owned) have.merge(name, 1, Integer::max); // the bag counts toward an upgrade's recipe
		Recipes recipes = Recipes.INSTANCE;
		Map<String, Set<String>> below = AccessoryPaths.below(names, n -> {
			Recipes.Recipe r = recipes == null ? null : recipes.recipe(n);
			return r == null ? List.of() : r.needs().keySet();
		});
		List<Entry> out = new ArrayList<>();
		for (String name : AccessoryPaths.missing(names, owned, below)) {
			Recipes.Recipe r = recipes == null ? null : recipes.recipe(name);
			Map<String, Integer> needs = r == null ? Map.of() : r.needs();
			out.add(new Entry(name, ProfileItems.plain(idOf.get(name)), r, needs, AccessoryPaths.craftable(needs, have)));
		}
		out.sort(Comparator.comparing((Entry e) -> !e.craftable).thenComparing(Entry::name));
		entries = out;
		haveCounts = have;
	}

	/** Asks dragsim.co what your profile holds now and then, for the accessories in bag pages you haven't opened. */
	private void askSite() {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		if (mc.player == null || now - siteAt < SITE_MS) return;
		siteAt = now;
		Set<String> accessoryIds = new HashSet<>(Catalog.accessories());
		Http.json("/api/player/" + Http.encode(mc.player.getName().getString()) + "?mode=main").thenAccept(json -> {
			if (!(json instanceof JsonObject root) || !(root.get("profile") instanceof JsonObject profile)) return;
			Set<String> found = new HashSet<>();
			collect(profile, accessoryIds, found);
			mc.execute(() -> {
				siteOwned = Set.copyOf(found);
				builtAt = 0;
			});
		});
	}

	/** The names of the accessories anywhere in a profile: any object whose "entry" is one. */
	private static void collect(JsonElement e, Set<String> accessoryIds, Set<String> out) {
		if (e instanceof JsonObject o) {
			if (o.get("entry") instanceof JsonElement entry && entry.isJsonPrimitive()) {
				String id = entry.getAsString().toLowerCase(java.util.Locale.ROOT);
				Catalog.Item item = accessoryIds.contains(id) ? Catalog.item(id) : null;
				if (item != null) out.add(item.name());
			}
			for (var v : o.entrySet()) collect(v.getValue(), accessoryIds, out);
		} else if (e != null && e.isJsonArray()) {
			for (JsonElement v : e.getAsJsonArray()) collect(v, accessoryIds, out);
		}
	}

	/** Where the panel sits: right of the window, or left of it when there's no room on the right. */
	private int[] box(AbstractContainerScreen<?> cs) {
		int w = COLS * CELL + PAD * 2;
		int rows = Math.max(1, (cs.imageHeight - HEAD - PAD) / CELL);
		int shown = Math.max(1, Math.min(rows, (entries.size() + COLS - 1) / COLS));
		int h = HEAD + shown * CELL + PAD;
		int x = cs.leftPos + cs.imageWidth + GAP;
		if (x + w > cs.width - 2) x = Math.max(2, cs.leftPos - w - GAP);
		return new int[]{x, cs.topPos, w, h, rows};
	}

	private void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> cs, int mx, int my) {
		askSite();
		long now = Util.getMillis();
		if (now - builtAt > REBUILD_MS) {
			builtAt = now;
			rebuild(cs);
		}
		Font font = Minecraft.getInstance().font;
		int[] b = box(cs);
		int x = b[0], y = b[1], w = b[2], h = b[3], rows = b[4];
		int maxScroll = Math.max(0, (entries.size() + COLS - 1) / COLS - rows);
		scroll = Math.clamp(scroll, 0, maxScroll);
		g.nextStratum();
		Draw.round(g, x, y, w, h, Theme.PANEL);
		Draw.text(g, font, "Missing", x + PAD + 1, y + 4, Theme.TEXT);
		Draw.textRight(g, font, entries.size() + (maxScroll > 0 ? " ↕" : ""), x + w - PAD - 1, y + 4, Theme.MUTED);
		if (entries.isEmpty()) {
			Draw.text(g, font, "None!", x + PAD + 1, y + HEAD + 4, Theme.GOOD);
			return;
		}
		Entry hovered = null;
		for (int i = scroll * COLS; i < entries.size() && i < (scroll + rows) * COLS; i++) {
			Entry e = entries.get(i);
			int at = i - scroll * COLS;
			int cx = x + PAD + (at % COLS) * CELL, cy = y + HEAD + (at / COLS) * CELL;
			slot(g, cx, cy, e.craftable);
			g.item(e.stack, cx + 1, cy + 1);
			if (Draw.inside(mx, my, cx, cy, CELL, CELL)) {
				hovered = e;
				Draw.rect(g, cx + 1, cy + 1, 16, 16, 0x80FFFFFF);
			}
		}
		List<Component> tip = hovered != null ? tooltip(hovered) : null;
		Entry open = entries.stream().filter(e -> e.name.equals(pinned)).findFirst().orElse(null);
		if (open != null) {
			List<Component> partTip = card(g, font, cs, open, mx, my);
			if (partTip != null) tip = partTip;
		}
		if (tip != null) g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
	}

	/** Where the recipe card sits: right of the panel, or under it when there's no room. */
	private int[] cardBox(AbstractContainerScreen<?> cs, Font font, Entry e) {
		int[] b = box(cs);
		int w = Math.max(PAD * 2 + 3 * CELL + 20 + CELL, font.width(e.name) + PAD * 2 + 2), h = HEAD + 3 * CELL + PAD;
		int x = b[0] + b[2] + GAP, y = b[1];
		if (x + w > cs.width - 2) {
			x = b[0];
			y = Math.min(b[1] + b[3] + GAP, cs.height - h - 2);
		}
		return new int[]{x, y, w, h};
	}

	/**
	 * The open recipe card: the accessory's 3x3 recipe with each part on a light slot when you have
	 * enough of it and a dark one when you don't, and the accessory itself. Returns the tooltip of
	 * what's under the mouse, or null.
	 */
	private List<Component> card(GuiGraphicsExtractor g, Font font, AbstractContainerScreen<?> cs, Entry e, int mx, int my) {
		int[] c = cardBox(cs, font, e);
		int x = c[0], y = c[1], w = c[2], h = c[3];
		Draw.round(g, x, y, w, h, Theme.PANEL);
		Draw.text(g, font, e.name, x + PAD + 1, y + 4, Theme.TEXT);
		int gx = x + PAD, gy = y + HEAD;
		if (e.recipe == null) {
			Draw.text(g, font, "Recipe not learned.", gx + 1, gy + 4, Theme.MUTED);
			Draw.text(g, font, "Open it in /recipes.", gx + 1, gy + 16, Theme.FAINT);
			return null;
		}
		List<Component> tip = null;
		List<int[]> counts = new ArrayList<>();
		for (int i = 0; i < 9 && i < e.recipe.grid().size(); i++) {
			int sx = gx + (i % 3) * CELL, sy = gy + (i / 3) * CELL;
			Recipes.Part part = e.recipe.grid().get(i);
			if (part == null) {
				slot(g, sx, sy, true);
				continue;
			}
			int have = haveCounts.getOrDefault(part.name(), 0), need = e.needs.getOrDefault(part.name(), part.count());
			slot(g, sx, sy, have >= need);
			ItemStack look = partLook(part.name());
			g.item(look, sx + 1, sy + 1);
			if (part.count() > 1) counts.add(new int[]{sx, sy, part.count()});
			if (Draw.inside(mx, my, sx, sy, CELL, CELL)) {
				Draw.rect(g, sx + 1, sy + 1, 16, 16, 0x80FFFFFF);
				tip = List.of(look.getHoverName(),
						Component.literal((have >= need ? "§a" : "§c") + "Have " + Text.compact(have) + " §8of " + Text.compact(need) + " needed"));
			}
		}
		g.nextStratum(); // the counts go over the items, as a slot's stack size does
		for (int[] n : counts) Draw.textRight(g, font, Text.compact(n[2]), n[0] + 18, n[1] + 10, 0xFFFFFFFF);
		int ax = gx + 3 * CELL + 4, ry = gy + CELL;
		Draw.text(g, font, "➜", ax, ry + 5, Theme.MUTED);
		int rx = ax + 12;
		slot(g, rx, ry, e.craftable);
		g.item(e.stack, rx + 1, ry + 1);
		if (Draw.inside(mx, my, rx, ry, CELL, CELL)) tip = tooltip(e);
		return tip;
	}

	/** How a recipe part looks: the catalogue's item of that name, or paper named for it when the catalogue doesn't know it. */
	private ItemStack partLook(String name) {
		return partLooks.computeIfAbsent(name, n -> {
			String id = Catalog.id(n);
			if (id != null) return ProfileItems.plain(id);
			ItemStack paper = new ItemStack(net.minecraft.world.item.Items.PAPER);
			paper.set(DataComponents.CUSTOM_NAME, Component.literal("§f" + n));
			return paper;
		});
	}

	/** A click on an accessory opens its recipe card, or closes it when it's open; true for any click on the panel or card. */
	private boolean clicked(AbstractContainerScreen<?> cs, double mx, double my) {
		Entry open = entries.stream().filter(e -> e.name.equals(pinned)).findFirst().orElse(null);
		if (open != null) {
			int[] c = cardBox(cs, Minecraft.getInstance().font, open);
			if (Draw.inside(mx, my, c[0], c[1], c[2], c[3])) return true;
		}
		int[] b = box(cs);
		if (!Draw.inside(mx, my, b[0], b[1], b[2], b[3])) return false;
		for (int i = scroll * COLS; i < entries.size() && i < (scroll + b[4]) * COLS; i++) {
			int at = i - scroll * COLS;
			if (!Draw.inside(mx, my, b[0] + PAD + (at % COLS) * CELL, b[1] + HEAD + (at / COLS) * CELL, CELL, CELL)) continue;
			String name = entries.get(i).name;
			pinned = name.equals(pinned) ? null : name;
		}
		return true;
	}

	/** A slot as the game draws one: dark top-left edge, white bottom-right, light grey inside when craftable, dark grey when not. */
	private static void slot(GuiGraphicsExtractor g, int x, int y, boolean craftable) {
		Draw.rect(g, x, y, CELL, CELL, 0xFF373737);
		Draw.rect(g, x + 1, y + 1, CELL - 1, CELL - 1, 0xFFFFFFFF);
		Draw.rect(g, x + 1, y + 1, CELL - 2, CELL - 2, craftable ? 0xFF8B8B8B : 0xFF5D5D5D);
	}

	/** The accessory's name, then its recipe part by part with how many you have, then whether you can craft it. */
	private List<Component> tooltip(Entry e) {
		List<Component> lines = new ArrayList<>();
		lines.add(e.stack.getHoverName());
		lines.add(Component.empty());
		if (e.needs.isEmpty()) {
			lines.add(Component.literal("§7No recipe learned yet."));
			lines.add(Component.literal("§8Open it in /recipes once to learn it."));
			lines.add(Component.literal("§eClick to pin it."));
			return lines;
		}
		lines.add(Component.literal("§7Recipe"));
		for (var part : e.needs.entrySet()) {
			int have = haveCounts.getOrDefault(part.getKey(), 0);
			boolean enough = have >= part.getValue();
			lines.add(Component.literal((enough ? "§a✔ " : "§c✘ ") + "§f" + Text.compact(part.getValue()) + "x " + part.getKey()
					+ " §8(" + Text.compact(have) + ")"));
		}
		lines.add(Component.empty());
		lines.add(Component.literal(e.craftable ? "§aYou have everything to craft it." : "§cYou're missing parts."));
		lines.add(Component.literal("§8Counts your inventory, bag, ender chest and storage pages you've opened."));
		lines.add(Component.literal(e.name.equals(pinned) ? "§eClick to close the recipe." : "§eClick to see the recipe."));
		return lines;
	}

	/** Scrolls the list a row at a time when the wheel turns over it; true when it did. */
	private boolean scrolled(AbstractContainerScreen<?> cs, double mx, double my, double vy) {
		int[] b = box(cs);
		if (!Draw.inside(mx, my, b[0], b[1], b[2], b[3])) return false;
		scroll -= (int) Math.signum(vy);
		return true;
	}
}
