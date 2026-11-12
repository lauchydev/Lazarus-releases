package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where a dungeon room sits in the world: its corner and which way it's turned. The same room is built at any of
 * four turns, so a place inside it is given by where it would be in the room unturned (0 to 30 each way) and
 * turned into the world by this. A room's frame is found by trying each turn, and a block or two either way of
 * where the map puts its corner, until the room's own blocks are where they should be. No Minecraft types, so it's
 * tested on its own.
 */
record RoomFrame(int cornerX, int cornerZ, int turn) {
	/** A room's last block along a side, counted from 0. */
	private static final int LAST = 30;

	/** Where a place in the unturned room ({@code x}, {@code z}, each 0 to 30) is in the world: {x, z}. */
	int[] real(int x, int z) {
		return switch (turn) {
			case 1 -> new int[]{cornerX + LAST - z, cornerZ + x};
			case 2 -> new int[]{cornerX + LAST - x, cornerZ + LAST - z};
			case 3 -> new int[]{cornerX + z, cornerZ + LAST - x};
			default -> new int[]{cornerX + x, cornerZ + z};
		};
	}

	/**
	 * The frame of a room whose corner the map puts near ({@code x}, {@code z}), the first that {@code fits} (the
	 * room's own blocks are where the frame says), trying the map's corner first; null when none does.
	 */
	static RoomFrame find(int x, int z, Predicate<RoomFrame> fits) {
		List<int[]> offsets = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) offsets.add(new int[]{dx, dz});
		offsets.sort((a, b) -> Integer.compare(Math.abs(a[0]) + Math.abs(a[1]), Math.abs(b[0]) + Math.abs(b[1])));
		for (int[] o : offsets) {
			for (int turn = 0; turn < 4; turn++) {
				RoomFrame f = new RoomFrame(x + o[0], z + o[1], turn);
				if (fits.test(f)) return f;
			}
		}
		return null;
	}
}
