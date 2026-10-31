package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number on a slider, clamped to its range. */
public final class IntSetting extends Setting<Integer> {
	public final int min, max;
	public final String unit;

	public IntSetting(String key, String name, String description, int fallback, int min, int max, String unit) {
		super(key, name, description, fallback);
		this.min = min;
		this.max = max;
		this.unit = unit;
	}

	@Override
	public void set(Integer v) {
		super.set(Math.clamp(v, min, max));
	}

	public String display() {
		return value + unit;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
	}
}
