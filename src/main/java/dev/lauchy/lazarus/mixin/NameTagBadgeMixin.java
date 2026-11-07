package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.cosmetic.TabBadges;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Name tags over players' heads, with the Lazarus badge in front like the tab list. */
@Mixin(LivingEntityRenderer.class)
abstract class NameTagBadgeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void lazarus$badge(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		if (TabBadges.INSTANCE != null && entity instanceof Player player && state.nameTag != null) {
			state.nameTag = TabBadges.INSTANCE.nameTag(player, state.nameTag);
		}
	}
}
