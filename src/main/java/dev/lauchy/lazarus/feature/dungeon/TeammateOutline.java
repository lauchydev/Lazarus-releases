package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Your teammates outlined in a dungeon, through walls, so you can see where everyone is. Only the players the
 * sidebar lists as your team: the dungeon's own mobs and people are players to the game too, and they're left out.
 */
public final class TeammateOutline extends Feature {
	private final ColorSetting colour = add(new ColorSetting("colour", "Colour", "The outline's colour.", 0xFF55FFFF));
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Show the outlines through walls.", true));

	public TeammateOutline() {
		super("teammateOutline", "Teammate Outline", "Outlines your teammates in a dungeon, through walls.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		World.paint(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (!enabled() || !Area.inDungeon()) return;
			Set<String> party = DungeonMap.party(Area.sidebar());
			for (Player p : mc.level.players()) {
				if (p == mc.player || !party.contains(p.getName().getString())) continue;
				World.box(p.getBoundingBox(), colour.argb(), true, false, through.on());
			}
		});
	}
}
