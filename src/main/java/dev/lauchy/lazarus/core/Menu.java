package dev.lauchy.lazarus.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What the menu lists: which categories are in the sidebar and which features each one shows.
 * Kept free of the game so it can be checked on its own.
 */
public final class Menu {
	private Menu() {
	}

	/**
	 * The sidebar's categories in order: the normal ones, then Lazarus+ and Capes, which everyone sees, then
	 * Staff, which only Dragsim staff see.
	 */
	public static List<Category> sidebar(boolean member, boolean staff) {
		return Arrays.stream(Category.values()).filter(c -> staff || !c.staffSection()).toList();
	}

	/** Whether the sidebar leaves a gap above this category: where the Lazarus+ section starts, and where Staff does. */
	public static boolean gapBefore(Category category) {
		return category == Category.PLUS || category.staffSection();
	}

	/**
	 * The features a category shows, the same for members and everyone else (a non-member sees Lazarus+ features
	 * locked). Each Lazarus+ feature shows in one place only: Capes in the Capes category, every other one under
	 * Lazarus+. Normal categories list their own features and never a Lazarus+ one.
	 */
	public static <F> List<F> listed(Collection<F> all, Category category, boolean member, Function<F, Category> home, Predicate<F> plus) {
		if (category == Category.PLUS) return all.stream().filter(f -> plus.test(f) && !home.apply(f).plusSection()).toList();
		if (category.plusSection()) return all.stream().filter(f -> home.apply(f) == category).toList();
		return all.stream().filter(f -> home.apply(f) == category && !plus.test(f)).toList();
	}

	/**
	 * The features matching a search, each once, in menu order. Lazarus+ features only turn up for members, and
	 * Staff ones only for staff.
	 */
	public static <F> List<F> search(Collection<F> all, boolean member, boolean staff, Function<F, Category> home, Predicate<F> plus, Predicate<F> matches) {
		return all.stream().filter(f -> member || !plus.test(f)).filter(f -> staff || !home.apply(f).staffSection()).filter(matches).distinct().toList();
	}
}
