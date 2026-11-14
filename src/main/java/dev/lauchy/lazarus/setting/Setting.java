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
	/** Whether only a Lazarus+ member may use it: the menu shows it locked to anyone else, and the feature leaves it be. */
	private boolean plus;

	protected Setting(String key, String name, String description, T fallback) {
		this.key = key;
		this.name = name;
		this.description = description;
		this.fallback = fallback;
		this.value = fallback;
	}

	/** Marks this setting as Lazarus+ only (a bind past the free allowance, say); returns it, for chaining onto {@code add}. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<?>> S plusOnly() {
		plus = true;
		return (S) this;
	}

	/** Whether only a Lazarus+ member may use it. */
	public boolean plus() {
		return plus;
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
