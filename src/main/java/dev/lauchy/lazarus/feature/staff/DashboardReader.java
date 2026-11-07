package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Sounds;
import dev.lauchy.lazarus.core.Text;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Works the server's Flag Dashboard behind your back. A read sends {@code /staff}, goes through the Staff Command
 * Center's Staff Tools Panel (where /staff has opened since Dragsim's 2026-11 update), clicks the dashboard,
 * reads every player's head (turning the pages), closes the menu and puts back the screen you had; a read
 * that reached the last page is shared with every staff member ({@link FlagSync#dashboard}). An open
 * goes the same way to one player's head, clicks it, and shows you their details; an alert read does that out of
 * sight and reads their alerts ({@link FlagAlerts}) page by page instead, back to an hour ago. While the menus are
 * worked through, the screen you came from draws in their place and gets your keys and clicks
 * ({@link #hiding()}). One at a time; the server not answering ends it with no result, and leaving the
 * screen you came from abandons it.
 */
public final class DashboardReader {
	/** Ticks to wait for the server at each step, ticks to let a click's answer arrive, and the most pages read. */
	private static final int WAIT_TICKS = 60, CLICK_TICKS = 3, MAX_PAGES = 30;
	/** The most pages of one player's alerts read. */
	private static final int MAX_ALERT_PAGES = 12;
	/** The titles of the staff panel and the dashboard, and the panel's button to it, in lower case. */
	private static final String PANEL = "staff panel", BOARD = "flag dashboard", BUTTON = "flag dashboard";
	/** Since 2026-11, /staff opens the Staff Command Center first; its "Staff Tools Panel" button leads to the panel. */
	private static final String CENTER = "staff command center", TO_PANEL = "staff tools panel";

	/**
	 * Where a run is: off, waiting for the panel, for the dashboard, reading a page, turning one, opening a
	 * player's details, reading a page of their alerts, turning one, or abandoned and closing any staff menu the
	 * server still sends.
	 */
	private enum Step {OFF, OPENING, TO_BOARD, READING, TURNING, TO_DETAILS, ALERTS, ALERTS_TURNING, DISCARDING}

	/** Where the run under way is. */
	private static Step step = Step.OFF;
	/** The screen to put back afterwards, and to draw in the hidden menu's place meanwhile. */
	private static Screen restore;
	/** Who gets a read's result, or null on an open. */
	private static Consumer<Map<String, DashboardLore.Entry>> done;
	/** Who hears whether an open worked, or null on a read. */
	private static Consumer<Boolean> opened;
	/** Who gets an alert read's alerts (null when they couldn't be read), or null on any other run. */
	private static Consumer<List<FlagAlerts.Alert>> alertsRead;
	/** The alerts read so far. */
	private static final List<FlagAlerts.Alert> alerts = new ArrayList<>();
	/** Whether the first page of alerts went newest first, so a page all older than an hour means the rest are too. */
	private static boolean newestFirst;
	/** The player (lower case) whose details an open is after, or null on a read. */
	private static String target;
	/** The flagged players read so far, by lowercase name. */
	private static final Map<String, DashboardLore.Entry> found = new LinkedHashMap<>();
	/** Every page read so far, to tell when a turn hasn't happened yet or the pages wrapped round. */
	private static final Set<String> pagesSeen = new HashSet<>();
	/** Ticks spent waiting at this step, ticks to hold off after a click, and pages read. */
	private static int waited, delay, pages;
	/** Whether the tick hook is registered. */
	private static boolean ready;
	/** Whether the read reached the dashboard's last page, so it holds every flagged player and may replace the shared read. */
	private static boolean complete;
	/** The menu the reader last closed itself, or null, so the server's echo of that close is ignored. */
	private static ClosedMenu closed;
	/** When the reader last pressed a menu button: the server's click sound for it, played at you, is kept quiet. */
	private static long pressedAt;
	private static final long CLICK_SOUND_MS = 500;

	private DashboardReader() {
	}

	/**
	 * Whether the server closing menu {@code id} only echoes a close the reader made, and must be ignored:
	 * Minecraft would close whatever screen is open, the roster the reader has just put back.
	 */
	public static boolean ignoresClose(int id) {
		return closed != null && closed.echoedBy(id, Util.getMillis());
	}

	/** Closes the open menu with the server, remembering it so the server's echo is ignored. */
	private static void close(Minecraft mc) {
		closed = new ClosedMenu(mc.player.containerMenu.containerId, Util.getMillis());
		mc.player.closeContainer();
	}

	/** Whether a menu being worked through is on screen and should stay hidden, its input going to the screen you came from. */
	public static boolean hiding() {
		return step != Step.OFF && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
	}

	/**
	 * Draws the screen the run will return to where the hidden menu would draw, so it seems never to have
	 * left: its background with {@code background}, else the rest of it. Nothing when there's no such screen.
	 */
	public static void cover(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, boolean background) {
		Screen s = restore;
		if (s == null) return;
		if (background) s.extractBackground(g, mouseX, mouseY, delta);
		else s.extractRenderState(g, mouseX, mouseY, delta);
	}

	/** A click on the hidden menu, handed to the screen drawn in its place. */
	public static void click(MouseButtonEvent event, boolean doubleClick) {
		if (restore != null) restore.mouseClicked(event, doubleClick);
	}

	/** A button let go over the hidden menu, handed to the screen drawn in its place. */
	public static void release(MouseButtonEvent event) {
		if (restore != null) restore.mouseReleased(event);
	}

	/** The wheel over the hidden menu, handed to the screen drawn in its place. */
	public static void scroll(double mx, double my, double dx, double dy) {
		if (restore != null) restore.mouseScrolled(mx, my, dx, dy);
	}

	/** A key pressed while the menu is hidden, handed to the screen drawn in its place (so Esc closes that, not the menu). */
	public static void key(KeyEvent event) {
		if (restore != null) restore.keyPressed(event);
	}

	/**
	 * Reads the dashboard and runs {@code then} on the game thread with every player who has a flag that counts
	 * (by lowercase name), or null when it couldn't be read. {@code back} is the screen to return to afterwards.
	 */
	static void read(Screen back, Consumer<Map<String, DashboardLore.Entry>> then) {
		if (!start(back)) {
			then.accept(null);
			return;
		}
		done = then;
	}

	/**
	 * Opens one player's details on the dashboard, out of sight until they show; {@code then} hears false (with
	 * {@code back} put back) when they aren't on it or the server didn't answer.
	 */
	static void open(String player, Screen back, Consumer<Boolean> then) {
		if (!start(back)) {
			then.accept(false);
			return;
		}
		target = player.toLowerCase(Locale.ROOT);
		opened = then;
	}

	/**
	 * Reads one player's alerts off their page on the dashboard, out of sight, back to an hour ago (or every page, when
	 * the page isn't newest first), and puts {@code back} back; {@code then} gets them, or null when the player isn't
	 * on the dashboard or the server didn't answer.
	 */
	static void alerts(String player, Screen back, Consumer<List<FlagAlerts.Alert>> then) {
		if (!start(back)) {
			then.accept(null);
			return;
		}
		target = player.toLowerCase(Locale.ROOT);
		alertsRead = then;
	}

	/** The player (lower case) whose alerts are being read, or null when no alert read is under way. */
	static String readingAlertsOf() {
		return working() && alertsRead != null ? target : null;
	}

	/** Sets a run going from the start, or says it can't (outside a world, or another run under way). */
	private static boolean start(Screen back) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || working()) return false;
		if (!ready) {
			ready = true;
			ClientTickEvents.END_CLIENT_TICK.register(DashboardReader::tick);
			// The server answers each press with a click sound at your feet; a menu worked behind your back shouldn't be heard.
			Sounds.register((id, x, y, z) -> {
				var me = Minecraft.getInstance().player;
				return step != Step.OFF && Util.getMillis() - pressedAt < CLICK_SOUND_MS && me != null && me.distanceToSqr(x, y, z) < 9;
			});
		}
		restore = back;
		done = null;
		opened = null;
		alertsRead = null;
		target = null;
		found.clear();
		alerts.clear();
		pagesSeen.clear();
		pages = 0;
		waited = 0;
		delay = 0;
		complete = false;
		throughCenter = false;
		step = Step.OPENING;
		mc.player.connection.sendCommand("staff");
		return true;
	}

	/** Whether this run has already pressed the Command Center's way through to the panel. */
	private static boolean throughCenter;

	/** Whether a run is under way that will hand a result back (not one being discarded). */
	static boolean working() {
		return step != Step.OFF && step != Step.DISCARDING;
	}

	/**
	 * Stops the run without a result and without putting anything back: the screen you came from is closing.
	 * A hidden menu is closed with the server, and one the server still sends in the next few seconds is closed
	 * as it arrives rather than shown.
	 */
	static void abandon() {
		if (!working()) return;
		step = Step.DISCARDING;
		waited = 0;
		delay = 0;
		restore = null;
		done = null;
		opened = null;
		alertsRead = null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) close(mc);
	}

	/** One step each tick: open the panel, click through to the dashboard, read or search each page, turn, finish. */
	private static void tick(Minecraft mc) {
		if (step == Step.OFF) return;
		if (mc.player == null || mc.gameMode == null) {
			finish(mc, false);
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		AbstractContainerScreen<?> screen = mc.screen instanceof AbstractContainerScreen<?> cs ? cs : null;
		String title = screen == null ? "" : Text.plain(screen.getTitle()).toLowerCase(Locale.ROOT);
		if (step == Step.DISCARDING) {
			if (title.contains(PANEL) || title.contains(BOARD) || title.contains(CENTER)) {
				close(mc);
				step = Step.OFF;
			} else if (++waited > WAIT_TICKS) {
				step = Step.OFF;
			}
			return;
		}
		// The screen you came from was swapped for another (the staff screen, a profile): leave it there.
		if (mc.screen != null && screen == null && mc.screen != restore) {
			finish(mc, false);
			return;
		}
		switch (step) {
			case OPENING -> {
				// /staff opens the Command Center now: one press through to the panel, then on as before.
				if (title.contains(CENTER) && !throughCenter) {
					int slot = find(screen, TO_PANEL);
					if (slot < 0) {
						finish(mc, false);
						return;
					}
					press(mc, screen, slot);
					throughCenter = true;
					waited = 0;
					return;
				}
				if (title.contains(PANEL)) {
					int slot = find(screen, BUTTON);
					if (slot < 0) {
						finish(mc, false);
						return;
					}
					press(mc, screen, slot);
					step = Step.TO_BOARD;
					waited = 0;
					return;
				}
				if (++waited > WAIT_TICKS) finish(mc, false);
			}
			case TO_BOARD -> {
				if (title.contains(BOARD)) {
					step = Step.READING;
					waited = 0;
					delay = CLICK_TICKS;
					return;
				}
				if (++waited > WAIT_TICKS) finish(mc, false);
			}
			case READING -> {
				if (!title.contains(BOARD)) {
					finish(mc, false);
					return;
				}
				List<Head> heads = heads(screen);
				String page = String.join("|", heads.stream().map(h -> h.key).toList());
				if (page.isEmpty() || !pagesSeen.add(page)) {
					// Empty means the heads haven't arrived yet; a page already read means the turn hasn't happened.
					if (++waited > WAIT_TICKS / 2) finish(mc, target == null && (!found.isEmpty() || pages > 0));
					return;
				}
				pages++;
				waited = 0;
				for (Head h : heads) {
					if (h.entry == null) continue;
					if (target != null && h.entry.name().equalsIgnoreCase(target)) {
						press(mc, screen, h.slot);
						step = Step.TO_DETAILS;
						return;
					}
					if (!h.entry.flags().isEmpty()) found.put(h.entry.name().toLowerCase(Locale.ROOT), h.entry);
				}
				int next = find(screen, "next");
				if (next < 0 || pages >= MAX_PAGES) {
					complete = next < 0;
					finish(mc, target == null);
					return;
				}
				press(mc, screen, next);
				step = Step.TURNING;
			}
			case TURNING -> {
				if (title.contains(BOARD)) {
					step = Step.READING;
					delay = CLICK_TICKS;
					return;
				}
				if (++waited > WAIT_TICKS) finish(mc, target == null);
			}
			case TO_DETAILS -> {
				// An alert read stays out of sight and reads the player's page once it's up.
				if (screen != null && alertsRead != null && FlagAlerts.player(Text.plain(screen.getTitle())) != null) {
					step = Step.ALERTS;
					pagesSeen.clear();
					pages = 0;
					waited = 0;
					delay = CLICK_TICKS;
					return;
				}
				// The details are up once the dashboard gives way to another menu: show it, and put nothing back.
				if (screen != null && alertsRead == null && !title.contains(BOARD)) {
					Consumer<Boolean> then = opened;
					step = Step.OFF;
					restore = null;
					opened = null;
					if (then != null) then.accept(true);
					return;
				}
				if (++waited > WAIT_TICKS) finish(mc, false);
			}
			case ALERTS -> readAlerts(mc, screen);
			case ALERTS_TURNING -> {
				if (screen != null && FlagAlerts.player(Text.plain(screen.getTitle())) != null) {
					step = Step.ALERTS;
					delay = CLICK_TICKS;
					return;
				}
				if (++waited > WAIT_TICKS) finish(mc, true);
			}
			default -> {
			}
		}
	}

	/** One page of a player's alerts: read what's on it, then turn to the next, or finish once an hour back is reached. */
	private static void readAlerts(Minecraft mc, AbstractContainerScreen<?> screen) {
		if (screen == null || FlagAlerts.player(Text.plain(screen.getTitle())) == null) {
			finish(mc, pages > 0);
			return;
		}
		long now = System.currentTimeMillis();
		List<FlagAlerts.Alert> page = new ArrayList<>();
		StringBuilder key = new StringBuilder();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) break;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			List<String> lore = Items.lore(stack);
			key.append(Items.name(stack)).append('|').append(String.join("/", lore)).append('\n');
			FlagAlerts.Alert a = FlagAlerts.read(Items.name(stack), lore, now);
			if (a != null) page.add(a);
		}
		if (key.isEmpty() || !pagesSeen.add(key.toString())) {
			// Nothing yet means the items haven't arrived; a page already read means the turn hasn't happened.
			if (++waited > WAIT_TICKS / 2) finish(mc, true);
			return;
		}
		pages++;
		waited = 0;
		alerts.addAll(page);
		if (pages == 1) newestFirst = newestFirst(page);
		long from = now - FlagAlerts.WINDOW_MS;
		boolean allOlder = !page.isEmpty() && page.stream().allMatch(a -> a.at() > 0 && a.at() <= from);
		int next = nextPage(screen);
		if (next < 0 || pages >= MAX_ALERT_PAGES || newestFirst && allOlder) {
			finish(mc, true);
			return;
		}
		press(mc, screen, next);
		step = Step.ALERTS_TURNING;
	}

	/** Whether a page's alerts go newest first (or say nothing either way). */
	private static boolean newestFirst(List<FlagAlerts.Alert> page) {
		for (int i = 1; i < page.size(); i++) if (page.get(i).at() > page.get(i - 1).at()) return false;
		return true;
	}

	/** The slot of the alert page's next-page arrow, or -1: a button named "next", never an alert. */
	private static int nextPage(AbstractContainerScreen<?> screen) {
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).container instanceof Inventory) break;
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) continue;
			String name = Items.name(stack);
			if (name.toLowerCase(Locale.ROOT).contains("next") && FlagAlerts.read(name, Items.lore(stack), 0) == null) return i;
		}
		return -1;
	}

	/** One item on the dashboard: its slot, how it reads, and a key to tell a page from the one before. */
	private record Head(int slot, String key, DashboardLore.Entry entry) {
	}

	/** Every item in the open menu (not your inventory), each read as a dashboard entry where it is one. */
	private static List<Head> heads(AbstractContainerScreen<?> screen) {
		List<Head> out = new ArrayList<>();
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (slot.container instanceof Inventory) break;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String name = Items.name(stack);
			out.add(new Head(i, name, DashboardLore.read(name, Items.lore(stack))));
		}
		return out;
	}

	/**
	 * The slot of the open window's button whose name contains {@code text} (ignoring case), or -1. A player's
	 * head is never a button, so one named "Nextgen" is never taken for the next-page arrow.
	 */
	private static int find(AbstractContainerScreen<?> screen, String text) {
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).container instanceof Inventory) break;
			ItemStack stack = slots.get(i).getItem();
			String name = Items.name(stack);
			if (name.toLowerCase(Locale.ROOT).contains(text) && DashboardLore.read(name, Items.lore(stack)) == null) return i;
		}
		return -1;
	}

	/** Left-clicks one of the menu's slots, then waits a moment for the server's answer. */
	private static void press(Minecraft mc, AbstractContainerScreen<?> screen, int index) {
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, index, 0, ContainerInput.PICKUP, mc.player);
		pressedAt = Util.getMillis();
		delay = CLICK_TICKS;
	}

	/**
	 * Closes the menu, puts the earlier screen back (unless you've moved on to another) and hands over what was
	 * read, or null when {@code ok} is false; an open hears whether it worked.
	 */
	private static void finish(Minecraft mc, boolean ok) {
		Consumer<Map<String, DashboardLore.Entry>> then = done;
		Consumer<Boolean> openThen = opened;
		Consumer<List<FlagAlerts.Alert>> alertsThen = alertsRead;
		Screen back = restore;
		Map<String, DashboardLore.Entry> result = ok ? new LinkedHashMap<>(found) : null;
		List<FlagAlerts.Alert> read = ok ? List.copyOf(alerts) : null;
		step = Step.OFF;
		done = null;
		opened = null;
		alertsRead = null;
		restore = null;
		if (mc.player != null) {
			if (mc.screen instanceof AbstractContainerScreen<?>) {
				close(mc);
			} else if (mc.player.containerMenu != mc.player.inventoryMenu) {
				// Another screen took the menu's place without closing it: tell the server it's closed.
				closed = new ClosedMenu(mc.player.containerMenu.containerId, Util.getMillis());
				mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
				mc.player.containerMenu = mc.player.inventoryMenu;
			}
		}
		if (back != null && mc.screen == null) mc.setScreen(back);
		// Only a read of every page may replace the shared one: a cut-short read would drop the players it missed.
		if (result != null && complete) FlagSync.dashboard(List.copyOf(result.values()));
		if (then != null) then.accept(result);
		if (openThen != null) openThen.accept(false);
		if (alertsThen != null) alertsThen.accept(read);
	}
}
