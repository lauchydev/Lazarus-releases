package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The server's live dungeon map on screen, without holding it. The map item the server
 * puts in your inventory keeps updating as rooms open; this draws its pixels and markers
 * in a movable frame, and hides it once the boss fight starts.
 */
public final class DungeonMap extends Feature {
	private static final int SIZE = 128, BORDER = 2;

	private final BoolSetting hideInBoss = add(new BoolSetting("hideInBoss", "Hide in boss room", "Hide the map once the boss speaks.", true));

	private boolean bossStarted;
	private Object level;

	public DungeonMap() {
		super("dungeonMap", "Dungeon Map", "The live dungeon map on screen while you explore.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (l.text().startsWith("[BOSS] ") && !l.text().startsWith("[BOSS] The Watcher")) bossStarted = true;
		});
		Hud.register(new HudElement(this, "dungeonMap", "Dungeon Map", 0.006f, 0.02f) {
			@Override
			protected boolean visible() {
				Minecraft mc = Minecraft.getInstance();
				if (mc.level != level) {
					level = mc.level;
					bossStarted = false;
				}
				return Area.inDungeon() && !(bossStarted && hideInBoss.on()) && map(mc) != null;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				int full = SIZE + BORDER * 2;
				if (g == null) return new int[]{full, full};
				Draw.round(g, 0, 0, full, full, Theme.HUD_BG);
				Minecraft mc = Minecraft.getInstance();
				MapId id = map(mc);
				MapItemSavedData data = id == null ? null : mc.level.getMapData(id);
				if (data == null) {
					Draw.textCentered(g, font, sample ? "Dungeon map" : "Waiting for map", full / 2, full / 2 - 4, Theme.MUTED);
					return new int[]{full, full};
				}
				MapRenderState state = new MapRenderState();
				mc.getMapRenderer().extractRenderState(id, data, state);
				var pose = g.pose();
				pose.pushMatrix();
				pose.translate(BORDER, BORDER);
				g.map(state);
				pose.popMatrix();
				return new int[]{full, full};
			}
		});
	}

	/** The map in your inventory, hotbar first. */
	private static MapId map(Minecraft mc) {
		if (mc.player == null) return null;
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			MapId id = s.get(DataComponents.MAP_ID);
			if (id != null) return id;
		}
		return null;
	}
}
