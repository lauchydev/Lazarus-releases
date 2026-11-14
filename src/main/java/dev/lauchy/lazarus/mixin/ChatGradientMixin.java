package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.NameGradients;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat lines pass through these two drawing helpers, focused and unfocused. */
@Mixin(targets = {"net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
		"net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"})
abstract class ChatGradientMixin {
	@ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence lazarus$gradient(FormattedCharSequence original) {
		return NameGradients.INSTANCE == null ? original : NameGradients.INSTANCE.animate(original);
	}
}
