package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.IntSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Makes gradient names flow. Any run of name characters the server colours as a
 * gradient (a different colour on nearly every letter) is redrawn with that gradient
 * sliding along it, in chat and the tab list. Works for every player's gradient, with
 * no colour list to maintain.
 */
public final class NameGradients extends Feature {
	public static NameGradients INSTANCE;
	private static final int MIN_RUN = 3;

	private final IntSetting speed = add(new IntSetting("speed", "Speed", "How fast the colours move.", 50, 5, 100, "%"));

	private record Glyph(int index, Style style, int codepoint) {
	}

	/** Where gradients sit in a line: {start, end (exclusive), colourA, colourB} each. */
	private record Plan(List<Glyph> glyphs, List<int[]> runs) {
	}

	private static final Plan NONE = new Plan(List.of(), List.of());
	private final WeakHashMap<Object, Plan> cache = new WeakHashMap<>();

	public NameGradients() {
		super("nameGradients", "Animated Name Gradients", "Gradient player names flow in chat and the tab list.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
	}

	public FormattedCharSequence animate(FormattedCharSequence original) {
		if (!enabled() || original == null) return original;
		Plan plan = cache.get(original);
		if (plan == null) {
			List<Glyph> glyphs = new ArrayList<>();
			boolean complete = original.accept((index, style, cp) -> glyphs.size() < 1024 && glyphs.add(new Glyph(index, style, cp)));
			plan = complete ? plan(glyphs) : NONE;
			remember(original, plan);
		}
		if (plan.runs.isEmpty()) return original;
		Plan p = plan;
		float phase = phase();
		return sink -> {
			for (int i = 0; i < p.glyphs.size(); i++) {
				Glyph g = p.glyphs.get(i);
				if (!sink.accept(g.index, styleAt(p, i, g.style, phase), g.codepoint)) return false;
			}
			return true;
		};
	}

	public Component animate(Component original) {
		if (!enabled() || original == null) return original;
		Plan plan = cache.get(original);
		if (plan == null) {
			List<Glyph> glyphs = new ArrayList<>();
			original.visit((style, text) -> {
				text.codePoints().forEach(cp -> glyphs.add(new Glyph(glyphs.size(), style, cp)));
				return Optional.empty();
			}, Style.EMPTY);
			plan = plan(glyphs);
			remember(original, plan);
		}
		if (plan.runs.isEmpty()) return original;
		float phase = phase();
		MutableComponent out = Component.empty();
		for (int i = 0; i < plan.glyphs.size(); i++) {
			Glyph g = plan.glyphs.get(i);
			out.append(Component.literal(Character.toString(g.codepoint)).setStyle(styleAt(plan, i, g.style, phase)));
		}
		return out;
	}

	private void remember(Object key, Plan plan) {
		if (cache.size() > 1024) cache.clear();
		cache.put(key, plan);
	}

	private float phase() {
		double periodMs = 6000 - speed.get() * 50.0;
		return (float) (Util.getMillis() % (long) periodMs / periodMs);
	}

	/** Finds gradient runs: consecutive name characters with a colour change at most letters. */
	private static Plan plan(List<Glyph> glyphs) {
		List<int[]> runs = new ArrayList<>();
		int i = 0;
		while (i < glyphs.size()) {
			if (!nameChar(glyphs.get(i).codepoint) || rgb(glyphs.get(i)) < 0) {
				i++;
				continue;
			}
			int start = i, changes = 0;
			while (i + 1 < glyphs.size() && nameChar(glyphs.get(i + 1).codepoint) && rgb(glyphs.get(i + 1)) >= 0) {
				if (rgb(glyphs.get(i + 1)) != rgb(glyphs.get(i))) changes++;
				i++;
			}
			int len = i - start + 1;
			if (len >= MIN_RUN && changes >= len * 2 / 3) {
				runs.add(new int[]{start, i + 1, rgb(glyphs.get(start)), rgb(glyphs.get(i))});
			}
			i++;
		}
		return runs.isEmpty() ? NONE : new Plan(glyphs, runs);
	}

	private static Style styleAt(Plan plan, int i, Style style, float phase) {
		for (int[] r : plan.runs) {
			if (i < r[0] || i >= r[1]) continue;
			float t = (float) (i - r[0]) / Math.max(1, r[1] - r[0] - 1);
			float wave = (t - phase + 1f) % 1f;
			float mix = wave < 0.5f ? wave * 2 : 2 - wave * 2; // A to B and back, looping
			return style.withColor(TextColor.fromRgb(lerp(r[2], r[3], mix)));
		}
		return style;
	}

	private static boolean nameChar(int cp) {
		return Character.isLetterOrDigit(cp) || cp == '_';
	}

	private static int rgb(Glyph g) {
		TextColor c = g.style.getColor();
		return c == null ? -1 : c.getValue() & 0xFFFFFF;
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
