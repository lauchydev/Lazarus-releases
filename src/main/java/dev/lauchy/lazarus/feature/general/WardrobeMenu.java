package dev.lauchy.lazarus.feature.general;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading the server's Wardrobe menu: which window it is, which page, and where each slot sits. A slot is an
 * item named "Slot N" (whatever follows), taken by its number; failing that, the Nth item that isn't filler
 * (blank glass) or a button (next, back, close). No Minecraft types, so it's tested on its own.
 */
final class WardrobeMenu {
	/** "Wardrobe", or with its page, "Wardrobe (1/2)". */
	private static final Pattern TITLE = Pattern.compile("^Wardrobe(?:\\s*\\((\\d+)\\s*/\\s*(\\d+)\\))?$", Pattern.CASE_INSENSITIVE);
	/** A slot named by its number: "Slot 3", "Wardrobe Slot 3", "Slot 3: Empty". */
	private static final Pattern NUMBERED = Pattern.compile("^(?:wardrobe\\s+)?slot\\s+(\\d+)\\b.*", Pattern.CASE_INSENSITIVE);
	/** The menu's buttons, which are never slots. */
	private static final Pattern BUTTON = Pattern.compile("^(?:next(?: page)?|previous(?: page)?|back|go back|close|page \\d+.*)$", Pattern.CASE_INSENSITIVE);

	private WardrobeMenu() {
	}

	/** Whether a window with this plain title is the Wardrobe. */
	static boolean is(String title) {
		return TITLE.matcher(title.trim()).matches();
	}

	/** Whether the Wardrobe titled this says it's on its last page; a title without pages doesn't say. */
	static boolean lastPage(String title) {
		Matcher m = TITLE.matcher(title.trim());
		return m.matches() && m.group(1) != null && Integer.parseInt(m.group(1)) >= Integer.parseInt(m.group(2));
	}

	/** Whether an item with this plain name is a slot: anything named, bar the buttons. */
	static boolean isSlot(String name) {
		String n = name.trim();
		return !n.isEmpty() && !BUTTON.matcher(n).matches();
	}

	/** The number a slot's name carries ("Slot 3" gives 3), or 0 when it has none. */
	static int number(String name) {
		Matcher m = NUMBERED.matcher(name.trim());
		return m.matches() ? Integer.parseInt(m.group(1)) : 0;
	}

	/**
	 * Where slot {@code n} (from 1) is among the window's item names: the item named with that number when the
	 * slots are numbered, else the {@code n}th slot in order; -1 when it isn't on this page.
	 */
	static int find(List<String> names, int n) {
		for (int i = 0; i < names.size(); i++) if (number(names.get(i)) == n) return i;
		if (names.stream().anyMatch(name -> number(name) > 0)) return -1; // numbered slots: this one is on another page
		for (int i = 0; i < names.size(); i++) if (isSlot(names.get(i)) && --n == 0) return i;
		return -1;
	}

	/** How many slots the window's item names hold. */
	static int count(List<String> names) {
		return (int) names.stream().filter(WardrobeMenu::isSlot).count();
	}

	/** Where the arrow to the next page is among the window's item names, or -1. */
	static int next(List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i).trim().toLowerCase(Locale.ROOT);
			if (name.equals("next page") || name.equals("next")) return i;
		}
		return -1;
	}

	/** Whether a slot with this plain lore is the one already worn, which a click would take off. */
	static boolean worn(List<String> lore) {
		return lore.stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).anyMatch(l -> l.contains("unequip") || l.contains("currently equipped") || l.contains("currently worn"));
	}

	/** The Wardrobe as a {@link SlotPicker} reads it. */
	static final class Reader implements SlotPicker.Reader {
		@Override
		public boolean is(String title) {
			return WardrobeMenu.is(title);
		}

		@Override
		public boolean lastPage(String title) {
			return WardrobeMenu.lastPage(title);
		}

		@Override
		public int find(List<String> names, int n) {
			return WardrobeMenu.find(names, n);
		}

		@Override
		public int count(List<String> names) {
			return WardrobeMenu.count(names);
		}

		@Override
		public int next(List<String> names) {
			return WardrobeMenu.next(names);
		}

		@Override
		public String already(String name, List<String> lore) {
			return worn(lore) ? "You're already wearing wardrobe slot " + name.trim() + "." : null;
		}

		@Override
		public String missing(int n) {
			return "No wardrobe slot " + n + ".";
		}

		@Override
		public String noMenu() {
			return "The wardrobe didn't open.";
		}
	}
}
