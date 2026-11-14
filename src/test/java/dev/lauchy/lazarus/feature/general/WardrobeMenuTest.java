package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeMenuTest {
	@Test
	void theWindowIsTheWardrobeWithOrWithoutAPage() {
		assertTrue(WardrobeMenu.is("Wardrobe"));
		assertTrue(WardrobeMenu.is("wardrobe (1/2)"));
		assertFalse(WardrobeMenu.is("Pets"));
		assertFalse(WardrobeMenu.lastPage("Wardrobe (1/2)"));
		assertTrue(WardrobeMenu.lastPage("Wardrobe (2/2)"));
		assertFalse(WardrobeMenu.lastPage("Wardrobe"));
	}

	@Test
	void numberedSlotsAreFoundByTheirNumberWhereverTheySit() {
		List<String> names = List.of("", "Slot 1", "Slot 2: Empty", "", "Wardrobe Slot 3", "Next Page");
		assertEquals(1, WardrobeMenu.find(names, 1, 0));
		assertEquals(2, WardrobeMenu.find(names, 2, 0));
		assertEquals(4, WardrobeMenu.find(names, 3, 0));
		assertEquals(-1, WardrobeMenu.find(names, 4, 0), "on another page");
		assertEquals(3, WardrobeMenu.count(names));
		assertEquals(5, WardrobeMenu.next(names));
	}

	@Test
	void aNumberedSlotOnALaterPageIsFoundByItsOwnNumber() {
		// Page two holds slots 10 to 12, after nine on page one (and whatever else page one counted).
		List<String> page = List.of("Slot 10: Ready", "Slot 11: Empty", "Slot 12: Ready", "Previous Page");
		assertEquals(2, WardrobeMenu.find(page, 12, 9));
		assertEquals(2, WardrobeMenu.find(page, 12, 13));
	}

	@Test
	void unnumberedSlotsAreCountedInOrderPastFillerAndButtons() {
		List<String> names = List.of("", "Dragon Set", " ", "Crypt Set", "Back", "Close");
		assertEquals(1, WardrobeMenu.find(names, 1, 0));
		assertEquals(3, WardrobeMenu.find(names, 2, 0));
		assertEquals(-1, WardrobeMenu.find(names, 3, 0));
		assertEquals(2, WardrobeMenu.count(names));
		assertEquals(-1, WardrobeMenu.next(names));
		// The same page after two slots on the page before: slot 4 is its second.
		assertEquals(3, WardrobeMenu.find(names, 4, 2));
	}

	@Test
	void theSlotAlreadyWornIsLeftOn() {
		assertTrue(WardrobeMenu.worn(List.of("§7Dragon Set", "§eClick to unequip!")));
		assertFalse(WardrobeMenu.worn(List.of("§7Dragon Set", "§eClick to equip!")));
		assertEquals("You're already wearing wardrobe slot 6.", new WardrobeMenu.Reader().already("Slot 6: Equipped", List.of("Click to unequip!"), 6));
	}

	@Test
	void theServerSayingASwapCameTooSoonIsKnown() {
		assertTrue(WardrobeMenu.tooSoon("Wait one second after opening the wardrobe before swapping armor."));
		assertTrue(WardrobeMenu.tooSoon("Wait one second after opening the wardrobe before swapping armor.  x2"), "repeated, as chat stacks it");
		assertFalse(WardrobeMenu.tooSoon("Equipped wardrobe slot 6!"));
		assertFalse(WardrobeMenu.tooSoon("kortx: Wait one second after opening the wardrobe before swapping armor."), "a player saying it");
	}

	@Test
	void theWardrobeWaitsOverASecondAndThePetMenuNotAtAll() {
		assertTrue(new WardrobeMenu.Reader().openWait() > 1000);
		assertEquals(0, new PetMenu.Reader().openWait());
		assertFalse(new PetMenu.Reader().tooSoon("Wait one second after opening the wardrobe before swapping armor."));
	}
}
