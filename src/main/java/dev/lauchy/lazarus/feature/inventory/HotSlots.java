package dev.lauchy.lazarus.feature.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hot slots: an item can be given a hotbar slot of its own. Shift-clicking it in your
 * inventory then puts it in that slot, swapping out what's there unless that item has a
 * different hot slot of its own; otherwise the click does what it always does. Hold the item and run {@code /lazarus slot <1-9>} to assign it.
 */
public final class HotSlots extends Feature {
	public static HotSlots INSTANCE;

	private final Path file = Config.dir().resolve("hotslots.json");
	/** Hotbar index (0-8) by item key: "u:" plus the item's own UUID when it has one, else its catalog id, else its name. */
	private final Map<String, Integer> slots = new LinkedHashMap<>();

	private final BoolSetting tooltip = add(new BoolSetting("tooltip", "Tooltip line", "Show \"Hot slot: N\" in the tooltip of an item that has one.", true));

	public HotSlots() {
		super("hotSlots", "Hot Slots", "Give items a hotbar slot; shift-click puts them there. /lazarus slot <1-9> with the item held.", Category.INVENTORY, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("slot")
				.executes(c -> list())
				.then(ClientCommands.literal("clear").executes(c -> clear()))
				.then(ClientCommands.literal("debug").executes(c -> debug()))
				.then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 9))
						.executes(c -> assign(IntegerArgumentType.getInteger(c, "slot"))))));
		// A line in the item's tooltip when it has a hot slot, so you can see it at a glance.
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!enabled() || !tooltip.on()) return;
			Integer slot = slotOf(stack);
			if (slot != null) lines.add(Component.literal("§8Hot slot: §b" + (slot + 1)));
		});
	}

	private static String key(ItemStack stack) {
		if (stack.isEmpty()) return null;
		String id = Items.id(stack);
		return id != null ? id : Items.name(stack);
	}

	/** The key for this one item: its UUID, so look-alikes each get their own slot. Null when it has none. */
	private static String uniqueKey(ItemStack stack) {
		String uuid = Items.unique(stack);
		return uuid == null ? null : "u:" + uuid;
	}

	/** This item's hot slot: its own binding first, then one shared by every item like it. */
	private Integer slotOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		String own = uniqueKey(stack);
		Integer slot = own == null ? null : slots.get(own);
		return slot != null ? slot : slots.get(key(stack));
	}

	/** Dumps the held item's custom-data tag and lore, to find what tells two look-alikes apart. */
	private int debug() {
		ItemStack held = held();
		if (held.isEmpty()) {
			Message.lazarus().error("Hold an item and run ").highlight("/lazarus slot debug").error(".").send();
			return 0;
		}
		String own = uniqueKey(held);
		var data = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		Message.lazarus().highlight(Items.name(held)).info(" (key: " + (own != null ? own : key(held)) + ")")
				.line().info("Item UUID: ").highlight(own == null ? "none, so it shares a hot slot with every item like it" : own.substring(2))
				.line().info("Custom data: ").highlight(data == null ? "none" : data.copyTag().toString()).send();
		return 1;
	}

	private int assign(int slot) {
		ItemStack held = held();
		String key = key(held);
		if (key == null) {
			Message.lazarus().error("Hold the item you want to give a hot slot, then run ").highlight("/lazarus slot " + slot).error(".").send();
			return 0;
		}
		String own = uniqueKey(held);
		if (own != null) {
			// This exact item only; drop a shared binding so look-alikes stop following it.
			slots.remove(key);
			key = own;
		}
		slots.put(key, slot - 1);
		save();
		Message.lazarus().highlight(Items.name(held)).success(" now goes to hotbar slot ").highlight(String.valueOf(slot)).success(" when you shift-click it.").send();
		return 1;
	}

	private int clear() {
		ItemStack held = held();
		String own = uniqueKey(held);
		String key = key(held);
		boolean removed = own != null && slots.remove(own) != null;
		if (!removed && (key == null || slots.remove(key) == null)) {
			Message.lazarus().error("The item you're holding has no hot slot.").send();
			return 0;
		}
		save();
		Message.lazarus().success("Hot slot removed from ").highlight(Items.name(held)).success(".").send();
		return 1;
	}

	private int list() {
		if (slots.isEmpty()) {
			Message.lazarus().info("No hot slots yet. Hold an item and run ").highlight("/lazarus slot <1-9>").info(".").send();
			return 1;
		}
		Message m = Message.lazarus().info("Hot slots:");
		slots.forEach((key, slot) -> m.line().highlight(String.valueOf(slot + 1)).info(" · " + key));
		m.send();
		return 1;
	}

	private static ItemStack held() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
	}

	/**
	 * Called from the slot click hook: a shift-click on an item with a hot slot, in your
	 * own inventory, swaps it into that hotbar slot unless the item there has a different
	 * hot slot. @return true when it did
	 */
	public boolean intercept(AbstractContainerScreen<?> screen, Slot slot, ContainerInput input) {
		if (!enabled() || input != ContainerInput.QUICK_MOVE || slot == null || !(screen.getMenu() instanceof InventoryMenu)) return false;
		if (!(slot.container instanceof Inventory) || slot.getContainerSlot() < 9 || slot.getContainerSlot() >= 36) return false;
		Integer target = slotOf(slot.getItem());
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.player == null || mc.gameMode == null) return false;
		// The one clicked trades places with whatever is there. Something with a hot slot of its
		// own (another slot) stays put; anything without one just takes the clicked item's place.
		ItemStack there = mc.player.getInventory().getItem(target);
		Integer theirs = there.isEmpty() ? null : slotOf(there);
		if (theirs != null && !theirs.equals(target)) return false;
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, target, ContainerInput.SWAP, mc.player);
		return true;
	}

	private void load() {
		JsonElement json = Config.readJson(file);
		if (json == null || !json.isJsonObject()) return;
		try {
			for (var e : json.getAsJsonObject().entrySet()) {
				int slot = e.getValue().getAsInt();
				if (slot >= 0 && slot < 9) slots.put(e.getKey(), slot);
			}
		} catch (RuntimeException ignored) {
			// Corrupt file: start clean.
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		slots.forEach(o::addProperty);
		Config.writeJson(file, o);
	}
}
