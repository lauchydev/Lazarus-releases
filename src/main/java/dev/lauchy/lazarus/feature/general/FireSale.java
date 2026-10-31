package dev.lauchy.lazarus.feature.general;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's fire sale menus as the indexer reads them: {@code /firesale} opens "Fire Sale -
 * Limited Time", whose clock ("Fire Sale History") opens every past sale, up to 28 items in the
 * middle seven columns of four rows with page arrows along the bottom. Each item's lore says
 * what it cost in gems, how many there were and who bought them; a gem is worth
 * {@link #coinsPerGem()} coins (the owner sets it with {@code /lazarus price gems}; 2.3B per 1,000 to start). Pure, so it is
 * tested without the game.
 */
final class FireSale {
	/** What a gem is worth in coins until the owner's rate arrives: 1,000 gems = 2.3B. */
	static final long COINS_PER_GEM = 2_300_000L;
	/** The rate prices saved before 1.29.0 were worked out at (1,000 gems = 3B), to turn them back into gems. */
	static final long OLD_COINS_PER_GEM = 3_000_000L;
	/** The slots that hold past sales: columns 2-8 of rows 2-5. */
	static final int[] ITEMS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
	/** The previous and next page arrows along the history's bottom row. */
	static final int PREVIOUS = 48, NEXT = 53;
	/** The clock on the fire sale menu that opens the history, by its name. */
	static final String HISTORY = "Fire Sale History";
	/** "1,500 Gems", "Price: 2,000 gems": the gems something cost. */
	private static final Pattern GEMS = Pattern.compile("(\\d[\\d,]*)\\s*gems?\\b", Pattern.CASE_INSENSITIVE);
	/** "Quantity: 50", "Stock: 50", "50x sold": how many there were. */
	private static final Pattern QUANTITY = Pattern.compile("(?:quantity|stock|amount|sold)\\D{0,4}(\\d[\\d,]*)|(\\d[\\d,]*)\\s*x?\\s*(?:sold|available)", Pattern.CASE_INSENSITIVE);

	/** "Buyers: Steve, Alex", "Buyers: 0", "Bought by: None": who bought one, as names or a count. */
	private static final Pattern BUYERS = Pattern.compile("(?i)\\b(?:buyers?|bought by|purchased by)\\b\\s*:?\\s*(.*)");
	/** "Sold: 0/1": how many of the stock were bought. */
	private static final Pattern SOLD_OF = Pattern.compile("(?i)\\bsold\\s*:?\\s*(\\d[\\d,]*)\\s*/\\s*\\d");

	/** What a gem is worth in coins now: the owner's shared rate once it's read. */
	private static volatile long coinsPerGem = COINS_PER_GEM;

	private FireSale() {
	}

	/**
	 * One past sale: what it cost in gems, how many there were and how many people bought one
	 * (each -1 when the lore doesn't say). A sale nobody bought from put none in circulation, so its
	 * price says nothing about what one is worth.
	 */
	record Listing(long gems, int quantity, int buyers) {
		Listing(long gems, int quantity) {
			this(gems, quantity, -1);
		}

		/** Whether its gem price counts as a value: anyone bought one, or the lore doesn't say. */
		boolean bought() {
			return buyers != 0;
		}

		/** What one cost in coins, by the rule of thumb. */
		long coins() {
			return FireSale.coins(gems);
		}
	}

	/** Whether a window's title is the fire sale menu. */
	static boolean isSale(String title) {
		return title != null && title.trim().toLowerCase(Locale.ROOT).startsWith("fire sale") && !isHistory(title);
	}

	/** Whether a window's title is the fire sale history. */
	static boolean isHistory(String title) {
		return title != null && title.trim().equalsIgnoreCase(HISTORY);
	}

	/** Whether a page arrow's name says it turns the page ("Next Page", "Previous Page"); a blank slot or filler doesn't. */
	static boolean turns(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		return n.contains("next") || n.contains("previous") || n.contains("page");
	}

	/** The sale an item's lore describes, or null when no line gives a real price in gems (more than one). */
	static Listing read(List<String> lore) {
		long gems = -1;
		int quantity = -1, buyers = -1;
		for (String line : lore) {
			String plain = line.replaceAll("§.", "").trim();
			Matcher b = BUYERS.matcher(plain);
			if (buyers < 0 && b.find()) buyers = buyers(b.group(1).trim());
			Matcher of = SOLD_OF.matcher(plain);
			if (buyers < 0 && of.find()) buyers = (int) Math.min(Integer.MAX_VALUE, number(of.group(1)));
			Matcher g = GEMS.matcher(line);
			if (gems < 0 && g.find()) gems = number(g.group(1));
			Matcher q = QUANTITY.matcher(line);
			if (quantity < 0 && q.find()) quantity = (int) Math.min(Integer.MAX_VALUE, number(q.group(1) != null ? q.group(1) : q.group(2)));
		}
		return priced(gems) ? new Listing(gems, quantity, buyers) : null;
	}

	/**
	 * Whether a price in gems is a real one: the history shows some items at 1 gem (a Terminator, a
	 * Dragon Claw), which is a placeholder or a misread, not what one is worth.
	 */
	static boolean priced(long gems) {
		return gems > 1;
	}

	/** What a price in gems is worth in coins, at the owner's rate. */
	static long coins(long gems) {
		return gems * coinsPerGem;
	}

	/** What a gem is worth in coins now. */
	static long coinsPerGem() {
		return coinsPerGem;
	}

	/** Sets what a gem is worth in coins, as the owner shared it; nothing at or below 0. */
	static void coinsPerGem(long coins) {
		if (coins > 0) coinsPerGem = coins;
	}

	/** How many bought, from what follows "Buyers:": a count, nobody ("None", "-", nothing), or a list of names. */
	private static int buyers(String rest) {
		Matcher n = Pattern.compile("^(\\d[\\d,]*)\\b").matcher(rest);
		if (n.find()) return (int) Math.min(Integer.MAX_VALUE, number(n.group(1)));
		if (rest.isEmpty() || rest.matches("(?i)none|nobody|no one|-+")) return 0;
		return rest.split("\\s*,\\s*").length;
	}

	/** A number with thousands commas; 0 when it overflows. */
	private static long number(String digits) {
		try {
			return Long.parseLong(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
