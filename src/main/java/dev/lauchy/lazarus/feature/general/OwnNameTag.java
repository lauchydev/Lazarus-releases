package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Your own name tag above your head when the camera is in third person, as other players see it (badge and
 * all); the game hides it. Nothing changes in first person.
 */
public final class OwnNameTag extends Feature {
	public static OwnNameTag INSTANCE;

	public OwnNameTag() {
		super("ownNameTag", "Own Name Tag", "See your own name tag above your head in third person.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
	}

	/** Whether this entity's name tag should show although the game would hide it: you, in third person, with this on. */
	public static boolean shows(Entity entity) {
		if (INSTANCE == null || !INSTANCE.enabled()) return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && entity == mc.player && !mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui;
	}
}
