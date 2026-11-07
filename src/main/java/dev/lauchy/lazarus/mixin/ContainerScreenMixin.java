package dev.lauchy.lazarus.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.lauchy.lazarus.feature.inventory.StorageOverview;
import dev.lauchy.lazarus.feature.staff.DashboardReader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
abstract class ContainerScreenMixin {
	/** A chest menu read behind your back (the Flag Dashboard) draws the screen you came from instead of its panel. */
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenRead(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.cover(g, mouseX, mouseY, delta, true);
		ci.cancel();
	}

	/**
	 * The chest's own panel is left out while the storage overview shows, since its slots sit on the cards instead.
	 * Optional: another mod redirecting the same call (Endsight) or Lunar Client reshaping this method leaves nothing to attach to, and the overview then draws over the panel instead.
	 */
	@Redirect(method = "extractBackground", require = 0, at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void lazarus$chestPanel(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int tw, int th) {
		StorageOverview.panelHook = true;
		if (StorageOverview.INSTANCE == null || !StorageOverview.showing((ContainerScreen) (Object) this)) g.blit(pipeline, texture, x, y, u, v, w, h, tw, th);
	}
}
