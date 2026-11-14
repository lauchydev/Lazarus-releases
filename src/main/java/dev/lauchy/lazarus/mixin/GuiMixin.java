package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.core.Events;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.DropAlerts;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The action bar packet never reaches the chat events, so it is read where the HUD stores it. Titles and the
 * action bar are also where the server's drop pop-up may write its words, which Drop Alerts can hide.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void lazarus$actionBar(Component message, boolean tinted, CallbackInfo ci) {
		Events.actionBar(Text.plain(message));
		if (DropAlerts.textPopUp(message)) ci.cancel();
	}

	@Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
	private void lazarus$title(Component title, CallbackInfo ci) {
		if (DropAlerts.textPopUp(title)) ci.cancel();
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
	private void lazarus$subtitle(Component subtitle, CallbackInfo ci) {
		if (DropAlerts.textPopUp(subtitle)) ci.cancel();
	}
}
