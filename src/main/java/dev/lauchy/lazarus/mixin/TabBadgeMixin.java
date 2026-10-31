package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.cosmetic.TabBadges;
import dev.lauchy.lazarus.feature.staff.StaffNames;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tab list's names, with the Lazarus badge in front and the staff marks after (a nicked player's real
 * name, a watched player's ⚠); the list sizes its columns from the same names. One injection does both:
 * a cancelling callback ends the method, so a second one at the same point would never run.
 */
@Mixin(PlayerTabOverlay.class)
abstract class TabBadgeMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void lazarus$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		if (TabBadges.INSTANCE != null) name = TabBadges.INSTANCE.badged(info, name);
		cir.setReturnValue(StaffNames.tab(info, name));
	}
}
