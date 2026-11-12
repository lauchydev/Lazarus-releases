package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the dungeon map sits on the world: how many blocks a map pixel covers and where its corner is. A room's cell
 * on the map (room and gap) is 32 blocks of the dungeon, which gives the scale; the corner comes from where your own
 * marker is drawn against where you really stand, taken as the middle of many sightings since a marker is only
 * drawn to half a pixel. No Minecraft types, so it's tested on its own.
 */
public final class MapFit {
	/** How many blocks one cell of the dungeon's grid is: a 31-block room and the wall after it. */
	public static final double CELL_BLOCKS = 32;
	private static final int KEPT = 200;

	private final List<double[]> offsets = new ArrayList<>();
	private double scale;
	/** How many times the sightings have changed, and the middle as of which change. */
	private int changes, middleOf = -1;
	private final double[] middle = new double[2];

	/** Sets the scale from the map's cell size in pixels; a new size (another dungeon) starts the fit again. */
	public void pitch(int pixels) {
		double s = CELL_BLOCKS / pixels;
		if (s != scale) {
			scale = s;
			offsets.clear();
			changes++;
		}
	}

	/** A fit already known: blocks to a pixel, and the world place of the map's corner. */
	public static MapFit of(double scale, double offsetX, double offsetZ) {
		MapFit fit = new MapFit();
		fit.scale = scale;
		fit.offsets.add(new double[]{offsetX, offsetZ});
		fit.changes++;
		return fit;
	}

	/** How far into a cell of the dungeon its doors are: in the middle of a 31-block room, and in the one-block gap after it. */
	public static final int DOOR_MIDDLE = 15, DOOR_GAP = 31;

	/**
	 * Where a door of the map really is in the world: {x, z, 1 when the way through runs east-west else 0}. The map
	 * draws a room smaller than it is and the gap between rooms wider, so a door's own pixel is two or three blocks
	 * out both ways (seen in a dungeon saved from the game, 2026-11). What does hold is the grid: each cell of the
	 * map is 32 blocks, a 31-block room and a one-block gap, and a door sits in that gap, in the middle of the
	 * room's side. So the door is placed from its cell's corner, not from where it's drawn.
	 */
	public int[] door(DungeonLayout layout, DungeonLayout.Door d) {
		int gx = (int) Math.floor((d.x() - layout.originX) / layout.pitch), gy = (int) Math.floor((d.y() - layout.originY) / layout.pitch);
		boolean alongX = Math.floorMod((int) Math.round(d.x()) - layout.originX, layout.pitch) >= layout.roomSize;
		double cornerX = worldX(layout.originX + gx * layout.pitch), cornerZ = worldZ(layout.originY + gy * layout.pitch);
		return new int[]{(int) Math.round(cornerX) + (alongX ? DOOR_GAP : DOOR_MIDDLE), (int) Math.round(cornerZ) + (alongX ? DOOR_MIDDLE : DOOR_GAP), alongX ? 1 : 0};
	}

	/** Blocks to a map pixel; 0 before the map's cell size is known. */
	public double scale() {
		return scale;
	}

	/** The world place of the map's corner, along x (0) or z (1). */
	public double offset(int axis) {
		return middle(axis);
	}

	/** Notes your marker drawn at a map pixel while you stand at a place in the world. */
	public void saw(double px, double py, double worldX, double worldZ) {
		if (scale == 0) return;
		offsets.add(new double[]{worldX - px * scale, worldZ - py * scale});
		if (offsets.size() > KEPT) offsets.removeFirst();
		changes++;
	}

	/** Whether the map has been placed: the scale is known and your marker has been seen. */
	public boolean ready() {
		return scale > 0 && !offsets.isEmpty();
	}

	/** Forgets the fit: another dungeon is another map. */
	public void clear() {
		offsets.clear();
		changes++;
		scale = 0;
	}

	public double worldX(double px) {
		return px * scale + middle(0);
	}

	public double worldZ(double py) {
		return py * scale + middle(1);
	}

	public double mapX(double worldX) {
		return (worldX - middle(0)) / scale;
	}

	public double mapY(double worldZ) {
		return (worldZ - middle(1)) / scale;
	}

	/** The middle sighting along x (0) or z (1): asked for many times a frame, so worked out once for each change to the sightings. */
	private double middle(int axis) {
		if (middleOf != changes) {
			for (int a = 0; a < 2; a++) {
				int ax = a;
				double[] v = offsets.stream().mapToDouble(o -> o[ax]).sorted().toArray();
				middle[a] = v.length == 0 ? 0 : v[v.length / 2];
			}
			middleOf = changes;
		}
		return middle[axis];
	}
}
