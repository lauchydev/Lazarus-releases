package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Terminal Highlight: at the gates, each terminal, lever and device is boxed by what its own label says: green
 * while it's still to do ("Inactive"), red once it's done ("Active"). So the ones left stand out across the room,
 * and the ones finished read as not worth the walk.
 */
public final class TerminalHighlight extends Feature {
	/** A label on something still to do, and on something done. */
	private static final Pattern TODO = Pattern.compile("^(Terminal|Inactive( Terminal| Device| Lever)?|Not Activated)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DONE = Pattern.compile("^((Terminal |Device |Lever )?Active( Terminal| Device| Lever)?|Activated|Completed?)$", Pattern.CASE_INSENSITIVE);

	private final ColorSetting todo = add(new ColorSetting("todo", "Still to do", "The colour of a terminal, lever or device not done yet.", 0xFF55FF55));
	private final ColorSetting done = add(new ColorSetting("done", "Done", "The colour of one that's done.", 0xFFFF5555));
	private final BoolSetting showDone = add(new BoolSetting("showDone", "Show the done ones", "Box the ones that are done as well.", true));
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Show the boxes through walls.", true));

	/** A box to draw, and whether its terminal is done. */
	private record Boxed(AABB box, boolean done) {
	}

	private List<Boxed> boxes = List.of();
	private int ticks;

	/** A label's first line, which says what it's the label of; a second line under it ("CLICK HERE") says nothing more. */
	static String line(String label) {
		for (String part : label.split("\\R")) if (!part.isBlank()) return part.trim();
		return "";
	}

	public TerminalHighlight() {
		super("terminalHighlight", "Terminal Highlight", "Boxes each terminal, lever and device at the gates: green while it's to do, red once it's done.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				boxes = List.of();
				return;
			}
			if (++ticks % 10 != 0) return;
			List<Boxed> found = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Player) continue;
				String label = line(Entities.name(e));
				if (label.isEmpty() || label.length() > 20) continue;
				boolean isDone = DONE.matcher(label).matches();
				if (!isDone && !TODO.matcher(label).matches()) continue;
				found.add(new Boxed(box(mc.level, e), isDone));
			}
			boxes = found;
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (Boxed b : boxes) {
				if (b.done() && !showDone.on()) continue;
				World.box(b.box(), (b.done() ? done : todo).argb(), true, true, through.on());
			}
		});
	}

	/** What a label is the label of: the command block or lever beside it, or failing that the block it floats in. */
	private static AABB box(Level level, Entity label) {
		BlockPos at = label.blockPosition(), best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -3, -2), at.offset(2, 1, 2))) {
			var s = level.getBlockState(p);
			if (!(s.getBlock() instanceof CommandBlock) && !s.is(Blocks.LEVER)) continue;
			double far = p.distSqr(at);
			if (far < bestD) {
				bestD = far;
				best = p.immutable();
			}
		}
		return new AABB(best != null ? best : at);
	}
}
