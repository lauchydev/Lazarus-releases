package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatSpyTest {
	@Test
	void spiedDmsAreReadWithSenderReceiverAndMessage() {
		ChatSpy.Line l = ChatSpy.parse("[Spy DM] oYuvin_ → shotgvnz: i do powder or na", 7); // Dragsim's real line
		assertNotNull(l);
		assertEquals("oYuvin_", l.from());
		assertEquals("shotgvnz", l.to());
		assertEquals("i do powder or na", l.message());
		assertEquals(7, l.at());
		assertEquals("§foYuvin_ §7→ §fshotgvnz§7: §fi do powder or na", l.row());
		assertEquals("u can do: like a 15m test", ChatSpy.parse("[Spy DM] shotgvnz → oYuvin_: u can do: like a 15m test", 0).message());
	}

	@Test
	void otherLinesAreNotSpiedDms() {
		assertNull(ChatSpy.parse("[MVP+] oYuvin_: no if u care abt money for msg dmg yes", 0));
		assertNull(ChatSpy.parse("Staff > [ADMIN] Lauchy: [Spy DM] a → b: hi", 0));
		assertNull(ChatSpy.parse("From [MVP+] Steve: hi", 0));
	}
}
