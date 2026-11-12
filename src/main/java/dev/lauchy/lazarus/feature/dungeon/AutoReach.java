package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.setting.IntSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * What Auto Chest and Auto Lever share: in a dungeon, the blocks of one kind within your own reach are clicked for
 * you, each once a run, one click at a time with a delay. Not in a puzzle room, where the wrong chest or lever
 * loses the puzzle, and not while a window is open. This plays the game for you, which Dragsim can ban for: both
 * are Lazarus+ features, off unless you turn them on, and marked BANNABLE in the menu.
 */
abstract class AutoReach extends Feature {
	/** How far round you is looked: a little past your reach, which is checked for each block. */
	private static final int LOOK = 6;

	private final IntSetting delay = add(new IntSetting("delay", "Delay", "About how long between its clicks; each varies a little.", 300, 120, 1500, "ms"));

	/** The blocks already clicked this run, the world they're in, and when the next click may be made. */
	private final Set<BlockPos> clicked = new HashSet<>();
	private Object level;
	private long nextAt;
	private int ticks;

	protected AutoReach(String id, String name, String description) {
		super(id, name, description, Category.DUNGEONS, false);
	}

	@Override
	public boolean plus() {
		return true;
	}

	/** It plays the game for you, which Dragsim's rules can ban for. */
	@Override
	public boolean bannable() {
		return true;
	}

	/** Whether a block is one this feature clicks. */
	protected abstract boolean wanted(Minecraft mc, BlockPos pos, BlockState state);

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) {
				level = mc.level;
				clicked.clear();
			}
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null || mc.screen != null || ++ticks % 2 != 0) return;
			if (Util.getMillis() < nextAt) return;
			DungeonLayout.Room here = Dungeon.roomHere();
			if (here != null && here.kind() == Kind.PUZZLE) return; // a puzzle's chests and levers are the puzzle
			BlockPos feet = mc.player.blockPosition();
			for (BlockPos p : BlockPos.betweenClosed(feet.offset(-LOOK, -LOOK, -LOOK), feet.offset(LOOK, LOOK, LOOK))) {
				if (clicked.contains(p) || !mc.player.isWithinBlockInteractionRange(p, 0)) continue;
				BlockState state = mc.level.getBlockState(p);
				if (!wanted(mc, p, state)) continue;
				if (DeviceClicks.block(mc, p)) {
					clicked.add(p.immutable());
					nextAt = Util.getMillis() + Math.round(delay.get() * (0.7 + Math.random() * 0.6));
				}
				return;
			}
		});
	}
}
