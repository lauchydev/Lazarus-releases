package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.dungeon.TerminalAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Terminal aura: the window of a terminal it opened for you is never put on screen, so you keep moving while it's solved. */
@Mixin(Minecraft.class)
abstract class MinecraftScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void lazarus$hideTerminal(Screen screen, CallbackInfo ci) {
		if (TerminalAura.hide(screen)) ci.cancel();
	}
}
