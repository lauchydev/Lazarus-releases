package dev.lauchy.lazarus.hud;

import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every enabled readout at once with sample data: drag to move, scroll to resize,
 * right-click to reset, the red X to switch its feature off. Edges and the screen centre
 * snap unless Shift is held.
 */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private final Screen parent;
	private final Map<HudElement, int[]> boxes = new LinkedHashMap<>();
	private HudElement dragging;
	private int grabX, grabY;
	private final long openedAt = Util.getMillis();
	private final Anim.Smooth guides = new Anim.Smooth(12f, 0f);
	private final Anim.Group<HudElement> hover = new Anim.Group<>(14f);
	private final Anim.Group<HudElement> closeHover = new Anim.Group<>(16f);
	private static final int X_SIZE = 9;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Move HUD"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		float open = Anim.since(openedAt, 220);
		g.fill(0, 0, width, height, Draw.alpha(0x000000, 0.38f * open));
		// The centre guides only show while something is being moved.
		float guides = this.guides.to(dragging != null ? 1f : 0f);
		if (guides > 0f) {
			int c = Draw.alpha(Theme.ACCENT, 0.35f * guides);
			g.fill(width / 2, 0, width / 2 + 1, height, c);
			g.fill(0, height / 2, width, height / 2 + 1, c);
		}
		boxes.clear();
		for (HudElement e : Hud.elements()) {
			if (!e.owner.enabled()) continue;
			int[] box = Hud.drawPlaced(e, g, font, true);
			if (box == null) continue;
			boxes.put(e, box);
		}
		HudElement onX = dragging != null ? null : closeAt(mouseX, mouseY);
		HudElement top = dragging != null ? dragging : onX != null ? onX : at(mouseX, mouseY);
		Draw.opacity = open;
		try {
			for (var en : boxes.entrySet()) {
				int[] box = en.getValue();
				float h = hover.to(en.getKey(), en.getKey() == top);
				Draw.outline(g, box[0] - 2, box[1] - 2, box[2] + 4, box[3] + 4, Anim.lerpColor(0x30FFFFFF, Theme.ACCENT, h));
				if (h > 0.01f) {
					String label = en.getKey().name + "  §7" + Math.round(Hud.scale(en.getKey().id) * 100) + "%";
					int lw = font.width(label) + 8, ly = box[1] - 15 < 0 ? box[1] + box[3] + 4 : box[1] - 15;
					float before = Draw.opacity;
					Draw.opacity *= h;
					Draw.round(g, box[0] - 2, ly, lw, 12, Theme.PANEL);
					Draw.text(g, font, label, box[0] + 2, ly + 2, Theme.TEXT);
					Draw.opacity = before;
				}
				// The red X on the top-right corner switches the feature off.
				int[] x = closeBox(box);
				float xh = closeHover.to(en.getKey(), en.getKey() == onX);
				float before = Draw.opacity;
				Draw.opacity *= 0.55f + 0.45f * Math.max(h, xh);
				Draw.round(g, x[0], x[1], X_SIZE, X_SIZE, Anim.lerpColor(0xCC8A2A2A, 0xFFE05555, xh));
				Draw.textCentered(g, font, "×", x[0] + X_SIZE / 2 + 1, x[1] + 1, 0xFFFFFFFF);
				Draw.opacity = before;
			}
			if (boxes.isEmpty()) Draw.textCentered(g, font, "Turn on a feature with a readout to place it here.", width / 2, height / 2 - 20, Theme.MUTED);
			String help = "Drag to move  ·  Scroll to resize  ·  Right-click to reset  ·  × to turn off  ·  Shift to stop snapping  ·  Esc when done";
			int hw = font.width(help) + 16;
			Draw.round(g, width / 2 - hw / 2, height - 22 + Math.round((1f - open) * 6), hw, 16, Theme.PANEL);
			Draw.textCentered(g, font, help, width / 2, height - 18 + Math.round((1f - open) * 6), Theme.MUTED);
		} finally {
			Draw.opacity = 1f;
		}
	}

	/** Where an element's X sits: on its outline's top-right corner. */
	private static int[] closeBox(int[] box) {
		return new int[]{box[0] + box[2] + 2 - X_SIZE / 2 - 1, box[1] - 2 - X_SIZE / 2};
	}

	private HudElement closeAt(double mx, double my) {
		HudElement hit = null;
		for (var en : boxes.entrySet()) {
			int[] x = closeBox(en.getValue());
			if (Draw.inside(mx, my, x[0], x[1], X_SIZE, X_SIZE)) hit = en.getKey();
		}
		return hit;
	}

	private HudElement at(double mx, double my) {
		HudElement hit = null;
		for (var en : boxes.entrySet()) {
			int[] b = en.getValue();
			if (Draw.inside(mx, my, b[0], b[1], b[2], b[3])) hit = en.getKey(); // topmost wins
		}
		return hit;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		HudElement off = event.button() == 0 ? closeAt(event.x(), event.y()) : null;
		if (off != null) {
			off.owner.setEnabled(false);
			Toast.toggled(off.owner.name, false);
			return true;
		}
		HudElement e = at(event.x(), event.y());
		if (e == null) return super.mouseClicked(event, doubleClick);
		if (event.button() == 1) {
			Hud.reset(e);
			return true;
		}
		int[] b = boxes.get(e);
		dragging = e;
		grabX = (int) event.x() - b[0];
		grabY = (int) event.y() - b[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		int[] b = boxes.get(dragging);
		if (b == null) return true;
		int x = (int) event.x() - grabX, y = (int) event.y() - grabY;
		if (!event.hasShiftDown()) {
			x = snap(x, b[2], width, true);
			y = snap(y, b[3], height, false);
		}
		Hud.move(dragging, x, y, width, height);
		return true;
	}

	private int snap(int pos, int size, int screen, boolean horizontal) {
		int[] targets = {0, screen / 2 - size / 2, screen - size};
		for (int t : targets) if (Math.abs(pos - t) <= SNAP) return t;
		for (var en : boxes.entrySet()) {
			if (en.getKey() == dragging) continue;
			int[] o = en.getValue();
			int start = horizontal ? o[0] : o[1], len = horizontal ? o[2] : o[3];
			for (int t : new int[]{start, start + len - size, start + len + 2, start - size - 2}) {
				if (Math.abs(pos - t) <= SNAP) return t;
			}
		}
		return pos;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		HudElement e = at(mx, my);
		if (e == null) return false;
		Hud.setScale(e, Hud.scale(e.id) + (dy > 0 ? 0.1f : -0.1f));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Config.save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
