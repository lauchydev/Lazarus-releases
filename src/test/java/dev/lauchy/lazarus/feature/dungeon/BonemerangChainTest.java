package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Menu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonemerangChainTest {
	private final BonemerangChain chain = new BonemerangChain();

	@Test
	void isMarkedBannable() {
		assertTrue(chain.bannable());
	}

	@Test
	void sitsOnlyUnderLazarusPlus() {
		List<Feature> all = List.of(chain);
		List<Category> shownIn = Menu.sidebar(true, true).stream().filter(c -> Menu.listed(all, c, true, f -> f.category, Feature::plus).contains(chain)).toList();
		assertEquals(List.of(Category.PLUS), shownIn);
	}
}
