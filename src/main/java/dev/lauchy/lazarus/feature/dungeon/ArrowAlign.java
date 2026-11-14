package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.ArrowPaths.Cell;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.Util;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arrow Align: the arrow device at the gates, a wall of item frames with an arrow in each, each click turning one
 * an eighth round. Every frame shows how many clicks its arrow still needs ({@link ArrowPaths}); the wall is found
 * from the frames near you, wherever it stands. Clicking stays yours, though a click on an arrow already right is
 * stopped (it would only turn it wrong again) unless you sneak.
 */
public final class ArrowAlign extends Feature {
	/** How far off the wall is looked for. */
	private static final double REACH = 14;

	private final BoolSetting block = add(new BoolSetting("block", "Block wrong clicks", "Stop a click on an arrow that's already pointing the right way. Sneak to click anyway.", true));

	/** The wall's arrow frames, and the turn each should end at, by entity id. */
	private final List<ItemFrame> frames = new ArrayList<>();
	private final Map<Integer, Integer> targets = new HashMap<>();
	/** The clicks on each arrow the server hasn't answered yet, ({@link ClickLedger}): an arrow counts as turned that many further. */
	private ClickLedger sent = new ClickLedger(8);
	private int ticks;

	public ArrowAlign() {
		super("arrowAlign", "Arrow Align", "Shows the clicks each arrow needs at the gates' arrow device.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				frames.clear();
				targets.clear();
				sent = new ClickLedger(8);
				return;
			}
			if (++ticks % 5 != 0) return;
			frames.clear();
			targets.clear();
			// Every arrow frame near you, grouped by the wall it hangs on: the device is the biggest group.
			Map<String, List<ItemFrame>> walls = new HashMap<>();
			Map<String, List<ItemFrame>> others = new HashMap<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof ItemFrame f) || f.distanceToSqr(mc.player) > REACH * REACH || f.getDirection().getAxis().isVertical()) continue;
				Direction d = f.getDirection();
				String wall = d + ":" + (d.getAxis() == Direction.Axis.X ? f.blockPosition().getX() : f.blockPosition().getZ());
				(f.getItem().is(Items.ARROW) ? walls : others).computeIfAbsent(wall, k -> new ArrayList<>()).add(f);
			}
			String wall = null;
			for (var w : walls.entrySet()) if (wall == null || w.getValue().size() > walls.get(wall).size()) wall = w.getKey();
			if (wall == null || walls.get(wall).size() < 3) return;
			frames.addAll(walls.get(wall));
			Map<Cell, ItemFrame> at = new HashMap<>();
			for (ItemFrame f : frames) at.put(cell(f), f);
			Set<Cell> ends = new HashSet<>();
			for (ItemFrame f : others.getOrDefault(wall, List.of())) if (f.getItem().is(Items.RED_WOOL)) ends.add(cell(f));
			ArrowPaths.targets(at.keySet(), ends).forEach((c, turn) -> targets.put(at.get(c).getId(), turn));
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() || !enabled() || !(entity instanceof ItemFrame f) || !targets.containsKey(f.getId())) return InteractionResult.PASS;
			if (ArrowPaths.clicks(turn(f), targets.get(f.getId())) == 0 && block.on() && !player.isShiftKeyDown()) return InteractionResult.FAIL;
			count(f);
			return InteractionResult.PASS;
		});
		World.paint(() -> {
			if (!enabled()) return;
			for (ItemFrame f : frames) {
				Integer to = targets.get(f.getId());
				if (to == null) continue;
				// Every arrow says how many clicks it still needs: 0 in green once it's right, then yellow, orange and red the further off it is.
				int left = ArrowPaths.clicks(turn(f), to);
				World.text(f.position().add(0, 0.1, 0), String.valueOf(left), left == 0 ? 0xFF55FF55 : left < 3 ? 0xFFFFFF55 : left < 6 ? 0xFFFFAA00 : 0xFFFF5555);
			}
		});
	}

	/** How long a click is waited on for the server's answer before it's given up on: longer on a slow connection. */
	private static long patience() {
		return Math.max(900, Terminals.ping() * 3 + 400);
	}

	/** Counts a click just made on an arrow as on its way. */
	private void count(ItemFrame f) {
		sent.take(f.getId(), f.getRotation(), f.getRotation(), 8, Util.getMillis(), patience());
	}

	/** A frame's place on its wall: how far left and how far up it is as you face it. */
	private static Cell cell(ItemFrame f) {
		Direction left = f.getDirection().getClockWise();
		return new Cell(f.blockPosition().getX() * left.getStepX() + f.blockPosition().getZ() * left.getStepZ(), f.blockPosition().getY());
	}

	/** The turn a frame will be at once the clicks on their way have landed. */
	private int turn(ItemFrame f) {
		return (f.getRotation() + sent.flying(f.getId(), f.getRotation(), f.getRotation(), Util.getMillis(), patience())) % 8;
	}
}
