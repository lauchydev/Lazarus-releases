package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.core.Events;
import dev.lauchy.lazarus.core.Text;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The action bar packet never reaches the chat events, so it is read where the HUD stores it. */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void lazarus$actionBar(Component message, boolean tinted, CallbackInfo ci) {
		Events.actionBar(Text.plain(message));
	}
}
