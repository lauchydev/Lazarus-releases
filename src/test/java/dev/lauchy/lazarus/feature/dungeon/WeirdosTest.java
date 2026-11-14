package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeirdosTest {
	@Test
	void dragsimsOwnRoomIsReadAsTheServerScoredIt() {
		// From a real run (2026-11): the server said the player "wasn't fooled by Montgomery" and "was fooled by" the other two.
		assertTrue(Weirdos.right("At least one of them is lying, and the reward is not in Madelia's chest!"));
		assertFalse(Weirdos.right("We are all telling the truth!"));
		assertFalse(Weirdos.right("Melrose is telling the truth and the reward is in his chest."));
	}

	@Test
	void theSixLinesOfTheRightNpcAreKnown() {
		assertTrue(Weirdos.right("The reward is not in my chest!"));
		assertTrue(Weirdos.right("My chest doesn't have the reward. We are all telling the truth."));
		assertTrue(Weirdos.right("My chest has the reward and I'm telling the truth!"));
		assertTrue(Weirdos.right("The reward isn't in any of our chests."));
		assertTrue(Weirdos.right("Both of them are telling the truth. Also, Baxter has the reward in their chest!"));
	}

	@Test
	void aLineWordedALittleDifferentlyStillMatchesAndTheWrongOnesDoNot() {
		assertTrue(Weirdos.right("My chest does not have the reward, we are all telling the truth"));
		assertTrue(Weirdos.right("The reward isn’t in any of our chests!"));
		assertFalse(Weirdos.right("The reward is in my chest!"));
		assertFalse(Weirdos.right("The reward is not in my chest! They are both lying."));
		assertFalse(Weirdos.right("My chest has the reward!"));
		assertFalse(Weirdos.right("Baxter is lying, the reward is in my chest."));
	}

	@Test
	void theGatesLinesSayWhoDidWhatAndHowManyAreDone() {
		// As Dragsim sends them (2026-11).
		org.junit.jupiter.api.Assertions.assertEquals(new TerminalProgress.Done("Gurrnos", TerminalProgress.Kind.TERMINAL, 3, 7), TerminalProgress.read("Gurrnos activated a terminal! (3/7)"));
		org.junit.jupiter.api.Assertions.assertEquals(new TerminalProgress.Done("sStepbr0", TerminalProgress.Kind.LEVER, 1, 2), TerminalProgress.read("sStepbr0 activated a lever! (1/2)"));
		org.junit.jupiter.api.Assertions.assertEquals(new TerminalProgress.Done("Gurrnos", TerminalProgress.Kind.DEVICE, 1, 1), TerminalProgress.read("[MOD] Gurrnos completed a device! (1/1) (x2)"));
		org.junit.jupiter.api.Assertions.assertNull(TerminalProgress.read("Steve: Gurrnos activated a terminal! (3/7)"));
	}
}
