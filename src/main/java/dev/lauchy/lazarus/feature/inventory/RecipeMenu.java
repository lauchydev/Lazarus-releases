package dev.lauchy.lazarus.feature.inventory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's recipe menu ({@code /recipes}) as the indexer reads it: a "Recipes (1/11)" page
 * of up to 28 items in the middle seven columns of four rows, filters down the left, page arrows
 * along the bottom; each item opens its recipe page, whose back arrow sits bottom left. Pure, so
 * it is tested without the game.
 */
final class RecipeMenu {
	/** "Recipes (3/11)": the page shown and how many there are. */
	private static final Pattern TITLE = Pattern.compile("^Recipes \\((\\d+)/(\\d+)\\)$");
	/** The slots that hold items with recipes: columns 2-8 of rows 2-5. */
	static final int[] ITEMS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
	/** The previous and next page arrows on the menu, and the back arrow on a recipe page. */
	static final int PREVIOUS = 48, NEXT = 53, BACK = 45;
	/** What an item in the menu says when clicking it shows its recipe. */
	private static final String VIEW = "Click to view recipe";

	private RecipeMenu() {
	}

	/** The page a window's title shows, {page, pages}, or null when it isn't the recipe menu. */
	static int[] page(String title) {
		Matcher m = TITLE.matcher(title == null ? "" : title.trim());
		return m.matches() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
	}

	/** Whether an item's lore says clicking it shows its recipe. */
	static boolean opensRecipe(List<String> lore) {
		return lore.stream().anyMatch(l -> l.contains(VIEW));
	}

	/** The arrow to click to get from page {@code at} toward {@code want}, or -1 when already there. */
	static int toward(int at, int want) {
		return at < want ? NEXT : at > want ? PREVIOUS : -1;
	}
}
