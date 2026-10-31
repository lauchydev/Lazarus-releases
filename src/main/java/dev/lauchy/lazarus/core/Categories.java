package dev.lauchy.lazarus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.EnumSet;
import java.util.Set;

/**
 * The menu's master switch per category. A category switched off stops every feature in it
 * without touching their own switches, so turning it back on restores them as they were.
 */
public final class Categories {
	private static final Set<Category> OFF = EnumSet.noneOf(Category.class);

	private Categories() {
	}

	public static boolean on(Category c) {
		return !OFF.contains(c);
	}

	public static void toggle(Category c) {
		if (!OFF.remove(c)) OFF.add(c);
	}

	static JsonElement save() {
		JsonArray out = new JsonArray();
		OFF.forEach(c -> out.add(c.name()));
		return out;
	}

	static void load(JsonElement json) {
		OFF.clear();
		if (json == null || !json.isJsonArray()) return;
		for (JsonElement e : json.getAsJsonArray()) {
			for (Category c : Category.values()) if (e.isJsonPrimitive() && c.name().equals(e.getAsString())) OFF.add(c);
		}
	}
}
