package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetMenuTest {
	private static final List<String> PAGE = List.of("", "Black Stained Glass Pane", "[Lvl 200] Golden Dragon", "[Lvl 100] Ender Dragon", "", "[Lvl 1] Goop", "Close", "Next Page");

	@Test
	void theMenuIsKnownByItsTitleWithOrWithoutPages() {
		assertTrue(PetMenu.is("Pets"));
		assertTrue(PetMenu.is("Pets (2/3)"));
		assertTrue(PetMenu.is("Pets Menu"));
		assertFalse(PetMenu.is("Pet Upgrades"));
		assertFalse(PetMenu.is("Wardrobe (1/2)"));
	}

	@Test
	void petsAreCountedInMenuOrderSkippingEverythingElse() {
		assertEquals(2, PetMenu.find(PAGE, 1));
		assertEquals(5, PetMenu.find(PAGE, 3));
		assertEquals(-1, PetMenu.find(PAGE, 4));
		assertEquals(3, PetMenu.count(PAGE));
		assertEquals(7, PetMenu.next(PAGE));
		assertEquals(-1, PetMenu.next(List.of("[Lvl 1] Goop", "Close")));
	}

	@Test
	void theLastPageIsOnlyKnownWhenTheTitleSaysSo() {
		assertTrue(PetMenu.lastPage("Pets (3/3)"));
		assertFalse(PetMenu.lastPage("Pets (1/3)"));
		assertFalse(PetMenu.lastPage("Pets"));
	}

	@Test
	void thePetAlreadyOutIsTheOneOfferingToPutItAway() {
		assertTrue(PetMenu.summoned(List.of("Strength +50", "", "Click to deactivate!")));
		assertFalse(PetMenu.summoned(List.of("Strength +50", "", "Click to summon!")));
	}
}
