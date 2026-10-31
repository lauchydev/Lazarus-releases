package dev.lauchy.lazarus.hud;

import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.ui.Anim;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A movable on-screen readout owned by a feature. It draws itself from (0, 0); the HUD
 * places and scales it.
 */
public abstract class HudElement {
	public final String id;
	public final String name;
	public final Feature owner;
	final float defaultX, defaultY;
	/** How far faded in it is, and the text it last showed, so it can fade out after it ends. */
	final Anim.Smooth fade = new Anim.Smooth(10f, 0f);
	List<String> lastLines;

	protected HudElement(Feature owner, String id, String name, float defaultX, float defaultY) {
		this.owner = owner;
		this.id = id;
		this.name = name;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	/** Whether there is anything to show right now; the owner being on is checked for you. */
	protected boolean visible() {
		return true;
	}

	/**
	 * Draw the readout. With {@code sample} it must draw example content, so it can be
	 * placed in the editor while standing anywhere. {@code g} is null when only measuring.
	 * @return {width, height}, or null to draw nothing
	 */
	public abstract int[] draw(GuiGraphicsExtractor g, Font font, boolean sample);

	boolean shouldDraw() {
		return owner.enabled() && visible();
	}
}
