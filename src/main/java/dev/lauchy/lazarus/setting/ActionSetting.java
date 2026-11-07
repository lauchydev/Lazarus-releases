package dev.lauchy.lazarus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * A button among a feature's settings: pressing it runs something (a preview, say). It holds no
 * value, so nothing is saved or loaded for it.
 */
public final class ActionSetting extends Setting<Void> {
	private final String label;
	private final Runnable action;

	/** {@code label} is on the button; {@code name} and {@code description} read as for any setting. */
	public ActionSetting(String key, String name, String description, String label, Runnable action) {
		super(key, name, description, null);
		this.label = label;
		this.action = action;
	}

	public String label() {
		return label;
	}

	public void press() {
		action.run();
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(JsonElement json) {
	}
}
