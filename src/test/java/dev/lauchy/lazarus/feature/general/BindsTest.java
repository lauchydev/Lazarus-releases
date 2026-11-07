package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindsTest {
	private static final Pattern PET = Pattern.compile("^pet(\\d+)Key$");

	@Test
	void aSavedFileBringsBackEveryBindItHoldsAndOneMore() {
		// Pet keys count from 1: nine saved keys, as before they were unlimited, give ten (one past the ninth).
		assertEquals(11, Binds.saved(List.of("enabled", "pet1Key", "pet9Key"), PET));
		assertEquals(1, Binds.saved(List.of("enabled"), PET));
		assertEquals(1, Binds.saved(List.of(), PET));
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
