package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.DropAlerts;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The totem animation, which the server plays for its drop pop-up; Drop Alerts may put its own in that one's place. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void lazarus$serverPopUp(ItemStack stack, CallbackInfo ci) {
		if (DropAlerts.itemPopUp(stack)) ci.cancel();
	}
}
