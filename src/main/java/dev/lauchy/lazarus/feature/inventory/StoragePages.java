package dev.lauchy.lazarus.feature.inventory;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How Dragsim's storage windows are read and how the storage overview lays them out, kept free of the
 * game so it can be checked on its own. Page 0 is the ender chest, which the server never numbers.
 */
public final class StoragePages {
	/** The ender chest's page number in the cache and the overview. */
	public static final int ENDER = 0;
	/** The most pages a Storage menu can hold, so a bad title never makes thousands of cards. */
	public static final int MAX_PAGES = 54;
	/** Columns in every storage window. */
	public static final int COLS = 9;
	/** One slot cell, as in a real inventory. */
	public static final int CELL = 18;
	/** A card: the page's name over its 9-wide grid. */
	public static final int CARD_W = COLS * CELL + 2, CARD_HEAD = 14;
	/** Room between cards, and the header and footer bands above and below them. */
	public static final int GAP = 10, TOP = 48, BOTTOM = 28;

	private static final Pattern PAGE_WINDOW = Pattern.compile("^Storage Page \\((\\d+)\\s*/\\s*(\\d+)\\)$");
	private static final Pattern PAGE_BUTTON = Pattern.compile("^Storage Page (\\d+)$");
	private static final Pattern LOCKED_LORE = Pattern.compile("\\b(?:locked|unlock|buy|purchase|cost|requires?)\\b");
	private static final Pattern CHROME_NAME = Pattern.compile("^(?:go back|close|next page|previous page|back)$");

	private StoragePages() {
	}

	/** The page a window shows: its number for "Storage Page (3/8)", {@link #ENDER} for the ender chest, else -1. */
	public static int pageOfWindow(String title) {
		Matcher m = PAGE_WINDOW.matcher(title);
		if (m.matches()) return bounded(m.group(1));
		return title.equals("Ender Chest") ? ENDER : -1;
	}

	/** How many pages a page window's title says there are ("Storage Page (3/8)" is 8), else -1. */
	public static int countOfWindow(String title) {
		Matcher m = PAGE_WINDOW.matcher(title);
		return m.matches() ? bounded(m.group(2)) : -1;
	}

	/** Whether this window is the Storage menu itself, the one listing every page. */
	public static boolean isRoot(String title) {
		return title.equals("Storage");
	}

	/** The page a button in the Storage menu opens ("Storage Page 4"), else -1. */
	public static int pageOfButton(String name) {
		Matcher m = PAGE_BUTTON.matcher(name);
		return m.matches() ? bounded(m.group(1)) : -1;
	}

	/** Whether a page's button says it can't be opened yet: a red pane or barrier, or lore about unlocking it. */
	public static boolean locked(String itemId, List<String> lore) {
		if (itemId.equals("minecraft:red_stained_glass_pane") || itemId.equals("minecraft:barrier")) return true;
		for (String line : lore) if (LOCKED_LORE.matcher(line.toLowerCase(Locale.ROOT)).find()) return true;
		return false;
	}

	/**
	 * Whether a window's top row is the server's buttons rather than storage. Page windows always carry one;
	 * the ender chest does when that row has a navigation button or is all filler glass.
	 * @param ids the top row's item ids, empty slots as ""
	 * @param names the top row's item names
	 */
	public static boolean chromeRow(int page, List<String> ids, List<String> names) {
		if (page != ENDER) return true;
		boolean allGlass = !ids.isEmpty();
		for (int i = 0; i < ids.size(); i++) {
			if (CHROME_NAME.matcher(names.get(i).toLowerCase(Locale.ROOT)).matches()) return true;
			allGlass &= ids.get(i).endsWith("_stained_glass_pane");
		}
		return allGlass;
	}

	/** A card's height for a page of {@code rows} rows. */
	public static int cardHeight(int rows) {
		return CARD_HEAD + rows * CELL + 1;
	}

	/** The grid of cards on a screen this size, at most three by three: page cards on the right, your inventory in the column left of them. */
	public static Layout layout(int width, int height, int cards, int rows) {
		int step = CARD_W + GAP;
		int cols = Math.max(1, Math.min(3, (width - 24 - step + GAP) / step));
		int cardH = cardHeight(rows);
		int visible = Math.clamp((height - TOP - BOTTOM + GAP) / (cardH + GAP), 1, 3); // at most a 3x3 grid; the rest scroll
		int total = Math.max(1, (cards + cols - 1) / cols);
		int left = (width - (cols + 1) * step + GAP) / 2;
		return new Layout(cols, visible, total, Math.max(12, left), cardH);
	}

	/**
	 * Where the cards go.
	 * @param left the inventory column's left edge; page cards start one column to its right
	 */
	public record Layout(int cols, int visibleRows, int totalRows, int left, int cardH) {
		/** The furthest the cards can scroll, in rows. */
		public int maxScroll() {
			return Math.max(0, totalRows - visibleRows);
		}

		/** The left edge of page column {@code col}. */
		public int x(int col) {
			return left + (col + 1) * (CARD_W + GAP);
		}

		/** The top edge of card row {@code row} when the view starts at {@code firstRow}. */
		public int y(int row, int firstRow) {
			return TOP + (row - firstRow) * (cardH + GAP);
		}

		/** The card at index {@code i} is on screen. */
		public boolean visible(int i, int firstRow) {
			int row = i / cols;
			return row >= firstRow && row < firstRow + visibleRows;
		}

		/** The scroll that keeps card {@code i} on screen, moving as little as possible from {@code firstRow}. */
		public int reveal(int i, int firstRow) {
			int row = i / cols;
			if (row < firstRow) return row;
			if (row >= firstRow + visibleRows) return Math.min(maxScroll(), row - visibleRows + 1);
			return Math.min(firstRow, maxScroll());
		}
	}

	/** The overview's name for a page: "Ender Chest" or "PV 3". */
	public static String label(int page) {
		return page == ENDER ? "Ender Chest" : "PV " + page;
	}

	/** How long ago a page was last seen, rounded the way a person would say it. */
	public static String age(long millis) {
		long s = Math.max(0, millis / 1000);
		if (s < 60) return "just now";
		if (s < 3600) return s / 60 + "m ago";
		if (s < 86400) return s / 3600 + "h ago";
		return s / 86400 + "d ago";
	}

	private static int bounded(String digits) {
		try {
			int n = Integer.parseInt(digits);
			return n >= 1 && n <= MAX_PAGES ? n : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
