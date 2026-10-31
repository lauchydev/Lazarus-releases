package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String key, String name, String description, boolean fallback) {
		super(key, name, description, fallback);
	}

	public boolean on() {
		return value;
	}

	public void toggle() {
		value = !value;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) value = json.getAsBoolean();
	}
}
