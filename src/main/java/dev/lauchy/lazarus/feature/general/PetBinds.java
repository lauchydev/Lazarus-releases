package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * A key for each pet slot: pressing Pet 3's key runs /pet, clicks the third pet in the menu
 * (turning pages if it's further on) and closes the menu. A bind can include Ctrl, Alt or Shift,
 * held while picking the key, e.g. Alt + 3. The pet already out is left out rather than put away.
 */
public final class PetBinds extends Feature {
	/** How many pet slots get a key. */
	private static final int SLOTS = 9;
	/** Ticks to wait for the menu (or its next page) before giving up. */
	private static final int TIMEOUT_TICKS = 100;
	/** Ticks the menu's items must sit still before clicking, so a page still filling in isn't misread. */
	private static final int SETTLE_TICKS = 3;
	/** Ticks after the click before closing the menu, if the server hasn't closed it itself. */
	private static final int CLOSE_TICKS = 4;

	private final KeySetting[] keys = new KeySetting[SLOTS];
	private final boolean[] held = new boolean[SLOTS];

	/** The pet slot asked for (from 1), or 0 when idle, and how far along the menu's pages it is from here. */
	private int asked, want;
	/** Ticks spent waiting, ticks the items have been still, and ticks left before closing after the click. */
	private int waited, still, closing;
	/** The menu's item names last tick, to tell when they've settled or a new page has come. */
	private List<String> last = List.of();

	public PetBinds() {
		super("petBinds", "Pet Binds", "A key for each pet slot that summons that pet from /pet.", Category.GENERAL, true);
		for (int i = 0; i < SLOTS; i++)
			keys[i] = add(new KeySetting("pet" + (i + 1) + "Key", "Pet " + (i + 1) + " key", "Summons the pet in slot " + (i + 1) + " of /pet. A key or a side mouse button; hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
	}

	@Override
	public void init() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) for (KeySetting k : keys) Keys.suppressVanilla(k); // Alt + 1 must not also pick hotbar slot 1
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean live = enabled() && mc.screen == null && mc.getConnection() != null && mc.player != null;
			for (int i = 0; i < SLOTS; i++) {
				boolean down = live && keys[i].bound() && Keys.down(keys[i]);
				if (down && !held[i] && asked == 0 && closing == 0) open(mc, i + 1);
				held[i] = down;
			}
			step(mc);
		});
	}

	/** Runs /pet, then waits for the menu to pick pet {@code n}. */
	private void open(Minecraft mc, int n) {
		asked = want = n;
		waited = still = 0;
		last = List.of();
		mc.getConnection().sendCommand("pet");
	}

	/** One tick of picking: wait for the menu to settle, then click the pet, turn the page, or say it isn't there. */
	private void step(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) {
			asked = closing = 0;
			return;
		}
		AbstractContainerScreen<?> menu = mc.screen instanceof AbstractContainerScreen<?> cs && PetMenu.is(Text.plain(cs.getTitle())) ? cs : null;
		if (closing > 0) {
			if (--closing == 0 && menu != null) mc.player.closeContainer(); // the server left the menu open after summoning
			return;
		}
		if (asked == 0) return;
		if (++waited > TIMEOUT_TICKS) {
			asked = 0;
			Message.lazarus().error("The pet menu didn't open.").send();
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
		int at = PetMenu.find(names, want), next = PetMenu.next(names);
		int here = PetMenu.count(names);
		if (at < 0 && next >= 0 && here > 0 && !PetMenu.lastPage(Text.plain(menu.getTitle()))) {
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
			Message.lazarus().error("No pet in slot " + n + ".").send();
		} else if (PetMenu.summoned(Items.lore(slots.get(at).getItem()))) {
			mc.player.closeContainer();
			Message.lazarus().info(names.get(at).replaceFirst("^\\[Lvl \\d+]\\s*", "") + " is already out.").send();
		} else {
			click(mc, menu, slots.get(at));
			closing = CLOSE_TICKS;
		}
	}

	/** Left-clicks one of the menu's slots. */
	private static void click(Minecraft mc, AbstractContainerScreen<?> menu, Slot slot) {
		mc.gameMode.handleContainerInput(menu.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}
}
