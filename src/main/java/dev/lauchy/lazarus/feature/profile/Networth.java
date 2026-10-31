package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.feature.general.ItemValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * A profile's networth as Lazarus values it. dragsim.co's networth ({@code /api/networth}) lists
 * every item the profile holds, with all its upgrades, and the site's own price for each; here
 * each is priced again by {@link ItemValue}: the clean item plus what the market actually pays
 * for its upgrades, where the site adds every upgrade at full price and gives stars and reforges
 * nothing. The site's price for the clean item is what its rows lend to items Lazarus has no
 * better price for. Pure, so it is tested without the game.
 *
 * @param total coins plus every priced item
 * @param site the site's own total
 * @param unpriced items neither could price
 * @param rows each item across all its stacks, most valuable first
 * @param missing the unpriced items by name, with how many of each, most first
 */
public record Networth(double total, double site, int unpriced, List<SiteNetworth.Row> rows, List<SiteNetworth.Row> missing) {
	/**
	 * One item in the site's list: the item in the site's shape, how many, the site's price for
	 * one clean one from sales or the bazaar (-1 when it gives none), and what it costs to craft by
	 * the site's reckoning (-1 when it didn't reckon).
	 */
	public record Held(JsonObject item, String name, long quantity, long siteBase, long siteCraft) {
	}

	/** Every item in the site's answer, with the clean price the site gives each; empty when hidden or not an answer. */
	public static List<Held> held(JsonObject site) {
		List<Held> out = new ArrayList<>();
		if (site == null || !(site.get("rows") instanceof com.google.gson.JsonArray rows)) return out;
		for (JsonElement e : rows) {
			if (!(e instanceof JsonObject r) || !(r.get("item") instanceof JsonObject item)) continue;
			String name = r.get("name") instanceof JsonElement n && n.isJsonPrimitive() ? n.getAsString() : "";
			out.add(new Held(item, name, Math.max(1, (long) num(r, "quantity")), siteBase(r, item), siteCraft(r, item)));
		}
		return out;
	}

	/**
	 * The site's price for the clean item in a row, from sales, the bazaar or a listing: the base
	 * it built an upgraded item's price on, or its price for an item with no upgrades; -1 when it
	 * priced neither, or priced it by an estimate ({@link #estimated}) Lazarus ranks lower itself.
	 */
	static long siteBase(JsonObject row, JsonObject item) {
		JsonObject v = clean(row, item);
		return v == null || estimated(v) ? -1 : Math.round(num(row.getAsJsonObject("valuation"), v.has("base_price") ? "base_price" : "price"));
	}

	/**
	 * What the site reckons the clean item in a row costs to craft, when that's how it priced it;
	 * -1 otherwise. Ranked after a sale, a listing, a fire sale and Lazarus's own recipe.
	 */
	static long siteCraft(JsonObject row, JsonObject item) {
		JsonObject v = clean(row, item);
		return v == null || !source(v).startsWith("Craft ingredient") ? -1 : Math.round(num(row.getAsJsonObject("valuation"), v.has("base_price") ? "base_price" : "price"));
	}

	/**
	 * The valuation of a row's clean item: the base valuation of an upgraded item (the row's own,
	 * with {@code base_price}, when the site gives no separate one), or the row's own for an
	 * item with no upgrades; null when the site priced the upgraded item without a base.
	 */
	private static JsonObject clean(JsonObject row, JsonObject item) {
		if (!(row.get("valuation") instanceof JsonObject v)) return null;
		if (v.has("base_price")) {
			JsonObject base = v.get("base_valuation") instanceof JsonObject b ? b : v;
			base = base.deepCopy();
			base.add("base_price", v.get("base_price")); // so the caller reads the base's price from the row
			return base;
		}
		return ItemValue.upgrades(item).isEmpty() ? v : null;
	}

	/**
	 * Whether a site valuation is an estimate rather than a price: the NPC floor, a gem-to-coin
	 * conversion or a craft cost. The site once put a Warden Helmet at 1.38M by one gem at its
	 * own rate, with a recipe costing 17.76B. Lazarus has its own NPC floor, fire sale prices and
	 * recipes, which rank after sales and listings; a craft cost is kept apart ({@link #siteCraft}).
	 */
	static boolean estimated(JsonObject valuation) {
		String source = source(valuation);
		return source.equals("NPC sell floor") || source.startsWith("Gem cost") || source.startsWith("Craft ingredient");
	}

	/** A valuation's {@code source}, or "". */
	private static String source(JsonObject valuation) {
		return valuation.get("source") instanceof JsonElement s && s.isJsonPrimitive() ? s.getAsString() : "";
	}

	/**
	 * The networth: the site's coins plus each held item at {@code value} (one item, its upgrades
	 * included; -1 when unknown), merged by name for the biggest-items list.
	 */
	public static Networth value(JsonObject site, List<Held> held, ToLongFunction<JsonObject> value) {
		double total = num(site, "coins_total");
		int unpriced = 0;
		Map<String, SiteNetworth.Row> byName = new LinkedHashMap<>(), missing = new LinkedHashMap<>();
		for (Held h : held) {
			long each = value.applyAsLong(h.item);
			String name = h.name.isEmpty() ? str(h.item, "entry") : h.name;
			if (each <= 0) {
				unpriced++;
				missing.merge(name, new SiteNetworth.Row(name, h.quantity, 0), (a, b) -> new SiteNetworth.Row(name, a.quantity() + b.quantity(), 0));
				continue;
			}
			double worth = (double) each * h.quantity;
			total += worth;
			byName.merge(name, new SiteNetworth.Row(name, h.quantity, worth), (a, b) -> new SiteNetworth.Row(name, a.quantity() + b.quantity(), a.value() + b.value()));
		}
		List<SiteNetworth.Row> rows = new ArrayList<>(byName.values());
		rows.sort((a, b) -> Double.compare(b.value(), a.value()));
		List<SiteNetworth.Row> unknown = new ArrayList<>(missing.values());
		unknown.sort((a, b) -> Long.compare(b.quantity(), a.quantity()));
		return new Networth(total, num(site, "total"), unpriced, rows, unknown);
	}

	/** The {@code limit} items worth most, biggest first. */
	public List<SiteNetworth.Row> top(int limit) {
		return rows.subList(0, Math.min(limit, rows.size()));
	}

	/** A number field, or 0 when it's missing or not a number. */
	private static double num(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e == null || !e.isJsonPrimitive() ? 0 : e.getAsDouble();
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** A text field, or "" when it's missing. */
	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
