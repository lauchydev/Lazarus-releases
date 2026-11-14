package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestPoolsTest {
	@Test
	void aWoodChestHoldsSuppliesAndCryptguardOnly() {
		List<String> wood = ChestPools.of(1, "Wood");
		assertTrue(wood.contains("spirit_leap"));
		assertTrue(wood.contains("cryptguard_boots"));
		assertFalse(wood.contains("wither_catalyst"), "a Diamond chest's drop");
		assertFalse(wood.contains("recall_totem"), "a rare item from Gold up");
		assertEquals(12, wood.size());
	}

	@Test
	void aDearerChestHoldsTheCheaperChestsRareItemsAndItsOwnDrops() {
		List<String> bedrock = ChestPools.of(1, "Bedrock");
		assertTrue(bedrock.contains("oathguard_boots"), "listed for Diamond, seen in Bedrock");
		assertTrue(bedrock.contains("wither_core"), "Bedrock's own");
		assertFalse(ChestPools.of(1, "Obsidian").contains("wither_core"));
		assertTrue(ChestPools.of(1, "Emerald").contains("arcane_blood"));
		assertFalse(ChestPools.of(1, "Diamond").contains("oathguard_leggings"), "from Emerald up");
	}

	@Test
	void noChestHoldsEssenceAndEachItemIsListedOnce() {
		for (String chest : List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock")) {
			List<String> ids = ChestPools.of(1, chest);
			assertTrue(ids.stream().noneMatch(id -> id.contains("essence")));
			assertEquals(ids.size(), ids.stream().distinct().count());
		}
	}

	@Test
	void anotherFloorHasItsOwnRareItems() {
		assertTrue(ChestPools.of(2, "Obsidian").contains("shadow_fury"));
		assertFalse(ChestPools.of(2, "Obsidian").contains("oathguard_chestplate"));
		assertEquals(List.of(), ChestPools.of(1, "Iron"));
	}

	@Test
	void theFloorIsReadFromTheSidebar() {
		assertEquals(1, ChestPools.floor(List.of("DRAGSIM", " The Catacombs (F1)", "Cleared: 97%")));
		assertEquals(3, ChestPools.floor(List.of("The Catacombs (F3)")));
		assertEquals(1, ChestPools.floor(List.of("Hub")), "no floor named: the first");
	}
}
