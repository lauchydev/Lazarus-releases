package dev.lauchy.lazarus.feature.general;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading the server's Pets menu: which window it is, which page, and where each pet sits. */
final class PetMenu {
	/** "Pets", "Pets Menu", or either with its page, "Pets (1/3)". */
	private static final Pattern TITLE = Pattern.compile("^Pets(?: Menu)?(?:\\s*\\((\\d+)\\s*/\\s*(\\d+)\\))?$", Pattern.CASE_INSENSITIVE);
	/** A pet as the menu names it: "[Lvl 100] Ender Dragon". */
	private static final Pattern PET = Pattern.compile("^\\[Lvl \\d+] .+");

	private PetMenu() {
	}

	/** Whether a window with this plain title is the Pets menu. */
	static boolean is(String title) {
		return TITLE.matcher(title.trim()).matches();
	}

	/** Whether the Pets menu titled this says it's on its last page; a title without pages doesn't say. */
	static boolean lastPage(String title) {
		Matcher m = TITLE.matcher(title.trim());
		return m.matches() && m.group(1) != null && Integer.parseInt(m.group(1)) >= Integer.parseInt(m.group(2));
	}

	/** Whether an item with this plain name is a pet. */
	static boolean isPet(String name) {
		return PET.matcher(name.trim()).matches();
	}

	/** Where the {@code n}th pet (from 1) is among the window's item names, or -1 when there are fewer pets. */
	static int find(List<String> names, int n) {
		for (int i = 0; i < names.size(); i++) if (isPet(names.get(i)) && --n == 0) return i;
		return -1;
	}

	/** How many pets the window's item names hold. */
	static int count(List<String> names) {
		return (int) names.stream().filter(PetMenu::isPet).count();
	}

	/** Where the arrow to the next page is among the window's item names, or -1. */
	static int next(List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i).trim().toLowerCase(Locale.ROOT);
			if (name.equals("next page") || name.equals("next")) return i;
		}
		return -1;
	}

	/** Whether a pet with this plain lore is the one already out, which a click would put away. */
	static boolean summoned(List<String> lore) {
		return lore.stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).anyMatch(l -> l.startsWith("click to deactivate") || l.startsWith("click to despawn"));
	}
}
