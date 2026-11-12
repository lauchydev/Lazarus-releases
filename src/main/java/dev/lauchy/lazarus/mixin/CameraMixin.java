package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.FreeCam;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Cam: the view is put where the free camera is, and counts as outside your body so your body is drawn. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void lazarus$freeCam(float partial, CallbackInfo ci) {
		if (!FreeCam.flying()) return;
		Vec3 at = FreeCam.at(partial);
		setRotation(FreeCam.yaw(), FreeCam.pitch());
		setPosition(at.x, at.y, at.z);
		detached = true;
	}
}
