package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.IceFillPath.Tile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceFillPathTest {
	/** A sheet from rows of text: '#' is ice, anything else a block standing on it. Row 0 is z = 0. */
	private static Set<Tile> sheet(String... rows) {
		Set<Tile> out = new HashSet<>();
		for (int z = 0; z < rows.length; z++) for (int x = 0; x < rows[z].length(); x++) if (rows[z].charAt(x) == '#') out.add(new Tile(x, z));
		return out;
	}

	private static void assertWalk(Set<Tile> tiles, Tile start, List<Tile> walk) {
		assertEquals(tiles.size(), walk.size(), "every tile once");
		assertEquals(tiles, new HashSet<>(walk));
		assertEquals(start, walk.getFirst());
		for (int i = 1; i < walk.size(); i++) {
			Tile a = walk.get(i - 1), b = walk.get(i);
			assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), "a step to a tile beside");
		}
	}

	@Test
	void aSheetWithBlocksOnItIsWalkedTileByTileOnce() {
		Set<Tile> tiles = sheet(
				"###",
				"#.#",
				"###");
		Tile start = new Tile(0, 0);
		List<Tile> walk = IceFillPath.solve(tiles, start);
		assertWalk(tiles, start, walk);
	}

	@Test
	void theWalkEndsAcrossTheSheetFromWhereItBegan() {
		Set<Tile> tiles = sheet(
				"#####",
				"#####",
				"#####");
		Tile start = new Tile(2, 0);
		List<Tile> walk = IceFillPath.solve(tiles, start);
		assertWalk(tiles, start, walk);
		assertEquals(2, walk.getLast().z(), "off at the far side: " + walk.getLast());
	}

	@Test
	void theBiggestSheetIsSolved() {
		// Seven by seven with blocks on it, as the room's last sheet is; on at the middle of the near side.
		Set<Tile> tiles = sheet(
				"#######",
				"#.#####",
				"#######",
				"#######",
				"###.###",
				"#####.#",
				"#######");
		Tile start = new Tile(3, 0);
		assertWalk(tiles, start, IceFillPath.solve(tiles, start));
	}

	@Test
	void aSheetNoSingleWalkCoversGivesNone() {
		// Three arms off one tile: any walk strands one of them.
		assertTrue(IceFillPath.solve(sheet(
				".#.",
				"###",
				".#."), new Tile(0, 1)).isEmpty());
		assertTrue(IceFillPath.solve(sheet("##"), new Tile(5, 5)).isEmpty(), "not standing on the sheet");
	}
}
