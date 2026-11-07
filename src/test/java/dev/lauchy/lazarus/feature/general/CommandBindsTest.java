package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

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
	void aSavedFileBringsBackEveryFreeBindUpToTheLastInUseAndOneMore() {
		JsonObject file = new JsonObject();
		file.addProperty("enabled", true);
		file.addProperty("wardrobeKey", 87);
		for (int i = 0; i < 44; i++) {
			file.addProperty("command" + i, "");
			file.addProperty("key" + i, -1);
		}
		// Every bind is saved, used or not: nothing in use is one free bind, not one more each launch.
		assertEquals(1, CommandBinds.savedBinds(file));
		// A command written with no key still counts, as does a key with no command.
		file.addProperty("command2", "/warp end");
		assertEquals(4, CommandBinds.savedBinds(file));
		file.addProperty("key7", 65);
		assertEquals(9, CommandBinds.savedBinds(file));
		// Nothing numbered: one free bind to start with.
		JsonObject other = new JsonObject();
		other.addProperty("lookupCommand", "/l %p");
		other.addProperty("keyX", 65);
		assertEquals(1, CommandBinds.savedBinds(other));
	}

	@Test
	void theLastFreeBindInUseBringsTheNext() {
		assertTrue(CommandBinds.wantsAnother(true, ""));
		assertTrue(CommandBinds.wantsAnother(false, "/warp end"));
		assertFalse(CommandBinds.wantsAnother(false, "  "));
	}
}
