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
		// Dragsim titles the window with the material alone (2026-11).
		assertEquals("Obsidian", ChestLoot.material("Obsidian"));
		assertEquals("Bedrock", ChestLoot.material("Bedrock"));
		assertNull(ChestLoot.material("Obsidian Sanctuary"));
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
	void anItemsAmountIsWhatItsLoreSaysNotItsStack() {
		// As Dragsim's reward chest showed them (2026-11): the stack can't hold 220.
		assertEquals(220, ChestLoot.amount(List.of("Amount: 220", "", "Click the chest below to purchase", "these rewards!", "", "EPIC"), 64));
		assertEquals(1234, ChestLoot.amount(List.of("Amount: 1,234"), 1));
		assertEquals(3, ChestLoot.amount(List.of("Just an item."), 3));
	}

	@Test
	void theRollLandsOnTheDearestItemThatIsNotEssence() {
		// 220 Undead Essence is worth more than the Revive Stones, but essence is never what the roll lands on.
		assertEquals(2, ChestLoot.winner(List.of("Undead Essence", "Wither Essence", "Revive Stone", "Trap Defusal Kit"), List.of(900_000L, 400_000L, 300_000L, 50_000L)));
		assertEquals(1, ChestLoot.winner(List.of("Trap Defusal Kit", "Necron's Handle"), List.of(-1L, 800_000_000L)), "an unpriced item loses to a priced one");
		assertEquals(-1, ChestLoot.winner(List.of("Undead Essence", "Wither Essence"), List.of(10L, 900L)), "only essence in the chest: nothing to roll for");
	}

	@Test
	void aRewardSaysHowManyAButtonDoesNot() {
		assertTrue(ChestLoot.listed(List.of("Amount: 24", "", "Click the chest below to purchase", "these rewards!", "", "EPIC")));
		assertFalse(ChestLoot.listed(List.of("Consumes one Kismet Feather.", "Replaces only this chest's rewards and price.", "Click to confirm a reroll!")));
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
