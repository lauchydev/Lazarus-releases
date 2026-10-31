package dev.lauchy.lazarus.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns every HUD readout: where it sits (as a fraction of the screen, so it survives
 * resizing and GUI scale changes), how big it is, and drawing them all in one layer.
 */
public final class Hud {
	public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;

	private static final List<HudElement> ELEMENTS = new ArrayList<>();
	private static final Map<String, float[]> PLACEMENT = new HashMap<>(); // {x, y, scale}
	private static final Map<String, int[]> LAST_SIZE = new HashMap<>();
	/** Where each element was last drawn on screen, {x, y, w, h}, for anything that takes the mouse over it. */
	private static final Map<String, int[]> LAST_BOX = new HashMap<>();

	private Hud() {
	}

	public static <E extends HudElement> E register(E element) {
		ELEMENTS.add(element);
		return element;
	}

	public static List<HudElement> elements() {
		return ELEMENTS;
	}

	public static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "readouts"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.options.hideGui || mc.screen instanceof HudEditorScreen) return;
			for (HudElement e : ELEMENTS) {
				boolean on = e.shouldDraw();
				float a = e.fade.to(on ? 1f : 0f);
				if (a <= 0f) {
					e.lastLines = null;
					continue;
				}
				try {
					Draw.opacity = Anim.easeOut(a);
					float rise = (1f - Anim.easeOut(a)) * 4f;
					if (on) {
						Draw.lastPanel = null;
						drawPlaced(e, g, mc.font, false, rise);
						e.lastLines = Draw.lastPanel == null ? null : List.copyOf(Draw.lastPanel);
					} else if (e.lastLines != null) {
						// The readout has ended; let its last text fade out where it stood.
						List<String> lines = e.lastLines;
						drawPlaced(new HudElement(e.owner, e.id, e.name, e.defaultX, e.defaultY) {
							@Override
							public int[] draw(GuiGraphicsExtractor gg, Font font, boolean sample) {
								return Draw.panel(gg, font, lines);
							}
						}, g, mc.font, false, rise);
					}
				} catch (RuntimeException ex) {
					Lazarus.LOG.error("HUD element {} failed to draw", e.id, ex);
				} finally {
					Draw.opacity = 1f;
				}
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "toast"), (g, delta) -> Toast.draw(g));
	}

	/** Draws at its saved place. @return the on-screen box {x, y, w, h}, or null */
	static int[] drawPlaced(HudElement e, GuiGraphicsExtractor g, Font font, boolean sample) {
		return drawPlaced(e, g, font, sample, 0f);
	}

	private static int[] drawPlaced(HudElement e, GuiGraphicsExtractor g, Font font, boolean sample, float rise) {
		int[] size = e.draw(null, font, sample);
		if (size == null) return null;
		float s = scale(e.id);
		int w = Math.round(size[0] * s), h = Math.round(size[1] * s);
		int[] at = position(e, w, h, g.guiWidth(), g.guiHeight());
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(at[0], at[1] + rise);
		pose.scale(s, s);
		e.draw(g, font, sample);
		pose.popMatrix();
		LAST_SIZE.put(e.id, new int[]{w, h});
		int[] box = {at[0], at[1], w, h};
		LAST_BOX.put(e.id, box);
		return box;
	}

	static int[] position(HudElement e, int w, int h, int screenW, int screenH) {
		float[] p = placement(e);
		int x = Math.round(p[0] * screenW), y = Math.round(p[1] * screenH);
		return new int[]{Math.clamp(x, 0, Math.max(0, screenW - w)), Math.clamp(y, 0, Math.max(0, screenH - h))};
	}

	static void move(HudElement e, int x, int y, int screenW, int screenH) {
		float[] p = placement(e);
		p[0] = (float) x / screenW;
		p[1] = (float) y / screenH;
	}

	/** Whether a screen point is over the element as it was last drawn. */
	public static boolean over(String id, double x, double y) {
		int[] b = LAST_BOX.get(id);
		return b != null && x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
	}

	public static float scale(String id) {
		float[] p = PLACEMENT.get(id);
		return p == null ? 1f : p[2];
	}

	static void setScale(HudElement e, float s) {
		placement(e)[2] = Math.clamp(s, MIN_SCALE, MAX_SCALE);
	}

	static void reset(HudElement e) {
		PLACEMENT.remove(e.id);
	}

	private static float[] placement(HudElement e) {
		return PLACEMENT.computeIfAbsent(e.id, k -> new float[]{e.defaultX, e.defaultY, 1f});
	}

	public static JsonObject save() {
		JsonObject out = new JsonObject();
		PLACEMENT.forEach((id, p) -> {
			JsonObject o = new JsonObject();
			o.addProperty("x", p[0]);
			o.addProperty("y", p[1]);
			o.addProperty("scale", p[2]);
			out.add(id, o);
		});
		return out;
	}

	public static void load(JsonObject json) {
		for (Map.Entry<String, JsonElement> en : json.entrySet()) {
			if (!en.getValue().isJsonObject()) continue;
			JsonObject o = en.getValue().getAsJsonObject();
			try {
				PLACEMENT.put(en.getKey(), new float[]{o.get("x").getAsFloat(), o.get("y").getAsFloat(),
						Math.clamp(o.get("scale").getAsFloat(), MIN_SCALE, MAX_SCALE)});
			} catch (RuntimeException ignored) {
				// A hand-edited entry; fall back to the default place.
			}
		}
	}
}
