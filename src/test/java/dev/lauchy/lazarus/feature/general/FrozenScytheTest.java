package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which particles and sounds the Frozen Scythe Effects list hides. */
class FrozenScytheTest {
	@Test
	void entriesWithoutANamespaceAreMinecrafts() {
		assertTrue(FrozenScythe.listed("snowflake, item_snowball", "minecraft:snowflake"));
		assertTrue(FrozenScythe.listed("snowflake,  Item_Snowball ", "minecraft:item_snowball"));
		assertFalse(FrozenScythe.listed("snowflake", "minecraft:snow"));
	}

	@Test
	void aNamespacedEntryMatchesOnlyThatNamespace() {
		assertTrue(FrozenScythe.listed("dragsim:scythe.swing", "dragsim:scythe.swing"));
		assertFalse(FrozenScythe.listed("dragsim:scythe.swing", "minecraft:scythe.swing"));
	}

	@Test
	void anEmptyListHidesNothing() {
		assertFalse(FrozenScythe.listed("", "minecraft:snowflake"));
		assertFalse(FrozenScythe.listed(" , ", "minecraft:snowflake"));
	}
}
