package dev.lauchy.lazarus.feature.general;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.mixin.MinecraftInvoker;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Clicks for you, under ten times a second, as if you pressed the mouse button each time. Left and right
 * click are set up on their own, each either while you hold that button or switched on and off with its
 * own key, so both can run at once. Dragsim allows auto clicking under 10 a second only while moving around,
 * so it clicks only while you move (jumping counts): standing at an altar spamming a terminator, or holding
 * right click still with a Flower of Truth, is what gets players banned. With a menu open nothing happens
 * unless In Menus is on, when each click goes to the menu under the mouse instead. Lazarus+.
 */
public final class AutoClicker extends Feature {
	/** When a button clicks: never, while it's held, or from its key until the key's pressed again. */
	public enum Mode {OFF, HOLD, TOGGLE}

	/** The fastest it goes: Dragsim allows auto clicking under ten a second, so ten itself is out. */
	static final int MOST = 9;

	/** Left starts in Hold, as the clicker always did; right starts off, so nobody uses an item without meaning to. */
	private final Side left = new Side("left", "Left click", "attacks and mines", true, Mode.HOLD);
	private final Side right = new Side("right", "Right click", "uses what you're holding", false, Mode.OFF);
	private final IntSetting rate = add(new IntSetting("cps", "Clicks a second", "How fast each button clicks. Dragsim allows under ten a second.", 8, 1, MOST, "cps"));
	private final BoolSetting inMenus = add(new BoolSetting("menus", "In menus", "Click in menus too: each click goes to whatever is under the mouse. You can't move in a menu, so Dragsim's moving rule is yours to weigh.", false));

	private final Moving moving = new Moving();

	public AutoClicker() {
		super("autoClicker", "Auto Clicker", "Clicks left, right or both for you, under ten times a second, while you hold the button or from a toggle key. Only while you're moving: Dragsim's rule.", Category.GENERAL, false);
	}

	@Override
	public boolean plus() {
		return true;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean live = enabled() && mc.player != null && mc.getConnection() != null && (mc.screen == null || inMenus.on());
			// Movement is followed whenever there's a player, so the first tick of a run already knows whether you're
			// walking; a position from before a world change isn't a step. In a menu nobody moves, so the rule stands aside.
			if (mc.player == null) moving.forget();
			boolean walking = mc.player != null && moving.at(mc.player.getX(), mc.player.getY(), mc.player.getZ()) || mc.screen != null;
			left.tick(mc, live, walking);
			right.tick(mc, live, walking);
		});
	}

	/** One mouse button's clicker: its mode and key, and whether it's running. */
	private final class Side {
		private final String name;
		private final boolean attack;
		private final ChoiceSetting<Mode> mode;
		private final KeySetting toggle;
		private final ClickRate clicks = new ClickRate();
		/** Whether Toggle mode has it switched on. */
		private boolean on;
		/** Whether the toggle key was down last tick, so holding it switches once. */
		private boolean keyHeld;
		/** Whether it was clicking last tick, so a fresh run starts with a click at once. */
		private boolean clicking;
		/** Whether this run has already said it's waiting for you to move, so it says so once. */
		private boolean toldToMove;

		Side(String key, String name, String does, boolean attack, Mode fallback) {
			this.name = name;
			this.attack = attack;
			mode = add(new ChoiceSetting<>(key, name, name + " " + does + ". Off: never. Hold: clicks while you hold the button. Toggle: its key below switches clicking on and off.", fallback));
			toggle = add(new KeySetting(key + "Key", name + " toggle key", "In Toggle mode, switches " + name.toLowerCase() + "ing on and off. Hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
		}

		void tick(Minecraft mc, boolean live, boolean walking) {
			boolean toggles = mode.get() == Mode.TOGGLE;
			boolean down = live && toggles && toggle.bound() && Keys.down(toggle);
			if (down && !keyHeld) {
				on = !on;
				Toast.show(on ? "§a" + name + " auto clicker on" : "§7" + name + " auto clicker off", on ? Theme.GOOD : Theme.MUTED);
			}
			keyHeld = down;
			if (!enabled() || !toggles) on = false; // switched off, or out of Toggle mode: it doesn't come back on by itself
			boolean wanted = live && switch (mode.get()) {
				case OFF -> false;
				case HOLD -> held(mc);
				case TOGGLE -> on;
			};
			if (!wanted) toldToMove = false;
			boolean now = wanted && walking;
			if (wanted && !walking && !toldToMove) {
				Toast.show("§7Auto clicker clicks only while you move", Theme.MUTED);
				toldToMove = true;
			}
			if (now && !clicking) clicks.reset();
			clicking = now;
			if (now && clicks.tick(rate.get())) click(mc);
		}

		/**
		 * One click, as the game does it for the mouse button, fired straight rather than queued as a key press:
		 * a queued press goes through the miss timer, which throws clicks away for half a second after a swing
		 * at nothing, so the rate set was never reached. Clearing the timer first lets every click land.
		 */
		private void click(Minecraft mc) {
			Screen menu = mc.screen;
			if (menu != null) {
				// A press and a release on the menu where the mouse is, as the game hands them over; never a double
				// click, which in a chest would gather up items.
				double x = mc.mouseHandler.getScaledXPos(mc.getWindow()), y = mc.mouseHandler.getScaledYPos(mc.getWindow());
				MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(attack ? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0));
				menu.mouseClicked(event, false);
				menu.mouseReleased(event);
				return;
			}
			MinecraftInvoker game = (MinecraftInvoker) mc;
			if (attack) {
				mc.missTime = 0;
				game.lazarus$startAttack();
			} else {
				game.lazarus$startUseItem();
			}
		}

		/** The vanilla key this button is bound to. */
		private KeyMapping mapping(Minecraft mc) {
			return attack ? mc.options.keyAttack : mc.options.keyUse;
		}

		/** Whether this button is physically held, whatever key or mouse button it's bound to. */
		private boolean held(Minecraft mc) {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping(mc));
			if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
			return key.getType() == InputConstants.Type.KEYSYM && Keys.down(key.getValue());
		}
	}
}
