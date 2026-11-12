package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DropColoursTest {
	@Test
	void aDropsColourIsTheOneItsNameIsWrittenIn() {
		assertEquals("§5", DropColours.colourOf("§6§lRARE DROP! §7(§5Summoning Eye§7) (§b✯ 266%§7)", "Summoning Eye"));
		assertEquals("§6", DropColours.colourOf("§5§lEPIC DROP! §r§6Golden Eye", "Golden Eye"));
		assertEquals("§d", DropColours.colourOf("§6§lLEGENDARY DROP! §r§dRift Pass", "rift pass"));
	}

	@Test
	void aPetsNameTakesTheColourOfItsLastLetter() {
		assertEquals("§6", DropColours.colourOf("§d§lRNGESUS DROP! §r§6§7[Lvl 1] §6Enderman", "[Lvl 1] Enderman"));
	}

	@Test
	void anUncolouredOrMissingNameGivesNothing() {
		assertNull(DropColours.colourOf("RARE DROP! Summoning Eye", "Summoning Eye"));
		assertNull(DropColours.colourOf("§5§lEPIC DROP! §r§6Golden Eye", "Warden Heart"));
		assertNull(DropColours.colourOf("§6Golden§r Eye", "Golden Eye"));
	}
}
