package dev.lauchy.lazarus.feature.general;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A finished trade between two players, as the server sums it up in chat: "Trade successful with
 * Name!", then a line per thing that changed hands, "  + Seraph's Halo" or "  -11x Goop" for an
 * item you got or gave, "  + Coins: 1,380,000,000" for coins. A trade of items for coins says
 * what someone paid for them, which is how items that never reach the auction house (skins
 * above all) get a price. Pure, so it is tested without the game.
 *
 * @param partner who the trade was with
 * @param got the items you got, by name, with how many
 * @param gave the items you gave, by name, with how many
 * @param coinsGot the coins you got
 * @param coinsGave the coins you gave
 */
public record Trade(String partner, Map<String, Integer> got, Map<String, Integer> gave, long coinsGot, long coinsGave) {
	/** "Trade successful with secrets!": a trade went through. */
	private static final Pattern DONE = Pattern.compile("^Trade successful with (\\w{1,16})!$");
	/** "  + Seraph's Halo", "  -11x Goop", "  + Coins: 1,380,000,000": one thing that changed hands. */
	private static final Pattern LINE = Pattern.compile("^\\s+([+-])\\s*(?:(\\d[\\d,]*)x\\s+)?(.+?)\\s*$");
	/** The coins on a line: "Coins: 1,380,000,000". */
	private static final Pattern COINS = Pattern.compile("^Coins: (\\d[\\d,]*)$");

	/** Reads the trade summary line by line; a trade is done at the first line that isn't part of it. */
	public static final class Reader {
		private String partner;
		private final Map<String, Integer> got = new LinkedHashMap<>(), gave = new LinkedHashMap<>();
		private long coinsGot, coinsGave;

		/** Takes one chat line; returns the trade it ended, or null. A line that starts a trade ends the one before. */
		public Trade feed(String line) {
			Matcher m = partner == null ? null : LINE.matcher(line);
			if (m != null && m.matches()) {
				boolean mine = m.group(1).equals("+");
				Matcher c = COINS.matcher(m.group(3));
				if (c.matches()) {
					long coins = number(c.group(1));
					if (mine) coinsGot += coins;
					else coinsGave += coins;
				} else {
					int count = m.group(2) == null ? 1 : (int) Math.min(Integer.MAX_VALUE, number(m.group(2)));
					(mine ? got : gave).merge(m.group(3), count, Integer::sum);
				}
				return null;
			}
			Trade done = finish();
			Matcher d = DONE.matcher(line.trim());
			if (d.matches()) partner = d.group(1);
			return done;
		}

		/** Ends the trade being read, when its lines have all come (nothing follows the last one); null when none was. */
		public Trade finish() {
			if (partner == null) return null;
			Trade t = new Trade(partner, Map.copyOf(got), Map.copyOf(gave), coinsGot, coinsGave);
			partner = null;
			got.clear();
			gave.clear();
			coinsGot = coinsGave = 0;
			return t;
		}
	}

	/**
	 * What one of each item in the trade sold for, by name, as far as the trade says. Items for
	 * coins and nothing else: one kind of item gets the coins over its count. Otherwise both
	 * sides are worth the same, so when exactly one kind of item has no {@code known} price
	 * (-1), it is worth the other side less the rest of its own. Anything else says nothing.
	 */
	public Map<String, Long> prices(ToLongFunction<String> known) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (got.isEmpty() && gave.isEmpty()) return out;
		// One kind of item one way, coins only the other: the plainest price there is.
		if (gave.size() == 1 && got.isEmpty() && coinsGave == 0 && coinsGot > 0) return each(gave, coinsGot);
		if (got.size() == 1 && gave.isEmpty() && coinsGot == 0 && coinsGave > 0) return each(got, coinsGave);
		String unknown = null;
		boolean onGot = false;
		double gotWorth = coinsGot, gaveWorth = coinsGave;
		for (int side = 0; side < 2; side++) {
			for (var e : (side == 0 ? got : gave).entrySet()) {
				long p = known.applyAsLong(e.getKey());
				if (p > 0) {
					if (side == 0) gotWorth += (double) p * e.getValue();
					else gaveWorth += (double) p * e.getValue();
				} else if (unknown != null) {
					return out; // two things unpriced: the trade can't say which is worth what
				} else {
					unknown = e.getKey();
					onGot = side == 0;
				}
			}
		}
		if (unknown == null) return out;
		int count = (onGot ? got : gave).get(unknown);
		double worth = onGot ? gaveWorth - gotWorth : gotWorth - gaveWorth;
		if (worth > 0) out.put(unknown, Math.round(worth / count));
		return out;
	}

	/**
	 * The same trade however each side saw it: who traded with whom and what went which way, so
	 * both players' mods report it once. Names are folded to lower case; {@code me} is this player.
	 */
	public String key(String me) {
		String a = me.toLowerCase(Locale.ROOT), b = partner.toLowerCase(Locale.ROOT);
		String mine = side(gave, coinsGave), theirs = side(got, coinsGot);
		String text = a.compareTo(b) <= 0 ? a + ">" + mine + "|" + b + ">" + theirs : b + ">" + theirs + "|" + a + ">" + mine;
		long h = 0xcbf29ce484222325L; // 64-bit FNV-1a: short, and the same in every mod
		for (int i = 0; i < text.length(); i++) {
			h ^= text.charAt(i);
			h *= 0x100000001b3L;
		}
		return Long.toHexString(h);
	}

	/** One side of a trade as text: its coins, then each item and count in name order. */
	private static String side(Map<String, Integer> items, long coins) {
		StringBuilder s = new StringBuilder().append(coins);
		for (var e : new TreeMap<>(items).entrySet()) s.append(',').append(e.getKey()).append('x').append(e.getValue());
		return s.toString();
	}

	/** The one kind of item in {@code items} at {@code coins} over its count. */
	private static Map<String, Long> each(Map<String, Integer> items, long coins) {
		var e = items.entrySet().iterator().next();
		return Map.of(e.getKey(), Math.round((double) coins / e.getValue()));
	}

	/** A whole number with thousands commas, saturating rather than overflowing. */
	private static long number(String digits) {
		try {
			return Long.parseLong(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	/** The middle of a list of prices (the lower middle of an even count), or -1 when there are none. */
	static long median(List<Long> prices) {
		if (prices.isEmpty()) return -1;
		long[] sorted = prices.stream().mapToLong(Long::longValue).sorted().toArray();
		return sorted[(sorted.length - 1) / 2];
	}
}
