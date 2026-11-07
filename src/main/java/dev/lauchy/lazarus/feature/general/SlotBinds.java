package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The binds of a feature that picks from a server menu (pet and wardrobe binds): each bind is a key and the slot it
 * picks, so J can pick slot 4 without binds set for slots 1 to 3. The list grows as {@link Binds} does, and the
 * first {@link #FREE} binds are everyone's, the rest Lazarus+, counted by bind whatever slot it picks. A bind's slot
 * starts at the bind's own number, so a bind saved before slots could be chosen picks the slot it always did. No
 * Minecraft types, so it's tested on its own.
 */
final class SlotBinds {
	/** How many binds everyone gets; the rest are Lazarus+. */
	static final int FREE = 2;
	/** The highest slot a bind can pick: a few pages of the menu, with the slider still fine enough to land on each. */
	static final int MOST = 50;

	private final String prefix, label, keyHelp, slotHelp;
	/** A bind's key as saved, numbered from 1: "pet3Key". */
	private final Pattern saved;
	/** Where each new setting goes: the feature's own list, so the menu shows it and the config saves it. */
	private final Consumer<Setting<?>> add;
	private final List<KeySetting> keys = new ArrayList<>();
	private final List<IntSetting> slots = new ArrayList<>();
	/** Whether each bind's key was down last tick, so holding it picks once. */
	private final List<Boolean> held = new ArrayList<>();

	/**
	 * Binds saved as "{prefix}3Key" and "{prefix}3Slot", named "{label} 3 key" and "{label} 3 slot" with these
	 * descriptions, each setting handed to {@code add} as it's made. Starts with one bind.
	 */
	SlotBinds(String prefix, String label, String keyHelp, String slotHelp, Consumer<Setting<?>> add) {
		this.prefix = prefix;
		this.label = label;
		this.keyHelp = keyHelp;
		this.slotHelp = slotHelp;
		this.add = add;
		this.saved = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d{1,6})Key$");
		more();
	}

	/** Adds the next bind, its slot at its own number; past the allowance it's Lazarus+. */
	private void more() {
		int i = keys.size(), n = i + 1;
		KeySetting key = new KeySetting(prefix + n + "Key", label + " " + n + " key", keyHelp);
		IntSetting slot = new IntSetting(prefix + n + "Slot", label + " " + n + " slot", slotHelp, Math.min(n, MOST), 1, MOST, "");
		if (Binds.plus(i, FREE)) {
			key.plusOnly();
			slot.plusOnly();
		}
		add.accept(key);
		add.accept(slot);
		keys.add(key);
		slots.add(slot);
		held.add(false);
	}

	/** Adds another bind once the last one's key is set, so there's always an empty one to fill. */
	void follow() {
		if (keys.getLast().bound()) more();
	}

	/** Adds the binds a saved file holds, up to the last one with its key set, before their values are read. */
	void loading(JsonObject file) {
		// Saved keys count from 1 where Binds counts from 0, so its count comes out one too many.
		int want = Binds.saved(file, saved) - 1;
		while (keys.size() < Math.max(1, want)) more();
	}

	/** How many binds there are, the empty one at the end included. */
	int size() {
		return keys.size();
	}

	/** Every bind's key, in order. */
	List<KeySetting> keys() {
		return Collections.unmodifiableList(keys);
	}

	/** The slot bind {@code i} (from 0) picks, counted from 1. */
	int slot(int i) {
		return slots.get(i).get();
	}

	/** Whether bind {@code i}'s key has just gone down, given whether it's down now: holding it picks once. */
	boolean pressed(int i, boolean down) {
		boolean was = held.set(i, down);
		return down && !was;
	}
}
