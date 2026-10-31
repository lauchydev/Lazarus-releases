package dev.lauchy.lazarus.core;

import java.util.ArrayList;
import java.util.List;

/** Sounds the server plays, by id ("minecraft:entity.player.hurt_freeze"), offered to features that want to watch or hide them. */
public final class Sounds {
	@FunctionalInterface
	public interface Filter {
		/** @return true to keep the sound from playing */
		boolean test(String id, double x, double y, double z);
	}

	private static final List<Filter> FILTERS = new ArrayList<>();

	private Sounds() {
	}

	public static void register(Filter filter) {
		FILTERS.add(filter);
	}

	public static boolean blocked(String id, double x, double y, double z) {
		boolean block = false;
		for (Filter f : FILTERS) block |= f.test(id, x, y, z); // every watcher hears every sound
		return block;
	}
}
