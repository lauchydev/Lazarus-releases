package dev.lauchy.lazarus.feature.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeMenuTest {
	@Test
	void readsThePageFromTheTitle() {
		assertArrayEquals(new int[]{1, 11}, RecipeMenu.page("Recipes (1/11)"));
		assertArrayEquals(new int[]{7, 11}, RecipeMenu.page(" Recipes (7/11) "));
		assertNull(RecipeMenu.page("Amethyst Helmet"));
		assertNull(RecipeMenu.page(null));
	}

	@Test
	void anItemOpensARecipeWhenItsLoreSaysSo() {
		assertTrue(RecipeMenu.opensRecipe(List.of("Health: +100", "", "Click to view recipe!")));
		assertFalse(RecipeMenu.opensRecipe(List.of("Filter: Mining")));
	}

	@Test
	void turnsTowardTheWantedPage() {
		assertEquals(RecipeMenu.NEXT, RecipeMenu.toward(1, 3));
		assertEquals(RecipeMenu.PREVIOUS, RecipeMenu.toward(5, 3));
		assertEquals(-1, RecipeMenu.toward(3, 3));
	}

	@Test
	void theItemSlotsAreTheMiddleSevenColumnsOfFourRows() {
		assertEquals(28, RecipeMenu.ITEMS.length);
		for (int slot : RecipeMenu.ITEMS) {
			assertTrue(slot / 9 >= 1 && slot / 9 <= 4 && slot % 9 >= 1 && slot % 9 <= 7, "slot " + slot);
		}
	}
}
