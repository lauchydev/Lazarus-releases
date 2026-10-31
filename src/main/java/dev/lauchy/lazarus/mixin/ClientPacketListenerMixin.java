package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.core.Sounds;
import dev.lauchy.lazarus.feature.end.ZealotTracker;
import dev.lauchy.lazarus.feature.general.RiftEffects;
import dev.lauchy.lazarus.feature.general.ServerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void lazarus$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (ServerStats.INSTANCE != null) ServerStats.INSTANCE.onTimePacket();
	}

	// TAIL: the handler first hops to the game thread, so this runs once, there.
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void lazarus$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		if (ZealotTracker.INSTANCE != null) ZealotTracker.INSTANCE.onDamage(packet.entityId(), packet.sourceCauseId(), packet.sourceDirectId());
	}

	// After the hop to the game thread, so the player and settings are read there.
	@Inject(method = "handleUpdateMobEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
	private void lazarus$onUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (RiftEffects.INSTANCE != null && player != null && packet.getEntityId() == player.getId() && RiftEffects.INSTANCE.blocks(packet.getEffect())) ci.cancel();
	}

	// After the hop to the game thread, so features read their settings there.
	@Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
	private void lazarus$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		if (Sounds.blocked(packet.getSound().value().location().toString(), packet.getX(), packet.getY(), packet.getZ())) ci.cancel();
	}

	@Inject(method = "handleSoundEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
	private void lazarus$onEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
		var level = Minecraft.getInstance().level;
		var entity = level == null ? null : level.getEntity(packet.getId());
		if (entity != null && Sounds.blocked(packet.getSound().value().location().toString(), entity.getX(), entity.getY(), entity.getZ())) ci.cancel();
	}
}
