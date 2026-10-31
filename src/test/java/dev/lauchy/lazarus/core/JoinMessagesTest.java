package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinMessagesTest {
	/** One run of text in a message and the style it's drawn with. */
	private record Run(String text, Style style) {
	}

	private static List<Run> runs(Component c) {
		List<Run> out = new ArrayList<>();
		c.visit((style, text) -> {
			if (!text.isEmpty()) out.add(new Run(text, style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static Run run(Component c, String text) {
		return runs(c).stream().filter(r -> r.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("No run \"" + text + "\" in " + c.getString()));
	}

	@Test
	void aPlayerWhoIsNotWhitelistedIsToldTheTwoWaysInAndOfferedToLinkDiscord() {
		assertEquals("""
				Lazarus ➜ Welcome to Lazarus.
				│\s
				│ Lazarus is invite only. Redeem an invite with /lazarus redeem <code>, or ask Lauchy on the Lazarus Discord.
				│\s
				│ Link Discord""", JoinMessages.notWhitelisted().getString());
	}

	@Test
	void theLinkDiscordButtonRunsTheDiscordCommand() {
		Run discord = run(JoinMessages.notWhitelisted(), "Link Discord");
		assertEquals(new ClickEvent.RunCommand("/lazarus discord"), discord.style().getClickEvent());
		assertEquals("Click to get your linking code", ((HoverEvent.ShowText) discord.style().getHoverEvent()).value().getString());
	}

	@Test
	void everythingAfterThePrefixThatIsntClickableIsBone() {
		for (Component c : List.of(JoinMessages.notWhitelisted(), JoinMessages.welcome("Steve", "Right Shift", "5 hours", true))) {
			for (Run r : runs(c).subList(2, runs(c).size())) {
				if (r.style().getClickEvent() != null || r.text().equals("│ ") || r.text().equals("\n")) continue;
				assertEquals(0xF2F2F5, r.style().getColor().getValue(), "\"" + r.text() + "\" should be Bone");
			}
		}
	}

	@Test
	void theLazarusDiscordOpensTheDiscord() {
		assertEquals(new ClickEvent.OpenUrl(URI.create("https://discord.gg/FBpjcYGNKW")), run(JoinMessages.notWhitelisted(), "Lazarus Discord").style().getClickEvent());
	}

	@Test
	void aMemberIsGreetedWithTheMenuKeyAndTheTimeLeft() {
		assertEquals("""
				Lazarus ➜ Welcome back, Steve.
				│\s
				│ Press Right Shift for the menu.
				│ Lazarus+ · 23 days, 4 hours left.""", JoinMessages.welcome("Steve", "Right Shift", "23 days, 4 hours", false).getString());
	}

	@Test
	void aNonMemberSeesLazarusPlusIsOffWithALinkToTheSite() {
		Component c = JoinMessages.welcome("Steve", "Right Shift", null, false);
		assertEquals("""
				Lazarus ➜ Welcome back, Steve.
				│\s
				│ Press Right Shift for the menu.
				│ Lazarus+ is off. Learn more""", c.getString());
		assertEquals(new ClickEvent.OpenUrl(URI.create("https://plus.lazaruss.co")), run(c, "Learn more").style().getClickEvent());
	}

	@Test
	void aPlayerWithoutALinkedDiscordIsAskedToLinkIt() {
		Component c = JoinMessages.welcome("Steve", "Right Shift", "5 hours", true);
		assertEquals("""
				Lazarus ➜ Welcome back, Steve.
				│\s
				│ Press Right Shift for the menu.
				│ Lazarus+ · 5 hours left.
				│\s
				│ Get your Discord roles. Link Discord""", c.getString());
		assertEquals(new ClickEvent.RunCommand("/lazarus discord"), run(c, "Link Discord").style().getClickEvent());
	}
}
