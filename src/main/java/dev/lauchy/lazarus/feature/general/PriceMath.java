package dev.lauchy.lazarus.feature.general;

import java.util.Arrays;
import java.util.List;
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

	/** A sale's say in the value halves every this long: a week-old sale counts an eighth of today's. */
	static final long HALF_LIFE_MS = 2 * 86_400_000L;

	/**
	 * The value a window of sale prices gives, every sale counting the same: their median, then the
	 * median again of those within 3x of it either way. -1 under {@link #MIN_SALES} sales.
	 */
	static long auctionValue(long[] prices) {
		return auctionValue(prices, new long[prices.length]);
	}

	/**
	 * The value a window of sale prices gives when the market moves: each sale's say halves every
	 * {@link #HALF_LIFE_MS} ({@code ages} in milliseconds, one per price), so a price that doubled
	 * this week reads as doubled, not as last week's median; the trim is then around that figure,
	 * so this week's sales aren't thrown out as outliers against last week's. A lone fresh sale
	 * still can't set it alone: the older sales together outweigh it until more follow.
	 * -1 under {@link #MIN_SALES} sales.
	 */
	static long auctionValue(long[] prices, long[] ages) {
		if (prices.length < MIN_SALES) return -1;
		Integer[] order = new Integer[prices.length];
		for (int i = 0; i < order.length; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> Long.compare(prices[a], prices[b]));
		double[] weights = new double[prices.length];
		for (int i = 0; i < prices.length; i++) weights[i] = Math.pow(0.5, Math.max(0, ages[i]) / (double) HALF_LIFE_MS);
		long first = weightedMedian(prices, weights, order, p -> true);
		long kept = weightedMedian(prices, weights, order, p -> p <= first * 3 && p * 3 >= first);
		return kept < 0 ? first : kept;
	}

	/** The price at which half the kept sales' weight has been passed, lowest first; -1 when none is kept. */
	private static long weightedMedian(long[] prices, double[] weights, Integer[] order, java.util.function.LongPredicate keep) {
		double total = 0;
		for (int i : order) if (keep.test(prices[i])) total += weights[i];
		if (total <= 0) return -1;
		double run = 0;
		for (int i : order) {
			if (!keep.test(prices[i])) continue;
			run += weights[i];
			if (run > total / 2) return prices[i];
		}
		return prices[order[order.length - 1]];
	}

	/** How much a piece of evidence {@code age} ms old counts: 1 now, a half after {@link #HALF_LIFE_MS}. */
	static double weight(long age) {
		return Math.pow(0.5, Math.max(0, age) / (double) HALF_LIFE_MS);
	}

	/** The value at which half the weight has been passed, lowest first, over values above 0; -1 with nothing weighed. */
	static double weightedMedian(double[] values, double[] weights) {
		Integer[] order = java.util.stream.IntStream.range(0, values.length).filter(i -> values[i] > 0 && weights[i] > 0).boxed()
				.sorted((a, b) -> Double.compare(values[a], values[b])).toArray(Integer[]::new);
		double total = 0;
		for (int i : order) total += weights[i];
		if (total <= 0) return -1;
		double run = 0;
		for (int i : order) {
			run += weights[i];
			if (run > total / 2) return values[i];
		}
		return values[order[order.length - 1]];
	}

	/** Two figures this many times apart, or nearer, agree. */
	static final double AGREE = 2;
	/** A shared figure this many times above or below what's known is held back until something agrees. */
	static final double OUTLIER = 10;

	/** Whether two figures agree: within {@link #AGREE} of each other. */
	static boolean agree(double a, double b) {
		return a > 0 && b > 0 && a <= b * AGREE && b <= a * AGREE;
	}

	/** One price level of the bazaar's buy orders: what each pays and how many it takes. */
	record Order(double price, long amount) {
	}

	/**
	 * What {@code quantity} of a bazaar item fetch sold now: each into the dearest buy order left
	 * (the order book, {@code buys}, in any order), and any past what those take at the higher of
	 * what an NPC pays and the lowest order that filled. 0 when nothing would buy them.
	 */
	static long bazaarSellNow(List<Order> buys, long quantity, long npc) {
		List<Order> sorted = buys.stream().filter(o -> o.price > 0 && o.amount > 0).sorted((a, b) -> Double.compare(b.price, a.price)).toList();
		double proceeds = 0, lowest = -1;
		long left = quantity;
		for (Order o : sorted) {
			if (left <= 0) break;
			long take = Math.min(left, o.amount);
			proceeds += take * o.price;
			lowest = o.price;
			left -= take;
		}
		double past = Math.max(npc, lowest);
		if (left > 0 && past > 0) proceeds += left * past;
		return Math.round(proceeds);
	}

	/** The lower quartile of sale prices, the newest counting most ({@code ages} in ms); -1 under {@link #MIN_SALES}. */
	static long lowerQuartile(long[] prices, long[] ages) {
		if (prices.length < MIN_SALES) return -1;
		Integer[] order = new Integer[prices.length];
		for (int i = 0; i < order.length; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> Long.compare(prices[a], prices[b]));
		double total = 0;
		for (int i = 0; i < prices.length; i++) total += weight(ages[i]);
		double run = 0;
		for (int i : order) {
			run += weight(ages[i]);
			if (run >= total / 4) return prices[i];
		}
		return prices[order[order.length - 1]];
	}

	/** A Buy It Now this far under the cheapest other one sells first. */
	static final double UNDERCUT = 0.99;

	/**
	 * What one item fetches put up for a quick sale on the auction house: the lower of the sales'
	 * lower quartile ({@link #lowerQuartile}) and just under the cheapest other Buy It Now ({@code
	 * listed}, -1 for none); with neither, {@code floor} (what an NPC pays, or its recipe's parts).
	 */
	static long auctionSellNow(long quartile, long listed, long floor) {
		long under = listed > 0 ? Math.round(listed * UNDERCUT) : -1;
		long best = quartile > 0 && under > 0 ? Math.min(quartile, under) : Math.max(quartile, under);
		return best > 0 ? best : floor;
	}

	/** An item worth more than this is counted at its Estimated value by the profit trackers; below it, at Sell now. */
	static final long TRACK_ESTIMATED_OVER = 100_000_000L;

	/**
	 * What a profit tracker counts one item at: its Estimated value when that's over {@link
	 * #TRACK_ESTIMATED_OVER} (a rare drop is kept, not dumped), else what it fetches sold now.
	 */
	static long trackerValue(long estimated, long sellNow) {
		if (estimated > TRACK_ESTIMATED_OVER || sellNow <= 0) return estimated;
		return sellNow;
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
