package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.FreeCam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Free Cam: while the camera is off on its own your clicks do nothing. They'd land where your body faces, not where you're looking. */
@Mixin(Minecraft.class)
abstract class MinecraftFreeCamMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void lazarus$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (FreeCam.flying()) cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void lazarus$noDig(boolean down, CallbackInfo ci) {
		if (FreeCam.flying()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void lazarus$noUse(CallbackInfo ci) {
		if (FreeCam.flying()) ci.cancel();
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void lazarus$noPick(CallbackInfo ci) {
		if (FreeCam.flying()) ci.cancel();
	}
}
