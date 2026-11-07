package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright as a white night-vision floor in the lightmap: the shader builds every texel
 * up from it, so the world lights evenly without touching gamma.
 */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	@Unique
	private static final Vector3fc LAZARUS_WHITE = new Vector3f(1f, 1f, 1f);

	@Inject(method = "extract", at = @At("RETURN"))
	private void lazarus$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		Fullbright f = Fullbright.INSTANCE;
		if (f == null || !f.enabled()) return;
		state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, f.strength());
		state.nightVisionColor = LAZARUS_WHITE;
		if (f.ignoreDarkness()) {
			state.darknessEffectScale = 0f;
			state.bossOverlayWorldDarkening = 0f;
		}
	}
}
