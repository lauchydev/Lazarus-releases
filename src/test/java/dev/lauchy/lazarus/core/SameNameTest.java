package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SameNameTest {
	private static List<String> skin(String set) {
		return List.of("§8Combinable in Anvil", "", "§7Item skins give your gear a fresh", "§7new look! Apply them just like an",
				"§7enchant book using the anvil.", "", "§7This skin can be applied to §6" + set, "§6Dragon Helmet§7.");
	}

	private static final Map<String, List<String>> BABIES = new LinkedHashMap<>();

	static {
		BABIES.put("protector_baby", skin("Protector"));
		BABIES.put("strong_baby", skin("Strong"));
		BABIES.put("superior_baby", skin("Superior"));
	}

	@Test
	void picksTheSkinWhoseLoreNamesTheHelmet() {
		// Wrapped differently in game, so the lines are read as one run of text.
		List<String> strong = List.of("Combinable in Anvil", "", "Item skins give your gear a fresh", "new look! Apply them just like an",
				"enchant book using the anvil.", "", "This skin can be applied to Strong Dragon", "Helmet.", "", "EPIC ITEM");
		assertEquals("strong_baby", SameName.byLore(BABIES, strong));
		List<String> protector = List.of("Combinable in Anvil", "This skin can be applied to Protector", "Dragon Helmet.", "EPIC ITEM");
		assertEquals("protector_baby", SameName.byLore(BABIES, protector));
	}

	@Test
	void saysNothingWhenTheLoreDoesNotTellThemApart() {
		assertNull(SameName.byLore(BABIES, List.of("Combinable in Anvil", "EPIC ITEM")));
		assertNull(SameName.byLore(BABIES, List.of("Something else entirely")));
	}
}
