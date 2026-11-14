package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The run you're in, as every dungeon feature reads it: the map's rooms and doors ({@link DungeonLayout}), where the
 * map sits on the world ({@link MapFit}, from your own marker), the room you're standing in, and the puzzles named so
 * far. Read from the map item a few times a second; forgotten when you leave the dungeon.
 */
public final class Dungeon {
	private static boolean ready;
	private static DungeonLayout layout;
	private static final MapFit fit = new MapFit();
	private static Object level;
	private static int ticks;
	/** The map's pixels as last read, as one number: the map is only read again when they change. */
	private static int mapHash;
	/** Where you were a second ago, to tell when you're standing still (a marker lags a moving player). */
	private static Vec3 was;
	private static int still;
	/** Each puzzle room's name once known, by its first cell ("3,1"). */
	private static final Map<String, String> puzzles = new HashMap<>();

	private Dungeon() {
	}

	/** Starts reading the map; safe to call from every feature that needs it. */
	static void init() {
		if (ready) return;
		ready = true;
		ClientTickEvents.END_CLIENT_TICK.register(Dungeon::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || !Area.inDungeon()) {
			forget();
			return;
		}
		if (mc.level != level) {
			forget();
			level = mc.level;
		}
		ticks++;
		MapItemSavedData data = data(mc);
		if (data == null) return;
		int pixels = ticks % 5 == 0 ? java.util.Arrays.hashCode(data.colors) : mapHash;
		if (ticks % 5 == 0 && (pixels != mapHash || layout == null)) {
			mapHash = pixels;
			int[] argb = new int[DungeonLayout.SIZE * DungeonLayout.SIZE];
			for (int i = 0; i < argb.length && i < data.colors.length; i++) {
				int packed = data.colors[i] & 0xFF;
				argb[i] = packed / 4 == 0 ? 0 : MapColor.getColorFromPackedId(packed) | 0xFF000000;
			}
			DungeonLayout read = DungeonLayout.read(argb);
			if (read != null) {
				layout = read;
				fit.pitch(read.pitch);
			}
		}
		// Your marker says where the map sits, most truly while you stand still.
		Vec3 here = mc.player.position();
		still = was != null && was.distanceToSqr(here) < 0.0004 ? still + 1 : 0;
		was = here;
		if (layout != null && (still >= 10 || !fit.ready()) && ticks % 2 == 0) {
			MapDecoration mine = mine(mc, data);
			if (mine != null) fit.saw(pixel(mine.x()), pixel(mine.y()), here.x, here.z);
		}
	}

	private static void forget() {
		layout = null;
		mapHash = 0;
		level = null;
		was = null;
		fit.clear();
		puzzles.clear();
	}

	/** A marker's byte (-128 to 127, half pixels) as a map pixel. */
	static double pixel(byte b) {
		return (b + 128) / 2.0;
	}

	/** The marker that is you: the only one, else the one drawn as the map's holder, else the nearest to where you stand. */
	static MapDecoration mine(Minecraft mc, MapItemSavedData data) {
		List<MapDecoration> all = new ArrayList<>();
		data.getDecorations().forEach(all::add);
		if (all.isEmpty()) return null;
		if (all.size() == 1) return all.getFirst();
		List<MapDecoration> frames = all.stream().filter(d -> d.type().equals(MapDecorationTypes.FRAME) || d.type().equals(MapDecorationTypes.PLAYER)).toList();
		if (frames.size() == 1) return frames.getFirst();
		if (!fit.ready() || mc.player == null) return null;
		double x = fit.mapX(mc.player.getX()), y = fit.mapY(mc.player.getZ());
		MapDecoration best = null;
		double bestD = 100;
		for (MapDecoration d : all) {
			double dd = Math.pow(pixel(d.x()) - x, 2) + Math.pow(pixel(d.y()) - y, 2);
			if (dd < bestD) {
				bestD = dd;
				best = d;
			}
		}
		return best;
	}

