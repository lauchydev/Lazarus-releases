package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBindsTest {
	@Test
	void anEmptyLookUpCommandOpensTheStaffPanelForStaffAndTheProfileForEveryoneElse() {
		assertEquals("/lazarus staff %p", CommandBinds.lookup("", true));
		assertEquals("/l %p", CommandBinds.lookup("  ", false));
		assertEquals("/l %p", CommandBinds.lookup("/l %p", true)); // what's set is what runs
	}

	@Test
	void aSavedFileBringsBackEveryFreeBindItHoldsAndOneMore() {
		// The eight binds saved before they were unlimited, all present whether used or not.
		assertEquals(9, CommandBinds.savedBinds(List.of("enabled", "wardrobeKey", "command0", "key0", "command7", "key7")));
		// A file with only a key for bind 3 still brings back binds 1 to 3, then a free one.
		assertEquals(5, CommandBinds.savedBinds(List.of("key3")));
		// Nothing saved, or nothing numbered: one free bind to start with.
		assertEquals(1, CommandBinds.savedBinds(List.of()));
		assertEquals(1, CommandBinds.savedBinds(List.of("enabled", "lookupCommand", "command", "keyX")));
	}

	@Test
	void theLastFreeBindInUseBringsTheNext() {
		assertTrue(CommandBinds.wantsAnother(true, ""));
		assertTrue(CommandBinds.wantsAnother(false, "/warp end"));
		assertFalse(CommandBinds.wantsAnother(false, "  "));
	}
}
