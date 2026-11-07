package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {
	/** The brand's Violet, only ever on the word "Lazarus"; everything else purple is the game's dark purple. */
	private static final int VIOLET = 0xB46CFF, PURPLE = 0xAA00AA, GREY = 0x8C8C9A, GOLD = 0xE9C27D;
	/** The game's own green, never the brand's. */
	private static final int GREEN = 0x55FF55, RED = 0xFF6B6B, ORANGE = 0xFF8A3D, WHITE = 0xF2F2F5;

	/** One run of text in a message and the style it's drawn with. */
	private record Run(String text, Style style) {
		int colour() {
			return style.getColor() == null ? -1 : style.getColor().getValue();
		}
	}

	/** Every button looks the same: underlined dark purple, never bold, with no brackets in its text. */
	private static void assertButton(Run r) {
		assertEquals(PURPLE, r.colour());
		assertFalse(r.style().isBold(), "a button isn't bold");
		assertTrue(r.style().isUnderlined(), "a button is underlined");
		assertFalse(r.text().contains("[") || r.text().contains("]"), "a button has no brackets");
	}

	private static List<Run> runs(Component c) {
		List<Run> out = new ArrayList<>();
		c.visit((style, text) -> {
			if (!text.isEmpty()) out.add(new Run(text, style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	@Test
	void startsWithThePurpleLazarusAndAGreyRoundTippedArrow() {
		Component c = Message.lazarus().info("Hello.").build();
		assertEquals("Lazarus ➜ Hello.", c.getString());
		List<Run> runs = runs(c);
		assertEquals("Lazarus", runs.get(0).text());
		assertEquals(VIOLET, runs.get(0).colour());
		assertEquals(" \u279C ", runs.get(1).text());
		assertEquals(GREY, runs.get(1).colour());
	}

	@Test
	void lazarusPlusMessagesHaveAGoldPlus() {
		Component c = Message.plus().info("Hello.").build();
		assertEquals("Lazarus+ ➜ Hello.", c.getString());
		List<Run> runs = runs(c);
		assertEquals(List.of("Lazarus", "+", " ➜ "), runs.subList(0, 3).stream().map(Run::text).toList());
		assertEquals(List.of(VIOLET, GOLD, GREY), runs.subList(0, 3).stream().map(Run::colour).toList());
	}

	@Test
	void aMessageOverSeveralLinesHasOnePrefixAndAThinAshBarOnEachFollowingLine() {
		Component c = Message.lazarus().info("One").line().info("Two").line().info("Three").build();
		assertEquals("Lazarus ➜ One\n│ Two\n│ Three", c.getString());
		List<Run> bars = runs(c).stream().filter(r -> r.text().contains("│")).toList();
		assertEquals(2, bars.size());
		for (Run bar : bars) assertEquals(0x5A5A66, bar.colour());
	}

	@Test
	void eachRoleHasItsColour() {
		List<Run> runs = runs(Message.lazarus().success("done").error("broke").warning("careful").info("note").highlight("detail").build());
		assertEquals(List.of("done", "broke", "careful", "note", "detail"), runs.subList(2, 7).stream().map(Run::text).toList());
		assertEquals(List.of(GREEN, RED, ORANGE, WHITE, PURPLE), runs.subList(2, 7).stream().map(Run::colour).toList());
	}

	@Test
	void warningsAreAnOrangeClearlyApartFromTheLazarusPlusGold() {
		int warning = runs(Message.lazarus().warning("careful").build()).get(2).colour();
		int r = warning >> 16 & 0xFF, g = warning >> 8 & 0xFF, b = warning & 0xFF;
		int gr = GOLD >> 16 & 0xFF, gg = GOLD >> 8 & 0xFF, gb = GOLD & 0xFF;
		// Orange: full red, about half green, little blue; and far from the gold on at least one channel.
		assertTrue(r > 0xE0 && g > 0x60 && g < 0xB0 && b < 0x60, String.format("#%06X isn't orange", warning));
		assertTrue(Math.abs(r - gr) + Math.abs(g - gg) + Math.abs(b - gb) > 120, String.format("#%06X is too close to the gold", warning));
	}

	@Test
	void aLinkIsAButtonOpensItsPageAndSaysWhatClickingDoes() {
		Run link = runs(Message.lazarus().info("Join ").link("Lazarus", "Click to join the Discord", "https://discord.gg/FBpjcYGNKW").build()).get(3);
		assertEquals("Lazarus", link.text());
		assertButton(link);
		assertEquals(new ClickEvent.OpenUrl(URI.create("https://discord.gg/FBpjcYGNKW")), link.style().getClickEvent());
		assertEquals("Click to join the Discord", ((HoverEvent.ShowText) link.style().getHoverEvent()).value().getString());
	}

	@Test
	void aCommandIsAButtonRunsItAndSaysWhatClickingDoes() {
		Run button = runs(Message.plus().command("Extend", "Click to extend Lazarus+", "/lazarus upgrade").build()).get(3);
		assertEquals("Extend", button.text());
		assertButton(button);
		assertEquals(new ClickEvent.RunCommand("/lazarus upgrade"), button.style().getClickEvent());
		assertEquals("Click to extend Lazarus+", ((HoverEvent.ShowText) button.style().getHoverEvent()).value().getString());
	}

	@Test
	void clickableTextAlwaysHasAHover() {
		assertThrows(IllegalArgumentException.class, () -> Message.lazarus().link("here", "", "https://plus.lazaruss.co"));
		assertThrows(IllegalArgumentException.class, () -> Message.lazarus().command("here", null, "/lazarus plus"));
	}

	@Test
	void aSpacerLineIsJustTheBar() {
		Component c = Message.lazarus().info("One").line().line().info("Two").build();
		assertEquals("Lazarus ➜ One\n│ \n│ Two", c.getString());
	}

	@Test
	void aCopyIsAButtonCopiesItsValueAndSaysWhatClickingDoes() {
		Run code = runs(Message.lazarus().info("Your invite code: ").copy("ABC123", "Click to copy the code", "ABC123").build()).get(3);
		assertEquals("ABC123", code.text());
		assertButton(code);
		assertEquals(new ClickEvent.CopyToClipboard("ABC123"), code.style().getClickEvent());
		assertEquals("Click to copy the code", ((HoverEvent.ShowText) code.style().getHoverEvent()).value().getString());
		assertThrows(IllegalArgumentException.class, () -> Message.lazarus().copy("x", " ", "x"));
	}

	@Test
	void aHudPanelInChatHasItsTitleAsAHeadingAndEachRowOnALineInTheHudsColours() {
		Component c = Message.panel(List.of("§5§lGolem Loot §7Session", "§7Kills §f12", "§f3x §7Tier Boost Core §8(1 in 4)", "§7Value §a1.1M §8(NPC)")).build();
		assertEquals("Lazarus ➜ Golem Loot Session\n│ Kills 12\n│ 3x Tier Boost Core (1 in 4)\n│ Value 1.1M (NPC)", c.getString());
		Run title = runs(c).get(2);
		assertEquals(PURPLE, title.colour());
		assertTrue(title.style().isBold());
		Run kills = runs(c).stream().filter(r -> r.text().equals("Kills ")).findFirst().orElseThrow();
		assertEquals(GREY, kills.colour());
		Run twelve = runs(c).stream().filter(r -> r.text().equals("12")).findFirst().orElseThrow();
		assertEquals(WHITE, twelve.colour());
		Run coins = runs(c).stream().filter(r -> r.text().equals("1.1M ")).findFirst().orElseThrow();
		assertEquals(GREEN, coins.colour());
		Run npc = runs(c).stream().filter(r -> r.text().equals("(NPC)")).findFirst().orElseThrow();
		assertEquals(0x5A5A66, npc.colour());
	}

	@Test
	void theBrandVioletIsOnlyEverOnTheWordLazarus() {
		Component c = Message.plus().heading("Title").highlight("detail").success("ok").command("Go", "Click to go", "/go").line()
				.rows(List.of("§5§lBoss §7Session", "§7Value §a1M")).build();
		List<Run> runs = runs(c);
		assertEquals(VIOLET, runs.get(0).colour());
		for (Run r : runs.subList(1, runs.size())) {
			assertFalse(r.colour() == VIOLET, "'" + r.text() + "' is in the brand violet");
			assertFalse(r.colour() == 0x5BE38A, "'" + r.text() + "' is in the brand green");
		}
	}

	@Test
	void aHeadingKeepsAMobsOwnColourFromItsCodes() {
		List<Run> runs = runs(Message.lazarus().heading("§5§lEndstone Protector §7All time").build());
		Run boss = runs.stream().filter(r -> r.text().equals("Endstone Protector ")).findFirst().orElseThrow();
		assertEquals(PURPLE, boss.colour());
		assertTrue(boss.style().isBold());
		Run period = runs.stream().filter(r -> r.text().equals("All time")).findFirst().orElseThrow();
		assertEquals(GREY, period.colour());
		assertFalse(period.style().isBold());
	}

	@Test
	void aHeadingIsBoldPurpleAndMutedTextIsFog() {
		List<Run> runs = runs(Message.lazarus().heading("Boss drops").muted(" · All time").build());
		assertEquals(PURPLE, runs.get(2).colour());
		assertTrue(runs.get(2).style().isBold());
		assertEquals(GREY, runs.get(3).colour());
		assertFalse(runs.get(3).style().isBold());
	}

	@Test
	void aRowsBoldTitleStopsAtTheNextColourCode() {
		List<Run> runs = runs(Message.lazarus().rows(List.of("§d§lVoidgloom §7Session")).build());
		Run title = runs.stream().filter(r -> r.text().equals("Voidgloom ")).findFirst().orElseThrow();
		assertTrue(title.style().isBold());
		Run period = runs.stream().filter(r -> r.text().equals("Session")).findFirst().orElseThrow();
		assertFalse(period.style().isBold());
		assertEquals(GREY, period.colour());
	}

	@Test
	void aFolderIsAButtonOpensItAndSaysWhatClickingDoes() {
		Path mods = Path.of("mods");
		Run folder = runs(Message.lazarus().folder("mods folder", "Click to open the mods folder", mods).build()).get(2);
		assertEquals("mods folder", folder.text());
		assertButton(folder);
		assertEquals(new ClickEvent.OpenFile(mods), folder.style().getClickEvent());
		assertEquals("Click to open the mods folder", ((HoverEvent.ShowText) folder.style().getHoverEvent()).value().getString());
	}
}
