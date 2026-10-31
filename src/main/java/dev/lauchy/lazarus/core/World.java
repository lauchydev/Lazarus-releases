package dev.lauchy.lazarus.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world drawing through the game's gizmos, with real depth. Features register a
 * painter once; it runs every client tick while the player is in a world. Gizmos added
 * during a tick are kept and drawn for every frame of it; ones added from the frame's
 * own gizmo event never showed (the game test checks this).
 */
public final class World {
	private static final List<Runnable> PAINTERS = new ArrayList<>();

	private World() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.player == null) return;
			for (Runnable p : PAINTERS) {
				try {
					p.run();
				} catch (RuntimeException e) {
					dev.lauchy.lazarus.Lazarus.LOG.error("World painter failed", e);
				}
			}
		});
	}

	public static void paint(Runnable painter) {
		PAINTERS.add(painter);
	}

	/** Translucent fill alpha used for every filled box, so they all read the same. */
	public static int fillOf(int argb) {
		return (argb & 0x00FFFFFF) | 0x40000000;
	}

	public static void box(AABB box, int argb, boolean outline, boolean fill, boolean throughWalls) {
		GizmoStyle style = outline && fill ? GizmoStyle.strokeAndFill(argb, 2f, fillOf(argb))
				: fill ? GizmoStyle.fill(fillOf(argb)) : GizmoStyle.stroke(argb, 2f);
		var g = Gizmos.cuboid(box, style);
		if (throughWalls) g.setAlwaysOnTop();
	}

	public static void block(BlockPos pos, int argb, boolean fill, boolean throughWalls) {
		box(new AABB(pos), argb, true, fill, throughWalls);
	}

	public static void line(Vec3 from, Vec3 to, int argb, float width, boolean throughWalls) {
		var g = Gizmos.line(from, to, argb, width);
		if (throughWalls) g.setAlwaysOnTop();
	}

	/** A line from just in front of the camera, for pointing at something off screen. */
	public static void tracer(Vec3 to, int argb) {
		Minecraft mc = Minecraft.getInstance();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(pt).add(mc.player.getViewVector(pt).scale(0.5));
		line(eye, to, argb, 1.5f, true);
	}
}
