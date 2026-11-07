package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.Lazarus;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The staff desk's type scale (docs/dragsim/desk-design): every family, weight and size the design uses, and nothing
 * else. Each is a font definition per desk scale under assets/lazarus/font/desk ({@code i600_13_15} is Inter 600 at
 * 13 px for a desk drawn at 1.5×), sized in screen pixels so glyphs are drawn one to one. A line's height and where
 * its baseline sits follow the browser's "normal" line height: the font's ascent and descent, each rounded.
 */
enum DeskType {
	I400_10(true, 400, 10), I400_11(true, 400, 11), I400_12(true, 400, 12), I400_13(true, 400, 13),
	I500_11(true, 500, 11), I500_12(true, 500, 12), I500_13(true, 500, 13), I500_14(true, 500, 14),
	I600_10(true, 600, 10), I600_11(true, 600, 11), I600_12(true, 600, 12), I600_13(true, 600, 13), I600_14(true, 600, 14),
	I600_15(true, 600, 15), I600_16(true, 600, 16), I700_20(true, 700, 20),
	M500_11(false, 500, 11), M500_12(false, 500, 12), M500_13(false, 500, 13), M600_12(false, 600, 12), M600_13(false, 600, 13),
	M600_18(false, 600, 18), M700_22(false, 700, 22), M700_26(false, 700, 26);

	/** The desk scales with font definitions: 0.75× (small windows), 1×, 1.5× and 2×. */
	static final float[] SCALES = Scales.ALL;
	/** Inter's and JetBrains Mono's ascent and descent, per em. */
	private static final float INTER_ASCENT = 0.96875f, INTER_DESCENT = 0.2412109f, MONO_ASCENT = 1.02f, MONO_DESCENT = 0.3f;

	final boolean inter;
	final int weight, size;
	private final FontDescription[] fonts = new FontDescription[Scales.ALL.length];

	DeskType(boolean inter, int weight, int size) {
		this.inter = inter;
		this.weight = weight;
		this.size = size;
		for (int i = 0; i < Scales.ALL.length; i++) {
			String id = "desk/" + (inter ? "i" : "m") + weight + "_" + size + "_" + Math.round(Scales.ALL[i] * 10);
			fonts[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Lazarus.ID, id));
		}
	}

	/** The font definition for a desk drawn at {@code scale} (one of {@link #SCALES}). */
	FontDescription font(float scale) {
		for (int i = SCALES.length - 1; i >= 0; i--) if (scale >= SCALES[i] - 0.01f) return fonts[i];
		return fonts[0];
	}

	/** The ascent at {@code scale}, in screen pixels, rounded as the browser rounds it. */
	int ascent(float scale) {
		return Math.round((inter ? INTER_ASCENT : MONO_ASCENT) * size * scale);
	}

	/** The line height at {@code scale} ("normal"), in screen pixels. */
	int lineHeight(float scale) {
		return ascent(scale) + Math.round((inter ? INTER_DESCENT : MONO_DESCENT) * size * scale);
	}

	/** The line height at 1×, in design pixels. */
	int lineHeight() {
		return lineHeight(1f);
	}

	/** Kept apart so the constants' constructor can read it. */
	private static final class Scales {
		static final float[] ALL = {0.75f, 1f, 1.5f, 2f};
	}
}
