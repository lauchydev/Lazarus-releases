package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuTest {
	/** A stand-in for a feature: its home category and whether it's part of Lazarus+. */
	record Item(String name, Category home, boolean plus) {
	}

	private static final Item CAPES = new Item("Capes", Category.CAPES, true);
	private static final Item WARDROBE = new Item("Wardrobe", Category.INVENTORY, false);
	private static final Item CHAIN = new Item("Bonemerang Chain", Category.DUNGEONS, true);
	private static final Item SECRETS = new Item("Secrets", Category.DUNGEONS, false);
	private static final Item TOOLS = new Item("Staff Tools", Category.STAFF, false);
	private static final List<Item> ALL = List.of(CAPES, WARDROBE, CHAIN, SECRETS, TOOLS);

	private static List<Item> listed(Category category, boolean member) {
		return Menu.listed(ALL, category, member, Item::home, Item::plus);
	}

	@Test
	void sidebarListsNormalCategoriesThenTheLazarusPlusSection() {
		List<Category> expected = List.of(Category.END, Category.SLAYERS, Category.DUNGEONS, Category.INVENTORY, Category.CHAT, Category.GENERAL,
				Category.DISCORD, Category.PLUS, Category.CAPES);
		assertEquals(expected, Menu.sidebar(true, false));
	}

	@Test
	void nonMembersSeeTheSameSidebar() {
		assertEquals(Menu.sidebar(true, false), Menu.sidebar(false, false));
	}

	@Test
	void staffGetAStaffSectionAtTheEnd() {
		List<Category> forStaff = Menu.sidebar(false, true);
		assertEquals(Category.STAFF, forStaff.getLast());
		assertEquals(Menu.sidebar(false, false), forStaff.subList(0, forStaff.size() - 1));
	}

	@Test
	void aGapComesBeforeLazarusPlusAndBeforeStaff() {
		assertEquals(List.of(Category.PLUS), Menu.sidebar(true, false).stream().filter(Menu::gapBefore).toList());
		assertEquals(List.of(Category.PLUS, Category.STAFF), Menu.sidebar(true, true).stream().filter(Menu::gapBefore).toList());
	}

	@Test
	void staffListsItsOwnFeatures() {
		assertEquals(List.of(TOOLS), listed(Category.STAFF, false));
		assertEquals(List.of(WARDROBE), listed(Category.INVENTORY, true));
	}

	@Test
	void lazarusPlusListsItsFeaturesForEveryone() {
		assertEquals(List.of(CHAIN), listed(Category.PLUS, true));
		assertEquals(List.of(CHAIN), listed(Category.PLUS, false));
	}

	@Test
	void capesIsItsOwnCategoryForEveryone() {
		assertEquals(List.of(CAPES), listed(Category.CAPES, true));
		assertEquals(List.of(CAPES), listed(Category.CAPES, false));
	}

	@Test
	void normalCategoriesLeaveOutLazarusPlusFeatures() {
		assertEquals(List.of(SECRETS), listed(Category.DUNGEONS, true));
		assertEquals(List.of(SECRETS), listed(Category.DUNGEONS, false));
		assertEquals(List.of(WARDROBE), listed(Category.INVENTORY, false));
	}

	@Test
	void everyLazarusPlusFeatureAppearsInOneCategoryOnly() {
		for (Item plus : List.of(CAPES, CHAIN)) {
			assertEquals(1, Menu.sidebar(true, true).stream().filter(c -> listed(c, true).contains(plus)).count(), plus.name());
		}
	}

	@Test
	void searchFindsEachLazarusPlusFeatureOnceForMembers() {
		List<Item> withRepeats = List.of(CAPES, CHAIN, CAPES, SECRETS, CHAIN);
		assertEquals(List.of(CAPES, CHAIN, SECRETS), Menu.search(withRepeats, true, false, Item::home, Item::plus, i -> true));
	}

	@Test
	void searchHidesLazarusPlusFeaturesFromNonMembers() {
		assertEquals(List.of(WARDROBE, SECRETS), Menu.search(ALL, false, false, Item::home, Item::plus, i -> true));
		assertEquals(List.of(), Menu.search(ALL, false, false, Item::home, Item::plus, i -> i.name().contains("Chain")));
	}

	@Test
	void searchShowsStaffFeaturesToStaffOnly() {
		assertEquals(List.of(WARDROBE, SECRETS, TOOLS), Menu.search(ALL, false, true, Item::home, Item::plus, i -> true));
		assertEquals(List.of(), Menu.search(ALL, true, false, Item::home, Item::plus, i -> i.name().contains("Staff")));
	}

	@Test
	void searchKeepsOnlyMatches() {
		assertEquals(List.of(CHAIN), Menu.search(ALL, true, false, Item::home, Item::plus, i -> i.name().contains("Chain")));
	}
}
