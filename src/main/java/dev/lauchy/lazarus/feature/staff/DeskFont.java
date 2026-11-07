package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.Lazarus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The staff desk's typeface: Inter (assets/lazarus/font/inter, SIL Open Font Licence), in place of Minecraft's
 * blocky pixel font. Bold text is Inter SemiBold rather than the game's doubled pixels. Each GUI scale has its own
 * font definition drawn at that many pixels per unit, so glyphs land on whole screen pixels and stay sharp.
 * Glyphs Inter lacks fall back to the game's font.
 */
final class DeskFont {
	private static final int SCALES = 4;
	private static final FontDescription[] REGULAR = fonts("desk_"), BOLD = fonts("desk_bold_");
	/** Converted strings, keyed by scale and text: the desk draws the same few hundred every frame. */
	private static final Map<String, Component> CACHE = new HashMap<>();

	private DeskFont() {
	}

	private static FontDescription[] fonts(String prefix) {
		FontDescription[] out = new FontDescription[SCALES];
		for (int i = 0; i < SCALES; i++) out[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Lazarus.ID, prefix + (i + 1)));
		return out;
	}

	/**
	 * Screen pixels per unit the desk is drawing at, when it draws smaller than the game's GUI scale; 0 means
	 * the GUI scale. The desk sets it for its frame and clears it after.
	 */
	static int pixels;

	/** Which of the per-scale definitions fits what's being drawn now. */
	private static int scale() {
		return pixels > 0 ? Math.clamp(pixels, 1, SCALES) - 1 : guiScale();
	}

	private static int guiScale() {
		return Math.clamp((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()), 1, SCALES) - 1;
	}

	/** A tooltip's text: tooltips draw after the desk, at the game's own scale. */
	static Component forTooltip(String text) {
		int before = pixels;
		pixels = 0;
		try {
			return of(text);
		} finally {
			pixels = before;
		}
	}

	/** {@code text} with its colour codes (§a, §l, §r…) turned into Inter text. */
	static Component of(String text) {
		int s = scale();
		if (CACHE.size() > 4096) CACHE.clear();
		return CACHE.computeIfAbsent(s + "\0" + text, key -> {
			MutableComponent out = Component.empty();
			append(out, text, Style.EMPTY, s);
			return out;
		});
	}

	/** A styled component (a server's tab-list name, say) in Inter, its colours kept. */
	static Component of(Component text) {
		int s = scale();
		MutableComponent out = Component.empty();
		text.visit((style, part) -> {
			append(out, part, style, s);
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Splits {@code text} on colour codes the way the game would, each run in Inter. */
	private static void append(MutableComponent out, String text, Style base, int s) {
		Style style = base;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			ChatFormatting code = c == '§' && i + 1 < text.length() ? ChatFormatting.getByCode(text.charAt(i + 1)) : null;
			if (code == null) {
				run.append(c);
				continue;
			}
			flush(out, run, style, s);
			if (code == ChatFormatting.RESET) style = base;
			else if (code.isColor()) style = Style.EMPTY.withColor(colour(code)); // a colour clears bold and the rest, as in game
			else style = style.applyFormat(code);
			i++;
		}
		flush(out, run, style, s);
	}

	/** The game's white and greys moved onto the desk's zinc scale: its dark grey is lost on the desk's dark cards. */
	private static TextColor colour(ChatFormatting code) {
		return switch (code) {
			case WHITE -> TextColor.fromRgb(DeskUi.TEXT & 0xFFFFFF);
			case GRAY -> TextColor.fromRgb(DeskUi.MUTED & 0xFFFFFF);
			case DARK_GRAY -> TextColor.fromRgb(DeskUi.FAINT & 0xFFFFFF);
			default -> TextColor.fromLegacyFormat(code);
		};
	}

	private static void flush(MutableComponent out, StringBuilder run, Style style, int s) {
		if (run.isEmpty()) return;
		boolean ours = style.getFont() instanceof FontDescription.Resource r && r.id().getNamespace().equals(Lazarus.ID) && r.id().getPath().startsWith("desk_");
		Style inter = ours ? style : style.withBold(false).withFont(style.isBold() ? BOLD[s] : REGULAR[s]);
		out.append(Component.literal(run.toString()).withStyle(inter));
		run.setLength(0);
	}
}
