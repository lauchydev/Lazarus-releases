package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** One of an enum's values; clicking cycles through them. */
public final class ChoiceSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] options;

	public ChoiceSetting(String key, String name, String description, E fallback) {
		super(key, name, description, fallback);
		this.options = fallback.getDeclaringClass().getEnumConstants();
	}

	public void cycle(int direction) {
		value = options[Math.floorMod(value.ordinal() + direction, options.length)];
	}

	public String display() {
		String n = value.name().replace('_', ' ').toLowerCase();
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void load(JsonElement json) {
		if (!json.isJsonPrimitive()) return;
		for (E e : options) if (e.name().equals(json.getAsString())) value = e;
	}
}
