package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Higher Or Lower: kill the blazes in order of health. A floating label beside the room's
 * title says which end goes first ("Highest health first."); each blaze's tag carries its
 * health ("[Lv100] Blaze 225000/225000❤"). Where there's no such label the chest tells: on top,
 * above the blazes, the strongest goes first; at the bottom, below them, the weakest. The blaze to kill now is boxed, and the one after it
 * in a second colour (a third too, if you turn it on), ordered by full health so a damaged
 * one doesn't jump the queue.
 */
public final class HigherOrLower extends Feature {
	private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kKmMbB]?)\\s*/\\s*([\\d.,]+[kKmMbB]?)\\s*❤");
	private static final double RANGE = 24, DEPTH = 70;

	private final BoolSetting through = add(new BoolSetting("walls", "Through walls", "Show the box through blocks.", true));

	private final ColorSetting now = add(new ColorSetting("now", "Current blaze", "Colour of the blaze to kill now.", 0xFF55FF55));
	private final ColorSetting after = add(new ColorSetting("after", "Next blaze", "Colour of the blaze to kill after it.", 0xFFFFAA00));
	private final BoolSetting showThird = add(new BoolSetting("showThird", "Show a third blaze", "Box the blaze after those two as well.", false));
	private final ColorSetting third = add(new ColorSetting("third", "Third blaze", "Colour of the third blaze, when it's shown.", 0xFFFF5555));

	/** Which end goes first: read from the room, or as you say when the room is read wrong. */
	public enum Order { AUTO, HIGHEST_FIRST, LOWEST_FIRST }

	private final ChoiceSetting<Order> order = add(new ChoiceSetting<>("order", "Order", "Auto reads the room: its label, or where its chest is (on top, strongest first; at the bottom, weakest first). Set it yourself if that's wrong.", Order.AUTO));
	private static HigherOrLower instance;

	private boolean said;
	private int ticks;
	private Vec3 origin;
	private Boolean highestFirst;
	/** The blazes to kill, soonest first: as many as are shown. */
	private List<Entity> next = List.of();

	public HigherOrLower() {
		super("higherOrLower", "Higher Or Lower", "Boxes the blaze to kill now and the one after it, in the order the room asks for.", Category.DUNGEONS, true);
		instance = this;
	}

	@Override
	public void init() {
		// The server never says you've entered the room, so the room is known by its blazes near you.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				next = List.of();
				return;
			}
			// Every mob's name is read to find the blazes: a few times a second is plenty, and the boxes follow their blazes between.
			if (++ticks % 4 != 0) return;
			next = List.of();
			origin = mc.player.position();
			List<Entity> blazes = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				// The blazes hang in a column forty blocks deep: near you on the flat, however far up or down.
				if (Math.abs(e.getX() - origin.x) > RANGE || Math.abs(e.getZ() - origin.z) > RANGE || Math.abs(e.getY() - origin.y) > DEPTH) continue;
				String n = Entities.name(e);
				if (e instanceof Display.TextDisplay && highestFirst == null) {
					String t = n.toLowerCase(Locale.ROOT);
					if (t.contains("highest") || t.contains("strongest")) highestFirst = true;
					else if (t.contains("lowest") || t.contains("weakest")) highestFirst = false;
				}
				if (n.contains("Blaze") && HEALTH.matcher(n).find()) blazes.add(e);
			}
			if (blazes.isEmpty()) {
				// Out of the room: the next one starts afresh.
				highestFirst = null;
				said = false;
				return;
			}
			if (order.get() != Order.AUTO) highestFirst = order.get() == Order.HIGHEST_FIRST;
			// No label says which way: the chest does. On top, above the blazes, the strongest goes first; at the bottom, below them, the weakest.
			if (highestFirst == null && ticks % 20 == 0) highestFirst = byChest(mc.level, blazes);
			if (highestFirst == null) return;
			if (!said) {
				said = true;
				say().info("Higher Or Lower: ").highlight(highestFirst ? "highest" : "lowest").info(" health first.").send();
			}
			blazes.removeIf(e -> body(e) instanceof Blaze b && b.isDeadOrDying());
			next = queue(blazes, HigherOrLower::maxHealth, highestFirst, showThird.on() ? 3 : 2).stream().map(HigherOrLower::body).toList();
		});
		World.paint(() -> {
			if (!enabled()) return;
			// Last first, so the blaze to kill now is drawn over the others where they overlap.
			for (int i = next.size() - 1; i >= 0; i--) World.box(next.get(i).getBoundingBox().inflate(0.1), (i == 0 ? now : i == 1 ? after : third).argb(), true, true, through.on());
		});
	}

	/**
	 * The colour a blaze should glow in, when it's one of those to kill next: the same colours as the boxes, so
	 * they stand out from the white glow on every other mob in the room. Null for anything else.
	 */
	public static Integer glowColour(Entity e) {
		HigherOrLower h = instance;
		if (h == null || h.next.isEmpty() || !h.enabled()) return null;
		int i = h.next.indexOf(e);
		return i < 0 ? null : (i == 0 ? h.now : i == 1 ? h.after : h.third).argb() & 0xFFFFFF;
	}

	/**
	 * Which way the chest says to go: true (strongest first) when it's on top, above the blazes; false (weakest
	 * first) when it's at the bottom, below them; null when no chest is in sight. The chest is the one nearest the
	 * blazes' column: a dungeon has others. (A room saved from Dragsim, 2026-11: ten blazes from y 27 to 63, the
	 * chest at y 69 in the middle of their column.)
	 */
	private static Boolean byChest(net.minecraft.world.level.Level level, List<Entity> blazes) {
		double x = 0, y = 0, z = 0;
		for (Entity b : blazes) {
			x += b.getX();
			y += b.getY();
			z += b.getZ();
		}
		net.minecraft.core.BlockPos mid = net.minecraft.core.BlockPos.containing(x / blazes.size(), y / blazes.size(), z / blazes.size());
		net.minecraft.core.BlockPos chest = null;
		double nearest = Double.MAX_VALUE;
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(mid.offset(-8, -45, -8), mid.offset(8, 45, 8))) {
			var s = level.getBlockState(p);
			if (!s.is(net.minecraft.world.level.block.Blocks.CHEST) && !s.is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) continue;
			double far = Math.pow(p.getX() - mid.getX(), 2) + Math.pow(p.getZ() - mid.getZ(), 2);
			if (far < nearest) {
				nearest = far;
				chest = p.immutable();
			}
		}
		return chest == null ? null : chest.getY() > mid.getY();
	}

	/** The first {@code count} of these in the order they're to be killed: by health, highest or lowest first. */
	static <T> List<T> queue(List<T> blazes, ToDoubleFunction<T> health, boolean highestFirst, int count) {
		Comparator<T> order = Comparator.comparingDouble(health);
		return blazes.stream().sorted(highestFirst ? order.reversed() : order).limit(count).toList();
	}

	private static double maxHealth(Entity e) {
		Matcher m = HEALTH.matcher(Entities.name(e));
		return m.find() ? Text.number(m.group(2)) : 0;
	}

	/** The blaze itself when the tag rides a stand above it. */
	private static Entity body(Entity tag) {
		Entity b = Entities.owner(tag, e -> e instanceof Blaze);
		return b != null ? b : tag;
	}
}
