package dev.lauchy.lazarus.feature.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Locked slots can't be clicked, shift-clicked, number-key swapped, dropped or collected
 * into, so a misclick can't sell or drop your gear. Hover a slot and press the lock key
 * to lock it. Shift + lock key on an inventory slot, then lock key on a hotbar slot, links
 * them: shift-clicking either swaps the two.
 */
public final class SlotLock extends Feature {
	public static SlotLock INSTANCE;
	private static final int OFFHAND = 40;

	private final KeySetting lockKey = add(new KeySetting("lockKey", "Lock key", "Press over a slot in your inventory to lock it."));
	private final IntSetting opacity = add(new IntSetting("opacity", "Opacity", "How strongly locked and linked slots are marked.", 100, 10, 100, "%"));

	private final Set<Integer> locked = new HashSet<>();
	private final Map<Integer, Integer> links = new HashMap<>(); // both directions
	private int linkFrom = -1;
	private final Path file = Config.dir().resolve("slots.json");

	public SlotLock() {
		super("slotLock", "Slot Lock", "Lock inventory slots and link slots to your hotbar.", Category.INVENTORY, true);
		lockKey.set(GLFW.GLFW_KEY_L);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> !(enabled() && keyPressed(cs, key.key(), key.hasShiftDown())));
		});
	}

	private static int index(Slot slot) {
		return slot != null && slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
	}

	private boolean keyPressed(AbstractContainerScreen<?> screen, int key, boolean shift) {
		if (!lockKey.bound() || key != lockKey.key()) return false;
		int i = index(screen.hoveredSlot);
		if (i < 0) return false;
		if (shift) {
			if (links.containsKey(i)) {
				links.remove(links.remove(i));
				Toast.show("Link removed", Theme.MUTED);
			} else if (i >= 9 && i < 36) {
				linkFrom = i;
				Toast.show("Press the lock key on a hotbar slot", Theme.ACCENT);
			}
		} else if (linkFrom >= 0 && i < 9) {
			links.remove(links.remove(linkFrom));
			links.remove(links.remove(i));
			links.put(linkFrom, i);
			links.put(i, linkFrom);
			linkFrom = -1;
			Toast.show("Linked to hotbar " + (i + 1), Theme.GOOD);
		} else {
			linkFrom = -1;
			if (!locked.remove(i)) locked.add(i);
			Toast.show(locked.contains(i) ? "Slot locked" : "Slot unlocked", locked.contains(i) ? Theme.BAD : Theme.GOOD);
		}
		save();
		return true;
	}

	/** Called from the slot click hook. @return true to cancel the click */
	public boolean intercept(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput input) {
		if (!enabled()) return false;
		int i = index(slot);
		Integer other = i < 0 ? null : links.get(i);
		if (input == ContainerInput.QUICK_MOVE && other != null) {
			int hotbar = Math.min(i, other), main = Math.max(i, other);
			Minecraft mc = Minecraft.getInstance();
			for (Slot s : screen.getMenu().slots) {
				if (index(s) == main && mc.gameMode != null) {
					mc.gameMode.handleContainerInput(screen.getMenu().containerId, s.index, hotbar, ContainerInput.SWAP, mc.player);
				}
			}
			return true;
		}
		if (i >= 0 && locked.contains(i)) return true;
		if (input == ContainerInput.SWAP && (button >= 0 && button < 9 || button == OFFHAND) && locked.contains(button)) return true;
		if (input == ContainerInput.PICKUP_ALL) {
			for (Slot s : screen.getMenu().slots) if (locked.contains(index(s))) return true;
		}
		return false;
	}

	/** Q on a locked hotbar slot. */
	public boolean blocksDrop() {
		Minecraft mc = Minecraft.getInstance();
		return enabled() && mc.player != null && locked.contains(mc.player.getInventory().getSelectedSlot());
	}

	/**
	 * Marks one slot as it's drawn (AbstractContainerScreenMixin), at the slot's own coordinates:
	 * drawing with the slots rather than after the screen keeps the marks under item tooltips.
	 */
	public void slotMark(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot s) {
		if (!enabled()) return;
		int i = index(s);
		if (i < 0) return;
		int x = s.x, y = s.y;
		if (locked.contains(i)) {
			Draw.rect(g, x, y, 16, 16, faded(0x40FF3030));
			Draw.outline(g, x - 1, y - 1, 18, 18, faded(0xC0FF5555));
		}
		Integer other = links.get(i);
		if (other != null && screen.getMenu() instanceof InventoryMenu) {
			Draw.outline(g, x - 1, y - 1, 18, 18, faded(0xC0B46CFF));
			Draw.text(g, Minecraft.getInstance().font, String.valueOf(Math.min(i, other) + 1), x + 11, y, faded(0xFF000000 | Theme.ACCENT));
		}
		if (i == linkFrom) Draw.outline(g, x - 1, y - 1, 18, 18, 0xFFFFFF55); // the link being made always shows in full
	}

	/** A colour with its alpha scaled by the opacity setting. */
	private int faded(int argb) {
		int alpha = (argb >>> 24) * opacity.get() / 100;
		return Math.max(alpha, 1) << 24 | argb & 0xFFFFFF;
	}

	private void load() {
		JsonElement json = Config.readJson(file);
		if (json == null || !json.isJsonObject()) return;
		JsonObject o = json.getAsJsonObject();
		try {
			if (o.has("locked")) o.getAsJsonArray("locked").forEach(e -> locked.add(e.getAsInt()));
			if (o.has("links")) o.getAsJsonObject("links").entrySet().forEach(e -> {
				int a = Integer.parseInt(e.getKey()), b = e.getValue().getAsInt();
				links.put(a, b);
				links.put(b, a);
			});
		} catch (RuntimeException ignored) {
			// Corrupt file: start clean.
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		JsonArray l = new JsonArray();
		locked.forEach(l::add);
		o.add("locked", l);
		JsonObject k = new JsonObject();
		links.forEach((a, b) -> {
			if (a > b) k.addProperty(String.valueOf(a), b);
		});
		o.add("links", k);
		Config.writeJson(file, o);
	}
}
