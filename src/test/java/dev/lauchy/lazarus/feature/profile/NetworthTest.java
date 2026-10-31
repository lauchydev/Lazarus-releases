package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworthTest {
	private static final JsonObject SITE = JsonParser.parseString("""
			{"total":9.0e9,"coins_total":1.0e9,"rows":[
			 {"name":"Giant's Sword","quantity":1,"item":{"entry":"giants_sword","item_modifiers":{"recombobulated":true}},
			  "valuation":{"price":8.3e9,"source":"Base item + known upgrades estimate","base_price":7.5e9}},
			 {"name":"Hot Potato Book","quantity":3,"item":{"entry":"hot_potato_book","item_modifiers":{}},"valuation":{"price":2.0e7}},
			 {"name":"Hot Potato Book","quantity":2,"item":{"entry":"hot_potato_book","item_modifiers":{}},"valuation":{"price":2.0e7}},
			 {"name":"Necron's Helmet","quantity":1,"item":{"entry":"necron_helmet","item_modifiers":{"star_level":5}},"valuation":{"price":5.5e9}},
			 {"name":"Mystery","quantity":1,"item":{"entry":"mystery"},"valuation":null},
			 {"name":"Warden Helmet","quantity":1,"item":{"entry":"warden_helmet","item_modifiers":{}},
			  "valuation":{"price":1384615.3,"source":"Base item + known upgrades estimate","estimated":true,"base_price":1384615.3,
			   "base_valuation":{"price":1384615.3,"source":"Gem cost conversion estimate","estimated":true,"gem_cost":1}}},
			 {"name":"Rotten Flesh","quantity":1,"item":{"entry":"rotten_flesh","item_modifiers":{}},"valuation":{"price":2,"source":"NPC sell floor","confidence":"Floor"}},
			 {"name":"Necron's Chestplate","quantity":1,"item":{"entry":"necron_chestplate","item_modifiers":{"hot_potato_books":10}},
			  "valuation":{"price":5.8e9,"source":"Base item + known upgrades estimate","estimated":true,"base_price":5.6e9,
			   "base_valuation":{"price":5.6e9,"source":"Craft ingredient cost estimate","estimated":true}}}]}""").getAsJsonObject();

	@Test
	void readsEachItemWithTheCleanPriceTheSiteGivesIt() {
		List<Networth.Held> held = Networth.held(SITE);
		assertEquals(8, held.size());
		assertEquals(7_500_000_000L, held.get(0).siteBase()); // the base it built the upgraded price on
		assertEquals(20_000_000L, held.get(1).siteBase()); // its price, since there are no upgrades
		assertEquals(-1, held.get(3).siteBase()); // an upgraded price with no base says nothing of the clean item
		assertEquals(-1, held.get(4).siteBase());
		assertEquals(-1, held.get(5).siteBase()); // a gem-to-coin guess (1 gem = 1.38M) isn't a price; Lazarus prices it by its recipe
		assertEquals(-1, held.get(6).siteBase()); // the NPC floor is Lazarus's own last resort, not evidence
		assertEquals(-1, held.get(7).siteBase()); // the site's own recipe costing isn't a price either...
		assertEquals(5_600_000_000L, held.get(7).siteCraft()); // ...but is kept, for after Lazarus's own recipe
		assertEquals(-1, held.get(0).siteCraft());
		assertEquals(-1, held.get(5).siteCraft());
		assertEquals(3, held.get(1).quantity());
	}

	@Test
	void addsCoinsAndEachPricedItemAndMergesStacksByName() {
		Networth n = Networth.value(SITE, Networth.held(SITE), item -> switch (item.get("entry").getAsString()) {
			case "giants_sword" -> 8_000_000_000L;
			case "hot_potato_book" -> 20_000_000L;
			default -> -1L;
		});
		assertEquals(1.0e9 + 8.0e9 + 5 * 2.0e7, n.total(), 1);
		assertEquals(9.0e9, n.site(), 1);
		assertEquals(5, n.unpriced()); // Necron's Helmet, Mystery and the three guessed rows
		assertEquals(List.of("Necron's Helmet", "Mystery", "Warden Helmet", "Rotten Flesh", "Necron's Chestplate"),
				n.missing().stream().map(SiteNetworth.Row::name).toList()); // shown on hover, so they can be priced
		assertEquals("Giant's Sword", n.top(1).getFirst().name());
		assertEquals(new SiteNetworth.Row("Hot Potato Book", 5, 1.0e8), n.rows().get(1));
	}
}
