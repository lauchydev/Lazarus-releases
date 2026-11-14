package dev.lauchy.lazarus.feature.dungeon;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a dungeon reward chest is worth: a panel beside its window adding up its items at Lazarus'
 * prices, less what opening it costs, and the chests you've looked at this run with the best one
 * marked, so you can pick which to open. A chest's button in a menu (its name a chest, a "Cost" in
 * its lore) gets the same sum in its tooltip.
 */
public final class ChestProfit extends Feature {
	/** The one instance, for the window hook and the roll. */
	public static ChestProfit INSTANCE;

	/** One item in a chest: how it looks, its name as the game colours it, how many, and what they're all worth (-1 unpriced). */
	record Reward(ItemStack stack, Component name, String id, int count, long value) {
	}

	/** A chest as last read: its rewards (dearest first), their worth, and the cost to open it. */
	record Chest(String material, List<Reward> rewards, long value, boolean unpriced, ChestLoot.Cost cost) {
		Long profit() {
			return cost == ChestLoot.Cost.NONE || !cost.known() ? null : value - cost.coins();
		}
	}

	/** List the run's chests under the open one. */
	private final BoolSetting compare = add(new BoolSetting("compare", "Compare the run's chests", "List every chest you've looked at this run under the open one, the best marked.", true));
	/** Sum a chest button's lore in its tooltip. */
	private final BoolSetting tooltip = add(new BoolSetting("tooltip", "In chest tooltips", "Add the value and profit to a reward chest's tooltip when its lore lists what's inside.", true));

	/** The panel's padding, row height, gap from the window and narrowest width. */
	private static final int PAD = 5, ROW = 10, GAP = 6, MIN_W = 130;
	/** How often the open chest is read again: prices land and slots fill after it opens. */
	private static final long READ_MS = 500;

	/** The chests read this run, by material, in the order first seen. */
	private final Map<String, Chest> run = new LinkedHashMap<>();
	/** Chest windows whose layout went to the log, so a wrong read can be fixed from it. */
	private final Set<String> logged = new HashSet<>();
	/** The world of the run, to start the list afresh in a new one. */
	private Object level;
	/** The window last read. */
	private AbstractContainerScreen<?> readFor;
	/** The open chest as last read. */
	private Chest open;
	/** When the open chest was last read. */
	private long readAt;

