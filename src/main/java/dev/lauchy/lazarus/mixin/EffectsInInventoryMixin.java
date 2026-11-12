package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.inventory.HideEffects;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class)
abstract class EffectsInInventoryMixin {
	/** The window draws its effect list only when it can see effects; hidden, it never does. */
	@Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
	private void lazarus$hideEffects(CallbackInfoReturnable<Boolean> cir) {
		if (HideEffects.hidden()) cir.setReturnValue(false);
	}
}
