package dev.lauchy.lazarus.core;

import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A self-contained feature: its own settings, its own listeners, nothing shared but the
 * services in {@code core}. Listeners are registered once in {@link #init()} and gate
 * themselves on {@link #enabled()}, so toggling never has to unregister anything.
 */
public abstract class Feature {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;

	private final BoolSetting enabled;
	private final KeySetting toggleKey;
	private final List<Setting<?>> settings = new ArrayList<>();

	protected Feature(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = new BoolSetting("enabled", "Enabled", "", enabledByDefault);
		this.toggleKey = new KeySetting("toggleKey", "Toggle key", "Press in game to switch " + name + " on or off.");
	}

	/** Called once at startup, after every feature exists and the config is loaded. */
	public abstract void init();

	/**
	 * On when its own switch and its category's master switch are both on, and it isn't waiting on Lazarus+.
	 * A Staff feature also needs the player to be Dragsim staff ({@link Staff}), so it never runs for anyone else.
	 */
	public boolean enabled() {
		return switchedOn() && !locked() && (!category.staffSection() || Staff.is());
	}

	/** Its own switch and its category's are on, whether or not it's waiting on Lazarus+. */
	public boolean switchedOn() {
		return enabled.on() && Categories.on(category);
	}

	/** A Lazarus+ feature: listed for everyone, but only runs while the account has Lazarus+. */
	public boolean plus() {
		return false;
	}

	/** Using it can get a player banned on Dragsim, so the menu marks it with a red BANNABLE badge. */
	public boolean bannable() {
		return false;
	}

	/** A Lazarus+ feature on an account without it. Its own switch is kept, so it comes back as it was. */
	public boolean locked() {
		return plus() && !Plus.unlocked();
	}

	public void setEnabled(boolean on) {
		if (on == enabled.on()) return;
		enabled.set(on);
		if (!Categories.on(category) || locked()) return; // takes effect when the category is switched back on
		if (on) onEnable();
		else onDisable();
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** The settings shown on this feature's page, in order. */
	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public BoolSetting enabledSetting() {
		return enabled;
	}

	public KeySetting toggleKey() {
		return toggleKey;
	}
}
