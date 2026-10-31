package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTest {
	private static final String ANN = "0123456789abcdef0123456789abcdef";
	private static final String BOB = "fedcba9876543210fedcba9876543210";
	private static final UUID ANN_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
	private static final UUID BOB_ID = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");

	private static String snapshot(String... entries) {
		return "{\"type\":\"snapshot\",\"online\":[" + String.join(",", entries) + "]}";
	}

	private static String entry(String id, String name, String kind) {
		return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}";
	}

	@Test
	void showsNoBadgeBeforeHearingAnything() {
		assertEquals(Live.Badge.NONE, new Live().badge(ANN_ID));
	}

	@Test
	void showsEachBadgeKindFromTheSnapshot() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple"), entry(BOB, "Bob", "gold")));
		assertEquals(Live.Badge.PURPLE, live.badge(ANN_ID));
		assertEquals(Live.Badge.GOLD, live.badge(BOB_ID));
	}

	@Test
	void showsAGreyBadgeForAModUserWhoIsNotWhitelisted() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "grey")));
		assertEquals(Live.Badge.GREY, live.badge(ANN_ID));
	}

	@Test
	void showsABadgeForSomeoneWhoJoinsAndDropsItWhenTheyLeave() {
		Live live = new Live();
		live.apply(snapshot());
		live.apply("{\"type\":\"join\"," + entry(BOB, "Bob", "purple").substring(1));
		assertEquals(Live.Badge.PURPLE, live.badge(BOB_ID));
		live.apply("{\"type\":\"leave\",\"id\":\"" + BOB + "\"}");
		assertEquals(Live.Badge.NONE, live.badge(BOB_ID));
	}

	@Test
	void changesABadgeWhenTheServerSaysItChanged() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		live.apply("{\"type\":\"badge\",\"id\":\"" + ANN + "\",\"kind\":\"gold\"}");
		assertEquals(Live.Badge.GOLD, live.badge(ANN_ID));
	}

	@Test
	void ignoresABadgeChangeForSomeoneNotOnline() {
		Live live = new Live();
		live.apply(snapshot());
		live.apply("{\"type\":\"badge\",\"id\":\"" + ANN + "\",\"kind\":\"gold\"}");
		assertEquals(Live.Badge.NONE, live.badge(ANN_ID));
	}

	@Test
	void aNewSnapshotReplacesEveryoneHeardBefore() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		live.apply(snapshot(entry(BOB, "Bob", "grey")));
		assertEquals(Live.Badge.NONE, live.badge(ANN_ID));
		assertEquals(Live.Badge.GREY, live.badge(BOB_ID));
	}

	@Test
	void findsSomeoneOnlineByTheirNameInAnyCase() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		assertEquals(ANN_ID, live.named("aNN"));
		assertNull(live.named("Bob"));
	}

	@Test
	void findsSomeoneGreyByNameSoServerDrawnTabCellsGetTheirGreyMark() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Lauchy", "grey")));
		assertEquals(ANN_ID, live.named("Lauchy"));
		live.apply("{\"type\":\"join\"," + entry(BOB, "Bob", "grey").substring(1));
		assertEquals(BOB_ID, live.named("bob"));
		assertEquals(Live.Badge.GREY, live.badge(ANN_ID));
	}

	@Test
	void aLazarusUsersNameWinsOverSomeoneGreyClaimingIt() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Lauchy", "grey"), entry(BOB, "Lauchy", "purple")));
		assertEquals(BOB_ID, live.named("Lauchy"));
		live.apply(snapshot(entry(ANN, "Lauchy", "gold"), entry(BOB, "Lauchy", "grey")));
		assertEquals(ANN_ID, live.named("Lauchy"));
	}

	@Test
	void forgetsANameOnceItsPlayerLeaves() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		live.apply("{\"type\":\"leave\",\"id\":\"" + ANN + "\"}");
		assertNull(live.named("Ann"));
	}

	private static String caped(String id, String name, String kind, String cape) {
		return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"cape\":\"" + cape + "\"}";
	}

	@Test
	void showsNoCapeBeforeHearingAnything() {
		assertEquals("", new Live().cape(ANN_ID));
	}

	@Test
	void showsEachCapeFromTheSnapshot() {
		Live live = new Live();
		live.apply(snapshot(caped(ANN, "Ann", "gold", "halo"), entry(BOB, "Bob", "purple")));
		assertEquals("halo", live.cape(ANN_ID));
		assertEquals("", live.cape(BOB_ID));
	}

	@Test
	void showsTheCapeOfSomeoneWhoJoinsAndDropsItWhenTheyLeave() {
		Live live = new Live();
		live.apply(snapshot());
		live.apply("{\"type\":\"join\"," + caped(BOB, "Bob", "gold", "halo").substring(1));
		assertEquals("halo", live.cape(BOB_ID));
		live.apply("{\"type\":\"leave\",\"id\":\"" + BOB + "\"}");
		assertEquals("", live.cape(BOB_ID));
	}

	@Test
	void changesACapeWhenTheServerSaysItChangedAndKeepsTheBadge() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "gold")));
		live.apply("{\"type\":\"cape\",\"id\":\"" + ANN + "\",\"cape\":\"halo\"}");
		assertEquals("halo", live.cape(ANN_ID));
		assertEquals(Live.Badge.GOLD, live.badge(ANN_ID));
		live.apply("{\"type\":\"cape\",\"id\":\"" + ANN + "\",\"cape\":\"\"}");
		assertEquals("", live.cape(ANN_ID));
	}

	@Test
	void keepsTheCapeWhenTheBadgeChanges() {
		Live live = new Live();
		live.apply(snapshot(caped(ANN, "Ann", "gold", "halo")));
		live.apply("{\"type\":\"badge\",\"id\":\"" + ANN + "\",\"kind\":\"gold\"}");
		assertEquals("halo", live.cape(ANN_ID));
	}

	@Test
	void ignoresACapeChangeForSomeoneNotOnline() {
		Live live = new Live();
		live.apply(snapshot());
		live.apply("{\"type\":\"cape\",\"id\":\"" + ANN + "\",\"cape\":\"halo\"}");
		assertEquals("", live.cape(ANN_ID));
	}

	@Test
	void passesOverMessagesItDoesNotKnowSoTheServerCanAddMore() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		assertFalse(live.apply("{\"type\":\"somethingNew\",\"id\":\"" + ANN + "\"}"));
		assertFalse(live.apply("not json"));
		assertTrue(live.apply("{\"type\":\"badge\",\"id\":\"" + ANN + "\",\"kind\":\"gold\"}"));
		assertEquals(Live.Badge.GOLD, live.badge(ANN_ID));
	}

	@Test
	void handsOnYourOwnLazarusPlusUpdateWithItsReceiptAndMessages() {
		Live live = new Live();
		List<JsonObject> heard = new ArrayList<>();
		live.onPlus(heard::add);
		assertTrue(live.apply("{\"type\":\"plus\",\"active\":true,\"plusReceipt\":\"r.sig\",\"messages\":[{\"id\":\"welcome_1\",\"kind\":\"welcome\"}]}"));
		assertEquals(1, heard.size());
		assertEquals("r.sig", heard.get(0).get("plusReceipt").getAsString());
		assertEquals("welcome_1", heard.get(0).getAsJsonArray("messages").get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	void aLazarusPlusUpdateLeavesTheBadgesAlone() {
		Live live = new Live();
		live.apply(snapshot(entry(ANN, "Ann", "purple")));
		live.apply("{\"type\":\"plus\",\"active\":true}");
		assertEquals(Live.Badge.PURPLE, live.badge(ANN_ID));
		assertEquals(ANN_ID, live.named("Ann"));
	}

	@Test
	void handsOnYourOwnLazarusPlusFromTheSnapshotSoWhatChangedWhileDisconnectedArrives() {
		Live live = new Live();
		List<JsonObject> heard = new ArrayList<>();
		live.onPlus(heard::add);
		live.apply("{\"type\":\"snapshot\",\"online\":[" + entry(ANN, "Ann", "gold") + "],\"plusReceipt\":\"r.sig\",\"messages\":[]}");
		assertEquals(1, heard.size());
		assertEquals("r.sig", heard.get(0).get("plusReceipt").getAsString());
		assertEquals(Live.Badge.GOLD, live.badge(ANN_ID));
	}

	@Test
	void aSnapshotWithoutYourOwnLazarusPlusHandsNothingOn() {
		Live live = new Live();
		List<JsonObject> heard = new ArrayList<>();
		live.onPlus(heard::add);
		live.apply(snapshot(entry(ANN, "Ann", "grey")));
		assertTrue(heard.isEmpty());
	}

	@Test
	void aCheckInAnswerIsTakenLikeTheSnapshot() {
		Live live = new Live();
		List<JsonObject> heard = new ArrayList<>();
		live.onPlus(heard::add);
		live.apply(snapshot(entry(BOB, "Bob", "purple")));
		assertTrue(live.checkedIn("{\"online\":[" + entry(ANN, "Ann", "gold") + "],\"plusReceipt\":\"r.sig\"}"));
		assertEquals(Live.Badge.GOLD, live.badge(ANN_ID));
		assertEquals(Live.Badge.NONE, live.badge(BOB_ID));
		assertEquals("r.sig", heard.get(0).get("plusReceipt").getAsString());
		assertFalse(live.checkedIn("not json"));
	}

	@Test
	void countsEveryAnswerFromTheServerAsContact() {
		Live live = new Live();
		int[] contacts = {0};
		live.onContact(() -> contacts[0]++);
		live.reached();
		live.apply(snapshot());
		live.apply("{\"type\":\"somethingNew\"}");
		live.checkedIn("{\"online\":[]}");
		assertEquals(4, contacts[0]);
		live.apply("not json");
		live.checkedIn("not json");
		assertEquals(4, contacts[0]);
	}
}
