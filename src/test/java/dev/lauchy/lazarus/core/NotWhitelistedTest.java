package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotWhitelistedTest {
	@Test
	void otherPlayersCapesAreDrawnForAPlayerWhoIsntWhitelisted() {
		assertTrue(NotWhitelisted.starts().contains(NotWhitelisted.Part.CAPES), "capes start for a player who isn't whitelisted");
	}

	@Test
	void aPlayerWhoIsntWhitelistedCantPickSaveOrShareACape() {
		assertFalse(NotWhitelisted.starts().contains(NotWhitelisted.Part.OWN_CAPE), "a cape of their own starts for a player who isn't whitelisted");
	}

	@Test
	void nothingStartsBeyondTheRedeemCommandTheLiveConnectionAndTheCosmetics() {
		assertEquals(EnumSet.of(NotWhitelisted.Part.REDEEM_COMMAND, NotWhitelisted.Part.LIVE_CONNECTION, NotWhitelisted.Part.BADGES, NotWhitelisted.Part.CAPES),
				NotWhitelisted.starts());
	}
}
