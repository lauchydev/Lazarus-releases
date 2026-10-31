package dev.lauchy.lazarus.core;

import net.minecraft.core.particles.ParticleOptions;

import java.util.ArrayList;
import java.util.List;

/** Client particle spawns, offered to features that want to watch or hide them. */
public final class Particles {
	@FunctionalInterface
	public interface Filter {
		/** @return true to stop the particle from spawning */
		boolean test(ParticleOptions particle, double x, double y, double z);
	}

	private static final List<Filter> FILTERS = new ArrayList<>();

	private Particles() {
	}

	public static void register(Filter filter) {
		FILTERS.add(filter);
	}

	public static boolean blocked(ParticleOptions particle, double x, double y, double z) {
		boolean block = false;
		for (Filter f : FILTERS) block |= f.test(particle, x, y, z); // every watcher sees every particle
		return block;
	}
}
