package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/**
 * Takes off the Darkness and Nausea the Rift puts on you as your timer runs down (Darkness under
 * five minutes, Nausea under one). The server's effect packets for them are dropped as they arrive,
 * so neither effect is ever put on you; a sweep each tick takes off any already on (say, from before
 * the feature was switched on). The server's timer runs as before.
 */
public final class RiftEffects extends Feature {
	public static RiftEffects INSTANCE;

	private final BoolSetting darkness = add(new BoolSetting("darkness", "Darkness", "Take off the dimming that starts with five minutes left.", true));
	private final BoolSetting nausea = add(new BoolSetting("nausea", "Nausea", "Take off the wobble that starts with one minute left.", true));
	private final BoolSetting riftOnly = add(new BoolSetting("riftOnly", "Only in the Rift", "Switch off to take these effects off everywhere.", true));

	public RiftEffects() {
		super("riftEffects", "Rift Effect Remover", "Removes the darkness and nausea the Rift puts on you as time runs out.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || !enabled()) return;
			for (MobEffectInstance effect : List.copyOf(mc.player.getActiveEffects())) {
				if (blocks(effect.getEffect())) mc.player.removeEffect(effect.getEffect());
			}
		});
	}

	/**
	 * Whether the server's packet putting this effect on you is dropped. Taking it off at the end of
	 * the tick wasn't enough: a packet lands before the tick, so the camera's spin for that tick was
	 * worked out and frames were drawn with Nausea on before it came off.
	 */
	public boolean blocks(Holder<MobEffect> effect) {
		if (!enabled()) return false;
		String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).getPath();
		return removes(id, Area.place(), darkness.on(), nausea.on(), riftOnly.on());
	}

	/**
	 * Whether the effect with this id ("darkness", "nausea") comes off, given where the player is and
	 * the settings: each effect has its own switch, and unless the Rift-only switch is off, only the Rift counts.
	 */
	static boolean removes(String effect, Area.Place place, boolean darkness, boolean nausea, boolean riftOnly) {
		if (riftOnly && place != Area.Place.RIFT) return false;
		return switch (effect) {
			case "darkness" -> darkness;
			case "nausea" -> nausea;
			default -> false;
		};
	}
}
