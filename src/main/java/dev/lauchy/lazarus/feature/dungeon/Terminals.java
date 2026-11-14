package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.CommandBlock;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Floor VII terminals: the slots to click are highlighted inside the terminal's own menu.
 * Each terminal is read from its title and contents every frame, so the hints follow
 * the server's updates. Clicks stay yours, but a wrong one is stopped before it's sent: a slot that isn't to be
 * clicked, the wrong button on a rubix pane, a later number before the next, and any click past the count a pane
 * needs (clicks already sent count until the server's update arrives, so clicking fast doesn't overshoot).
 */
public final class Terminals extends Feature {
	private static final Pattern STARTS = Pattern.compile("^What starts with: '(\\w)'\\?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLOR = Pattern.compile("^Select all the ([A-Za-z ]+) items!$", Pattern.CASE_INSENSITIVE);
	private static final int[] RUBIX = {12, 13, 14, 21, 22, 23, 30, 31, 32};
	/** The rubix colour wheel; a left click moves a pane one step forward. Lime is the server's green. */
	private static final List<String> WHEEL = List.of("red", "orange", "yellow", "lime", "blue");
	/** Words an item's name can start with for each colour, dyes' old names included. */
	private static final Map<String, List<String>> WORDS = Map.ofEntries(
			Map.entry("white", List.of("white", "bone meal", "wool")), Map.entry("orange", List.of("orange")),
			Map.entry("magenta", List.of("magenta")), Map.entry("light blue", List.of("light blue")),
			Map.entry("yellow", List.of("yellow", "dandelion")), Map.entry("lime", List.of("lime")),
			Map.entry("pink", List.of("pink")), Map.entry("gray", List.of("gray")),
			Map.entry("light gray", List.of("light gray", "silver")), Map.entry("cyan", List.of("cyan")),
			Map.entry("purple", List.of("purple")), Map.entry("blue", List.of("blue", "lapis")),
			Map.entry("brown", List.of("brown", "cocoa")), Map.entry("green", List.of("green", "cactus")),
			Map.entry("red", List.of("red", "rose")), Map.entry("black", List.of("black", "ink")));
	/** Items that shine without being picked, so their glint says nothing. */
	private static final Set<String> SHINY = Set.of("enchanted_golden_apple", "experience_bottle", "written_book", "nether_star",
			"enchanted_book", "end_crystal", "debug_stick", "golden_apple");

	/** One slot to click: how many times, and with the right button when {@code right}. */
	private record Hint(int slot, int clicks, boolean right, boolean first) {
	}

	private final BoolSetting numbers = add(new BoolSetting("numbers", "Show click counts", "Number the rubix clicks and the order.", true));

	private final BoolSetting block = add(new BoolSetting("block", "Block wrong clicks", "Stop a click on the wrong slot, out of order, or past the count a pane needs. The timed terminal's button is never stopped.", true));

	private final BoolSetting noAbility = add(new BoolSetting("noAbility", "No ability on a terminal", "Right-clicking a terminal only opens it: the item in your hand isn't used, so a teleport doesn't fire.", true));
	private final BoolSetting lead = add(new BoolSetting("lead", "Allow for ping", "In the click-on-time terminal, show the click a ping early, so it lands when the pane is in place.", true));

	public static Terminals INSTANCE;
	/** The beat of the click-on-time terminal's moving pane, to lead it by your ping. */
	private final MelodyTiming timing = new MelodyTiming();
	/** Clicks sent on each slot the server hasn't answered yet: how many, and the item the slot held when they began. */
	private ClickLedger sent = new ClickLedger(0);
	private Object sentFor;

	public Terminals() {
		super("terminals", "Terminals", "Highlights what to click in Floor VII terminals, and stops wrong clicks.", Category.DUNGEONS, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		// A right click on a terminal that the game doesn't count as used goes on to use the item in your hand, and a
		// teleport fires. Saying the click was used sends it to the terminal and stops there.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || !enabled() || !noAbility.on() || !Area.inDungeon()) return InteractionResult.PASS;
			return level.getBlockState(hit.getBlockPos()).getBlock() instanceof CommandBlock ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() || !enabled() || !noAbility.on() || !Area.inDungeon()) return InteractionResult.PASS;
			return Entities.name(entity).contains("Terminal") ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				if (enabled()) draw(g, cs);
			});
		});
	}

	/**
	 * Whether a click in a terminal should be stopped: it isn't on a slot to click, it's the wrong button, it's out of
	 * order, or the slot has had all the clicks it needs. False for any window that isn't a terminal.
	 */
	public boolean intercept(AbstractContainerScreen<?> screen, Slot slot, int button) {
		if (!enabled() || !block.on()) return false;
		List<Slot> slots = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			slots.add(s);
		}
		List<ItemStack> items = slots.stream().map(Slot::getItem).toList();
		String title = Text.plain(screen.getTitle());
		if (!terminal(title, items.size())) return false;
		opened(screen.getMenu(), title);
		int at = slot == null ? -1 : slots.indexOf(slot);
		if (button != 0 && button != 1) return true;
		// The timed terminal: its button is never stopped. When to press it is a matter of your ping, which no
		// reading of the window can be sure of, and a click stopped at the right moment costs more than one let
		// through at the wrong one. Only a click anywhere else is stopped.
		if (title.equals("Click the button on time!")) return at != melodyButton(items);
		// In order: the next number not yet clicked, counting the clicks still on their way, so you can click
		// ahead of the server instead of waiting a ping for each.
		if (title.equals("Click in order!")) return at != nextInOrder(items) || !claim(at, items, 1);
		Hint hint = solve(title, items).stream().filter(h -> h.slot == at).findFirst().orElse(null);
		if (hint == null) return true;
		if (hint.clicks > 0 && hint.right != (button == 1)) return true;
		return !claim(at, items, title.equals("Change all to same color!") ? hint.clicks : 1);
	}

	/** A terminal's window, as it's first looked at: its clicks in flight start afresh. */
	private void opened(AbstractContainerMenu menu, String title) {
		if (menu == sentFor) return;
		sentFor = menu;
		sent = new ClickLedger(title.equals("Change all to same color!") ? WHEEL.size() : 0);
	}

	/**
	 * Takes one of the clicks a slot still needs ({@link ClickLedger}): false when the clicks already on their way
	 * are all it needs. A click is given up on after a second or so, longer on a slow connection.
	 */
	private boolean claim(int at, List<ItemStack> items, int need) {
		ItemStack stack = items.get(at);
		int held = ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
		int step = WHEEL.indexOf(id(stack).replace("_stained_glass_pane", "").replace("green", "lime"));
		return sent.take(at, held, step, need, Util.getMillis(), Math.max(1200, ping() * 3 + 400));
	}

	/** The slot of the next number to click in the order terminal, counting the ones clicked but not yet answered as done; -1 when none is left. */
	private int nextInOrder(List<ItemStack> items) {
		List<Integer> red = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) if (id(items.get(i)).equals("red_stained_glass_pane")) red.add(i);
		red.sort(Comparator.comparingInt(i -> items.get(i).getCount()));
		long now = Util.getMillis(), patience = Math.max(1200, ping() * 3 + 400);
		for (int slot : red) {
			ItemStack stack = items.get(slot);
			if (sent.flying(slot, ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount(), -1, now, patience) == 0) return slot;
		}
		return -1;
	}

	/** The button of the row that's live in the timed terminal, whether or not now is the moment to press it; -1 when no row is. */
	private static int melodyButton(List<ItemStack> items) {
		for (int row = 1; row <= 3; row++) {
			for (int col = 1; col <= 5; col++) if (id(items.get(row * 9 + col)).equals("red_stained_glass_pane")) return row * 9 + 7;
		}
		return -1;
	}

	/** Whether a window with this title is one of the terminals, whatever its size. */
	public static boolean titled(String title) {
		return title.equals("Click in order!") || title.equals("Correct all the panes!") || STARTS.matcher(title).matches() || COLOR.matcher(title).matches()
				|| title.equals("Change all to same color!") || title.equals("Click the button on time!");
	}

	/** Whether a window with this title and size is one of the terminals. */
	private static boolean terminal(String title, int size) {
		return title.equals("Click in order!") && size == 36 || title.equals("Correct all the panes!") && size == 45
				|| STARTS.matcher(title).matches() && size == 45 || COLOR.matcher(title).matches() && size == 54
				|| title.equals("Change all to same color!") && size == 45 || title.equals("Click the button on time!") && size == 54;
	}

	private void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen) {
		List<Slot> slots = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			slots.add(s);
		}
		List<ItemStack> items = slots.stream().map(Slot::getItem).toList();
		List<Hint> hints = solve(Text.plain(screen.getTitle()), items);
		if (hints.isEmpty()) return;
		var font = Minecraft.getInstance().font;
		// The slot to click next breathes gently so it stands out from the rest.
		float pulse = 0.75f + 0.25f * (float) Math.sin(Util.getMillis() / 160.0);
		for (Hint h : hints) {
			Slot s = slots.get(h.slot);
			int x = screen.leftPos + s.x, y = screen.topPos + s.y;
			Draw.round(g, x, y, 16, 16, h.first ? Draw.alpha(0x55FF55, 0.8f * pulse) : h.right ? 0x90FF5555 : 0x8055FFFF);
			if (numbers.on() && h.clicks > 0) Draw.textCentered(g, font, (h.right ? "-" : "") + h.clicks, x + 8, y + 4, 0xFFFFFFFF);
		}
	}

	private static List<Hint> solve(String title, List<ItemStack> items) {
		Matcher m;
		if (title.equals("Click in order!") && items.size() == 36) return order(items);
		if (title.equals("Correct all the panes!") && items.size() == 45) return panes(items);
		if ((m = STARTS.matcher(title)).matches() && items.size() == 45) {
			String letter = m.group(1).toLowerCase(Locale.ROOT);
			return pick(items, n -> n.startsWith(letter));
		}
		if ((m = COLOR.matcher(title)).matches() && items.size() == 54) {
			String colour = m.group(1).toLowerCase(Locale.ROOT).trim();
			List<String> words = WORDS.get(colour.equals("silver") ? "light gray" : colour);
			if (words == null) return List.of();
			// "blue" must not catch "light blue", nor "gray" catch "light gray".
			return pick(items, n -> words.stream().anyMatch(n::startsWith) && !(colour.equals("blue") && n.startsWith("light blue"))
					&& !(colour.equals("gray") && n.startsWith("light gray")));
		}
		if (title.equals("Change all to same color!") && items.size() == 45) return rubix(items);
		if (title.equals("Click the button on time!") && items.size() == 54) return INSTANCE == null ? List.of() : INSTANCE.melody(items);
		return List.of();
	}

	/** Red panes numbered by stack size: the lowest is next. */
	private static List<Hint> order(List<ItemStack> items) {
		List<Integer> red = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) if (id(items.get(i)).equals("red_stained_glass_pane")) red.add(i);
		red.sort(Comparator.comparingInt(i -> items.get(i).getCount()));
		List<Hint> out = new ArrayList<>();
		for (int k = 0; k < Math.min(3, red.size()); k++) out.add(new Hint(red.get(k), items.get(red.get(k)).getCount(), false, k == 0));
		return out;
	}

	private static List<Hint> panes(List<ItemStack> items) {
		List<Hint> out = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) if (id(items.get(i)).equals("red_stained_glass_pane")) out.add(new Hint(i, 0, false, true));
		return out;
	}

	/** Unpicked items in the inner grid whose name passes {@code test}. A picked item gains a glint. */
	private static List<Hint> pick(List<ItemStack> items, Predicate<String> test) {
		List<Hint> out = new ArrayList<>();
		int rows = items.size() / 9;
		for (int i = 0; i < items.size(); i++) {
			int row = i / 9, col = i % 9;
			if (row == 0 || row == rows - 1 || col == 0 || col == 8) continue;
			ItemStack s = items.get(i);
			if (s.isEmpty() || id(s).equals("black_stained_glass_pane")) continue;
			boolean picked = Boolean.TRUE.equals(s.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || s.hasFoil() && !SHINY.contains(id(s));
			if (!picked && test.test(Items.name(s).toLowerCase(Locale.ROOT))) out.add(new Hint(i, 0, false, true));
		}
		return out;
	}

	/** Every pane turned to one colour in the fewest clicks. Left clicks only, each a step forward round the wheel: Dragsim doesn't take a right click to go back. */
	private static List<Hint> rubix(List<ItemStack> items) {
		int[] colours = new int[9];
		for (int i = 0; i < 9; i++) {
			String id = id(items.get(RUBIX[i]));
			colours[i] = WHEEL.indexOf(id.replace("_stained_glass_pane", "").replace("green", "lime"));
			if (colours[i] < 0) return List.of();
		}
		int target = 0, bestCost = Integer.MAX_VALUE;
		for (int t = 0; t < WHEEL.size(); t++) {
			int cost = 0;
			for (int c : colours) cost += turn(c, t);
			if (cost < bestCost) {
				bestCost = cost;
				target = t;
			}
		}
		List<Hint> out = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			int n = turn(colours[i], target);
			if (n != 0) out.add(new Hint(RUBIX[i], n, false, false));
		}
		return out;
	}

	/** Steps forward round the wheel from one colour to another. */
	static int turn(int from, int to) {
		return Math.floorMod(to - from, WHEEL.size());
	}

	/**
	 * Melody: a magenta pane above and below marks the target column; the live track is the
	 * row with red panes, and its lime pane moves. Click that row's button the moment the
	 * lime pane sits in the marked column.
	 */
	private List<Hint> melody(List<ItemStack> items) {
		int target = -1;
		for (int i = 0; i < items.size(); i++) {
			int row = i / 9;
			if ((row == 0 || row >= 4) && id(items.get(i)).equals("magenta_stained_glass_pane")) target = i % 9;
		}
		if (target < 1) return List.of();
		for (int row = 1; row <= 3; row++) {
			int mover = -1;
			boolean live = false;
			for (int col = 1; col <= 5; col++) {
				String id = id(items.get(row * 9 + col));
				if (id.equals("red_stained_glass_pane")) live = true;
				if (id.equals("lime_stained_glass_pane")) mover = col;
			}
			if (!live || mover < 0) continue;
			// Where the pane will be when a click made now lands: a whole ping on from what's on screen.
			long now = Util.getMillis();
			timing.see(row, mover, now);
			if (timing.at(now, lead.on() ? ping() : 0) == target) return List.of(new Hint(row * 9 + 7, 0, false, true));
		}
		return List.of();
	}

	/** Your ping as the server last gave it, in milliseconds; 0 when it hasn't. */
	static long ping() {
		Minecraft mc = Minecraft.getInstance();
		var info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : Math.clamp(info.getLatency(), 0, 1000);
	}

	private static String id(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
	}
}
