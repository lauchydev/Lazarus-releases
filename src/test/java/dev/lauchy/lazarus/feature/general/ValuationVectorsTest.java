package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The valuation cases the worker's pricing.js answers too (valuation/vectors.json): the Valuer must
 * give the same value, source, confidence and upgrades for each, so the Discord bot and /l agree.
 */
class ValuationVectorsTest {
	private static final Pattern PET = Pattern.compile("(.+)_pet_(common|uncommon|rare|epic|legendary|mythic)");

	@TestFactory
	List<DynamicTest> everyCaseValuesAsTheWorkerDoes() throws Exception {
		JsonObject file;
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/valuation/vectors.json")), StandardCharsets.UTF_8)) {
			file = JsonParser.parseReader(in).getAsJsonObject();
		}
		long now = file.get("now").getAsLong();
		List<DynamicTest> tests = new ArrayList<>();
		for (JsonElement e : file.getAsJsonArray("cases")) {
			JsonObject c = e.getAsJsonObject();
			tests.add(DynamicTest.dynamicTest(c.get("name").getAsString(), () -> {
				Valuer valuer = new Valuer(new Snapshot(obj(c, "known"), now));
				JsonObject row = c.get("row") instanceof JsonObject r ? r : null;
				JsonObject item = row != null ? row.getAsJsonObject("item") : c.getAsJsonObject("item");
				long quantity = row != null && row.has("quantity") ? row.get("quantity").getAsLong() : 1;
				String owner = c.has("owner") ? c.get("owner").getAsString() : "";
				Valuer.Valuation v = valuer.value(item, row == null ? null : Valuer.Site.of(row), owner, quantity);
				JsonObject want = c.getAsJsonObject("expect");
				assertEquals(want.get("unit").getAsLong(), v.unit(), "unit");
				assertEquals(want.get("total").getAsLong(), v.total(), "total");
				assertEquals(want.get("source").getAsString(), v.source(), "source");
				assertEquals(want.get("confidence").isJsonNull() ? null : want.get("confidence").getAsString(), v.confidence(), "confidence");
				assertEquals(want.get("kind").getAsString(), v.kind(), "kind");
				assertEquals(want.get("past").getAsLong(), v.past(), "past");
				List<String> parts = new ArrayList<>();
				for (JsonElement p : want.getAsJsonArray("parts")) parts.add(p.getAsJsonObject().get("kind").getAsString() + "=" + p.getAsJsonObject().get("value").getAsLong());
				assertEquals(parts, v.parts().stream().map(p -> p.kind().key + "=" + p.value()).toList(), "parts");
			}));
		}
		return tests;
	}

	/** What a case knows, read from its JSON the way pricing.js reads the same object. */
	private record Snapshot(JsonObject k, long now) implements Known {
		@Override
		public String alias(String id) {
			if (id.startsWith("~")) id = id.substring(1);
			if (knows(id)) return id;
			Matcher m = PET.matcher(id);
			if (!m.matches() || !(obj(k, "pets").get(m.group(1)) instanceof JsonElement pet)) return id;
			String named = pet.getAsString().toLowerCase(Locale.ROOT).replaceAll("§.", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "") + "_pet_" + m.group(2);
			return knows(named) ? named : id;
		}

		private boolean knows(String id) {
			return obj(k, "npc").has(id) || obj(k, "items").has(id) || obj(k, "names").entrySet().stream().anyMatch(e -> e.getValue().getAsString().equals(id));
		}

		@Override
		public long npc(String id) {
			if (num(obj(k, "npc"), id) > 0) return (long) num(obj(k, "npc"), id);
			if (obj(k, "items").get(id) instanceof JsonArray a && a.size() > 2 && a.get(2).getAsDouble() > 0) return a.get(2).getAsLong();
			Matcher m = PET.matcher(id);
			if (!m.matches()) return -1;
			JsonObject byType = obj(k, "petNpc");
			String type = byType.has(m.group(1)) ? m.group(1) : obj(k, "pets").keySet().stream().filter(t -> alias(t + "_pet_" + m.group(2)).equals(id)).findFirst().orElse("");
			return num(obj(byType, type), m.group(2)) > 0 ? (long) num(obj(byType, type), m.group(2)) : -1;
		}

		@Override
		public Fresh prices(String id) {
			JsonObject entry = obj(k, "prices").get(id) instanceof JsonObject o ? o : null;
			return entry == null ? Fresh.NONE : Fresh.of(entry, (long) num(entry, "at"), now, siteBase(id));
		}

		@Override
		public long manual(String id) {
			double p = num(obj(obj(k, "manual"), id), "price");
			return p > 0 ? (long) p : -1;
		}

		@Override
		public boolean forced(String id) {
			JsonElement f = obj(obj(k, "manual"), id).get("force");
			return f != null && f.getAsBoolean();
		}

		@Override
		public List<Traded> trades(String id) {
			List<Traded> out = new ArrayList<>();
			if (obj(obj(k, "trades"), id).get("p") instanceof JsonArray list) {
				for (JsonElement t : list) {
					JsonArray a = t.getAsJsonArray();
					out.add(new Traded(a.get(0).getAsLong(), a.get(1).getAsLong(), a.size() > 3 ? a.get(3).getAsString() : ""));
				}
			}
			return out;
		}

		@Override
		public long fireSale(String id) {
			JsonObject sale = obj(obj(k, "firesale"), id);
			double gems = num(sale, "gems");
			if (gems <= 1 || sale.has("buyers") && num(sale, "buyers") == 0) return -1;
			long rate = num(k, "gemCoins") > 0 ? (long) num(k, "gemCoins") : FireSale.COINS_PER_GEM;
			return (long) gems * rate;
		}

		@Override
		public List<ItemValue.Recipe> recipes(String id) {
			ItemValue.Recipe shipped = ItemValue.recipes().get(id);
			return shipped == null ? List.of() : List.of(shipped);
		}

		@Override
		public List<String> stones(String reforge) {
			List<String> out = new ArrayList<>();
			if (obj(k, "stones").get(reforge) instanceof JsonArray a) for (JsonElement s : a) out.add(s.getAsString());
			return out;
		}

		@Override
		public long siteBase(String id) {
			return num(obj(k, "siteBase"), id) > 0 ? (long) num(obj(k, "siteBase"), id) : -1;
		}

		@Override
		public long siteCraft(String id) {
			return num(obj(k, "siteCraft"), id) > 0 ? (long) num(obj(k, "siteCraft"), id) : -1;
		}

		@Override
		public List<Listing> listings(String id) {
			List<Listing> out = new ArrayList<>();
			if (obj(k, "listings").get(id) instanceof JsonArray a) {
				for (JsonElement e : a) {
					JsonObject l = e.getAsJsonObject();
					out.add(new Listing(l.get("price").getAsLong(), ItemValue.variant(l.getAsJsonObject("item")), l.get("seller").getAsString()));
				}
			}
			return out;
		}

		@Override
		public ItemValue.Model shares() {
			return k.get("shares") instanceof JsonObject s ? ItemValue.Model.shares(s) : ItemValue.model();
		}
	}

	private static JsonObject obj(JsonObject o, String key) {
		return o != null && o.get(key) instanceof JsonObject x ? x : new JsonObject();
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
	}
}
