package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlusMessagesTest {
	@Test
	void theWelcomeSaysExactlyThis() {
		assertEquals("Lazarus+ ➜ Welcome to Lazarus+. Everything is unlocked.", PlusMessages.welcome().getString());
	}

	@Test
	void anEndingNoticeIsWordedByTheModAndCarriesTheExtendButton() {
		Component c = PlusMessages.notice("warn7", "Lazarus+ ends in 7 days", "It doesn't renew on its own. Extend it with /lazarus upgrade.", 30);
		assertEquals("""
				Lazarus+ ➜ Your Lazarus+ plan ends in 7 days.
				│ Extend now""", c.getString());
		assertEquals(new ClickEvent.RunCommand("/lazarus upgrade"), click(c, "Extend now"));
	}

	@Test
	void anEndedNoticeOffersToRenew() {
		Component c = PlusMessages.notice("ended", "Lazarus+ has ended", "", 30);
		assertEquals("""
				Lazarus+ ➜ Your Lazarus+ plan has ended.
				│ Renew now""", c.getString());
		assertEquals(new ClickEvent.RunCommand("/lazarus upgrade"), click(c, "Renew now"));
	}

	@Test
	void anUnknownKindIsSaidAsTheServerWroteIt() {
		Component c = PlusMessages.notice("news", "Something new", "The server has news.", 30);
		assertEquals("""
				Lazarus+ ➜ Something new
				│ The server has news.""", c.getString());
		assertNull(click(c, "Extend now"));
	}

	private static ClickEvent click(Component c, String text) {
		ClickEvent[] found = {null};
		c.visit((style, s) -> {
			if (s.equals(text)) found[0] = style.getClickEvent();
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}
}
