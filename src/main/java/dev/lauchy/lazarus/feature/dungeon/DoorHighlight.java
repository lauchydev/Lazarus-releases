package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Wither and blood doors boxed: each locked door the map shows (black for wither, red for blood) is found in the
 * world by its blocks (coal for a wither door, red terracotta for the blood door) and outlined, dark grey or red,
 * until it's opened. Only the door: an outline, not a filled box, and not the room behind it.
 */
public final class DoorHighlight extends Feature {
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Show the boxes through walls.", true));

	/** A door's box and colour. */
	private record Boxed(AABB box, int colour) {
	}

	private List<Boxed> doors = List.of();
	private int ticks;

	public DoorHighlight() {
		super("doorHighlight", "Door Highlight", "Boxes the wither and blood doors still locked.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || !Dungeon.placed() || mc.level == null || mc.player == null) {
				doors = List.of();
				return;
			}
			if (++ticks % 20 != 0) return;
			List<Boxed> found = new ArrayList<>();
			MapFit fit = Dungeon.fit();
			for (DungeonLayout.Door d : Dungeon.layout().doors) {
				if (d.lock() == DungeonLayout.Lock.NONE) continue;
				var block = d.lock() == DungeonLayout.Lock.BLOOD ? net.minecraft.world.level.block.Blocks.RED_TERRACOTTA : net.minecraft.world.level.block.Blocks.COAL_BLOCK;
				int[] at = fit.door(Dungeon.layout(), d);
				BlockPos about = new BlockPos(at[0], mc.player.getBlockY(), at[1]);
				AABB box = Dungeon.doorBox(mc.level, about, block);
				if (box != null) found.add(new Boxed(box, d.lock() == DungeonLayout.Lock.BLOOD ? 0xFFFF3030 : 0xFF404040));
			}
			doors = found;
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (Boxed b : doors) World.box(b.box(), b.colour(), true, false, through.on());
		});
	}
}
