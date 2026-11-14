package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChestLooksTest {
	@Test
	void everyItemAChestCanHoldHasALook() {
		for (int floor = 1; floor <= 5; floor++) {
			for (String chest : List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock")) {
				for (String id : ChestPools.of(floor, chest)) {
					ChestLooks.Look look = ChestLooks.of(id);
					assertNotNull(look, id);
					assertFalse(look.name().isBlank(), id);
					assertFalse(look.material().isBlank(), id + " would be drawn as a barrier");
				}
			}
		}
	}

	@Test
	void armourKeepsItsColourAndAHeadItsTexture() {
		ChestLooks.Look boots = ChestLooks.of("oathguard_boots");
		assertEquals("Oathguard Boots", boots.name());
		assertEquals("EPIC", boots.rarity());
		assertEquals("leather_boots", boots.material());
		assertEquals("221,201,115", boots.colour());
		assertEquals("", boots.skin());
		ChestLooks.Look stone = ChestLooks.of("revive_stone");
		assertEquals("player_head", stone.material());
		String skin = new String(Base64.getDecoder().decode(stone.skin()), StandardCharsets.UTF_8);
		assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + stone.texture() + "\"}}}", skin);
		assertNull(ChestLooks.of("undead_essence"));
	}
}
