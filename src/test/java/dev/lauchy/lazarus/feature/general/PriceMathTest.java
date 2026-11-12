package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	private static final long DAY = 86_400_000L;

	@Test
	void aPriceThatMovedThisWeekReadsAsMovedNotAsLastWeeksMedian() {
		// Six sales at 100 a week ago, four at 250 over the last day: last week's median would say 100.
		long[] prices = {100, 100, 100, 100, 100, 100, 250, 240, 260, 250};
		long[] ages = {7 * DAY, 7 * DAY, 7 * DAY, 7 * DAY, 7 * DAY, 7 * DAY, DAY, DAY / 2, DAY / 4, 0};
		assertEquals(250, PriceMath.auctionValue(prices, ages));
	}

	@Test
	void oneFreshSaleCannotSetThePriceAlone() {
		long[] prices = {100, 100, 100, 100, 100, 400};
		long[] ages = {2 * DAY, 2 * DAY, 2 * DAY, DAY, DAY, 0};
		assertEquals(100, PriceMath.auctionValue(prices, ages));
	}

	@Test
	void salesWithNoAgesCountTheSame() {
		assertEquals(PriceMath.auctionValue(new long[]{100, 120, 110, 105}), PriceMath.auctionValue(new long[]{100, 120, 110, 105}, new long[4]));
	}

	@Test
	void aBazaarStackSoldNowFillsTheDearestOrdersFirst() {
		List<PriceMath.Order> buys = List.of(new PriceMath.Order(80, 5), new PriceMath.Order(100, 10));
		assertEquals(10 * 100 + 2 * 80, PriceMath.bazaarSellNow(buys, 12, 0));
	}

	@Test
	void unitsPastTheBuyOrdersGoAtTheNpcPriceOrTheLowestOrderThatFilled() {
		List<PriceMath.Order> buys = List.of(new PriceMath.Order(100, 10), new PriceMath.Order(2, 5));
		assertEquals(10 * 100 + 5 * 2 + 5 * 40, PriceMath.bazaarSellNow(buys, 20, 40), "the NPC pays more than the 2-coin order");
		assertEquals(10 * 100 + 5 * 100, PriceMath.bazaarSellNow(List.of(new PriceMath.Order(100, 10)), 15, 40), "the last order that filled pays more");
		assertEquals(0, PriceMath.bazaarSellNow(List.of(), 3, 0), "nothing buys them");
	}

	@Test
	void anAuctionItemSoldNowGoesForTheLowerOfTheQuartileAndJustUnderTheCheapestListing() {
		assertEquals(990, PriceMath.auctionSellNow(1200, 1000, 0));
		assertEquals(800, PriceMath.auctionSellNow(800, 1000, 0));
		assertEquals(800, PriceMath.auctionSellNow(800, -1, 0));
		assertEquals(50, PriceMath.auctionSellNow(-1, -1, 50), "with neither, the NPC or recipe floor");
	}

	@Test
	void theLowerQuartileFavoursTheNewestSales() {
		assertEquals(100, PriceMath.lowerQuartile(new long[]{100, 200, 300, 400}, new long[4]));
		assertEquals(-1, PriceMath.lowerQuartile(new long[]{100, 200}, new long[2]), "too few sales");
		long week = 7 * DAY;
		assertEquals(300, PriceMath.lowerQuartile(new long[]{100, 200, 300, 400}, new long[]{week, week, 0, 0}), "last week's cheap sales count an eighth");
	}

	@Test
	void trackersCountRareDropsAtTheirEstimateAndTheRestAtSellNow() {
		assertEquals(500_000_000L, PriceMath.trackerValue(500_000_000L, 300_000_000L));
		assertEquals(40_000_000L, PriceMath.trackerValue(50_000_000L, 40_000_000L));
		assertEquals(50_000_000L, PriceMath.trackerValue(50_000_000L, -1), "nothing would buy it now: the estimate stands");
	}

	@Test
	void figuresTenTimesApartAreOutliersAndTwiceApartAgree() {
		assertTrue(PriceMath.agree(150, 100));
		assertFalse(PriceMath.agree(250, 100));
	}
}
