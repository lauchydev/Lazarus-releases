package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Keys that put armour on: each bind is a key and the wardrobe slot it puts on, so J can put on slot 4. Pressing it
 * runs /wardrobe, clicks that slot in the menu (turning pages if it's further on) once the wardrobe lets you swap,
 * and closes the menu. A key can include Ctrl, Alt or Shift, held while picking it. The slot already worn is left
 * on rather than taken off. There is always one unset bind after the last one set; the first
 * {@link SlotBinds#FREE} are everyone's, the rest Lazarus+.
 */
public final class WardrobeBinds extends Feature {
	private final SlotBinds binds = new SlotBinds("wardrobe", "Wardrobe bind",
			"Puts on the armour in this bind's slot from /wardrobe. A key or a side mouse button; hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear.",
			"Which wardrobe slot the key puts on, counting on across pages of /wardrobe.", this::add);
	private final SlotPicker picker = new SlotPicker("wardrobe", new WardrobeMenu.Reader());

	public WardrobeBinds() {
		super("wardrobeBinds", "Wardrobe Binds", "Keys that put armour on from /wardrobe: each bind picks its own slot, so J can put on slot 4. Two free, the rest Lazarus+.", Category.GENERAL, true);
	}

	/** The file holds however many wardrobe binds were saved: they're all added before their values are read. */
	@Override
	protected void loading(JsonObject saved) {
		binds.loading(saved);
	}

	@Override
	public void init() {
		Chat.hide(line -> picker.turnedDown(line.text())); // the wardrobe's "wait one second", when the bind picks again
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) for (KeySetting k : binds.keys()) if (usable(k)) Keys.suppressVanilla(k); // Alt + 1 must not also pick hotbar slot 1
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			binds.follow(); // the last key just set brings the next bind
			boolean live = enabled() && mc.screen == null && mc.getConnection() != null && mc.player != null;
			for (int i = 0; i < binds.size(); i++) {
				KeySetting key = binds.keys().get(i);
				boolean down = live && usable(key) && Keys.down(key);
				if (binds.pressed(i, down) && !picker.busy()) picker.open(mc, binds.slot(i));
			}
			picker.tick(mc);
		});
	}

	/** Whether a wardrobe key may run: bound, and not a Lazarus+ bind on an account without Lazarus+. */
	private static boolean usable(KeySetting key) {
		return key.bound() && (!key.plus() || Plus.unlocked());
	}
}
