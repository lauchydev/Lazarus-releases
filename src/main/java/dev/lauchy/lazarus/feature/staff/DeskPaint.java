package dev.lauchy.lazarus.feature.staff;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the staff desk in the design's own units (docs/dragsim/desk-design: CSS pixels on a 1280×760 page) onto whole
 * screen pixels: {@code scale} screen pixels to the design pixel, from {@code (ox, oy)}. Every edge is rounded to a
 * screen pixel, corners and borders are anti-aliased masks, text is the desk's own fonts at screen size. The pose this
 * draws under maps one unit to one screen pixel (or less when the window is too small for the desk at 1×).
 * <p>
 * Each shape goes to Minecraft as one element ({@link DeskShape}) and each run of text as one per font page
 * ({@link DeskText}): placing an element among the others is the costly part of drawing a GUI, so the desk hands over
 * as few as it can. {@link #group()} folds several shapes into one element.
 */
final class DeskPaint {
	final GuiGraphicsExtractor g;
	final Font font;
	/** Screen pixels per design pixel, and where the design's origin is on screen. */
	final float scale, ox, oy;
	/** Screen pixels to GUI units, for clipping (which works in GUI units). */
	private final float toGui;
	/** Everything drawn is faded by this. */
	float alpha = 1f;
	private final List<int[]> clips = new ArrayList<>();
	/** The shape being collected, and how many groups are open around it (the shape is handed over when the last closes). */
	private DeskShape.Builder shape;
	private int depth;

	/** A cached piece of text in a type: its component for drawing and, once measured, its width in screen pixels. */
	private static final class Text {
		final Component component;
		float width = -1;

		Text(Component component) {
			this.component = component;
		}
	}

	private static final Map<String, Text> TEXT = new HashMap<>();
	private static final Map<String, String> FITS = new HashMap<>();
	private static final Map<String, List<String>> WRAPS = new HashMap<>();
	private static final int CACHE_MAX = 6000;

	DeskPaint(GuiGraphicsExtractor g, Font font, float scale, float ox, float oy, float toGui) {
		this.g = g;
		this.font = font;
		this.scale = scale;
		this.ox = ox;
		this.oy = oy;
		this.toGui = toGui;
	}

	int sx(float x) {
		return Math.round(ox + x * scale);
	}

	int sy(float y) {
		return Math.round(oy + y * scale);
	}

	/** A length in screen pixels, at least {@code min}. */
	private int px(float v, int min) {
		return Math.max(min, Math.round(v * scale));
	}

	private int fade(int colour) {
		if (alpha >= 1f) return colour;
		int a = Math.round((colour >>> 24) * alpha);
		return a << 24 | colour & 0xFFFFFF;
	}

	// ---- elements ----------------------------------------------------------------------------

	/** From here until {@link #endGroup()}, every shape drawn is one element: for a shadow's rings, a fade's lines. */
	void group() {
		begin();
	}

	void endGroup() {
		end();
	}

	private void begin() {
		if (depth++ == 0) shape = new DeskShape.Builder();
	}

	private void end() {
		if (--depth == 0) {
			flush();
			shape = null;
		}
	}

	/** Hands the collected shape to Minecraft, if there is one, and starts afresh. */
	private void flush() {
		if (shape == null || shape.isEmpty()) return;
		g.guiRenderState.addGuiElement(shape.build(RenderPipelines.GUI_TEXTURED, DeskMasks.setup(), new Matrix3x2f(g.pose()), g.scissorStack.peek()));
		shape = new DeskShape.Builder();
	}

	/** A quad of the mask atlas's white patch: a plain fill. */
	private void fill(int x0, int y0, int x1, int y1, int c) {
		if (x1 <= x0 || y1 <= y0) return;
		begin();
		shape.add(x0, y0, x1, y1, DeskMasks.WHITE, DeskMasks.WHITE, DeskMasks.WHITE, DeskMasks.WHITE, c);
		end();
	}

	/** A quad showing part of a mask, {@code (mu, mv)} to {@code (mu + w, mv + h)} on the atlas, at {@code (x, y)}. */
	private void mask(DeskMasks.Mask m, int x, int y, int mu, int mv, int w, int h, int c) {
		if (w <= 0 || h <= 0) return;
		float s = DeskMasks.SIZE;
		begin();
		shape.add(x, y, x + w, y + h, (m.u() + mu) / s, (m.v() + mv) / s, (m.u() + mu + w) / s, (m.v() + mv + h) / s, c);
		end();
	}

	// ---- boxes -------------------------------------------------------------------------------

	/** A plain rectangle. */
	void rect(float x, float y, float w, float h, int colour) {
		if ((colour >>> 24) == 0) return;
		fill(sx(x), sy(y), sx(x + w), sy(y + h), fade(colour));
	}

	/** A rectangle with corners of radius {@code r} (clamped to half the shorter side, so 99 is a pill). */
	void round(float x, float y, float w, float h, float r, int colour) {
		if ((colour >>> 24) == 0) return;
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		roundPx(x0, y0, x1, y1, Math.round(r * scale), fade(colour));
	}

	/** A one-pixel border (one screen pixel per design pixel, at least one) inside a rounded rectangle. */
	void border(float x, float y, float w, float h, float r, int colour) {
		if ((colour >>> 24) == 0) return;
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		int b = px(1, 1);
		int rp = Math.min(Math.round(r * scale), Math.min(x1 - x0, y1 - y0) / 2);
		int c = fade(colour);
		begin();
		if (rp <= b) {
			fill(x0, y0, x1, y0 + b, c);
			fill(x0, y1 - b, x1, y1, c);
			fill(x0, y0 + b, x0 + b, y1 - b, c);
			fill(x1 - b, y0 + b, x1, y1 - b, c);
		} else {
			quadrants(DeskMasks.ring(rp, b), x0, y0, x1, y1, rp, c);
			fill(x0 + rp, y0, x1 - rp, y0 + b, c);
			fill(x0 + rp, y1 - b, x1 - rp, y1, c);
			fill(x0, y0 + rp, x0 + b, y1 - rp, c);
			fill(x1 - b, y0 + rp, x1, y1 - rp, c);
		}
		end();
	}

	/**
	 * A filled box with a border: the design's cards, buttons and pills. The fill sits inside the border rather than under
	 * it: Minecraft batches what's drawn and doesn't always keep a thin line above a fill drawn before it, so nothing here
	 * relies on drawing order. Fill and border are one element.
	 */
	void box(float x, float y, float w, float h, float r, int fill, int edge) {
		if ((edge >>> 24) == 0) {
			round(x, y, w, h, r, fill);
			return;
		}
		begin();
		if ((fill >>> 24) != 0) {
			int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
			int b = px(1, 1);
			int rp = Math.min(Math.round(r * scale), Math.min(x1 - x0, y1 - y0) / 2);
			roundPx(x0 + b, y0 + b, x1 - b, y1 - b, Math.max(0, rp - b), fade(fill));
		}
		border(x, y, w, h, r, edge);
		end();
	}

	/** A rounded rectangle in screen pixels. */
	private void roundPx(int x0, int y0, int x1, int y1, int rp, int c) {
		if (x1 <= x0 || y1 <= y0) return;
		rp = Math.min(rp, Math.min(x1 - x0, y1 - y0) / 2);
		if (rp <= 0) {
			fill(x0, y0, x1, y1, c);
			return;
		}
		begin();
		quadrants(DeskMasks.disc(rp), x0, y0, x1, y1, rp, c);
		fill(x0 + rp, y0, x1 - rp, y0 + rp, c);
		fill(x0, y0 + rp, x1, y1 - rp, c);
		fill(x0 + rp, y1 - rp, x1 - rp, y1, c);
		end();
	}

	/** A dashed one-pixel border, as the browser draws one: dashes three times the line's width with equal gaps. */
	void dashed(float x, float y, float w, float h, float r, int colour) {
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		int b = px(1, 1), dash = b * 3;
		int rp = Math.min(Math.round(r * scale), Math.min(x1 - x0, y1 - y0) / 2);
		int c = fade(colour);
		begin();
		if (rp > b) quadrants(DeskMasks.ring(rp, b), x0, y0, x1, y1, rp, c);
		for (int i = x0 + rp; i < x1 - rp; i += dash * 2) {
			fill(i, y0, Math.min(i + dash, x1 - rp), y0 + b, c);
			fill(i, y1 - b, Math.min(i + dash, x1 - rp), y1, c);
		}
		for (int i = y0 + rp; i < y1 - rp; i += dash * 2) {
			fill(x0, i, x0 + b, Math.min(i + dash, y1 - rp), c);
			fill(x1 - b, i, x1, Math.min(i + dash, y1 - rp), c);
		}
		end();
	}

	/** A soft shadow under a box, as {@code 0 dy blur rgba(0,0,0,a)}: rings of falling strength, one element. */
	void shadow(float x, float y, float w, float h, float r, float dy, float blur, float strength) {
		int steps = 8;
		begin();
		for (int i = steps; i >= 1; i--) {
			float grow = blur * i / steps;
			float a = strength * (1f - (float) i / (steps + 1)) / steps * 2f;
			round(x - grow / 2, y + dy - grow / 2, w + grow, h + grow, r + grow / 2, Math.round(Math.min(1f, a) * 255) << 24);
		}
		end();
	}

	/** The four quarters of a {@code 2r} mask at the corners of a box, tinted. */
	private void quadrants(DeskMasks.Mask m, int x0, int y0, int x1, int y1, int r, int c) {
		begin();
		mask(m, x0, y0, 0, 0, r, r, c);
		mask(m, x1 - r, y0, r, 0, r, r, c);
		mask(m, x0, y1 - r, 0, r, r, r, c);
		mask(m, x1 - r, y1 - r, r, r, r, r, c);
		end();
	}

	// ---- text --------------------------------------------------------------------------------

	private Text text(String text, DeskType type) {
		String key = type.ordinal() + "|" + scale + "|" + text;
		Text t = TEXT.get(key);
		if (t == null) {
			if (TEXT.size() > CACHE_MAX) TEXT.clear();
			t = new Text(Component.literal(text).withStyle(Style.EMPTY.withFont(type.font(scale))));
			TEXT.put(key, t);
		}
		return t;
	}

	/** How wide {@code text} draws, in design pixels; {@code tracking} is letter spacing in em. */
	float width(String text, DeskType type, float tracking) {
		Text t = text(text, type);
		if (t.width < 0) {
			t.width = font.getSplitter().stringWidth(t.component);
		}
		float w = t.width / scale;
		return w + (tracking == 0 ? 0 : text.length() * tracking * type.size);
	}

	float width(String text, DeskType type) {
		return width(text, type, 0);
	}

	/** Text whose line box starts at {@code top} (the browser's "normal" line height): its baseline sits the font's ascent below. */
	void text(String text, float x, float top, DeskType type, int colour) {
		text(text, x, top, type, colour, 0);
	}

	void text(String text, float x, float top, DeskType type, int colour, float tracking) {
		if (text.isEmpty() || (colour >>> 24) == 0) return;
		int c = fade(colour);
		if ((c >>> 24) <= 4) return;
		int baseline = sy(top) + type.ascent(scale);
		// Anything collected so far goes under the text, as it was drawn.
		flush();
		DeskText.Collector glyphs = new DeskText.Collector();
		if (tracking == 0) {
			font.prepareText(text(text, type).component.getVisualOrderText(), sx(x), baseline - 7, c, false, false, 0).visit(glyphs);
		} else {
			float at = ox + x * scale, extra = tracking * type.size * scale;
			for (int i = 0; i < text.length(); ) {
				int cp = text.codePointAt(i);
				String ch = new String(Character.toChars(cp));
				font.prepareText(text(ch, type).component.getVisualOrderText(), Math.round(at), baseline - 7, c, false, false, 0).visit(glyphs);
				at += width(ch, type) * scale + extra;
				i += Character.charCount(cp);
			}
		}
		if (glyphs.isEmpty()) return;
		for (DeskText run : glyphs.build(new Matrix3x2f(g.pose()), g.scissorStack.peek())) {
			g.guiRenderState.addGuiElement(run);
		}
	}

	/** Text cut to {@code max} design pixels with an ellipsis, when it doesn't fit. */
	String fit(String text, DeskType type, float max) {
		if (width(text, type) <= max) return text;
		String key = type.ordinal() + "|" + scale + "|" + max + "|" + text;
		String out = FITS.get(key);
		if (out == null) {
			if (FITS.size() > CACHE_MAX) FITS.clear();
			String t = text;
			while (t.length() > 1 && width(t + "…", type) > max) t = t.substring(0, t.length() - 1);
			out = t.stripTrailing() + "…";
			FITS.put(key, out);
		}
		return out;
	}

	/** {@code text} broken into lines no wider than {@code max}, at spaces (a word longer than a line is cut). */
	List<String> wrap(String text, DeskType type, float max) {
		String key = type.ordinal() + "|" + scale + "|" + max + "|" + text;
		List<String> lines = WRAPS.get(key);
		if (lines != null) return lines;
		if (WRAPS.size() > CACHE_MAX) WRAPS.clear();
		lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (width(tryLine, type) <= max || line.isEmpty()) {
				line.setLength(0);
				line.append(width(tryLine, type) <= max ? tryLine : fit(tryLine, type, max));
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(width(word, type) <= max ? word : fit(word, type, max));
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		lines = List.copyOf(lines);
		WRAPS.put(key, lines);
		return lines;
	}

	// ---- icons and faces ---------------------------------------------------------------------

	/** An icon {@code size} design pixels square. */
	void icon(DeskIcons icon, float x, float y, float size, int colour) {
		int p = px(size, 4);
		mask(DeskMasks.icon(icon, p), sx(x), sy(y), 0, 0, p, p, fade(colour));
	}

	/** A player's face, its corners rounded to {@code r} against {@code behind} (the colour it sits on). */
	void face(String name, float x, float y, float size, float r, int behind) {
		Minecraft mc = Minecraft.getInstance();
		PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(name);
			if (info != null) skin = info.getSkin();
		}
		int x0 = sx(x), y0 = sy(y), s = px(size, 1);
		if (alpha < 0.99f) {
			round(x, y, size, size, r, 0xFF3F3F46);
			return;
		}
		flush();
		// The head and its hat layer, one element on the skin's texture (as PlayerFaceExtractor draws them).
		AbstractTexture tex = mc.getTextureManager().getTexture(skin.body().texturePath());
		DeskShape.Builder b = new DeskShape.Builder();
		b.add(x0, y0, x0 + s, y0 + s, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, -1);
		b.add(x0, y0, x0 + s, y0 + s, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, -1);
		g.guiRenderState.addGuiElement(b.build(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()),
				new Matrix3x2f(g.pose()), g.scissorStack.peek()));
		int rp = Math.min(Math.round(r * scale), s / 2);
		if (rp > 0 && (behind >>> 24) != 0) quadrants(DeskMasks.outside(rp), x0, y0, x0 + s, y0 + s, rp, behind);
	}

	// ---- clipping ----------------------------------------------------------------------------

	/** Clips to a box, inward to whole GUI units (clipping can't be finer), until {@link #unclip()}. */
	void clip(float x, float y, float w, float h) {
		flush();
		int gx0 = (int) Math.ceil(sx(x) * toGui - 0.001), gy0 = (int) Math.ceil(sy(y) * toGui - 0.001);
		int gx1 = (int) Math.floor(sx(x + w) * toGui + 0.001), gy1 = (int) Math.floor(sy(y + h) * toGui + 0.001);
		var pose = g.pose();
		pose.pushMatrix();
		pose.identity();
		g.enableScissor(gx0, gy0, Math.max(gx0, gx1), Math.max(gy0, gy1));
		pose.popMatrix();
		clips.add(new int[]{gx0, gy0, gx1, gy1});
	}

	void unclip() {
		flush();
		g.disableScissor();
		if (!clips.isEmpty()) clips.removeLast();
	}
}
