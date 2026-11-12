package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Free Cam: at the press of its key your view leaves your body and flies where you steer it, through walls too,
 * while you stay standing where you were. Your movement keys and mouse steer the camera instead of you, and your
 * clicks do nothing, since they'd land wherever your body is facing rather than where you're looking. Press the key
 * again to come back. Nothing is sent to the server: to it you're standing still. It shows you what you couldn't
 * otherwise see, which Dragsim can ban for: it's a Lazarus+ feature, off unless you turn it on, and marked BANNABLE
 * in the menu.
 */
public final class FreeCam extends Feature {
	private static FreeCam instance;

	private final KeySetting key = add(new KeySetting("key", "Toggle key", "Sends the camera off, and brings it back. Hold Ctrl, Alt or Shift while pressing to bind a combo. Right-click to clear."));
	private final IntSetting speed = add(new IntSetting("speed", "Speed", "How fast the camera flies. Holding sprint doubles it.", 12, 2, 60, " blocks/s"));

	/** Whether the camera is off on its own, where it is and was a tick ago, and where it looks. */
	private boolean flying, wasDown;
	private Vec3 at = Vec3.ZERO, was = Vec3.ZERO;
	private float yaw, pitch;
	private Object level;

	public FreeCam() {
		super("freeCam", "Free Cam", "Flies your view away from your body at the press of a key, through walls too. You stay where you were.", Category.GENERAL, false);
		instance = this;
	}

	@Override
	public boolean plus() {
		return true;
	}

	/** It shows what you couldn't otherwise see, which Dragsim's rules can ban for. */
	@Override
	public boolean bannable() {
		return true;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || mc.player == null || mc.level == null || mc.level != level && flying) {
				flying = false;
				level = mc.level;
				return;
			}
			level = mc.level;
			boolean down = mc.screen == null && key.bound() && Keys.down(key);
			if (down && !wasDown) {
				flying = !flying;
				if (flying) {
					at = was = mc.player.getEyePosition();
					yaw = mc.player.getYRot();
					pitch = mc.player.getXRot();
				}
			}
			wasDown = down;
			if (!flying) return;
			was = at;
			if (mc.screen != null) return;
			var o = mc.options;
			double ahead = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
			double left = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
			double up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
			double rad = Math.toRadians(yaw), sin = Math.sin(rad), cos = Math.cos(rad);
			Vec3 move = new Vec3(-sin * ahead + cos * left, up, cos * ahead + sin * left);
			if (move.lengthSqr() < 1e-6) return;
			at = at.add(move.normalize().scale(speed.get() / 20.0 * (o.keySprint.isDown() ? 2 : 1)));
		});
	}

	/** Whether the camera is off on its own right now. */
	public static boolean flying() {
		FreeCam f = instance;
		return f != null && f.flying;
	}

	/** Where the camera is, this far through the tick. */
	public static Vec3 at(float partial) {
		FreeCam f = instance;
		return f.was.lerp(f.at, partial);
	}

	public static float yaw() {
		return instance.yaw;
	}

	public static float pitch() {
		return instance.pitch;
	}

	/** The mouse moved: the camera turns, as you would have. */
	public static void turn(double dx, double dy) {
		FreeCam f = instance;
		f.yaw += (float) dx * 0.15f;
		f.pitch = Mth.clamp(f.pitch + (float) dy * 0.15f, -90, 90);
	}
}
