package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;

/** The work happens in LightmapMixin; this holds the switches. */
public final class Fullbright extends Feature {
	public static Fullbright INSTANCE;

	private final IntSetting strength = add(new IntSetting("strength", "Brightness", "How strongly dark areas are lifted.", 100, 10, 100, "%"));
	private final BoolSetting darkness = add(new BoolSetting("ignoreDarkness", "Ignore darkness effects", "Also cancel Darkness pulses and boss-fight dimming.", true));

	public Fullbright() {
		super("fullbright", "Fullbright", "Lights up caves and nights without changing gamma.", Category.GENERAL, false);
		INSTANCE = this;
	}

	@Override
	public void init() {
	}

	public float strength() {
		return strength.get() / 100f;
	}

	public boolean ignoreDarkness() {
		return darkness.on();
	}
}
