package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketHistoryTest {
	private static final long NOW = 1_793_175_553_534L;

	private static MarketHistory read(String json) {
		return MarketHistory.read(JsonParser.parseString(json).getAsJsonObject(), NOW);
	}

	@Test
	void anAuctionItemIsTheMedianOfItsSalesWithAOneCoinTransferIgnored() {
		MarketHistory h = read("{\"sales\":[{\"unit_price\":175000000},{\"unit_price\":180000000},{\"unit_price\":1250000},{\"price\":340000000,\"item\":{\"count\":2}}],"
				+ "\"quotes\":[],\"product\":null}");
		assertEquals(175_000_000, h.auction());
		assertEquals(4, h.sales());
		assertEquals(-1, h.bazaar());
	}

	@Test
	void oneSaleSetsNoValue() {
		assertEquals(-1, read("{\"sales\":[{\"unit_price\":11000000000}]}").auction());
	}

	@Test
	void aBazaarItemIsTheMedianOfTheLastDaysQuotes() {
		MarketHistory h = read("{\"sales\":[],\"quotes\":["
				+ "{\"ts\":" + (NOW - 2 * 86_400_000L) + ",\"bid\":1,\"ask\":2},"
				+ "{\"ts\":" + (NOW - 60_000) + ",\"bid\":46000000,\"ask\":60000000},"
				+ "{\"ts\":" + (NOW - 30_000) + ",\"bid\":48000000,\"ask\":60000000},"
				+ "{\"ts\":" + NOW + ",\"bid\":null,\"ask\":null}]}");
		assertEquals(54_000_000, h.bazaar(), 1);
	}

	@Test
	void withNoRecentQuotesTheCurrentOrdersAreUsedAndAJokeAskWithNoBidIsNothing() {
		assertEquals(53_000_000, read("{\"quotes\":[],\"product\":{\"best_bid\":46000000,\"best_ask\":60000000}}").bazaar(), 1);
		assertEquals(-1, read("{\"quotes\":[],\"product\":{\"best_bid\":null,\"best_ask\":1000000000000}}").bazaar());
	}

	/** Reads a sale as clean by taking 100M off for a recomb. */
	private static final MarketHistory.Cleaner RECOMB = (price, item) -> item.has("item_modifiers") ? price - 100_000_000 : price;

	@Test
	void salesAreReadCleanBeforeTheirMedian() {
		MarketHistory h = MarketHistory.read(JsonParser.parseString("{\"sales\":["
				+ "{\"unit_price\":300000000,\"item\":{\"item_modifiers\":{\"recombobulated\":true}}},"
				+ "{\"unit_price\":200000000,\"item\":{}},"
				+ "{\"unit_price\":210000000,\"item\":{}}]}").getAsJsonObject(), NOW, RECOMB);
		assertEquals(210_000_000, h.auction());
		assertEquals(200_000_000, h.clean());
		assertEquals(200_000_000, h.cleanValue());
	}

	@Test
	void oneOrTwoSalesAreKeptApartAsAThinValue() {
		MarketHistory h = read("{\"sales\":[{\"unit_price\":100000000},{\"unit_price\":140000000}]}");
		assertEquals(-1, h.clean());
		assertEquals(120_000_000, h.thin());
		assertEquals(120_000_000, h.cleanValue());
	}

	@Test
	void withNoSalesTheCheapestBuyItNowListingIsUsed() {
		MarketHistory h = read("{\"sales\":[],\"auctions\":["
				+ "{\"unit_price\":900000000,\"bin\":true,\"state\":\"active\"},"
				+ "{\"unit_price\":500000000,\"bin\":false,\"state\":\"active\"},"
				+ "{\"unit_price\":700000000,\"bin\":true,\"state\":\"active\"}]}");
		assertEquals(700_000_000, h.listed());
		assertEquals(700_000_000, h.cleanValue());
	}

	@Test
	void anUpgradedListingCapsTheCleanPriceAsAsked() {
		JsonObject o = JsonParser.parseString("{\"sales\":[],\"auctions\":["
				+ "{\"unit_price\":90000000,\"bin\":true,\"state\":\"active\"},"
				+ "{\"unit_price\":100000000,\"bin\":true,\"state\":\"active\"}]}").getAsJsonObject();
		MarketHistory h = MarketHistory.read(o, 0, (price, item) -> price - 81_000_000);
		assertEquals(90_000_000, h.listed(), "a 90M helmet with books is not a 9M clean one");
	}
}
