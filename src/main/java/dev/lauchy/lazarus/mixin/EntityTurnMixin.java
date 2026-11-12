package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.FreeCam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Cam: while the camera is off on its own the mouse turns it, not you. */
@Mixin(Entity.class)
abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void lazarus$freeCam(double dx, double dy, CallbackInfo ci) {
		if (!FreeCam.flying() || (Object) this != Minecraft.getInstance().player) return;
		FreeCam.turn(dx, dy);
		ci.cancel();
	}
}
