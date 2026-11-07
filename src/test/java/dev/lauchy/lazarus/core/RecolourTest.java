package dev.lauchy.lazarus.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Restyling words and names inside a line the server sent. */
class RecolourTest {
	private static final Pattern STAFF = Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:lauchy|fcxxv)(?![A-Za-z0-9_])");

	/** Each piece of a component as "text", or "text*" when it's red. */
	private static List<String> pieces(Component c) {
		List<String> out = new ArrayList<>();
		c.visit((style, text) -> {
			if (!text.isEmpty()) out.add(text + (style.getColor() != null && style.getColor().getValue() == ChatFormatting.RED.getColor() ? "*" : ""));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	@Test
	void namesAfterTheColonAreRestyledAndTheSendersIsnt() {
		Component line = Component.literal("[MOD] fcxxv: ask Lauchy, lauchy_alt isn't him");
		Component out = Recolour.afterColon(line, STAFF, w -> s -> s.withColor(ChatFormatting.RED));
		assertEquals(List.of("[MOD] fcxxv:", " ask ", "Lauchy*", ", lauchy_alt isn't him"), pieces(out));
	}

	@Test
	void everywhereColoursAPlayerListToo() {
		Component line = Component.literal("Players online (3): fcxxv, Steve, Lauchy");
		Component out = Recolour.everywhere(line, STAFF, w -> s -> s.withColor(ChatFormatting.RED));
		assertEquals(List.of("Players online (3): ", "fcxxv*", ", Steve, ", "Lauchy*"), pieces(out));
	}

	@Test
	void theSendersNameIsReplacedOnce() {
		Component line = Component.literal("[MOD] fcxxv: fcxxv is me");
		Component out = Recolour.name(line, "fcxxv", n -> n.copy().withStyle(ChatFormatting.RED));
		assertEquals(List.of("[MOD] ", "fcxxv*", ": fcxxv is me"), pieces(out));
		assertSame(line, Recolour.name(line, "Steve", n -> n), "a line without the name comes back as it was");
	}

	@Test
	void aGradientNameSentOneLetterAtATimeIsStillFoundAndReplacedWhole() {
		MutableComponent line = Component.literal("[DRAGON] ");
		for (char ch : "_nephis".toCharArray()) line.append(Component.literal(String.valueOf(ch)).withStyle(s -> s.withColor(0x40FF40 + ch)));
		line.append(Component.literal(": is strong"));
		Component out = Recolour.name(line, "_nephis", n -> n.copy().withStyle(s -> s.withUnderlined(true)));
		assertEquals("[DRAGON] _nephis: is strong", out.getString());
		List<String> underlined = new ArrayList<>();
		out.visit((style, text) -> {
			if (Boolean.TRUE.equals(style.isUnderlined()) && !text.isEmpty()) underlined.add(text);
			return Optional.empty();
		}, Style.EMPTY);
		assertEquals(List.of("_", "n", "e", "p", "h", "i", "s"), underlined, "every letter keeps its own piece and colour, and takes what the replacement adds");
		assertEquals(List.of("[DRAGON] ", "_", "n", "e", "p", "h", "i", "s", ": is strong"), pieces(out));
	}

	@Test
	void darkTextIsLiftedToReadableGrey() {
		assertEquals(0x555555, Recolour.readable(0x000000), "black becomes the game's dark grey");
		assertEquals(0xFFAA00, Recolour.readable(0xFFAA00), "gold is bright enough already");
		int navy = Recolour.readable(0x000040);
		assertTrue((navy & 0xFF) > (navy >> 16 & 0xFF), "a dark blue stays blue");
		Component line = Component.literal("Dyed: ").append(Component.literal("Dye of the Pure Black").withStyle(s -> s.withColor(0)));
		List<Integer> colours = new ArrayList<>();
		Recolour.readable(line).visit((style, text) -> {
			if (text.startsWith("Dye of")) colours.add(style.getColor().getValue());
			return Optional.empty();
		}, Style.EMPTY);
		assertEquals(List.of(0x555555), colours);
		Component bright = Component.literal("Damage: +305");
		assertSame(bright, Recolour.readable(bright), "a line with nothing dark is left as it was");
	}
}
