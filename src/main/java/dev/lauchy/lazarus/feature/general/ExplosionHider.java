package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Particles;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.minecraft.core.particles.ParticleTypes;

public final class ExplosionHider extends Feature {
	private final BoolSetting small = add(new BoolSetting("small", "Small explosions", "Hide the single puff as well as the big cloud.", true));

	public ExplosionHider() {
		super("explosionHider", "Explosion Particle Hider", "Hides explosion clouds that block your view in fights.", Category.GENERAL, false);
	}

	@Override
	public void init() {
		Particles.register((p, x, y, z) -> enabled()
				&& (p.getType() == ParticleTypes.EXPLOSION_EMITTER || small.on() && p.getType() == ParticleTypes.EXPLOSION));
	}
}
