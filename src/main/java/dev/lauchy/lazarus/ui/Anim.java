package dev.lauchy.lazarus.ui;

import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Easing and smoothing for the UI. Values ease towards a target at a rate measured in
 * real time, so animations run at the same speed at 30 fps and 300.
 */
public final class Anim {
	private Anim() {
	}

	public static float easeOut(float t) {
		float u = 1f - Math.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	public static float easeInOut(float t) {
		t = Math.clamp(t, 0f, 1f);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Progress from 0 to 1 over {@code ms} since {@code start}, eased out. */
	public static float since(long start, long ms) {
		return easeOut((Util.getMillis() - start) / (float) ms);
	}

	public static int lerpColor(int from, int to, float t) {
		t = Math.clamp(t, 0f, 1f);
		int a = lerp(from >>> 24, to >>> 24, t), r = lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
		int g = lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, t), b = lerp(from & 0xFF, to & 0xFF, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	/** A value that glides towards wherever it is told to go. */
	public static final class Smooth {
		private final float speed;
		private float value;
		private long last;

		/** @param speed how quickly it closes the gap; about 1/speed seconds to get most of the way */
		public Smooth(float speed, float start) {
			this.speed = speed;
			this.value = start;
		}

		public float to(float target) {
			long now = Util.getMillis();
			float dt = last == 0 ? 1f : Math.min(0.25f, (now - last) / 1000f);
			last = now;
			value += (target - value) * (1f - (float) Math.exp(-speed * dt));
			if (Math.abs(target - value) < 0.002f) value = target;
			return value;
		}

		public float get() {
			return value;
		}

		public void snap(float v) {
			value = v;
		}
	}

	/** One {@link Smooth} per key, made on first use: hover glows, toggle knobs and the like. */
	public static final class Group<K> {
		private final Map<K, Smooth> values = new HashMap<>();
		private final float speed;

		public Group(float speed) {
			this.speed = speed;
		}

		public float to(K key, float target) {
			return values.computeIfAbsent(key, k -> new Smooth(speed, target)).to(target);
		}

		public float to(K key, boolean on) {
			return to(key, on ? 1f : 0f);
		}

		public float get(K key) {
			Smooth s = values.get(key);
			return s == null ? 0f : s.get();
		}
	}
}
