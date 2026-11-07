package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dragsim.co's own networth for a profile ({@code /api/networth/<name>?profile=<id>&mode=main}),
 * the figure the site's profile page shows. It prices upgrades, recipes and pets from market
 * history, which the local estimate can't, so the profile viewer prefers it. Pure, so it is
 * tested without the game.
 */
public record SiteNetworth(double total, boolean hidden, boolean partial, int unpriced, List<Row> rows) {
	/** One item across all its stacks: its name, how many, and what they're worth together. */
	public record Row(String name, long quantity, double value) {
	}

	/** The site's answer read, or null when it isn't one. */
	public static SiteNetworth read(JsonObject o) {
		if (o == null) return null;
		boolean hidden = bool(o, "hidden");
		if (!hidden && !(o.get("total") instanceof JsonElement t && t.isJsonPrimitive())) return null;
		Map<String, Row> byName = new LinkedHashMap<>();
		if (o.has("rows") && o.get("rows").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("rows")) {
				if (!e.isJsonObject()) continue;
				JsonObject r = e.getAsJsonObject();
				double value = num(r, "value");
				String name = r.has("name") && r.get("name").isJsonPrimitive() ? r.get("name").getAsString() : "";
				if (value <= 0 || name.isEmpty()) continue;
				long quantity = Math.max(1, (long) num(r, "quantity"));
				byName.merge(name, new Row(name, quantity, value), (a, b) -> new Row(name, a.quantity + b.quantity, a.value + b.value));
			}
		}
		List<Row> rows = new ArrayList<>(byName.values());
		rows.sort((a, b) -> Double.compare(b.value, a.value));
		return new SiteNetworth(hidden ? 0 : num(o, "total"), hidden, bool(o, "partial"), (int) num(o, "unpriced"), rows);
	}

	/** The {@code limit} items worth most, biggest first. */
	public List<Row> top(int limit) {
		return rows.subList(0, Math.min(limit, rows.size()));
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || !e.isJsonPrimitive() ? 0 : e.getAsDouble();
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}
}
