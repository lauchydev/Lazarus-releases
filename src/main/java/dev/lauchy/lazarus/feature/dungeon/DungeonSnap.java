package dev.lauchy.lazarus.feature.dungeon;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A dungeon as it stood at one moment, saved to a file by {@code /lazarus snap}: the map's pixels, where the map
 * sits on the world, where you stood, every block round you, the things in it, and the trails that were showing.
 * It holds no one's name. A snap can be read back anywhere, so what the mod worked out in a real dungeon can be
 * worked out again in a test, and a wrong answer fixed against the dungeon it was wrong in. No Minecraft types.
 */
public final class DungeonSnap {
	/** What stands in a column the game hadn't loaded. */
	public static final String UNLOADED = "lazarus:unloaded";

	/** Something in the dungeon: its kind ("minecraft:silverfish"), the name over it (none for a player), and where. */
	public static final class Thing {
		public String type, name;
		public double x, y, z;
	}

	/** A trail as it was showing: its colour, the stops it was asked to go through ({x, z, 'x' / 'z' / '.'} each) and the points it was drawn through. */
	public static final class Shown {
		public int colour;
		/** Whether it was asked for the whole way, or only to the first stop. */
		public boolean whole;
		public List<int[]> stops = new ArrayList<>();
		public List<double[]> points = new ArrayList<>();
	}

	/** The map's 128×128 pixels as colours, row by row, 0 where nothing is drawn. */
	public int[] map = new int[0];
	/** Where the map sits on the world: blocks to a pixel, and the world place of its corner. */
	public double scale, offsetX, offsetZ;
	/** Where you stood and which way you faced. */
	public double x, y, z;
	public float yaw;
	/** The place the sidebar names for this dungeon ("690,-456"), "" when it names none. */
	public String origin = "";
	/** The blocks saved: the corner of the box and its size. */
	public int minX, minY, minZ, sizeX, sizeY, sizeZ;
	/** Each kind of block in the box ("minecraft:stone"), and whether you'd bump into it. */
	public List<String> palette = new ArrayList<>();
	public List<Boolean> solid = new ArrayList<>();
	/** The box's blocks as runs of one kind: {kind, how many} pairs, going along x, then z, then up. */
	public int[] runs = new int[0];
	public List<Thing> things = new ArrayList<>();
	public List<Shown> trails = new ArrayList<>();

	private transient int[] cells;

	/** The kind of block at a place, as an index into {@link #palette}; -1 outside the box. */
	public int kind(int bx, int by, int bz) {
		int dx = bx - minX, dy = by - minY, dz = bz - minZ;
		if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return -1;
		if (cells == null) {
			cells = new int[sizeX * sizeY * sizeZ];
			int at = 0;
			for (int i = 0; i + 1 < runs.length; i += 2) for (int n = 0; n < runs[i + 1] && at < cells.length; n++) cells[at++] = runs[i];
		}
		return cells[(dy * sizeZ + dz) * sizeX + dx];
	}

	/** The block at a place ("minecraft:coal_block"), "" outside the box. */
	public String block(int bx, int by, int bz) {
		int k = kind(bx, by, bz);
		return k < 0 ? "" : palette.get(k);
	}

	/** Packs a box of blocks, given as an index into {@link #palette} for each, into {@link #runs}. */
	public void pack(int[] kinds) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < kinds.length; ) {
			int n = 1;
			while (i + n < kinds.length && kinds[i + n] == kinds[i]) n++;
			out.add(kinds[i]);
			out.add(n);
			i += n;
		}
		runs = out.stream().mapToInt(Integer::intValue).toArray();
		cells = null;
	}

	/** The index of a kind of block in the palette, added when it's new. */
	public int kindOf(String id, boolean bumps) {
		for (int i = 0; i < palette.size(); i++) if (palette.get(i).equals(id) && solid.get(i) == bumps) return i;
		palette.add(id);
		solid.add(bumps);
		return palette.size() - 1;
	}

	/** The snap's blocks as a trail plan reads them. Outside the box nothing is solid and nothing is loaded. */
	TrailPlan.Blocks blocks() {
		return new TrailPlan.Blocks() {
			@Override
			public boolean solid(int bx, int by, int bz) {
				int k = kind(bx, by, bz);
				return k >= 0 && solid.get(k);
			}

			@Override
			public boolean shut(int bx, int by, int bz) {
				return solid(bx, by, bz) && !doorBlock(block(bx, by, bz));
			}

			@Override
			public boolean loaded(int bx, int bz) {
				int k = kind(bx, minY, bz);
				return k >= 0 && !palette.get(k).equals(UNLOADED);
			}
		};
	}

	/** Whether a block is what a shut door is made of: bars, a wither door's coal, the blood door's terracotta. */
	static boolean doorBlock(String id) {
		return id.equals("minecraft:iron_bars") || id.equals("minecraft:coal_block") || id.equals("minecraft:red_terracotta");
	}

	/** Where the map sits on the world, as the snap had it. */
	MapFit fit() {
		return MapFit.of(scale, offsetX, offsetZ);
	}

	public void write(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
			new Gson().toJson(this, w);
		}
	}

	public static DungeonSnap read(Path file) throws IOException {
		try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
			return new Gson().fromJson(r, DungeonSnap.class);
		}
	}
}
