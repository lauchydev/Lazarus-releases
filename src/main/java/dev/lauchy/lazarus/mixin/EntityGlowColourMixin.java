package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.dungeon.HigherOrLower;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The colour a thing glows in: the blazes to kill next glow in the solver's colours, not the white every mob gets. */
@Mixin(Entity.class)
abstract class EntityGlowColourMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void lazarus$solverColour(CallbackInfoReturnable<Integer> cir) {
		Integer colour = HigherOrLower.glowColour((Entity) (Object) this);
		if (colour != null) cir.setReturnValue(colour);
	}
}
