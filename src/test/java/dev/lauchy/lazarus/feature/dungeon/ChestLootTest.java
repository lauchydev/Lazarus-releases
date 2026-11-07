package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestLootTest {
	private static final Map<String, Long> PRICES = Map.of("Wither Essence", 2_000L, "Bedrock Chest Key", 5_000_000L);

	private static long price(String name) {
		return PRICES.getOrDefault(name, -1L);
	}

	@Test
	void rewardChestWindowsAreKnownByMaterial() {
		assertEquals("Bedrock", ChestLoot.material("Bedrock Chest"));
		assertEquals("Wood", ChestLoot.material("Wooden Chest"));
		assertEquals("Gold", ChestLoot.material("Golden Chest"));
		assertEquals("Obsidian", ChestLoot.material("obsidian chest"));
		assertNull(ChestLoot.material("Large Chest"));
		assertNull(ChestLoot.material("Wither Chestplate"));
	}

	@Test
	void costIsReadFromTheLinesUnderIt() {
		ChestLoot.Cost c = ChestLoot.cost(List.of("Opens the chest.", "", "Cost", "2,000,000 Coins", "", "Click to open!"), ChestLootTest::price);
		assertEquals(2_000_000L, c.coins());
		assertTrue(c.known());
	}

	@Test
	void costOnOneLineWithSuffixesAndItems() {
		assertEquals(2_500_000L, ChestLoot.cost(List.of("Cost: 2.5M Coins"), ChestLootTest::price).coins());
		ChestLoot.Cost both = ChestLoot.cost(List.of("Cost", "1,000,000 Coins", "Bedrock Chest Key"), ChestLootTest::price);
		assertEquals(6_000_000L, both.coins());
		assertTrue(both.known());
		ChestLoot.Cost free = ChestLoot.cost(List.of("Cost", "FREE"), ChestLootTest::price);
		assertEquals(0L, free.coins());
		assertTrue(free.known());
	}

	@Test
	void anUnpricedCostItemLeavesTheCostUnknown() {
		ChestLoot.Cost c = ChestLoot.cost(List.of("Cost", "Mystery Key"), ChestLootTest::price);
		assertFalse(c.known());
		assertEquals(ChestLoot.Cost.NONE, ChestLoot.cost(List.of("Just a chest."), ChestLootTest::price));
	}

	@Test
	void countsBeforeOrAfterTheName() {
		assertEquals(new ChestLoot.Counted("Wither Essence", 55), ChestLoot.counted("Wither Essence x55"));
		assertEquals(new ChestLoot.Counted("Undead Essence", 1234), ChestLoot.counted("1,234x Undead Essence"));
		assertEquals(new ChestLoot.Counted("Necron's Handle", 1), ChestLoot.counted("Necron's Handle"));
	}

	@Test
	void theBestChestPaysMostOfThoseWithAKnownCost() {
		assertEquals("Obsidian", ChestLoot.best(List.of("Bedrock", "Obsidian", "Wood"), java.util.Arrays.asList(-5L, 12L, null)));
		assertNull(ChestLoot.best(List.of("Wood"), java.util.Arrays.asList((Long) null)));
	}
}
