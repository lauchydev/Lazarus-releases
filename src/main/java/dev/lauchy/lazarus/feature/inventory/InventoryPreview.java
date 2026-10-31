package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Your main inventory (the three rows above the hotbar) as a movable HUD grid. */
public final class InventoryPreview extends Feature {
	private final IntSetting opacity = add(new IntSetting("opacity", "Background", "Backdrop opacity.", 45, 0, 100, "%"));
	private final BoolSetting lines = add(new BoolSetting("lines", "Slot lines", "Lines between the slots, like a real inventory.", true));
	private final BoolSetting hideEmpty = add(new BoolSetting("hideEmpty", "Hide when empty", "Hide the grid if those rows are empty.", true));

	public InventoryPreview() {
		super("inventoryPreview", "Inventory Preview", "A movable HUD grid of your inventory items.", Category.INVENTORY, false);
	}

	@Override
	public void init() {
		Hud.register(new HudElement(this, "inventoryPreview", "Inventory Preview", 0.70f, 0.80f) {
			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player == null && !sample) return null;
				boolean any = sample;
				if (!sample) for (int i = 9; i < 36; i++) any |= !mc.player.getInventory().getItem(i).isEmpty();
				if (!any && hideEmpty.on()) return null;
				int w = 9 * 18 + 5, h = 3 * 18 + 5;
				if (g == null) return new int[]{w, h};
				Draw.rect(g, 0, 0, w, h, Draw.alpha(0x000000, opacity.get() / 100f));
				if (lines.on()) Draw.slotGrid(g, 3, 3, 9, 3, 0x40FFFFFF);
				for (int i = 0; i < 27; i++) {
					ItemStack s = sample ? (i % 4 == 0 ? new ItemStack(Items.DIAMOND_SWORD) : ItemStack.EMPTY) : mc.player.getInventory().getItem(9 + i);
					int x = 3 + (i % 9) * 18, y = 3 + (i / 9) * 18;
					if (s.isEmpty()) continue;
					if (RarityLines.INSTANCE != null) RarityLines.INSTANCE.previewMark(g, s, x, y);
					g.item(s, x, y);
					g.itemDecorations(font, s, x, y);
				}
				return new int[]{w, h};
			}
		});
	}
}
