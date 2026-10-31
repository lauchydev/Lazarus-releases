package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.feature.end.MiningMaterials.Gains;
import dev.lauchy.lazarus.feature.end.MiningMaterials.Material;
import dev.lauchy.lazarus.feature.end.MiningMaterials.Units;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningMaterialsTest {
	/** The NPC prices from dragsim.co's catalogue; plain End Stone isn't in it. */
	private static final Map<String, Long> NPC = Map.ofEntries(
			Map.entry("Enchanted End Stone", 8_000L), Map.entry("Refined End Stone", 128_000L),
			Map.entry("Obsidian", 400L), Map.entry("Enchanted Obsidian", 64_000L), Map.entry("Refined Obsidian", 1_024_000L),
			Map.entry("Rough Amethyst", 50L), Map.entry("Flawed Amethyst", 8_160L), Map.entry("Fine Amethyst", 1_305_600L),
			Map.entry("Flawless Amethyst", 104_448_000L), Map.entry("Perfect Amethyst", 522_240_000L));
	private static final ToLongFunction<String> PRICE = name -> NPC.getOrDefault(name, -1L);

	@Test
	void readsEveryTierInRawBlocks() {
		assertEquals(160, MiningMaterials.piece("enchanted_obsidian", 1).units());
		assertEquals(2_560 * 3, MiningMaterials.piece("Refined End Stone", 3).units());
		assertEquals(6_400, MiningMaterials.piece("Fine Amethyst", 1).units());
		assertEquals(Material.CRYING_OBSIDIAN, MiningMaterials.piece("Crying Obsidian", 5).material());
		assertTrue(MiningMaterials.piece("Flawed Amethyst", 1).compacted());
		assertFalse(MiningMaterials.piece("Obsidian", 64).compacted());
	}

	@Test
	void toolsAndArmourAreNotMaterial() {
		assertNull(MiningMaterials.piece("Obsidian Pickaxe", 1));
		assertNull(MiningMaterials.piece("Amethyst Chestplate", 1));
		assertNull(MiningMaterials.piece("Refined Obsidian Pickaxe", 1));
		assertNull(MiningMaterials.piece("Obsidian", 0));
	}

	@Test
	void valuesTheWayTheBagHoldsIt() {
		assertEquals(64_000, MiningMaterials.value(Material.OBSIDIAN, 160, PRICE));
		assertEquals(1_024_000 + 64_000 + 400, MiningMaterials.value(Material.OBSIDIAN, 2_560 + 160 + 1, PRICE));
		// A Fine is worth more than 80 Flawed, and leftover Rough less than either.
		assertEquals(1_305_600 + 8_160 + 250, MiningMaterials.value(Material.AMETHYST, 6_400 + 80 + 5, PRICE));
		assertEquals(0, MiningMaterials.value(Material.AMETHYST, -1, PRICE));
	}

	@Test
	void anUnpricedRawBlockGoesAtTheRateAboveIt() {
		// 8,000 for 160 is 50 a block.
		assertEquals(8_000 + 50 * 10, MiningMaterials.value(Material.END_STONE, 170, PRICE));
		assertEquals(0, MiningMaterials.value(Material.END_STONE, 10, name -> -1));
	}

	@Test
	void saysAmountsInTheChosenTier() {
		assertEquals("48 Ench", MiningMaterials.amount(Material.OBSIDIAN, 48 * 160, Units.ENCHANTED));
		assertEquals("3 Ref", MiningMaterials.amount(Material.OBSIDIAN, 3 * 2_560 + 5, Units.REFINED));
		// Less than one Refined is said in Enchanted, less than one Enchanted in blocks.
		assertEquals("8 Ench", MiningMaterials.amount(Material.OBSIDIAN, 8 * 160, Units.REFINED));
		assertEquals("90", MiningMaterials.amount(Material.OBSIDIAN, 90, Units.REFINED));
		assertEquals("12 Fine", MiningMaterials.amount(Material.AMETHYST, 12 * 6_400, Units.REFINED));
		assertEquals("3.2k", MiningMaterials.amount(Material.END_STONE, 3_200, Units.RAW));
	}

	@Test
	void compactingMinedMaterialChangesNothing() {
		Gains g = new Gains();
		// 80 Rough out, then 1 Flawed in, a tick apart.
		assertEquals(-80, g.apply(Material.AMETHYST, 80, -80, 0, 1_000));
		assertEquals(80, g.apply(Material.AMETHYST, 0, 80, 80, 1_050));
	}

	@Test
	void compactingBroughtMaterialIsNotMining() {
		Gains g = new Gains();
		assertEquals(0, g.apply(Material.AMETHYST, 0, -80, 0, 1_000));
		assertEquals(0, g.apply(Material.AMETHYST, 0, 80, 80, 1_050));
		// Only the part the count couldn't absorb is cancelled.
		g.clear();
		assertEquals(-20, g.apply(Material.AMETHYST, 20, -80, 0, 2_000));
		assertEquals(20, g.apply(Material.AMETHYST, 0, 80, 80, 2_050));
	}

	@Test
	void freshlyMinedBlocksAlwaysCount() {
		Gains g = new Gains();
		assertEquals(0, g.apply(Material.AMETHYST, 0, -80, 0, 1_000));
		// New Rough with no compacted rise doesn't pay the debt.
		assertEquals(7, g.apply(Material.AMETHYST, 0, 7, 0, 1_020));
		// Rough and the returned Flawed in one look: only the Flawed is cancelled.
		assertEquals(7, g.apply(Material.AMETHYST, 7, 87, 80, 1_040));
		// Another material never shares the debt.
		assertEquals(0, g.apply(Material.AMETHYST, 0, -80, 0, 2_000));
		assertEquals(160, g.apply(Material.OBSIDIAN, 0, 160, 160, 2_010));
	}

	@Test
	void aDebtExpires() {
		Gains g = new Gains();
		assertEquals(0, g.apply(Material.AMETHYST, 0, -160, 0, 1_000));
		assertEquals(0, g.apply(Material.AMETHYST, 0, 80, 80, 1_100));
		assertEquals(80, g.apply(Material.AMETHYST, 0, 80, 80, 1_000 + Gains.CARRY_MS + 1));
		// A clock that went backwards drops the debt rather than holding it.
		assertEquals(0, g.apply(Material.AMETHYST, 0, -80, 0, 5_000));
		assertEquals(80, g.apply(Material.AMETHYST, 0, 80, 80, 4_999));
	}

	@Test
	void readsMiningTime() {
		assertEquals("12m 34s", MiningMaterials.time(754_000));
		assertEquals("1h 5m", MiningMaterials.time(3_900_000));
		assertEquals("9s", MiningMaterials.time(9_999));
	}
}
