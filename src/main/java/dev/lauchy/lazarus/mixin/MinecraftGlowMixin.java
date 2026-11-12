package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.dungeon.RoomGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Whether a thing is drawn glowing: Room Glow decides for a dungeon's mobs, by the room they're in. */
@Mixin(Minecraft.class)
abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void lazarus$roomGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		// A blaze the solver has picked out always glows, in the solver's colour.
		if (dev.lauchy.lazarus.feature.dungeon.HigherOrLower.glowColour(entity) != null) {
			cir.setReturnValue(true);
			return;
		}
		Boolean glows = RoomGlow.glows(entity);
		if (glows != null) cir.setReturnValue(glows);
	}
}
