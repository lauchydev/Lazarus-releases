package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.slayer.FadedSouls;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Faded Souls: a soul's body takes the game's see-through "invisible teammate" path. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void lazarus$fadeSouls(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		FadedSouls souls = FadedSouls.INSTANCE;
		if (souls == null) return;
		if (souls.faded(entity)) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;
		}
		if (souls.tagHidden(entity)) state.nameTag = null;
	}
}
