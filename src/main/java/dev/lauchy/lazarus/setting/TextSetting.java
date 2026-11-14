package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of text, edited in place in the menu. */
public final class TextSetting extends Setting<String> {
	public final int maxLength;

	public TextSetting(String key, String name, String description, String fallback, int maxLength) {
		super(key, name, description, fallback);
		this.maxLength = maxLength;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement json) {
		if (json.isJsonPrimitive()) value = json.getAsString();
	}
}
