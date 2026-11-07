package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.NameGradients;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerTabOverlay.class)
abstract class TabGradientMixin {
	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 1)
	private Component lazarus$gradient(Component original) {
		return NameGradients.INSTANCE == null ? original : NameGradients.INSTANCE.animate(original);
	}
}
