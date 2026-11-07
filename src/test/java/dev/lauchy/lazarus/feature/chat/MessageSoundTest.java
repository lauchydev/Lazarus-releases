package dev.lauchy.lazarus.feature.chat;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageSoundTest {
	@Test
	void privateMessageLinesAreReadWithTheirDirectionAndName() {
		Matcher m = MessageSound.dm("From [MVP+] Steve: are you on?");
		assertNotNull(m);
		assertEquals("From", m.group(1));
		assertEquals("Steve", m.group(2));
		m = MessageSound.dm("To Steve: yes");
		assertNotNull(m);
		assertEquals("To", m.group(1));
		m = MessageSound.dm("From Steve: no rank");
		assertNotNull(m);
		assertEquals("Steve", m.group(2));
	}

	@Test
	void chatSpyAndOrdinaryChatAreNotPrivateMessages() {
		assertNull(MessageSound.dm("[SocialSpy] Steve -> Alex: hi"));
		assertNull(MessageSound.dm("[Spy] From Steve: hi")); // anything before "From" is someone else's message
		assertNull(MessageSound.dm("[MVP+] Steve: From the top"));
		assertNull(MessageSound.dm("Staff > [ADMIN] Lauchy: To be fair"));
		assertNull(MessageSound.dm("From: Steve"));
	}
}
