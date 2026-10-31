package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;

/**
 * One saved, user-editable value on a feature. Settings know how to persist themselves;
 * the menu knows how to draw each kind.
 */
public abstract class Setting<T> {
	public final String key;
	public final String name;
	public final String description;
	private final T fallback;
	protected T value;

	protected Setting(String key, String name, String description, T fallback) {
		this.key = key;
		this.name = name;
		this.description = description;
		this.fallback = fallback;
		this.value = fallback;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		value = fallback;
	}

	public abstract JsonElement save();

	/** Restores a saved value, keeping the current one if the saved shape is wrong. */
	public abstract void load(JsonElement json);
}
