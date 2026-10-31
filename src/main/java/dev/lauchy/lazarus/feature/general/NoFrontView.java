package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Takes the front-facing camera out of the F5 cycle: first person, then third person from behind,
 * then back to first person. The game moves to the front view when F5 is pressed during a tick,
 * and this moves on to first person at that tick's end, before anything is drawn, so the front
 * view never shows.
 */
public final class NoFrontView extends Feature {
	public NoFrontView() {
		super("noFrontView", "Skip Front View", "F5 goes from behind you straight back to first person.", Category.GENERAL, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}
}
