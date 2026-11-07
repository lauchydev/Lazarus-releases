package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteNetworthTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void readsTheTotalAndListsItemsBiggestFirstWithStacksAddedTogether() {
		SiteNetworth w = SiteNetworth.read(json("""
				{"hidden":false,"partial":true,"total":149012173139.2,"unpriced":12,"rows":[
				 {"name":"Warden Catalyst","quantity":20,"value":3.0e9},
				 {"name":"Glacial Scythe","quantity":1,"value":2.0e10},
				 {"name":"Warden Catalyst","quantity":10,"value":2.0e9},
				 {"name":"Dirt","quantity":64,"value":0}]}"""));
		assertEquals(149012173139.2, w.total(), 1);
		assertTrue(w.partial());
		assertEquals(12, w.unpriced());
		assertEquals(2, w.rows().size());
		assertEquals("Glacial Scythe", w.top(1).getFirst().name());
		assertEquals(new SiteNetworth.Row("Warden Catalyst", 30, 5.0e9), w.rows().get(1));
	}

	@Test
	void aHiddenInventoryHasNoTotal() {
		SiteNetworth w = SiteNetworth.read(json("{\"hidden\":true}"));
		assertTrue(w.hidden());
		assertEquals(0, w.rows().size());
	}

	@Test
	void anErrorIsNotANetworth() {
		assertNull(SiteNetworth.read(json("{\"error\":\"Unable to load this profile.\"}")));
	}
}