	public ChestProfit() {
		super("chestProfit", "Chest Profit", "Shows what each dungeon reward chest is worth after its cost, and which chest this run pays best.", Category.DUNGEONS, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) { // a new run is a new world
				level = mc.level;
				run.clear();
			}
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs) || material(cs) == null) return;
			ScreenEvents.remove(screen).register(s -> {
				if (readFor == cs) {
					readFor = null;
					open = null;
				}
			});
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!enabled() || !tooltip.on()) return;
			Chest c = fromLore(stack);
			if (c == null) return;
			lines.add(Component.empty());
			lines.add(Component.literal("§7Value: §6" + Text.compact(c.value) + (c.unpriced ? " §8+ ?" : "")));
			Long profit = c.profit();
			if (profit != null) lines.add(Component.literal("§7Profit: " + signed(profit)));
		});
	}

	/** The chest's material when this window is a reward chest ("Bedrock"), else null. */
	static String material(AbstractContainerScreen<?> cs) {
		return ChestLoot.material(Text.plain(cs.getTitle()));
	}

	/** Draws the panel as the window starts drawing its contents (from {@code AbstractContainerScreenMixin}), under its slots and tooltips. */
	public void backdrop(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
		if (!enabled() || material(cs) == null) return;
		long now = Util.getMillis();
		if (readFor != cs || now - readAt > READ_MS) {
			readFor = cs;
			readAt = now;
			open = read(cs);
			if (open != null && !open.rewards.isEmpty()) run.put(open.material, open);
		}
		if (open == null || open.rewards.isEmpty()) return;
		draw(g, cs, open);
	}

	/** The open chest's rewards, dearest first; empty until the server has filled the window. For the roll as well. */
	static Chest read(AbstractContainerScreen<?> cs) {
		String material = material(cs);
		if (material == null) return null;
		List<Reward> rewards = new ArrayList<>();
		ChestLoot.Cost cost = ChestLoot.Cost.NONE;
		List<String> layout = new ArrayList<>();
		// Dragsim's rewards each say how many there are ("Amount: 24"); where any does, a slot that doesn't is a
		// button (the reroll feather, Close), not loot.
		boolean marked = false;
		for (Slot s : cs.getMenu().slots) if (!(s.container instanceof Inventory) && ChestLoot.listed(Items.lore(s.getItem()))) marked = true;
		for (Slot s : cs.getMenu().slots) {
			if (s.container instanceof Inventory) continue;
			ItemStack st = s.getItem();
			if (st.isEmpty()) continue;
			List<String> lore = Items.lore(st);
			layout.add(s.index + ": " + Items.name(st) + " " + lore);
			if (ChestLoot.costLine(lore) >= 0) {
				ChestLoot.Cost c = ChestLoot.cost(lore, ChestProfit::worth);
				if (c != ChestLoot.Cost.NONE) cost = c;
				continue;
			}
			if (marked && !ChestLoot.listed(lore)) continue;
			Reward r = reward(st);
			if (r != null) rewards.add(r);
		}
		// Logged once with rewards and once without, so a window read as empty can be fixed from the log too.
		if (INSTANCE != null && !layout.isEmpty() && INSTANCE.logged.add(material + (rewards.isEmpty() ? " (nothing read)" : ""))) {
			Lazarus.LOG.info("[Chest Profit] {} Chest read as {} rewards, cost {}; its slots: {}", material, rewards.size(), cost, layout);
		}
		return chest(material, rewards, cost);
	}

	private static Chest chest(String material, List<Reward> rewards, ChestLoot.Cost cost) {
		rewards.sort(Comparator.comparingLong(Reward::value).reversed());
		long value = 0;
		boolean unpriced = false;
		for (Reward r : rewards) {
			if (r.value < 0) unpriced = true;
			else value += r.value;
		}
		return new Chest(material, List.copyOf(rewards), value, unpriced, cost);
	}

	/**
	 * A slot's item as a reward, or null when it is the window's dressing: glass, a button, anything
	 * that is neither a known item, an essence nor a book, nor shows a rarity.
	 */
	private static Reward reward(ItemStack st) {
		String name = Items.name(st);
		if (name.isBlank() || BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("glass_pane")) return null;
		String id = Items.id(st);
		Catalog.Item def = id == null ? null : Catalog.item(id);
		// A plain game item ("Chest") is the window's button, not loot: dungeon loot always has a rarity.
		if (def != null && (def.tier() == null || def.tier().isEmpty()) && Items.rarity(st).isEmpty()) return null;
		int stack = ChestLoot.amount(Items.lore(st), st.getCount());
		if (id != null) return new Reward(st, st.getHoverName(), id, stack, times(unitValue(st, id), stack));
		ChestLoot.Counted c = ChestLoot.counted(name);
		String named = Catalog.id(c.name());
		boolean book = st.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
		if (named == null && !book && Items.rarity(st).isEmpty()) return null;
		if (book && named == null) {
			List<String> lore = Items.lore(st);
			if (!lore.isEmpty()) named = Catalog.id(lore.getFirst().trim()); // a book names its enchantment on its first line
		}
		int count = c.count() * stack;
		long each = named == null ? -1 : worth(Catalog.item(named) != null ? Catalog.item(named).name() : c.name());
		return new Reward(st, st.getHoverName(), named, count, times(each, count));
	}

	/** One of this item as a profit tracker counts it (PriceDatabase.trackerValue), upgrades and all; -1 when it has no price. */
	private static long unitValue(ItemStack st, String id) {
		PriceDatabase db = PriceDatabase.INSTANCE;
		if (db == null) return Catalog.npcSell(id) > 0 ? Catalog.npcSell(id) : -1;
		JsonObject data = Items.data(st);
		if (!data.has("entry")) data.addProperty("entry", id);
		long v = db.trackerValue(data, true);
		return v > 0 ? v : -1;
	}

	/** One of an item by name, as the loot trackers price it; -1 when it has no price. */
	private static long worth(String name) {
		long v = PriceDatabase.worth(name);
		return v > 0 ? v : -1;
	}

	private static long times(long each, int count) {
		return each < 0 ? -1 : each * count;
	}

	/**
	 * A chest's button whose lore lists what's in it and what it costs: its rewards read from the
	 * lines before the cost (the first line naming an item onward), or null when it isn't one.
	 */
	private Chest fromLore(ItemStack stack) {
		String material = ChestLoot.material(Items.name(stack));
		if (material == null) return null;
		List<String> lore = Items.lore(stack);
		int costAt = ChestLoot.costLine(lore);
		if (costAt < 0) return null;
		List<Reward> rewards = new ArrayList<>();
		for (int i = 0; i < costAt; i++) {
			String line = lore.get(i).trim();
			if (line.isEmpty()) continue;
			ChestLoot.Counted c = ChestLoot.counted(line);
			String id = Catalog.id(c.name());
			if (id == null) continue; // a heading or a note, not an item
			rewards.add(new Reward(ItemStack.EMPTY, Component.literal(line), id, c.count(), times(worth(c.name()), c.count())));
		}
		if (rewards.isEmpty()) return null;
		Chest chest = chest(material, rewards, ChestLoot.cost(lore, ChestProfit::worth));
		run.put(material, chest);
		return chest;
	}

	private void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> cs, Chest c) {
		Font font = Minecraft.getInstance().font;
		List<String[]> head = new ArrayList<>();
		head.add(new String[]{"Value", "§6" + Text.compact(c.value) + (c.unpriced ? " §8+ ?" : "")});
		if (c.cost != ChestLoot.Cost.NONE) head.add(new String[]{"Cost", c.cost.coins() == 0 && c.cost.known() ? "§aFree" : "§c" + Text.compact(c.cost.coins()) + (c.cost.known() ? "" : " §8+ ?")});
		Long profit = c.profit();
		if (profit != null) head.add(new String[]{"Profit", signed(profit)});
		List<Chest> others = compare.on() && run.size() > 1 ? new ArrayList<>(run.values()) : List.of();
		String best = ChestLoot.best(others.stream().map(Chest::material).toList(), others.stream().map(Chest::profit).toList());

		int w = MIN_W;
		for (String[] r : head) w = Math.max(w, font.width(r[0]) + font.width(r[1]) + 14 + PAD * 2);
		for (Reward r : c.rewards) w = Math.max(w, font.width(r.name) + font.width(rewardWorth(r)) + 12 + PAD * 2);
		int h = PAD + ROW + 2 + head.size() * ROW + 4 + c.rewards.size() * ROW + PAD;
		if (!others.isEmpty()) h += 6 + ROW + others.size() * ROW;
		int x = cs.leftPos + cs.imageWidth + GAP, y = cs.topPos;
		if (x + w > cs.width - 2) x = Math.max(2, cs.leftPos - w - GAP);
		y = Math.max(2, Math.min(y, cs.height - h - 2));

		g.nextStratum();
		Draw.round(g, x, y, w, h, Theme.PANEL);
		int ty = y + PAD;
		Draw.text(g, font, c.material + " Chest", x + PAD, ty, Theme.TEXT);
		ty += ROW + 2;
		for (String[] r : head) {
			Draw.text(g, font, r[0], x + PAD, ty, Theme.MUTED);
			Draw.textRight(g, font, r[1], x + w - PAD, ty, Theme.TEXT);
			ty += ROW;
		}
		Draw.rect(g, x + PAD, ty + 1, w - PAD * 2, 1, Theme.LINE);
		ty += 4;
		for (Reward r : c.rewards) {
			g.text(font, r.name, x + PAD, ty, 0xFFFFFFFF, true);
			Draw.textRight(g, font, rewardWorth(r), x + w - PAD, ty, Theme.TEXT);
			ty += ROW;
		}
		if (others.isEmpty()) return;
		ty += 2;
		Draw.rect(g, x + PAD, ty, w - PAD * 2, 1, Theme.LINE);
		ty += 4;
		Draw.text(g, font, "This run", x + PAD, ty, Theme.MUTED);
		ty += ROW;
		for (Chest o : others) {
			boolean top = o.material.equals(best);
			Long p = o.profit();
			Draw.text(g, font, (top ? "§a★ " : "§8  ") + (o.material.equals(c.material) ? "§f" : "§7") + o.material, x + PAD, ty, Theme.TEXT);
			Draw.textRight(g, font, p == null ? "§6" + Text.compact(o.value) + " §8value" : signed(p), x + w - PAD, ty, Theme.TEXT);
			ty += ROW;
		}
	}

	private static String rewardWorth(Reward r) {
		String count = r.count > 1 && !Items.name(r.stack).matches(".*[x×]\\s?[\\d,]+$") && !r.stack.isEmpty() ? "§8x" + r.count + " " : "";
		return count + (r.value < 0 ? "§8?" : "§6" + Text.compact(r.value));
	}

	private static String signed(long profit) {
		return (profit >= 0 ? "§a+" : "§c-") + Text.compact(Math.abs(profit));
	}
}
