package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoClickerTest {
	private final AutoClicker clicker = new AutoClicker();

	@Test
	void isALazarusPlusFeature() {
		assertTrue(clicker.plus());
		assertFalse(clicker.ownerOnly());
	}

	@Test
	void leftAndRightAreSetUpOnTheirOwnWithTheirOwnToggleKeys() {
		var keys = clicker.settings().stream().map(s -> s.key).toList();
		assertTrue(keys.containsAll(java.util.List.of("left", "leftKey", "right", "rightKey", "cps", "menus")), keys.toString());
	}

	@Test
	void neverReachesTenASecond() {
		assertTrue(AutoClicker.MOST < 10);
	}

	@Test
	void startsOffSoNobodyClicksWithoutMeaningTo() {
		assertFalse(clicker.enabledSetting().on());
	}
}
