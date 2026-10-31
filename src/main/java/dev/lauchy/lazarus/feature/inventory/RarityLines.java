package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rarity mark at the foot of each item in the hotbar and in open inventories, in the
 * colour of the rarity written in its lore ("EPIC SWORD"). Items without one get none.
 */
public final class RarityLines extends Feature {
	public static RarityLines INSTANCE;
	private static final Pattern RARITY = Pattern.compile("\\b(VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b");
	/** No rarity line: drawn as nothing. */
	private static final int NONE = 0;

	private final BoolSetting hotbar = add(new BoolSetting("hotbar", "Hotbar", "Lines under the hotbar items.", true));
	private final BoolSetting glow = add(new BoolSetting("glow", "Glow", "A soft glow of the rarity colour at the foot of the slot, under the accent.", true));
	private final BoolSetting screens = add(new BoolSetting("screens", "Inventories", "Lines under items in open inventories and chests.", true));
	private final BoolSetting preview = add(new BoolSetting("preview", "Inventory preview", "Lines under items in the Inventory Preview HUD.", true));

	/** Rarity colour by stack; lore is only read again when a stack changes. */
	private final Map<ItemStack, Integer> colours = new WeakHashMap<>();

	public RarityLines() {
		super("rarityLines", "Rarity Lines", "A thin line under each item in the hotbar and inventories, coloured by its rarity.", Category.INVENTORY, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "rarity_lines"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (!enabled() || !hotbar.on() || mc.player == null || mc.player.isSpectator() || mc.options.hideGui) return;
			int x = g.guiWidth() / 2 - 90, y = g.guiHeight() - 19;
			for (int i = 0; i < 9; i++) line(g, mc.player.getInventory().getItem(i), x + 2 + i * 20, y);
		});
		// What the held item's rarity was read as, and from where, for when a line is missing.
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("rarity").executes(c -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return 0;
			ItemStack held = mc.player.getMainHandItem();
			int colour = colour(held);
			List<String> lore = Items.lore(held);
			Message m = Message.lazarus().highlight(Items.name(held)).info(": ");
			if (colour == NONE) m.error("no rarity found");
			else m.info(String.format("colour #%06X", colour & 0xFFFFFF));
			m.info(" (id " + Items.id(held) + ", last lore line: " + (lore.isEmpty() ? "none" : Text.plain(lore.getLast())) + ")").send();
			return 1;
		})));
	}

	/**
	 * For container screens, called as each slot is drawn (AbstractContainerScreenMixin), with
	 * the slot's own coordinates. Drawing with the slots rather than after the screen keeps
	 * the marks under the item tooltip.
	 */
	public void slotMark(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (enabled() && screens.on()) line(g, stack, x, y);
	}

	/** For the Inventory Preview HUD: the mark for an item drawn at (x, y), if switched on there. */
	public void previewMark(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (enabled() && preview.on()) line(g, stack, x, y);
	}

	/** How strongly each glow row shows, from the bottom row of the slot up. */
	private static final float[] GLOW = {0.30f, 0.20f, 0.13f, 0.08f, 0.04f, 0.02f};

	/**
	 * The mark for an item drawn at (x, y): a soft glow of its rarity colour rising from the
	 * bottom of its 16-pixel square, and a short accent centred on the bottom edge whose
	 * ends fade out, rather than a hard bar across the slot.
	 */
	private void line(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) return;
		int colour = colours.computeIfAbsent(stack, RarityLines::colour);
		if (colour == NONE) return;
		int rgb = colour & 0xFFFFFF;
		if (glow.on()) {
			for (int i = 0; i < GLOW.length; i++) Draw.rect(g, x + 1, y + 15 - i, 14, 1, Draw.alpha(rgb, GLOW[i]));
		}
		Draw.rect(g, x + 5, y + 15, 6, 1, Draw.alpha(rgb, 0.95f));
		Draw.rect(g, x + 4, y + 15, 1, 1, Draw.alpha(rgb, 0.45f));
		Draw.rect(g, x + 11, y + 15, 1, 1, Draw.alpha(rgb, 0.45f));
	}

	private static int colour(ItemStack stack) {
		// The rarity line is in capitals, near the foot of the lore.
		List<String> lore = Items.lore(stack);
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).trim();
			if (!line.equals(line.toUpperCase(Locale.ROOT))) continue;
			Matcher m = RARITY.matcher(line);
			if (m.find()) return tier(m.group(1));
		}
		return NONE;
	}

	private static int tier(String rarity) {
		return switch (rarity) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY SPECIAL" -> 0xFFFF5555;
			default -> NONE;
		};
	}
}
