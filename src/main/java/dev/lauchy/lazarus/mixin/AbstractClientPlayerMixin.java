package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.cosmetic.Capes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capes: the cape you picked goes on your own skin, wherever the game draws you. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void lazarus$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		if (Capes.INSTANCE != null) cir.setReturnValue(Capes.INSTANCE.dress((Player) (Object) this, cir.getReturnValue()));
	}
}
