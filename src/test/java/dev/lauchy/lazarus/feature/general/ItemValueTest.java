package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.feature.general.ItemValue.Kind;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValueTest {
	/** Upgrade prices for the tests: books 20M, golden books 100M, a recomb 500M, the Renowned stones 100M and 60M, residue 20M. */
	private static final ItemValue.Prices PRICES = new ItemValue.Prices() {
		private final Map<String, Long> known = Map.of("hot_potato_book", 20_000_000L, "golden_hot_potato_book", 100_000_000L,
				"recombobulator_3000", 500_000_000L, "dragon_horn", 100_000_000L, "cheap_horn", 60_000_000L, "dye_aurora", 1_000_000_000L,
				"implosion_scroll", 4_000_000_000L, "wither_shield_scroll", 1_000_000_000L, "ritual_residue", 20_000_000L, "gold_claws", 400_000_000L);

		@Override
		public long of(String id) {
			return known.getOrDefault(id, -1L);
		}

		@Override
		public List<String> stones(String reforge) {
			return reforge.equals("renowned") ? List.of("dragon_horn", "cheap_horn") : List.of();
		}
	};

	/** Half of every upgrade's price. */
	private static final ItemValue.Model HALF;

	static {
		Map<Kind, Double> share = new EnumMap<>(Kind.class);
		for (Kind k : Kind.values()) share.put(k, 0.5);
		HALF = new ItemValue.Model(share);
	}

	private static JsonObject item(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void readsEveryUpgradeOnAnItem() {
		JsonObject sword = item("{\"entry\":\"giants_sword\",\"item_data\":{\"reforge\":\"Renowned\"},"
				+ "\"item_modifiers\":{\"hot_potato_books\":15,\"golden_hot_potato_books\":5,\"recombobulated\":true,\"star_level\":5,\"art_of_war\":true,"
				+ "\"enrichment\":\"magic_find_enrichment\",\"rune_id\":\"end_rune\"},"
				+ "\"extra\":{\"item_dye\":{\"dyeId\":\"dye_aurora\"},\"item_skin\":{\"skinId\":\"superior_shimmer\"},\"wither_scrolls\":{\"mask\":5},"
				+ "\"drill\":{\"engine\":\"void_polished_engine\",\"coated\":true}}}");
		List<Kind> kinds = ItemValue.upgrades(sword).stream().map(ItemValue.Upgrade::kind).toList();
		assertEquals(List.of(Kind.REFORGE, Kind.HOT_POTATO_BOOKS, Kind.FUMING_POTATO_BOOKS, Kind.GOLDEN_HOT_POTATO_BOOKS, Kind.RECOMBOBULATOR,
				Kind.STARS, Kind.DYE, Kind.ITEM_SKIN, Kind.ART_OF_WAR, Kind.WITHER_SCROLLS, Kind.WITHER_SCROLLS, Kind.ENRICHMENT, Kind.RUNE,
				Kind.DRILL_PARTS, Kind.COATING), kinds);
		ItemValue.Upgrade books = ItemValue.upgrades(sword).get(1);
		assertEquals(10, books.count());
		assertEquals(5, ItemValue.upgrades(sword).get(2).count());
	}

	@Test
	void aCleanItemHasNoUpgrades() {
		assertTrue(ItemValue.upgrades(item("{\"entry\":\"bonemerang\",\"item_data\":{\"reforge\":\"\"},\"item_modifiers\":{\"hot_potato_books\":0,"
				+ "\"recombobulated\":false,\"star_level\":0},\"extra\":{}}")).isEmpty());
	}

	@Test
	void gameDataStoresFlagsAsNumbers() {
		assertEquals(Kind.RECOMBOBULATOR, ItemValue.upgrades(item("{\"item_modifiers\":{\"recombobulated\":1}}")).getFirst().kind());
		assertTrue(ItemValue.upgrades(item("{\"item_modifiers\":{\"recombobulated\":0}}")).isEmpty());
	}

	@Test
	void eachUpgradeAddsItsShareOfItsOwnPriceAndStarsOfTheResidueTheyTook() {
		JsonObject bow = item("{\"entry\":\"bonemerang\",\"item_data\":{\"reforge\":\"Renowned\"},"
				+ "\"item_modifiers\":{\"hot_potato_books\":10,\"recombobulated\":true,\"star_level\":5}}");
		ItemValue.Valued v = ItemValue.value(bow, 400_000_000, PRICES, HALF);
		// Reforge: the cheaper stone, 60M, half is 30M. Books: 10 x 20M x 0.5. Recomb: 250M. Stars: 1+2+3+4+5 residue x 20M x 0.5.
		assertEquals(30_000_000 + 100_000_000 + 250_000_000 + 150_000_000, v.upgrades());
		assertEquals(400_000_000 + 530_000_000, v.total());
	}

	@Test
	void fumingBooksArePricedAsHotPotatoBooksWhenTheyHaveNoPriceOfTheirOwn() {
		ItemValue.Valued v = ItemValue.value(item("{\"item_modifiers\":{\"hot_potato_books\":12}}"), 100, PRICES, HALF);
		assertEquals(12 * 10_000_000, v.upgrades());
	}

	@Test
	void scrollsCountByTheirBits() {
		ItemValue.Valued v = ItemValue.value(item("{\"extra\":{\"wither_scrolls\":{\"mask\":5}}}"), 100, PRICES, HALF);
		assertEquals(2_000_000_000L + 500_000_000L, v.upgrades());
		assertEquals(List.of("Implosion", "Wither Shield"), v.parts().stream().map(ItemValue.Part::detail).toList());
	}

	@Test
	void aPetCountsItsHeldItemAndSkin() {
		// A pet as dragsim.co's market lists it, 2026-10-29.
		JsonObject pet = item("{\"entry\":\"ender_dragon_pet_legendary\",\"extra\":{\"pet_data\":{\"totalXp\":0,\"equippedItem\":\"gold_claws\","
				+ "\"skinOverride\":\"pet_skin_enderdragon_neon_red\"}}}");
		ItemValue.Valued v = ItemValue.value(pet, 900_000_000, PRICES, HALF);
		assertEquals(List.of(new ItemValue.Part(Kind.PET_SKIN, ItemValue.Part.UNPRICED, "pet_skin_enderdragon_neon_red"),
				new ItemValue.Part(Kind.PET_ITEM, 200_000_000, "gold_claws")), v.parts());
		assertTrue(ItemValue.upgrades(item("{\"extra\":{\"pet_data\":{\"equippedItem\":\"null\"}}}")).isEmpty());
	}

	@Test
	void eachStarTakesOneMoreResidueThanTheLast() {
		assertEquals(1, ItemValue.residue(1));
		assertEquals(3, ItemValue.residue(2));
		assertEquals(15, ItemValue.residue(5));
	}

	@Test
	void anUpgradeNothingPricesShowsButAddsNothing() {
		ItemValue.Valued v = ItemValue.value(item("{\"extra\":{\"item_skin\":{\"skinId\":\"mystery_skin\"}}}"), 100, PRICES, HALF);
		assertEquals(List.of(new ItemValue.Part(Kind.ITEM_SKIN, ItemValue.Part.UNPRICED, "mystery_skin")), v.parts());
		assertEquals(0, v.upgrades());
		assertEquals(100, v.total());
	}

	@Test
	void withNoCleanPriceTheItemHasNoValue() {
		assertEquals(-1, ItemValue.value(item("{\"item_modifiers\":{\"recombobulated\":true}}"), -1, PRICES, HALF).total());
	}

	@Test
	void aSaleReadCleanUndoesItsUpgrades() {
		JsonObject bow = item("{\"item_data\":{\"reforge\":\"Renowned\"},\"item_modifiers\":{\"hot_potato_books\":10,\"recombobulated\":true,\"star_level\":5}}");
		long total = ItemValue.value(bow, 400_000_000, PRICES, HALF).total();
		assertEquals(400_000_000, ItemValue.clean(total, bow, PRICES, HALF), 1);
	}

	@Test
	void aSaleReadCleanIsNeverBelowATenthOfItsPrice() {
		assertEquals(10_000_000, ItemValue.clean(100_000_000, item("{\"item_modifiers\":{\"recombobulated\":true}}"), PRICES, HALF), 1);
	}

	@Test
	void unfittedSharesFallBackAndFumingBooksFollowHotPotatoBooks() {
		ItemValue.Model m = ItemValue.Model.read(item("{\"share\":{\"hot_potato_books\":0.5}}"));
		assertEquals(0.5, m.share(Kind.HOT_POTATO_BOOKS));
		assertEquals(0.5, m.share(Kind.FUMING_POTATO_BOOKS));
		assertEquals(1.0, m.share(Kind.DYE)); // nothing fitted: the upgrade counts at its full price
	}

	@Test
	void theShippedFileHasFittedSharesAndRecipes() {
		ItemValue.Model m = ItemValue.model();
		for (Kind k : List.of(Kind.HOT_POTATO_BOOKS, Kind.GOLDEN_HOT_POTATO_BOOKS, Kind.RECOMBOBULATOR, Kind.REFORGE, Kind.STARS)) {
			assertTrue(m.share().containsKey(k), k.name());
			assertTrue(m.share(k) >= 0 && m.share(k) <= 3, k.name());
		}
		Map<String, ItemValue.Recipe> recipes = ItemValue.recipes();
		assertFalse(recipes.isEmpty());
		recipes.values().forEach(r -> assertTrue(r.makes() >= 1 && !r.parts().isEmpty()));
	}

	@Test
	void aFurfskySkinIsTwinnedWithItsPlainName() {
		assertEquals("hyperion_skin", ItemValue.twin("furfsky_hyperion_skin"), "the fire sale names it Hyperion Skin");
		assertEquals("furfsky_scylla_skin", ItemValue.twin("scylla_skin"));
		assertNull(ItemValue.twin("hyperion"));
		assertNull(ItemValue.twin("pet_skin_wolf_doge"));
		assertNull(ItemValue.twin("furfsky_"));
	}

	@Test
	void aPartIsNotNamedForItsKindTwice() {
		assertEquals("Dye of the Pure Black", ItemValue.partName(Kind.DYE, "Dye of the Pure Black"));
		assertEquals("Black Sheep Skin", ItemValue.partName(Kind.PET_SKIN, "Black Sheep Skin"));
		assertEquals("Reforge Suspicious", ItemValue.partName(Kind.REFORGE, "Suspicious"));
		assertEquals("Recombobulator 3000", ItemValue.partName(Kind.RECOMBOBULATOR, ""));
	}
}
