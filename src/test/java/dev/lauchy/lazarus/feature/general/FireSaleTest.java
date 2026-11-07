package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireSaleTest {
	@Test
	void aThousandGemsAreTwoPointThreeBillion() {
		assertEquals(2_300_000_000L, new FireSale.Listing(1000, 1).coins());
		assertEquals(2_300_000_000L, FireSale.coins(1000));
	}

	@Test
	void followsTheOwnersRate() {
		try {
			FireSale.coinsPerGem(2_500_000L);
			assertEquals(2_500_000_000L, FireSale.coins(1000));
			FireSale.coinsPerGem(0);
			assertEquals(2_500_000_000L, FireSale.coins(1000), "a rate of 0 is ignored");
		} finally {
			FireSale.coinsPerGem(FireSale.COINS_PER_GEM);
		}
	}

	@Test
	void readsGemsAndQuantityFromTheLore() {
		FireSale.Listing l = FireSale.read(List.of("§7Price: §a1,500 Gems", "§7Quantity: §e50", "", "§7Buyers: Steve, Alex"));
		assertEquals(1500, l.gems());
		assertEquals(50, l.quantity());
		assertEquals(3_450_000_000L, l.coins());
	}

	@Test
	void countsTheBuyers() {
		assertEquals(2, FireSale.read(List.of("§a1,500 Gems", "§7Buyers: Steve, Alex")).buyers());
		assertEquals(12, FireSale.read(List.of("§a1,500 Gems", "§7Buyers: §e12")).buyers());
		assertEquals(0, FireSale.read(List.of("§a100,000 Gems", "§7Buyers: §cNone")).buyers());
		assertEquals(0, FireSale.read(List.of("§a100,000 Gems", "§7Sold: 0/1")).buyers());
		assertEquals(-1, FireSale.read(List.of("§a800 gems")).buyers());
	}

	@Test
	void aSaleNobodyBoughtFromIsWorthNothing() {
		assertFalse(new FireSale.Listing(100_000, 1, 0).bought());
		assertTrue(new FireSale.Listing(2675, 22, 5).bought());
		assertTrue(new FireSale.Listing(2675, 22).bought(), "a sale whose lore doesn't say still counts");
	}

	@Test
	void aOneGemPriceIsNotAPrice() {
		assertNull(FireSale.read(List.of("§a1 Gem", "§7Quantity: 1000")), "the Terminator showed at 1 gem");
		assertFalse(FireSale.priced(1));
		assertTrue(FireSale.priced(2));
	}

	@Test
	void gemsOnTheirOwnAreEnough() {
		FireSale.Listing l = FireSale.read(List.of("§a800 gems", "Sold out!"));
		assertEquals(800, l.gems());
		assertEquals(-1, l.quantity());
	}

	@Test
	void loreWithoutAGemPriceSaysNothing() {
		assertNull(FireSale.read(List.of("§7A rare skin.", "§7Quantity: 50")));
		assertNull(FireSale.read(List.of()));
	}

	@Test
	void knowsTheMenusByTitle() {
		assertTrue(FireSale.isSale("Fire Sale - Limited Time"));
		assertFalse(FireSale.isHistory("Fire Sale - Limited Time"));
		assertTrue(FireSale.isHistory("Fire Sale History"));
		assertFalse(FireSale.isSale("Fire Sale History"));
		assertFalse(FireSale.isSale("Recipes (1/11)"));
	}

	@Test
	void onlyAnArrowTurnsThePage() {
		assertTrue(FireSale.turns("Next Page"));
		assertTrue(FireSale.turns("§ePrevious Page"));
		assertFalse(FireSale.turns(""));
		assertFalse(FireSale.turns("Close"));
	}
}
