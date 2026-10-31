package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.inventory.SlotLock;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Q and Ctrl+Q on the hotbar never pass through a container screen. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void lazarus$drop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
		if (SlotLock.INSTANCE != null && SlotLock.INSTANCE.blocksDrop()) cir.setReturnValue(false);
	}
}
