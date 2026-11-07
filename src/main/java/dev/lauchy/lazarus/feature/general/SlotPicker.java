package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Works a server menu for a bind: runs its command, waits for the menu to open and settle, clicks the thing
 * asked for in it (turning pages when it's further on), and closes the menu. What the menu is and where things
 * sit in it come from a {@link Reader}, so pet and wardrobe binds share this and differ only in their reading.
 * One pick at a time.
 */
final class SlotPicker {
	/** How a bind reads its menu. Item names and lore are plain text, colour codes off. */
	interface Reader {
		/** Whether a window with this title is the menu. */
		boolean is(String title);

		/** Whether the menu titled this says it's on its last page; a title without pages doesn't say. */
		boolean lastPage(String title);

		/** Where the {@code n}th thing (from 1) is among the window's item names, or -1 when there are fewer on this page. */
		int find(List<String> names, int n);

		/** How many things this page holds. */
		int count(List<String> names);

		/** Where the arrow to the next page is among the window's item names, or -1. */
		int next(List<String> names);

		/** What to say instead of clicking, when the thing is already in use ("Steve is already out."), or null to click it. */
		String already(String name, List<String> lore);

		/** What to say when thing {@code n} isn't there. */
		String missing(int n);

		/** What to say when the menu never opened. */
		String noMenu();
	}

	/** Ticks to wait for the menu (or its next page) before giving up. */
	private static final int TIMEOUT_TICKS = 100;
	/** Ticks the menu's items must sit still before clicking, so a page still filling in isn't misread. */
	private static final int SETTLE_TICKS = 3;
	/** Ticks after the click before closing the menu, if the server hasn't closed it itself. */
	private static final int CLOSE_TICKS = 4;

	private final String command;
	private final Reader reader;
	/** The thing asked for (from 1), or 0 when idle, and how far along the menu's pages it is from here. */
	private int asked, want;
	/** Ticks spent waiting, ticks the items have been still, and ticks left before closing after the click. */
	private int waited, still, closing;
	/** The menu's item names last tick, to tell when they've settled or a new page has come. */
	private List<String> last = List.of();

	/** A picker that runs {@code command} (without its slash) and reads the menu it opens with {@code reader}. */
	SlotPicker(String command, Reader reader) {
		this.command = command;
		this.reader = reader;
	}

	/** Whether a pick is under way, so another key press waits. */
	boolean busy() {
		return asked != 0 || closing != 0;
	}

	/** Runs the command, then waits for the menu to pick thing {@code n}. */
	void open(Minecraft mc, int n) {
		asked = want = n;
		waited = still = 0;
		last = List.of();
		mc.getConnection().sendCommand(command);
	}

	/** One tick of picking: wait for the menu to settle, then click the thing, turn the page, or say it isn't there. */
	void tick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) {
			asked = closing = 0;
			return;
		}
		AbstractContainerScreen<?> menu = mc.screen instanceof AbstractContainerScreen<?> cs && reader.is(Text.plain(cs.getTitle())) ? cs : null;
		if (closing > 0) {
			if (--closing == 0 && menu != null) mc.player.closeContainer(); // the server left the menu open
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
		int at = reader.find(names, want), next = reader.next(names);
		int here = reader.count(names);
		if (at < 0 && next >= 0 && here > 0 && !reader.lastPage(Text.plain(menu.getTitle()))) {
			want -= here;
			still = waited = 0;
			last = List.of(); // the next page must show new names before it's read
			click(mc, menu, slots.get(next));
			return;
		}
		int n = asked;
		asked = 0;
		if (at < 0) {
			mc.player.closeContainer();
			Message.lazarus().error(reader.missing(n)).send();
			return;
		}
		String already = reader.already(names.get(at), Items.lore(slots.get(at).getItem()));
		if (already != null) {
			mc.player.closeContainer();
			Message.lazarus().info(already).send();
			return;
		}
		click(mc, menu, slots.get(at));
		closing = CLOSE_TICKS;
	}

	/** Left-clicks one of the menu's slots. */
	private static void click(Minecraft mc, AbstractContainerScreen<?> menu, Slot slot) {
		mc.gameMode.handleContainerInput(menu.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}
}
