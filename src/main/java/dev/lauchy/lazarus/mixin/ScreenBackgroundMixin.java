package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.inventory.StorageOverview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class ScreenBackgroundMixin {
	/** The storage overview draws its cards here: over the blurred world, under the window's slots, items and tooltips. */
	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void lazarus$storageOverview(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (StorageOverview.INSTANCE != null) StorageOverview.INSTANCE.backdrop((Screen) (Object) this, g, mouseX, mouseY, false);
	}
}
