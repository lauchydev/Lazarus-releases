package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.general.FreeCam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Cam: while the camera is off on its own your movement keys steer it, so your body is given none of them. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void lazarus$freeCam(CallbackInfo ci) {
		if (!FreeCam.flying()) return;
		keyPresses = Input.EMPTY;
		moveVector = Vec2.ZERO;
	}
}
