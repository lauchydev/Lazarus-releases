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
		return sidebar(member, staff, false);
	}

	/** {@link #sidebar(boolean, boolean)}, the Staff section also listed for the mod's owner, whose own features sit there. */
	public static List<Category> sidebar(boolean member, boolean staff, boolean owner) {
		return Arrays.stream(Category.values()).filter(c -> staff || owner || !c.staffSection()).toList();
	}

	/**
	 * Whether someone sees a feature at all: the owner's own features only the owner, other Staff features only
	 * staff, the rest everyone.
	 */
	private static <F> boolean seen(F f, boolean staff, boolean owner, Function<F, Category> home, Predicate<F> ownerOnly) {
		if (ownerOnly.test(f)) return owner;
		return staff || !home.apply(f).staffSection();
	}

	/** Whether the sidebar leaves a gap above this category: where the Lazarus+ section starts, and where Staff does. */
	public static boolean gapBefore(Category category) {
		return category == Category.PLUS || category.staffSection();
	}

	/**
	 * The features a category shows. Each Lazarus+ feature shows in one place only: Capes in the Capes category
	 * (where a non-member sees them locked), every other one under Lazarus+, which lists them for members alone:
	 * for anyone else that page is the Lazarus+ card on its own, a landing page. A feature whose home is Lazarus+
	 * itself (one everyone runs but only members set up, like Join Messages) lists there too. Normal categories
	 * list their own features and never a Lazarus+ one.
	 */
	public static <F> List<F> listed(Collection<F> all, Category category, boolean member, Function<F, Category> home, Predicate<F> plus) {
		return listed(all, category, member, true, false, home, plus, f -> false);
	}

	/**
	 * {@link #listed(Collection, Category, boolean, Function, Predicate)}, leaving out what this player doesn't see:
	 * the owner's own features ({@code ownerOnly}) for anyone but the owner, other Staff features for anyone not staff.
	 */
	public static <F> List<F> listed(Collection<F> all, Category category, boolean member, boolean staff, boolean owner, Function<F, Category> home,
			Predicate<F> plus, Predicate<F> ownerOnly) {
		Predicate<F> seen = f -> seen(f, staff, owner, home, ownerOnly);
		if (category == Category.PLUS) {
			if (!member) return List.of();
			return all.stream().filter(f -> home.apply(f) == Category.PLUS || plus.test(f) && !home.apply(f).plusSection()).filter(seen).toList();
		}
		if (category.plusSection()) return all.stream().filter(f -> home.apply(f) == category).filter(seen).toList();
		return all.stream().filter(f -> home.apply(f) == category && !plus.test(f)).filter(seen).toList();
	}

	/**
	 * The features matching a search, each once, in menu order. Lazarus+ features only turn up for members, and
	 * Staff ones only for staff.
	 */
	public static <F> List<F> search(Collection<F> all, boolean member, boolean staff, Function<F, Category> home, Predicate<F> plus, Predicate<F> matches) {
		return search(all, member, staff, false, home, plus, f -> false, matches);
	}

	/** {@link #search(Collection, boolean, boolean, Function, Predicate, Predicate)}, the owner's own features turning up for the owner alone. */
	public static <F> List<F> search(Collection<F> all, boolean member, boolean staff, boolean owner, Function<F, Category> home, Predicate<F> plus,
			Predicate<F> ownerOnly, Predicate<F> matches) {
		return all.stream().filter(f -> member || !plus.test(f)).filter(f -> seen(f, staff, owner, home, ownerOnly)).filter(matches).distinct().toList();
	}
}
