package dev.lauchy.lazarus.feature.general;

import java.util.Arrays;
import java.util.Locale;

/**
 * How {@link PriceDatabase} turns market data into one value, kept apart from the game so it
 * is tested on its own: a joke bazaar order or a coin transfer on the auction house must not
 * set what an item is worth.
 */
final class PriceMath {
	/** Fewer auction sales than this can be one coin transfer (a dirt block "sold" for 50B), so they don't set a value. */
	static final int MIN_SALES = 3;
	/** A bazaar ask more than this many times the bid is a joke order, not a price. */
	static final double MAX_SPREAD = 3;

	private PriceMath() {
	}

	/**
	 * What one quote says an item is worth: the middle of bid and ask, but the bid alone when the
	 * ask is a joke (over {@link #MAX_SPREAD} times the bid), and nothing from an ask with no bid,
	 * since anyone can list at any price. -1 when the quote says nothing.
	 */
	static double bazaarValue(double bid, double ask) {
		if (bid <= 0) return -1;
		if (ask <= 0 || ask > bid * MAX_SPREAD) return bid;
		return (bid + ask) / 2;
	}

	/**
	 * The value a window of sale prices gives: their median, then the median again of those within
	 * 3x of it either way. -1 under {@link #MIN_SALES} sales.
	 */
	static long auctionValue(long[] prices) {
		if (prices.length < MIN_SALES) return -1;
		long[] sorted = prices.clone();
		Arrays.sort(sorted);
		long first = sorted[sorted.length / 2];
		long[] kept = Arrays.stream(sorted).filter(p -> p <= first * 3 && p * 3 >= first).toArray();
		return kept.length == 0 ? first : kept[kept.length / 2];
	}

	/**
	 * Coins as someone types them: "400000000", "400,000,000", "400m", "1.5b", "250k", "2t".
	 * -1 when it isn't a number of coins.
	 */
	static long coins(String text) {
		String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
		if (t.isEmpty()) return -1;
		long scale = switch (t.charAt(t.length() - 1)) {
			case 'k' -> 1_000L;
			case 'm' -> 1_000_000L;
			case 'b' -> 1_000_000_000L;
			case 't' -> 1_000_000_000_000L;
			default -> 1L;
		};
		if (scale > 1) t = t.substring(0, t.length() - 1);
		if (!t.matches("\\d+(\\.\\d+)?|\\.\\d+")) return -1;
		double value = Double.parseDouble(t) * scale;
		return value >= Long.MAX_VALUE ? -1 : Math.round(value);
	}
}
