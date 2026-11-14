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
	 * A Staff feature also needs the player to be Dragsim staff ({@link Staff}), and one of the owner's own
	 * ({@link #ownerOnly}) the mod's owner, so it never runs for anyone else.
	 */
	public boolean enabled() {
		return switchedOn() && !locked() && allowed();
	}

	/**
	 * Whether this player may use it at all: the owner's own features only the mod's owner, whatever their rank
	 * or Lazarus+; other Staff features only Dragsim staff; the rest anyone.
	 */
	public boolean allowed() {
		if (ownerOnly()) return LazarusServer.admin();
		return !category.staffSection() || Staff.is();
	}

	/** One of the mod owner's own features: listed, and run, for the owner alone (the worker's ADMINS accounts). */
	public boolean ownerOnly() {
		return false;
	}

	/**
	 * Whether a tracker counts: always, switched on or off, shown or hidden, so its tallies are right
	 * whenever its readout is brought back. Only what stops it running at all stops it counting:
	 * waiting on Lazarus+, or a feature this player isn't allowed ({@link #allowed}).
	 */
	protected boolean counting() {
		return !locked() && allowed();
	}

	/** Its own switch and its category's are on, whether or not it's waiting on Lazarus+. */
	public boolean switchedOn() {
		return enabled.on() && Categories.on(category);
	}

	/** A Lazarus+ feature: listed for everyone, but only runs while the account has Lazarus+. */
	public boolean plus() {
		return false;
	}

	/** A chat line from this feature in the middle of play, behind the short name: "LZ >", or "LZ+ >" from a Lazarus+ feature. */
	protected Message say() {
		return Message.brief(plus());
	}

	/** A Lazarus+ feature on an account without it. Its own switch is kept, so it comes back as it was. */
	public boolean locked() {
		return plus() && !Plus.unlocked();
	}

	public void setEnabled(boolean on) {
		if (on == enabled.on()) return;
		enabled.set(on);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** The settings shown on this feature's page, in order. */
	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	/**
	 * Runs just before this feature's saved settings are loaded, with what was saved for it, so a feature whose
	 * settings grow in number (Command Binds) can add the ones the file holds before they're read.
	 */
	protected void loading(com.google.gson.JsonObject saved) {
	}

	public BoolSetting enabledSetting() {
		return enabled;
	}

	public KeySetting toggleKey() {
		return toggleKey;
	}
}