	/** The map in your inventory, hotbar first, or null. */
	static MapId mapId(Minecraft mc) {
		if (mc.player == null) return null;
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			MapId id = s.get(DataComponents.MAP_ID);
			if (id != null) return id;
		}
		return null;
	}

	static MapItemSavedData data(Minecraft mc) {
		MapId id = mapId(mc);
		return id == null || mc.level == null ? null : mc.level.getMapData(id);
	}

	/** The run's rooms and doors, or null before the map shows a dungeon. */
	static DungeonLayout layout() {
		return layout;
	}

	/** Where the map sits on the world; check {@link MapFit#ready} first. */
	static MapFit fit() {
		return fit;
	}

	/** Whether rooms can be placed in the world: the map is read and your marker has been seen. */
	static boolean placed() {
		return layout != null && fit.ready();
	}

	/** The room you're standing in, or null when it can't be told. */
	static DungeonLayout.Room roomHere() {
		Minecraft mc = Minecraft.getInstance();
		if (!placed() || mc.player == null) return null;
		return layout.roomAt(fit.mapX(mc.player.getX()), fit.mapY(mc.player.getZ()));
	}

	/** A room's key for remembering things about it: its first cell. */
	static String key(DungeonLayout.Room r) {
		int[] c = r.cells().getFirst();
		for (int[] o : r.cells()) if (o[0] < c[0] || o[0] == c[0] && o[1] < c[1]) c = o;
		return c[0] + "," + c[1];
	}

	/** A puzzle room's name once someone has been in it, or null. */
	static String puzzle(DungeonLayout.Room r) {
		return puzzles.get(key(r));
	}

	static void name(DungeonLayout.Room r, String puzzle) {
		puzzles.put(key(r), puzzle);
	}

	/**
	 * A locked door's own blocks, found near where the map puts it: the block of its kind nearest that spot, and
	 * every block of that kind joined to it, a few blocks each way at most. The room behind is often built of the
	 * same block (the blood room is full of red terracotta), so nothing further off counts. Null when none is in
	 * sight.
	 */
	static net.minecraft.world.phys.AABB doorBox(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos about, net.minecraft.world.level.block.Block block) {
		net.minecraft.core.BlockPos seed = null;
		double best = Double.MAX_VALUE;
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(about.offset(-7, -12, -7), about.offset(7, 12, 7))) {
			if (!level.getBlockState(p).is(block)) continue;
			double far = Math.pow(p.getX() - about.getX(), 2) + Math.pow(p.getZ() - about.getZ(), 2) + Math.abs(p.getY() - about.getY()) * 0.01;
			if (far < best) {
				best = far;
				seed = p.immutable();
			}
		}
		if (seed == null) return null;
		// The door is the blocks joined to that one, block to block; a door is a few blocks each way, so the spread stops there.
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(seed);
		java.util.Set<net.minecraft.core.BlockPos> seen = new java.util.HashSet<>(java.util.List.of(seed));
		java.util.ArrayDeque<net.minecraft.core.BlockPos> spread = new java.util.ArrayDeque<>(java.util.List.of(seed));
		while (!spread.isEmpty()) {
			net.minecraft.core.BlockPos p = spread.poll();
			box = box.minmax(new net.minecraft.world.phys.AABB(p));
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
				net.minecraft.core.BlockPos n = p.relative(side);
				if (Math.abs(n.getX() - seed.getX()) > 4 || Math.abs(n.getZ() - seed.getZ()) > 4 || Math.abs(n.getY() - seed.getY()) > 5) continue;
				if (level.getBlockState(n).is(block) && seen.add(n)) spread.add(n);
			}
		}
		return box;
	}

	/** A room's bounds in the world: {minX, minZ, maxX, maxZ} over all its cells. */
	static double[] bounds(DungeonLayout.Room r) {
		double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (int[] c : r.cells()) {
			double px = layout.originX + c[0] * layout.pitch, py = layout.originY + c[1] * layout.pitch;
			x0 = Math.min(x0, fit.worldX(px));
			z0 = Math.min(z0, fit.worldZ(py));
			x1 = Math.max(x1, fit.worldX(px + layout.roomSize));
			z1 = Math.max(z1, fit.worldZ(py + layout.roomSize));
		}
		return new double[]{x0, z0, x1, z1};
	}
}
