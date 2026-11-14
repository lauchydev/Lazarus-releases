package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindsTest {
	private static final Pattern NUMBERED = Pattern.compile("^key(\\d+)$");

	@Test
	void aSavedFileBringsBackEveryBindUpToTheLastInUseAndOneMore() {
		JsonObject file = new JsonObject();
		file.addProperty("enabled", true);
		file.addProperty("key0", 65);
		file.addProperty("key1", -1);
		file.addProperty("key2", 66);
		for (int i = 3; i < 40; i++) file.addProperty("key" + i, -1); // empty binds saved by every launch before
		assertEquals(4, Binds.saved(file, NUMBERED), "binds 0 to 2, the gap kept, then one empty");
	}

	@Test
	void aFileWithNothingInUseStartsWithOneBind() {
		JsonObject file = new JsonObject();
		file.addProperty("enabled", true);
		file.addProperty("key0", -1);
		assertEquals(1, Binds.saved(file, NUMBERED));
		assertEquals(1, Binds.saved(new JsonObject(), NUMBERED));
	}

	@Test
	void theFreeAllowanceComesFirst() {
		assertFalse(Binds.plus(0, 2));
		assertFalse(Binds.plus(1, 2));
		assertTrue(Binds.plus(2, 2));
		assertFalse(Binds.plus(4, 5));
		assertTrue(Binds.plus(5, 5));
	}
}
