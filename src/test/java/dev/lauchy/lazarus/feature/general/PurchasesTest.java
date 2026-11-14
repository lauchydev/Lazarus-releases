package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchasesTest {
	/** 9 Nov 2026, 12:00 UTC. */
	private static final long NOON = 1_794_225_600_000L;

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	/** One sale as dragsim.co's feeds give it (2026-11), trimmed to what matters. */
	private static String sale(String entry, int count, String buyer, long price, long at, String modifiers) {
		return "{\"id\":\"s" + at + "\",\"item\":{\"entry\":\"" + entry + "\",\"count\":" + count + ",\"item_modifiers\":{" + modifiers + "}},"
				+ "\"seller\":\"Alex\",\"buyer\":\"" + buyer + "\",\"price\":" + price + ",\"unit_price\":" + price / count + ",\"sold_at\":" + at + "}";
	}

	private static JsonObject feed(String... sales) {
		return json("{\"sales\":[" + String.join(",", sales) + "]}");
	}

	private static JsonObject item(String entry, String modifiers) {
		return json("{\"entry\":\"" + entry + "\",\"item_modifiers\":{" + modifiers + "}}");
	}

	@Test
	void whatYouPaidIsReadFromTheSalesYouWereTheBuyerOf() {
		Purchases p = new Purchases();
		assertTrue(p.read(feed(sale("bal_pet_epic", 1, "Lauchy", 76_000_000, NOON, ""), sale("glacial_scythe", 1, "Steve", 9_000_000, NOON, "")), "lauchy"));

		Purchases.Paid paid = p.paid(item("bal_pet_epic", ""), null);
		assertEquals(76_000_000, paid.unit());
		assertEquals("9 Nov 2026", paid.day(ZoneOffset.UTC));
		assertNull(p.paid(item("glacial_scythe", ""), null), "someone else's purchase isn't yours");
	}

	@Test
	void aStackIsPricedForOneOfIt() {
		Purchases p = new Purchases();
		p.read(feed(sale("summoning_eye", 4, "Lauchy", 2_000_000, NOON, "")), "Lauchy");
		assertEquals(500_000, p.paid(item("summoning_eye", ""), null).unit());
	}

	@Test
	void theNewestPurchaseOfAnItemStandsAndReadingItAgainChangesNothing() {
		Purchases p = new Purchases();
		JsonObject feed = feed(sale("bal_pet_epic", 1, "Lauchy", 80_000_000, NOON, ""), sale("bal_pet_epic", 1, "Lauchy", 70_000_000, NOON - 86_400_000L, ""));
		assertTrue(p.read(feed, "Lauchy"));
		assertFalse(p.read(feed, "Lauchy"));
		assertEquals(80_000_000, p.paid(item("bal_pet_epic", ""), null).unit());
	}

	@Test
	void aCopyWithOtherUpgradesIsADifferentItem() {
		Purchases p = new Purchases();
		p.read(feed(sale("glacial_scythe", 1, "Lauchy", 30_000_000, NOON, "\"hot_potato_books\":10")), "Lauchy");
		assertEquals(30_000_000, p.paid(item("glacial_scythe", "\"hot_potato_books\":10"), null).unit());
		assertNull(p.paid(item("glacial_scythe", ""), null));
	}

	@Test
	void aPriceTiedToACopyStaysWithItThroughLaterUpgrades() {
		Purchases p = new Purchases();
		p.read(feed(sale("glacial_scythe", 1, "Lauchy", 30_000_000, NOON, "")), "Lauchy");
		assertTrue(p.tie(item("glacial_scythe", ""), "1b0f2c3d-0000-4000-8000-000000000001"));
		assertFalse(p.tie(item("glacial_scythe", ""), "1b0f2c3d-0000-4000-8000-000000000001"));

		JsonObject upgraded = item("glacial_scythe", "\"hot_potato_books\":10");
		assertEquals(30_000_000, p.paid(upgraded, "1b0f2c3d-0000-4000-8000-000000000001").unit());
		assertNull(p.paid(upgraded, null), "a copy the server gave no UUID can't be followed");
		assertFalse(p.tie(upgraded, null));
	}

	@Test
	void purchasesComeBackFromWhatWasSaved() {
		Purchases p = new Purchases();
		p.read(feed(sale("bal_pet_epic", 1, "Lauchy", 76_000_000, NOON, "")), "Lauchy");
		Purchases back = new Purchases();
		back.load(json(p.save().toString()));
		assertEquals(new Purchases.Paid(76_000_000, NOON), back.paid(item("bal_pet_epic", ""), null));
	}

	@Test
	void aFeedWithNoSalesOrNoNameTeachesNothing() {
		Purchases p = new Purchases();
		assertFalse(p.read(json("{\"products\":[]}"), "Lauchy"));
		assertFalse(p.read(feed(sale("bal_pet_epic", 1, "", 76_000_000, NOON, "")), ""));
	}
}
