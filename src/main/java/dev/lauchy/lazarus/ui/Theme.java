package dev.lauchy.lazarus.ui;

/** The mod's palette, ARGB. One place so every screen and readout matches. */
public final class Theme {
	public static final int SCRIM = 0xB0101014;
	public static final int PANEL = 0xEC15151B;
	public static final int PANEL_HI = 0xF01C1C24;
	public static final int ROW_HOVER = 0x14FFFFFF;
	public static final int LINE = 0x12FFFFFF;
	public static final int FIELD = 0x26000000;
	public static final int HUD_BG = 0x80101014;
	public static final int ACCENT = 0xFFB46CFF;
	public static final int ACCENT_SOFT = 0x33B46CFF;
	/** Lilac: the accent under the mouse. */
	public static final int ACCENT_HOVER = 0xFFCDA4FF;
	public static final int TEXT = 0xFFF2F2F5;
	public static final int MUTED = 0xFF8C8C9A;
	public static final int FAINT = 0xFF5A5A66;
	public static final int GOOD = 0xFF5BE38A;
	public static final int BAD = 0xFFFF6B6B;
	public static final int WARN = 0xFFFFC857;
	public static final int OFF = 0xFF3A3A46;
	/** Lazarus+ only, per the brand guide: the plus, the badge, Plus headers. Never buttons, links or alerts. */
	public static final int HALO = 0xFFE9C27D;

	private Theme() {
	}
}
