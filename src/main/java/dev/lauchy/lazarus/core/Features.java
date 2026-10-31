package dev.lauchy.lazarus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every feature, in menu order. */
public final class Features {
	private static final List<Feature> ALL = new ArrayList<>();

	private Features() {
	}

	public static <F extends Feature> F register(F feature) {
		ALL.add(feature);
		return feature;
	}

	public static List<Feature> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static Feature byId(String id) {
		for (Feature f : ALL) if (f.id.equals(id)) return f;
		return null;
	}

	public static List<Feature> in(Category category) {
		return ALL.stream().filter(f -> f.category == category).toList();
	}
}
