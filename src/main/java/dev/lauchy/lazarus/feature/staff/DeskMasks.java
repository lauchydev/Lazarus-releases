package dev.lauchy.lazarus.feature.staff;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.lauchy.lazarus.Lazarus;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * White, anti-aliased masks the desk tints as it draws, made once per size with Java2D: a filled circle (a rounded
 * corner's four quadrants), a ring (a rounded border's corners), the corners left outside a circle (to round off a
 * player's face) and each icon stroked at a pixel size. All in screen pixels, so they're drawn one to one, and all
 * on one texture: the desk's shapes then share a texture and Minecraft draws them in one go. A solid white patch
 * at its corner is what plain fills sample.
 */
final class DeskMasks {
	/** Where a mask sits on the atlas, in pixels. */
	record Mask(int u, int v, int w, int h) {
	}

	/** The atlas's side; a mask lives at a pixel size, and the largest (a 2× pill's 72 px disc) are few. */
	static final int SIZE = 1024;
	/** The middle of the white patch, as a texture coordinate. */
	static final float WHITE = 2.5f / SIZE;
	private static final Identifier ID = Identifier.fromNamespaceAndPath(Lazarus.ID, "desk_masks");
	private static final Map<String, Mask> MASKS = new HashMap<>();
	private static NativeImage atlas;
	private static DynamicTexture texture;
	private static TextureSetup setup;
	/** The next free spot on the shelf being filled, and that shelf's height. */
	private static int nextX, nextY, rowH;

	private DeskMasks() {
	}

	/** The atlas as a texture setup for a GUI element, made on first use. */
	static TextureSetup setup() {
		ensure();
		return setup;
	}

	/** A filled circle {@code 2r} across. */
	static Mask disc(int r) {
		return mask("disc" + r, r * 2, r * 2, g -> g.fill(new Ellipse2D.Double(0, 0, r * 2, r * 2)));
	}

	/** A ring {@code 2r} across, {@code stroke} pixels wide on the inside. */
	static Mask ring(int r, int stroke) {
		return mask("ring" + r + "_" + stroke, r * 2, r * 2, g -> {
			Area a = new Area(new Ellipse2D.Double(0, 0, r * 2, r * 2));
			int in = Math.max(0, r - stroke);
			if (in > 0) a.subtract(new Area(new Ellipse2D.Double(stroke, stroke, in * 2, in * 2)));
			g.fill(a);
		});
	}

	/** What's left of a {@code 2r} square outside a circle: drawn in the colour behind, it rounds a square's corners. */
	static Mask outside(int r) {
		return mask("out" + r, r * 2, r * 2, g -> {
			Area a = new Area(new java.awt.Rectangle(0, 0, r * 2, r * 2));
			a.subtract(new Area(new Ellipse2D.Double(0, 0, r * 2, r * 2)));
			g.fill(a);
		});
	}

	/** An icon stroked at {@code px} pixels square (its 2-unit line scaled with it). */
	static Mask icon(DeskIcons icon, int px) {
		return mask("icon_" + icon.name().toLowerCase(Locale.ROOT) + "_" + px, px, px, g -> {
			g.scale(px / 24.0, px / 24.0);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (Shape s : icon.shapes()) g.draw(s);
		});
	}

	private interface Painter {
		void paint(Graphics2D g);
	}

	private static Mask mask(String key, int w, int h, Painter painter) {
		Mask m = MASKS.get(key);
		if (m == null) {
			m = make(w, h, painter);
			MASKS.put(key, m);
		}
		return m;
	}

	/** Makes the atlas, with its white patch, the first time a mask is needed. */
	private static void ensure() {
		if (texture != null) return;
		atlas = new NativeImage(SIZE, SIZE, true);
		for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) atlas.setPixelABGR(x, y, 0xFFFFFFFF);
		nextX = 6;
		rowH = 6;
		texture = new DynamicTexture(() -> "Lazarus desk masks", atlas);
		Minecraft.getInstance().getTextureManager().register(ID, texture);
		// Nearest: every mask is drawn at its own size, pixel for pixel.
		setup = TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
	}

	/** Paints a mask and puts it on the next free spot of the atlas (a pixel of space around each), then uploads. */
	private static Mask make(int w, int h, Painter painter) {
		ensure();
		w = Math.max(1, w);
		h = Math.max(1, h);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(Color.WHITE);
		painter.paint(g);
		g.dispose();
		if (nextX + w + 1 > SIZE) {
			nextX = 0;
			nextY += rowH + 1;
			rowH = 0;
		}
		if (nextY + h + 1 > SIZE) {
			// Full: start over. Everything made so far is remade on its next use.
			Lazarus.LOG.warn("The desk's mask atlas is full; starting it again");
			MASKS.clear();
			atlas.fillRect(0, 0, SIZE, SIZE, 0);
			for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) atlas.setPixelABGR(x, y, 0xFFFFFFFF);
			nextX = 6;
			nextY = 0;
			rowH = 6;
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int alpha = img.getRGB(x, y) >>> 24;
				atlas.setPixelABGR(nextX + x, nextY + y, alpha << 24 | 0xFFFFFF);
			}
		}
		Mask m = new Mask(nextX, nextY, w, h);
		nextX += w + 1;
		rowH = Math.max(rowH, h);
		texture.upload();
		return m;
	}
}
