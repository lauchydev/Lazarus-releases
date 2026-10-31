package dev.lauchy.lazarus.feature.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryPathsTest {
	private static final List<String> ALL = List.of("Rose Talisman", "Rose Ring", "Rose Artifact", "Rose Relic",
			"Wilted Rose Talisman", "Defender Talisman", "Protector's Shield", "Warden's Blade", "Weak Greedness", "Low Greed");
	private static final Map<String, List<String>> RECIPES = Map.of(
			"Protector's Shield", List.of("Giant's Core", "Defender Talisman"),
			"Warden's Blade", List.of("Warden Core", "Defender Talisman"),
			"Low Greed", List.of("Lucky Clover", "Weak Greedness"));

	private static List<String> missing(String... owned) {
		return AccessoryPaths.missing(ALL, Set.of(owned), AccessoryPaths.below(ALL, n -> RECIPES.getOrDefault(n, List.of())));
	}

	@Test
	void anOwnedTierHidesTheTiersBelowIt() {
		List<String> m = missing("Rose Artifact");
		assertTrue(m.contains("Rose Relic"));
		assertFalse(m.contains("Rose Artifact"));
		assertFalse(m.contains("Rose Ring"));
		assertFalse(m.contains("Rose Talisman"));
		assertTrue(m.contains("Wilted Rose Talisman"), "another family that shares a word");
	}

	@Test
	void recipesLinkFamiliesWithoutTieredNames() {
		List<String> m = missing("Low Greed", "Warden's Blade");
		assertFalse(m.contains("Weak Greedness"));
		assertFalse(m.contains("Defender Talisman"));
		assertTrue(m.contains("Protector's Shield"), "the other branch is still missing");
	}

	@Test
	void ownedNothingMissesEverything() {
		assertEquals(ALL, missing());
	}

	@Test
	void craftableNeedsEveryPart() {
		assertTrue(AccessoryPaths.craftable(Map.of("Null Atom", 16), Map.of("Null Atom", 20)));
		assertFalse(AccessoryPaths.craftable(Map.of("Null Atom", 16, "Void Talisman", 1), Map.of("Null Atom", 20)));
		assertFalse(AccessoryPaths.craftable(Map.of(), Map.of()), "an unknown recipe");
	}
}
