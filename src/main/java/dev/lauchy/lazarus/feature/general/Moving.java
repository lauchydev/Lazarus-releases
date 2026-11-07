package dev.lauchy.lazarus.feature.general;

/**
 * Whether the player is moving around, from their position tick by tick: Dragsim allows auto clicking only
 * while moving, so standing at an altar spamming a terminator is out. Jumping on the spot counts, so the
 * height is followed too. A short grace after the last step covers turning round at a wall, and the top of
 * a jump, without the clicks stuttering. No Minecraft types, so it's tested on its own.
 */
final class Moving {
	/** How far a tick counts as a step, in blocks: a sneak is three times this, a walk ten, a jump twenty. */
	static final double STEP = 0.02;
	/** Ticks after the last step that still count as moving. */
	static final int GRACE = 5;

	private double x, y, z;
	private boolean seen;
	/** Ticks since the last step; starts out past the grace, so it's still until a step is seen. */
	private int still = GRACE + 1;

	/** One tick at ({@code x}, {@code y}, {@code z}): whether the player counts as moving now. */
	boolean at(double x, double y, double z) {
		boolean stepped = seen && Math.sqrt(sq(x - this.x) + sq(y - this.y) + sq(z - this.z)) >= STEP;
		this.x = x;
		this.y = y;
		this.z = z;
		seen = true;
		still = stepped ? 0 : still + 1;
		return still <= GRACE;
	}

	private static double sq(double d) {
		return d * d;
	}

	/** Forgets where the player was, as when they've left the world: the next tick can't count as a step. */
	void forget() {
		seen = false;
		still = GRACE + 1;
	}
}
