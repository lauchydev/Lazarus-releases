package dev.lauchy.lazarus.feature.staff;

/** The staff desk's colours, as the design writes them (docs/dragsim/desk-design), with full alpha. */
final class DeskColours {
	static final int BG = 0xFF0B0B0E, CARD = 0xFF111114, FIELD = 0xFF18181B, BORDER = 0xFF27272A, SOFT = 0xFF1F1F23, INSET = 0xFF0B0B0E,
			POPUP = 0xFF16161A, HOVER = 0xFF1C1C20, BUTTON_HOVER = 0xFF232328, DIM = 0xFF3F3F46;
	static final int TEXT = 0xFFFAFAFA, SUB = 0xFFE4E4E7, SUB2 = 0xFFD4D4D8, MUTED = 0xFFA1A1AA, FAINT = 0xFF71717A;
	static final int RED = 0xFFB91C1C, RED_HOT = 0xFFDC2626, RED_EDGE = 0xFF7F1D1D, RED_BG = 0xFF1F0F10, RED_CARD_EDGE = 0xFF4C1D1D, RED_CHIP = 0xFF3A1414,
			RED_TEXT = 0xFFFCA5A5, RED_SOFT = 0xFFFECACA, RED_PICK = 0xFF2A1012, RED_DOT = 0xFFF87171;
	static final int VIOLET_BG = 0xFF2A1D42, VIOLET_EDGE = 0xFF3B2A5C, VIOLET_TEXT = 0xFFE9D5FF, VIOLET = 0xFFC4A1FF, SELECTED = 0xFF1D1630,
			VIOLET_SOFT = 0xFFC4B5FD;
	static final int GREEN_BG = 0xFF0F2E1C, GREEN = 0xFF4ADE80, GREEN_TEXT = 0xFF86EFAC;
	static final int ORANGE = 0xFFFB923C, ORANGE_TEXT = 0xFFFDBA74, ORANGE_BG = 0xFF3A2010, AMBER_BG = 0xFF3A2610, GOLD = 0xFFFCD34D;
	static final int PINK = 0xFFF472B6, PINK_TEXT = 0xFFF9A8D4, PINK_BG = 0xFF3A1530;
	static final int YELLOW = 0xFFFDE047, YELLOW_BG = 0xFF33290A, WARN_BTN = 0xFFA16207;
	static final int BLUE = 0xFF7DD3FC, BLUE_BG = 0xFF0F2A3A, PURPLE = 0xFFD8B4FE, PURPLE_BG = 0xFF2E1A47, DRAGON = 0xFFC084FC;

	private DeskColours() {
	}

	/** {@code a} blended toward {@code b} by {@code t} (0 to 1), alpha included. */
	static int mix(int a, int b, float t) {
		if (t <= 0) return a;
		if (t >= 1) return b;
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
