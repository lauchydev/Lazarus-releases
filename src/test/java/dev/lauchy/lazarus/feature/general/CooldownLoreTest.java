package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownLoreTest {
	private static final List<String> BLINDING_SWORD = List.of(
			"Damage: +250", "Strength: +100", "",
			"Ability: Final Spark  RIGHT CLICK", "Blinds enemies around you for 3s.", "Mana Cost: 50", "Cooldown: 12s", "",
			"Ability: Flash  SNEAK RIGHT CLICK", "Teleport 8 blocks ahead.", "Mana Cost: 30", "",
			"LEGENDARY SWORD");

	@Test
	void theSectionNamingTheAbilityWins() {
		assertEquals(12.0, CooldownLore.cooldown(BLINDING_SWORD, "Final Spark", true));
	}

	@Test
	void aSectionWithoutACooldownLineSaysNothingRatherThanZero() {
		// Flash has no Cooldown line: the catalog and the built-in fallback must get their turn.
		assertNull(CooldownLore.cooldown(BLINDING_SWORD, "Flash", true));
		assertNull(CooldownLore.cooldown(BLINDING_SWORD, "Unknown", true), "a sneak use with no named section falls to the sneak section");
	}

	@Test
	void anUnnamedUseFallsToTheSectionMatchingHowItWasUsed() {
		assertEquals(12.0, CooldownLore.cooldown(BLINDING_SWORD, "", false));
	}

	@Test
	void loreWithNoAbilityHeadersUsesItsOnlyCooldownLine() {
		assertEquals(4.5, CooldownLore.cooldown(List.of("Damage: +10", "Cooldown: 4.5s"), "Anything", false));
		assertNull(CooldownLore.cooldown(List.of("Damage: +10"), "Anything", false));
	}

	@Test
	void sectionsStartAtAbilityOrClickHeaders() {
		List<CooldownLore.Section> s = CooldownLore.sections(BLINDING_SWORD);
		assertEquals(2, s.size());
		assertEquals(12.0, s.get(0).cooldown());
		assertFalse(s.get(0).sneak());
		assertTrue(s.get(1).sneak());
		assertEquals(0.0, s.get(1).cooldown());
	}

	@Test
	void theIgnoredListMatchesByNameFragmentIgnoringCase() {
		String list = "Golden Cannon, Golden Gun, Terminator";
		assertTrue(CooldownLore.ignored("Golden Gun", list));
		assertTrue(CooldownLore.ignored("✪✪ Fabled Terminator", list));
		assertTrue(CooldownLore.ignored("GOLDEN CANNON", list));
		assertFalse(CooldownLore.ignored("Blinding Sword", list));
		assertFalse(CooldownLore.ignored("Blinding Sword", ""));
	}
}
