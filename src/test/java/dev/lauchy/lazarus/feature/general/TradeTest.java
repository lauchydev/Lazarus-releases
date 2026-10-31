package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTest {
	/** Reads lines as chat brings them, then ends the trade as the tick does when nothing follows. */
	private static Trade read(String... lines) {
		Trade.Reader r = new Trade.Reader();
		for (String l : lines) assertNull(r.feed(l));
		return r.finish();
	}

	@Test
	void readsWhatChangedHandsFromTheServersSummary() {
		// From Lauchlan's log, 2026-10-27.
		Trade t = read("Trade successful with sStepbr0!", "  -11x Goop", "  + Coins: 1,380,000,000");
		assertEquals("sStepbr0", t.partner());
		assertEquals(Map.of("Goop", 11), t.gave());
		assertEquals(1_380_000_000L, t.coinsGot());
		assertEquals(Map.of("Goop", 125_454_545L), t.prices(name -> -1));
	}

	@Test
	void anItemWithNoCountIsOne() {
		Trade t = read("Trade successful with secrets!", "  + Seraph's Tally", "  + Seraph's Halo");
		assertEquals(Map.of("Seraph's Tally", 1, "Seraph's Halo", 1), t.got());
		assertTrue(t.prices(name -> -1).isEmpty(), "a gift says nothing of price");
	}

	@Test
	void theNextLineThatIsntPartOfItEndsTheTrade() {
		Trade.Reader r = new Trade.Reader();
		assertNull(r.feed("Trade successful with Steve!"));
		assertNull(r.feed("  + Superior Shimmer Skin"));
		assertNull(r.feed("  - Coins: 2,000,000,000"));
		Trade t = r.feed("The Endstone Protector is rising from the ground!");
		assertEquals(Map.of("Superior Shimmer Skin", 2_000_000_000L), t.prices(name -> -1));
		assertNull(r.finish());
	}

	@Test
	void theOneUnpricedItemIsWorthTheOtherSideLessTheRest() {
		Trade t = read("Trade successful with Steve!", "  + Superior Shimmer Skin", "  - Coins: 500,000,000", "  -2x Recombobulator 3000");
		Map<String, Long> known = Map.of("Recombobulator 3000", 600_000_000L);
		assertEquals(Map.of("Superior Shimmer Skin", 1_700_000_000L), t.prices(name -> known.getOrDefault(name, -1L)));
	}

	@Test
	void twoUnpricedItemsOrEverythingPricedSayNothing() {
		Trade t = read("Trade successful with Steve!", "  + Skin A", "  + Skin B", "  - Coins: 5,000,000");
		assertTrue(t.prices(name -> -1).isEmpty());
		assertTrue(t.prices(name -> 1_000_000L).isEmpty());
	}

	@Test
	void bothTradersMakeTheSameKey() {
		Trade mine = read("Trade successful with Steve!", "  + Superior Shimmer Skin", "  - Coins: 2,000,000,000");
		Trade theirs = read("Trade successful with Alex!", "  - Superior Shimmer Skin", "  + Coins: 2,000,000,000");
		assertEquals(mine.key("Alex"), theirs.key("steve"));
		Trade other = read("Trade successful with Steve!", "  + Superior Shimmer Skin", "  - Coins: 1,900,000,000");
		assertNotEquals(mine.key("Alex"), other.key("Alex"));
	}

	@Test
	void medianOfPrices() {
		assertEquals(-1, Trade.median(List.of()));
		assertEquals(5, Trade.median(List.of(9L, 5L, 1L)));
		assertEquals(2, Trade.median(List.of(2L, 8L)));
	}
}
