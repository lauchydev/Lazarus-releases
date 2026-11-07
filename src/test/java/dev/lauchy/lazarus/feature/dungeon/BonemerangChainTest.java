package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Menu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonemerangChainTest {
	private final BonemerangChain chain = new BonemerangChain();

	@Test
	void isMarkedBannable() {
		assertTrue(chain.bannable());
	}

	@Test
	void isALazarusPlusFeatureForAnyMember() {
		assertTrue(chain.plus());
		assertFalse(chain.ownerOnly());
	}

	/** The categories a player sees it listed in. */
	private List<Category> shownIn(boolean member, boolean staff, boolean owner) {
		List<Feature> all = List.of(chain);
		return Menu.sidebar(member, staff, owner).stream()
				.filter(c -> Menu.listed(all, c, member, staff, owner, f -> f.category, Feature::plus, Feature::ownerOnly).contains(chain)).toList();
	}

	@Test
	void sitsUnderLazarusPlusForMembersAndNowhereForAnyoneElse() {
		assertEquals(List.of(Category.PLUS), shownIn(true, false, false));
		assertEquals(List.of(Category.PLUS), shownIn(true, true, true), "the owner is a member like any other");
		assertEquals(List.of(), shownIn(false, false, false), "the Lazarus+ page is a landing page without membership");
		assertEquals(List.of(), shownIn(false, false, true), "the owner too, without Lazarus+");
	}
}
