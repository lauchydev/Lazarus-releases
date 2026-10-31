package dev.lauchy.lazarus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Features starred in the menu, shown together under Favourites in the order they were starred. */
public final class Favourites {
	private static final Set<String> IDS = new LinkedHashSet<>();

	private Favourites() {
	}

	public static boolean has(Feature f) {
		return IDS.contains(f.id);
	}

	public static void toggle(Feature f) {
		if (!IDS.remove(f.id)) IDS.add(f.id);
	}

	public static boolean isEmpty() {
		return IDS.isEmpty();
	}

	public static List<Feature> features() {
		return IDS.stream().map(Features::byId).filter(java.util.Objects::nonNull).toList();
	}

	static JsonElement save() {
		JsonArray out = new JsonArray();
		IDS.forEach(out::add);
		return out;
	}

	static void load(JsonElement json) {
		IDS.clear();
		if (json == null || !json.isJsonArray()) return;
		for (JsonElement e : json.getAsJsonArray()) if (e.isJsonPrimitive()) IDS.add(e.getAsString());
	}
}
