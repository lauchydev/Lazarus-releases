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
		assertEquals(1, WardrobeMenu.find(names, 1));
		assertEquals(2, WardrobeMenu.find(names, 2));
		assertEquals(4, WardrobeMenu.find(names, 3));
		assertEquals(-1, WardrobeMenu.find(names, 4), "on another page");
		assertEquals(3, WardrobeMenu.count(names));
		assertEquals(5, WardrobeMenu.next(names));
	}

	@Test
	void unnumberedSlotsAreCountedInOrderPastFillerAndButtons() {
		List<String> names = List.of("", "Dragon Set", " ", "Crypt Set", "Back", "Close");
		assertEquals(1, WardrobeMenu.find(names, 1));
		assertEquals(3, WardrobeMenu.find(names, 2));
		assertEquals(-1, WardrobeMenu.find(names, 3));
		assertEquals(2, WardrobeMenu.count(names));
		assertEquals(-1, WardrobeMenu.next(names));
	}

	@Test
	void theSlotAlreadyWornIsLeftOn() {
		assertTrue(WardrobeMenu.worn(List.of("§7Dragon Set", "§eClick to unequip!")));
		assertFalse(WardrobeMenu.worn(List.of("§7Dragon Set", "§eClick to equip!")));
	}
}
