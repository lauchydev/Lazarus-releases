package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceMathTest {
	@Test
	void aBazaarItemIsWorthTheMiddleOfItsBidAndAsk() {
		assertEquals(150, PriceMath.bazaarValue(100, 200));
	}

	@Test
	void aJokeAskOrAnAskWithNoBidDoesNotSetTheValue() {
		assertEquals(100, PriceMath.bazaarValue(100, 50_000_000_000.0));
		assertEquals(100, PriceMath.bazaarValue(100, 0));
		assertEquals(-1, PriceMath.bazaarValue(0, 50_000_000_000.0));
	}

	@Test
	void oneOrTwoSalesCanBeACoinTransferSoTheyGiveNoValue() {
		assertEquals(-1, PriceMath.auctionValue(new long[]{50_000_000_000L}));
		assertEquals(-1, PriceMath.auctionValue(new long[]{50_000_000_000L, 10}));
	}

	@Test
	void anOutlierSaleDoesNotMoveTheMedian() {
		assertEquals(110, PriceMath.auctionValue(new long[]{100, 120, 110, 90_000_000_000L, 105}));
	}

	@Test
	void coinsReadAsTyped() {
		assertEquals(400_000_000, PriceMath.coins("400000000"));
		assertEquals(400_000_000, PriceMath.coins("400,000,000"));
		assertEquals(400_000_000, PriceMath.coins("400M"));
		assertEquals(1_500_000_000, PriceMath.coins("1.5b"));
		assertEquals(250_000, PriceMath.coins("250k"));
		assertEquals(0, PriceMath.coins("0"));
		assertEquals(-1, PriceMath.coins("lots"));
		assertEquals(-1, PriceMath.coins("-5m"));
		assertEquals(-1, PriceMath.coins(""));
	}
}
