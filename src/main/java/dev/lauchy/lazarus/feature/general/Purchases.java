package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What you paid for the items you bought at auction, read from dragsim.co's record of sales: each sale names its
 * buyer, so yours are picked out of every feed Lazarus reads anyway. A sale doesn't say which copy of an item it
 * was, so a purchase is kept by its {@link ItemValue#variant variant} (the item and exactly its upgrades), the
 * newest one standing; the first time an item with that variant is looked at, the price is tied to that copy's own
 * UUID when the server gave it one, so it stays with it through later upgrades. No game types, so it's tested on
 * its own.
 */
public final class Purchases {
	/** The date a purchase is shown with: "9 Nov 2026". */
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
	/** The key a price tied to one copy is kept under, before its UUID. */
	private static final String OWN = "u:";

	/** One purchase: what one of the item cost, and when it sold (epoch milliseconds). */
	public record Paid(long unit, long at) {
		/** The day it was bought, in this computer's time zone. */
		public String day(ZoneId zone) {
			return DATE.format(Instant.ofEpochMilli(at).atZone(zone));
		}
	}

	/** Purchases by variant, and by "u:" + UUID for the copies a price was tied to. */
	private final Map<String, Paid> paid = new HashMap<>();

	/**
	 * Takes in one of dragsim.co's feeds (the market's, or one item's history): every sale in it bought by
	 * {@code me} is remembered, the newest of a variant standing. True when anything new was learned.
	 */
	public boolean read(JsonObject feed, String me) {
		if (me == null || me.isEmpty() || !(feed.get("sales") instanceof com.google.gson.JsonArray sales)) return false;
		boolean changed = false;
		for (JsonElement e : sales) {
			if (!(e instanceof JsonObject sale) || !me.equalsIgnoreCase(str(sale, "buyer"))) continue;
			if (!(sale.get("item") instanceof JsonObject item) || str(item, "entry").isEmpty()) continue;
			long at = time(sale.get("sold_at")), count = Math.max(1, num(item, "count"));
			long unit = num(sale, "unit_price") > 0 ? num(sale, "unit_price") : num(sale, "price") / count;
			if (at <= 0 || unit <= 0) continue;
			String variant = ItemValue.variant(item);
			Paid known = paid.get(variant);
			if (known != null && known.at >= at) continue;
			paid.put(variant, new Paid(unit, at));
			changed = true;
		}
		return changed;
	}

	/**
	 * What you paid for this item, or null when you didn't buy it at auction (or Lazarus wasn't there to see).
	 * {@code uuid} is the copy's own UUID, or null when the server gave it none.
	 */
	public Paid paid(JsonObject item, String uuid) {
		Paid own = uuid == null ? null : paid.get(OWN + uuid);
		return own != null ? own : paid.get(ItemValue.variant(item));
	}

	/**
	 * Ties the price paid for this item's variant to this copy, so it's still shown once the copy is upgraded.
	 * A newer purchase of the same variant takes the copy's place. True when that changed anything.
	 */
	public boolean tie(JsonObject item, String uuid) {
		Paid bought = uuid == null ? null : paid.get(ItemValue.variant(item));
		if (bought == null || bought.equals(paid.get(OWN + uuid))) return false;
		Paid own = paid.get(OWN + uuid);
		if (own != null && own.at >= bought.at) return false;
		paid.put(OWN + uuid, bought);
		return true;
	}

	/** Every purchase as it's saved: {key: {unit, at}}. */
	public JsonObject save() {
		JsonObject root = new JsonObject();
		paid.forEach((key, p) -> {
			JsonObject o = new JsonObject();
			o.addProperty("unit", p.unit);
			o.addProperty("at", p.at);
			root.add(key, o);
		});
		return root;
	}

	/** Reads back what {@link #save} wrote, skipping anything that isn't a purchase. */
	public void load(JsonObject root) {
		for (var e : root.entrySet()) {
			if (e.getValue() instanceof JsonObject o && num(o, "unit") > 0 && num(o, "at") > 0) paid.put(e.getKey(), new Paid(num(o, "unit"), num(o, "at")));
		}
	}

	/** A sale's time as epoch milliseconds, from the site's milliseconds or seconds; 0 when it isn't a number. */
	private static long time(JsonElement e) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return 0;
		double v = e.getAsDouble();
		return (long) (v < 1e11 ? v * 1000 : v);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static long num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
	}
}
