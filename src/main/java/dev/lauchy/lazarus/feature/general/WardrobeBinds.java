package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A key for each wardrobe slot: pressing Wardrobe 2's key runs /wardrobe, clicks the second slot in the menu
 * (turning pages if it's further on) and closes the menu, so the armour in it goes on. A bind can include
 * Ctrl, Alt or Shift, held while picking the key. The slot already worn is left on rather than taken off.
 * There is always one unset wardrobe key after the last one set; the first {@link #FREE} are everyone's, the
 * rest Lazarus+.
 */
public final class WardrobeBinds extends Feature {
	/** How many wardrobe keys everyone gets; the rest are Lazarus+. */
	static final int FREE = 2;
	/** A wardrobe key as saved, numbered from 1: "wardrobe3Key". */
	private static final Pattern SAVED = Pattern.compile("^wardrobe(\\d{1,6})Key$");

	private final List<KeySetting> keys = new ArrayList<>();
	private final List<Boolean> held = new ArrayList<>();
	private final SlotPicker picker = new SlotPicker("wardrobe", new WardrobeMenu.Reader());

	public WardrobeBinds() {
		super("wardrobeBinds", "Wardrobe Binds", "A key for each wardrobe slot that puts that armour on from /wardrobe. Two free, the rest Lazarus+.", Category.GENERAL, true);
		slot();
	}

	/** Adds the next wardrobe key; past the allowance it's Lazarus+. */
	private void slot() {
		int i = keys.size();
		KeySetting key = add(new KeySetting("wardrobe" + (i + 1) + "Key", "Wardrobe " + (i + 1) + " key", "Puts on the armour in slot " + (i + 1) + " of /wardrobe. A key or a side mouse button; hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
		if (Binds.plus(i, FREE)) key.plusOnly();
		keys.add(key);
		held.add(false);
	}

	/** The file holds however many wardrobe keys were saved: they're all added before their values are read. */
	@Override
	protected void loading(JsonObject saved) {
		// Saved keys count from 1 where Binds counts from 0, so one past the highest saved is the highest itself.
		int want = Binds.saved(saved.keySet(), SAVED) - 1;
		while (keys.size() < Math.max(1, want)) slot();
	}

	@Override
	public void init() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) for (KeySetting k : keys) if (usable(k)) Keys.suppressVanilla(k); // Alt + 1 must not also pick hotbar slot 1
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (keys.getLast().bound()) slot(); // the last key just set brings the next
			boolean live = enabled() && mc.screen == null && mc.getConnection() != null && mc.player != null;
			for (int i = 0; i < keys.size(); i++) {
				boolean down = live && usable(keys.get(i)) && Keys.down(keys.get(i));
				if (down && !held.get(i) && !picker.busy()) picker.open(mc, i + 1);
				held.set(i, down);
			}
			picker.tick(mc);
		});
	}

	/** Whether a wardrobe key may run: bound, and not a Lazarus+ key on an account without Lazarus+. */
	private static boolean usable(KeySetting key) {
		return key.bound() && (!key.plus() || Plus.unlocked());
	}
}
