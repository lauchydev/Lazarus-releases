package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LootRowsTest {
	private static final ToLongFunction<String> PRICE = item -> switch (item) {
		case "Rift Goo" -> 1_000;
		case "Ender Pearl" -> 5;
		default -> -1;
	};
	private static final Function<String, String> COLOUR = item -> item.equals("Rift Goo") ? "§9" : "§7";

	@Test
	void dropsAreMostValuableFirstWithTheirRateAndACutCount() {
		Map<String, Integer> drops = new LinkedHashMap<>();
		drops.put("Crypt Cache", 9);
		drops.put("Ender Pearl", 200);
		drops.put("Rift Goo", 40);
		assertEquals(List.of("§f40x §9Rift Goo §8(6.7 per fight)\t§a40k", "§f200x §7Ender Pearl §8(33.3 per fight)\t§a1k", "§8+1 more", "§7Value\t§a41k §7+ ?"),
				LootRows.drops(drops, 6, "fight", 2, PRICE, COLOUR));
	}

	@Test
	void dropsWithNoPriceComeLastMostCommonFirst() {
		Map<String, Integer> drops = new LinkedHashMap<>();
		drops.put("Crypt Cache", 1);
		drops.put("Mystery", 5);
		drops.put("Ender Pearl", 2);
		assertEquals(List.of("§f2x §7Ender Pearl\t§a10", "§f5x §7Mystery\t§8?", "§f1x §7Crypt Cache\t§8?", "§7Value\t§a10 §7+ ?"),
				LootRows.drops(drops, 0, "fight", 5, PRICE, COLOUR));
	}

	@Test
	void anItemWithNoPriceShowsAQuestionMarkForItsValue() {
		assertEquals("§8?", LootRows.worth("Crypt Cache", 3, PRICE));
		assertEquals("§a3k", LootRows.worth("Rift Goo", 3, PRICE));
	}

	@Test
	void raritiesTakeTheGamesColours() {
		assertEquals("§6", LootRows.rarity("LEGENDARY"));
		assertEquals("§5", LootRows.rarity("epic"));
		assertEquals("§c", LootRows.rarity("VERY_SPECIAL"));
		assertEquals("§f", LootRows.rarity(null));
	}

	@Test
	void noDropsMeansNoRows() {
		assertEquals(List.of(), LootRows.drops(Map.of(), 6, "fight", 5, PRICE, COLOUR));
	}

	@Test
	void rates() {
		assertEquals("1 in 12", LootRows.rate(1, 12, "kill"));
		assertEquals("2.5 per kill", LootRows.rate(5, 2, "kill"));
	}

	@Test
	void sinceRowsListOnlyTheRaresWithACount() {
		Function<String, String> colour = item -> item.equals("Golem") ? "§6" : "§5";
		assertEquals(List.of("§8Kills since last drop", "§6Golem §f6"), LootRows.since(Map.of("Golem", 6), List.of("Tier Boost Core", "Golem"), "Kills since last drop", colour));
		assertEquals(List.of(), LootRows.since(Map.of(), List.of("Golem"), "Kills since last drop", colour));
	}

	@Test
	void aPetCountsAsItsRare() {
		List<String> rares = List.of("Tier Boost Core", "Golem");
		assertEquals("Golem", LootRows.rareFor("Golem Pet", rares));
		assertEquals("Tier Boost Core", LootRows.rareFor("tier boost core", rares));
		assertNull(LootRows.rareFor("Ender Pearl", rares));
	}

	@Test
	void namesSplitsASettingAndDropsBlanks() {
		assertEquals(List.of("A", "B C"), LootRows.names("A, B C,, "));
	}
}
