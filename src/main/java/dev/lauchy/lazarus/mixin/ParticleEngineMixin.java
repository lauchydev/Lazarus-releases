package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.core.Particles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void lazarus$filter(ParticleOptions particle, double x, double y, double z,
								double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		if (Particles.blocked(particle, x, y, z)) cir.setReturnValue(null);
	}
}
