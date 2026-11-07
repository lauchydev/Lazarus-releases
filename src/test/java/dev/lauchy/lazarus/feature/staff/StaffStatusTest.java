package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffStatusTest {
	@Test
	void vanishingAndComingBackAreRead() {
		assertEquals(new StaffStatus.Vanished(true), StaffStatus.read("You are now vanished. Other players cannot see you."));
		assertEquals(new StaffStatus.Vanished(false), StaffStatus.read("You are no longer vanished."));
		assertEquals(new StaffStatus.Vanished(false), StaffStatus.read("You are no longer vanished. (x2)"), "a stacked repeat says the same");
	}

	@Test
	void aNickGivesTheNameAndTheRankItShows() {
		assertEquals(new StaffStatus.Nicked("GhostRogue56", "DRAGON"), StaffStatus.read("You are now nicked as [DRAGON] GhostRogue56! Use /nick reset to go back."));
		assertEquals(new StaffStatus.Nicked("Plain_One", ""), StaffStatus.read("You are now nicked as Plain_One! Use /nick reset to go back."));
		assertEquals(new StaffStatus.Nicked("", ""), StaffStatus.read("Your nick has been reset."));
		assertEquals(new StaffStatus.Nicked("", ""), StaffStatus.read("You are no longer nicked."));
		assertNull(StaffStatus.read("Setting up your nick..."));
	}

	@Test
	void theChatChannelIsRead() {
		assertEquals(new StaffStatus.Channel("STAFF"), StaffStatus.read("You are now in the STAFF channel"));
		assertEquals("Staff", StaffStatus.channelName("STAFF"));
	}

	@Test
	void aPlayerSayingItIsNotYou() {
		assertNull(StaffStatus.read("Steve: You are now vanished. Other players cannot see you."));
		assertNull(StaffStatus.read("[DRAGON] Steve: You are now nicked as [ADMIN] Owner!"));
	}

	@Test
	void theTabListMarksAVanishedStaffMember() {
		assertTrue(StaffStatus.vanishTag("[V] [ADMIN] Lauchy"));
		assertFalse(StaffStatus.vanishTag("[ADMIN] Lauchy"));
	}

	@Test
	void aPagedTabListRowNamesItsPlayerAndTheInfoRowsNameNobody() {
		assertEquals("Porier", StaffStatus.nameIn("[V] [OWNER] Porier"));
		assertEquals("Lauchy", StaffStatus.nameIn(" [V] [ADMIN] Lauchy"));
		assertEquals("HakuiKoyorix", StaffStatus.nameIn("[DRAGON] HakuiKoyorix ⚠"));
		assertEquals("petseklapsas", StaffStatus.nameIn("petseklapsas ✎"));
		assertEquals("LungeWhy", StaffStatus.nameIn("LungeWhy"));
		for (String info : new String[]{"Area: The End", "Lobby: #2", "Strength 571", "Active Boosters (1)", "Dragons:", "End Island (104) 1/6",
				"Whale Booster 13m 47s by [DRAGON] xiTrapz", "Protector Dragon 3,700,000♥", "Next expiry: < 1 hour", ""}) {
			assertNull(StaffStatus.nameIn(info), info);
		}
	}

	@Test
	void onlyARowOfThePageSaysAStaffMemberIsNotVanished() {
		assertTrue(StaffStatus.pageRow(true, 1), "an entry for another profile: the server's own row");
		assertTrue(StaffStatus.pageRow(false, 2), "shown twice: their page is up, row and own entry");
		assertFalse(StaffStatus.pageRow(false, 1), "only their own entry (another page up): it never carries the [V]");
	}

	@Test
	void glistAllNamesEachServersPlayers() {
		assertEquals(new StaffStatus.Server("rift", List.of("midoo00", "masterchiff", "Gurrnos")), StaffStatus.glist("[rift] (3): midoo00, masterchiff, Gurrnos"));
		assertEquals(List.of("Speedy_089", "Quan976"), StaffStatus.glist("[end] (2): Speedy_089, Quan976 ⚠").players(), "a mark after a name");
		assertTrue(StaffStatus.isGlist("[dungeons] (1): LinNa_OvO"));
		assertTrue(StaffStatus.isGlist("165 players are currently connected to the proxy."));
		assertNull(StaffStatus.glist("[DRAGON] Steve: [end] (2): a, b"));
		assertFalse(StaffStatus.isGlist("Steve: 165 players are currently connected to the proxy."));
	}

	@Test
	void staffNamedByGlistOnAnotherServerCountUntilTheyAreNotNamedForAWhile() {
		StaffStatus.Seen seen = new StaffStatus.Seen();
		seen.saw("Lauchy", "ADMIN", "", true, 1000);
		seen.listed("Gurrnos", "MOD", "", "rift", 1000);
		seen.listed("Lauchy", "ADMIN", "", "end", 1000);
		assertEquals(2, seen.count());
		assertTrue(seen.members().getFirst().vanished(), "the tab list's [V] stands when glist names them too");
		assertEquals("rift", seen.members().get(1).server());
		seen.leftServer(); // another lobby: its tab list starts afresh, glist's names stand
		assertEquals(2, seen.count());
		seen.forget(1000 + StaffStatus.Seen.LISTED_MS + 1);
		assertEquals(0, seen.count());
	}

	@Test
	void youArriveInALobbyVisibleUnlessTheServerSaysYouKeptYourVanish() {
		assertEquals(new StaffStatus.Vanished(true), StaffStatus.read("You are vanished (persisted from last session)."));
		assertFalse(StaffStatus.vanishedOnArrival(0, 100_000), "nothing said: visible");
		assertTrue(StaffStatus.vanishedOnArrival(98_000, 100_000), "said just before the game noticed the new lobby");
		assertFalse(StaffStatus.vanishedOnArrival(40_000, 100_000), "said in the lobby before, long ago: that lobby's");
	}

	@Test
	void aStaffMemberKeepsTheVanishTheirPageLastShowedAndCountsUntilGoneAWhile() {
		StaffStatus.Seen seen = new StaffStatus.Seen();
		seen.saw("Lauchy", "ADMIN", "", false, 1000);
		seen.saw("Ghost", "MOD", "", true, 1000);
		seen.saw("Ghost", "MOD", "", null, 6000); // another page up: their tags can't be read
		seen.forget(6000);
		assertEquals(2, seen.count());
		assertEquals(1, seen.vanished(), "still vanished as their page last showed");
		seen.saw("Ghost", "MOD", "", null, 1000 + StaffStatus.Seen.GONE_MS);
		seen.forget(1000 + StaffStatus.Seen.GONE_MS + 1);
		assertEquals(1, seen.count(), "Lauchy gone a while: logged off");
	}

	@Test
	void staffAreListedTheHighestRankFirstWithTheNickTheyGoBy() {
		StaffStatus.Seen seen = new StaffStatus.Seen();
		seen.saw("Fexous", "MOD", "petseklapsas", false, 0);
		seen.saw("Lauchy", "ADMIN", "", true, 0);
		seen.saw("Aide", "HELPER", "", false, 0);
		assertEquals(List.of("Lauchy", "Fexous", "Aide"), seen.members().stream().map(StaffStatus.Seen.Member::name).toList());
		assertEquals("petseklapsas", seen.members().get(1).as());
	}
}
