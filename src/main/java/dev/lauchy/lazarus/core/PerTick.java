package dev.lauchy.lazarus.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Something worked out once a game tick and kept for every frame of it. A panel's rows are drawn hundreds of times a
 * second but what they say changes twenty times a second at most, so adding up drops and prices for each frame is
 * work nobody sees. Ask with a key when one holder serves several things (a panel for each boss).
 */
public final class PerTick<T> {
	private static long tick;

	private final Map<Object, T> made = new HashMap<>();
	private long madeAt = -1;

	/** Starts the count of ticks; called once as the mod starts. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick++);
	}

	/** The tick it is now, counted from when the mod started. */
	public static long now() {
		return tick;
	}

	/** What {@code make} gives, made afresh at most once a tick. */
	public T get(Supplier<T> make) {
		return get("", make);
	}

	/** What {@code make} gives for this key, made afresh at most once a tick. */
	public T get(Object key, Supplier<T> make) {
		if (madeAt != tick) {
			made.clear();
			madeAt = tick;
		}
		return made.computeIfAbsent(key, k -> make.get());
	}
}
