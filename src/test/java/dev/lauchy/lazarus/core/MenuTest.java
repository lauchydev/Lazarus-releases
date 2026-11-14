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
	private static final Item PATH = new Item("Breaker Path", Category.DUNGEONS, true);
	private static final Item SECRETS = new Item("Secrets", Category.DUNGEONS, false);
	private static final Item TOOLS = new Item("Staff Tools", Category.STAFF, false);
	/** Everyone runs it, only members set it up: it lives under Lazarus+ without being locked for anyone. */
	private static final Item JOIN = new Item("Join Messages", Category.PLUS, false);
	private static final List<Item> ALL = List.of(CAPES, WARDROBE, PATH, SECRETS, TOOLS, JOIN);

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
	void lazarusPlusListsItsFeaturesForMembersAndIsALandingPageForEveryoneElse() {
		assertEquals(List.of(PATH, JOIN), listed(Category.PLUS, true));
		assertEquals(List.of(), listed(Category.PLUS, false));
	}

	@Test
	void aFeatureWhoseHomeIsLazarusPlusListsThereAndNowhereElse() {
		for (Category c : Menu.sidebar(true, true)) assertEquals(c == Category.PLUS, listed(c, true).contains(JOIN), c.name());
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
		for (Item plus : List.of(CAPES, PATH)) {
			assertEquals(1, Menu.sidebar(true, true).stream().filter(c -> listed(c, true).contains(plus)).count(), plus.name());
		}
	}

	@Test
	void searchFindsEachLazarusPlusFeatureOnceForMembers() {
		List<Item> withRepeats = List.of(CAPES, PATH, CAPES, SECRETS, PATH);
		assertEquals(List.of(CAPES, PATH, SECRETS), Menu.search(withRepeats, true, false, Item::home, Item::plus, i -> true));
	}

	@Test
	void searchHidesLazarusPlusFeaturesFromNonMembers() {
		assertEquals(List.of(WARDROBE, SECRETS, JOIN), Menu.search(ALL, false, false, Item::home, Item::plus, i -> true), "Join Messages isn't locked, so a search finds it");
		assertEquals(List.of(), Menu.search(ALL, false, false, Item::home, Item::plus, i -> i.name().contains("Breaker")));
	}

	@Test
	void searchShowsStaffFeaturesToStaffOnly() {
		assertEquals(List.of(WARDROBE, SECRETS, TOOLS, JOIN), Menu.search(ALL, false, true, Item::home, Item::plus, i -> true));
		assertEquals(List.of(), Menu.search(ALL, true, false, Item::home, Item::plus, i -> i.name().contains("Staff")));
	}

	@Test
	void searchKeepsOnlyMatches() {
		assertEquals(List.of(PATH), Menu.search(ALL, true, false, Item::home, Item::plus, i -> i.name().contains("Breaker")));
	}

	/** The owner's own feature: in the Staff section, not Lazarus+. */
	private static final Item OWNERS = new Item("Owner's Chain", Category.STAFF, false);
	private static final List<Item> WITH_OWNERS = List.of(WARDROBE, TOOLS, OWNERS);

	private static List<Item> listed(Category category, boolean member, boolean staff, boolean owner) {
		return Menu.listed(WITH_OWNERS, category, member, staff, owner, Item::home, Item::plus, i -> i == OWNERS);
	}

	private static List<Item> search(boolean member, boolean staff, boolean owner) {
		return Menu.search(WITH_OWNERS, member, staff, owner, Item::home, Item::plus, i -> i == OWNERS, i -> true);
	}

	@Test
	void theOwnerGetsTheStaffSectionForTheirOwnFeaturesEvenWithoutAStaffRank() {
		assertEquals(Category.STAFF, Menu.sidebar(false, false, true).getLast());
		assertEquals(List.of(OWNERS), listed(Category.STAFF, false, false, true), "the owner who isn't staff sees only their own");
		assertEquals(List.of(TOOLS, OWNERS), listed(Category.STAFF, false, true, true));
	}

	@Test
	void nobodyButTheOwnerSeesTheOwnersFeaturesStaffAndMembersIncluded() {
		assertEquals(List.of(TOOLS), listed(Category.STAFF, true, true, false));
		assertEquals(List.of(), listed(Category.PLUS, true, true, false));
		assertEquals(List.of(WARDROBE, TOOLS), search(true, true, false));
		assertEquals(List.of(WARDROBE), search(true, false, false));
		assertEquals(List.of(WARDROBE, OWNERS), search(false, false, true));
	}
}
