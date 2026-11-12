package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Works a server menu for a bind: runs its command, waits for the menu to open and settle, clicks the slot asked
 * for in it (turning pages when it's further on), and closes the menu. What the menu is and where things sit in it
 * come from a {@link Reader}, so pet and wardrobe binds share this and differ only in their reading. A menu that
 * turns a click down in its first moments (the wardrobe) is given that long first, and a click it still turns down
 * goes again. One pick at a time.
 */
final class SlotPicker {
	/** How a bind reads its menu. Item names and lore are plain text, colour codes off. */
	interface Reader {
		/** Whether a window with this title is the menu. */
		boolean is(String title);

		/** Whether the menu titled this says it's on its last page; a title without pages doesn't say. */
		boolean lastPage(String title);

		/**
		 * Where slot {@code slot} (from 1) is among the window's item names, the pages already turned past having
		 * held {@code before} slots, or -1 when it isn't on this page.
		 */
		int find(List<String> names, int slot, int before);

		/** How many slots this page holds. */
		int count(List<String> names);

		/** Where the arrow to the next page is among the window's item names, or -1. */
		int next(List<String> names);

		/** What to say instead of clicking slot {@code slot}, when it's already in use ("Steve is already out."), or null to click it. */
		String already(String name, List<String> lore, int slot);

		/** What to say when slot {@code slot} isn't there. */
		String missing(int slot);

		/** What to say when the menu never opened. */
		String noMenu();

		/** How long after the menu opens the server takes a click, in milliseconds: 0 when it takes one straight away. */
		long openWait();

		/** Whether a chat line is the server turning a click down for coming too soon after the menu opened. */
		boolean tooSoon(String line);
	}

	/** A click on the slot asked for: the slot, how many slots the pages before it held, and the window it was in. */
	private record Picked(int slot, int before, int window) {
	}

	/** Ticks to wait for the menu (or its next page) before giving up. */
	private static final int TIMEOUT_TICKS = 100;
	/** Ticks the menu's items must sit still before clicking, so a page still filling in isn't misread. */
	private static final int SETTLE_TICKS = 3;

	/** The picker that last opened a menu: the only one that may close one, and while it's picking, every bind waits. */
	private static SlotPicker active;

	private final String command;
	private final Reader reader;
	/** When a click may go, for a menu that turns one down too soon after it opens. */
	private final OpenWait wait;
	/** The slot asked for (from 1), or 0 when idle, and how many slots the pages already turned past held. */
	private int asked, before;
	/** Ticks spent waiting, ticks the items have been still, and ticks since the click while closing what it left open. */
	private int waited, still, sinceClick;
	/** Whether the menus the server shows after the click are being closed ({@link AfterClick}). */
	private boolean closing;
	/** The menu's item names last tick, to tell when they've settled or a new page has come. */
	private List<String> last = List.of();
	/** This pick's click, or null before it, and whether the server turned it down as too soon, so it goes again. */
	private Picked picked;
	private boolean again;

	/** A picker that runs {@code command} (without its slash) and reads the menu it opens with {@code reader}. */
	SlotPicker(String command, Reader reader) {
		this.command = command;
		this.reader = reader;
		this.wait = new OpenWait(reader.openWait());
	}

	/**
	 * Whether any bind's pick is under way (this one's or another's), so a key press waits: two menus asked for at
	 * once would take each other's place.
	 */
	boolean busy() {
		return active != null && (active.asked != 0 || active.again);
	}

	/** Runs the command, then waits for the menu to pick slot {@code slot} (from 1). */
	void open(Minecraft mc, int slot) {
		active = this;
		wait.reset();
		picked = null;
		again = false;
		closing = false;
		ask(mc, slot);
	}

	/** Asks for {@code slot} from the first page, running the command to open the menu. */
	private void ask(Minecraft mc, int slot) {
		asked = slot;
		before = waited = still = 0;
		last = List.of();
		mc.getConnection().sendCommand(command);
	}

	/**
	 * Whether a chat line is the server turning this pick's click down as too soon: the pick then goes again once
	 * the wait is over, and the line stays out of chat.
	 */
	boolean turnedDown(String line) {
		if (picked == null || !reader.tooSoon(line) || !wait.turnedDown(Util.getMillis())) return false;
		again = true;
		return true;
	}

	/** One tick of picking: wait for the menu to settle, then click the slot, turn the page, or say it isn't there. */
	void tick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) {
			asked = 0;
			again = closing = false;
			return;
		}
		AbstractContainerScreen<?> menu = mc.screen instanceof AbstractContainerScreen<?> cs && reader.is(Text.plain(cs.getTitle())) ? cs : null;
		if (menu != null) wait.seen(menu.getMenu().containerId, Util.getMillis());
		if (again) {
			again = false;
			closing = false;
			if (menu != null && menu.getMenu().containerId == picked.window()) {
				// Still on the page it was clicked on: click it there again once the wait is over.
				asked = picked.slot();
				before = picked.before();
				waited = 0;
			} else {
				ask(mc, picked.slot());
			}
			return;
		}
		if (closing) {
			// The server may leave the menu open, or open it again refreshed a moment later: close whatever it shows.
			boolean serverMenu = mc.screen instanceof AbstractContainerScreen<?> cs && cs.getMenu() != mc.player.inventoryMenu;
			switch (AfterClick.act(++sinceClick, serverMenu, mc.screen != null && !serverMenu, active == this)) {
				case CLOSE -> mc.player.closeContainer();
				case DONE -> closing = false;
				case WAIT -> {
				}
			}
			return;
		}
		if (asked == 0) return;
		if (++waited > TIMEOUT_TICKS) {
			asked = 0;
			Message.lazarus().error(reader.noMenu()).send();
			return;
		}
		if (menu == null) return;
		List<Slot> slots = new ArrayList<>();
		for (Slot s : menu.getMenu().slots) if (!(s.container instanceof Inventory)) slots.add(s);
		List<String> names = slots.stream().map(s -> Items.name(s.getItem())).toList();
		if (names.stream().allMatch(String::isEmpty)) return; // still arriving
		still = names.equals(last) ? still + 1 : 0;
		last = names;
		if (still < SETTLE_TICKS) return;
		int at = reader.find(names, asked, before), next = reader.next(names);
		int here = reader.count(names);
		if (at < 0 && next >= 0 && here > 0 && !reader.lastPage(Text.plain(menu.getTitle()))) {
			before += here;
			still = waited = 0;
			last = List.of(); // the next page must show new names before it's read
			click(mc, menu, slots.get(next));
			return;
		}
		int slot = asked;
		if (at < 0) {
			asked = 0;
			mc.player.closeContainer();
			Message.lazarus().error(reader.missing(slot)).send();
			return;
		}
		String already = reader.already(names.get(at), Items.lore(slots.get(at).getItem()), slot);
		if (already != null) {
			asked = 0;
			mc.player.closeContainer();
			Message.lazarus().info(already).send();
			return;
		}
		if (!wait.ready(Util.getMillis())) return; // the wardrobe turns a swap down in its first second
		asked = 0;
		click(mc, menu, slots.get(at));
		picked = new Picked(slot, before, menu.getMenu().containerId);
		wait.clicked(Util.getMillis());
		closing = true;
		sinceClick = 0;
	}

	/** Left-clicks one of the menu's slots. */
	private static void click(Minecraft mc, AbstractContainerScreen<?> menu, Slot slot) {
		mc.gameMode.handleContainerInput(menu.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}
}
