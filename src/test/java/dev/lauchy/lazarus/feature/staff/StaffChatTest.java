package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Chat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffChatTest {
	@Test
	void staffLinesAreReadWithRankNameAndMessage() {
		StaffChat.Line l = StaffChat.parse("Staff > [ADMIN] Lauchy: anyone free?", 7);
		assertNotNull(l);
		assertEquals("ADMIN", l.rank());
		assertEquals("Lauchy", l.name());
		assertEquals("anyone free?", l.message());
		assertEquals(7, l.at());
		assertEquals("§cADMIN §fLauchy§7: §fanyone free?", l.row()); // a staff rank in its own colour
		assertEquals("§5MVP+ §fSteve§7: §fhi", new StaffChat.Line("MVP+", "Steve", "hi", 0).row()); // any other in dark purple
	}

	@Test
	void publicChatIsNotStaffChat() {
		assertNull(StaffChat.parse("[MVP+] Steve: Staff > hello", 0));
		assertNull(StaffChat.parse("You were teleported to Steve by Lauchy.", 0));
	}

	@Test
	void speakersAreReadFromEveryChatShape() {
		Chat.Speaker s = Chat.speaker("[MVP+] Steve: hi there");
		assertNotNull(s);
		assertEquals("Steve", s.name());
		assertEquals("MVP+", s.rank());
		assertEquals("hi there", s.message());
		assertFalse(s.staffChat());
		s = Chat.speaker("Steve: hi: with colons");
		assertNotNull(s);
		assertEquals("", s.rank());
		assertEquals("hi: with colons", s.message());
		assertTrue(Chat.speaker("Staff > [MOD] Steve: on it").staffChat());
		assertNull(Chat.speaker("Teleported to Steve."));
		assertNull(Chat.speaker("[Debug] Warden · Roll 0.15/100 · MF 1 · Hot Potato Book"));
	}

	@Test
	void theOpenChatWindowShowsTheNewestLinesAndScrollsBack() {
		List<StaffChat.Line> all = new ArrayList<>();
		for (int i = 0; i < 25; i++) all.add(new StaffChat.Line("MOD", "Steve", "line " + i, i));
		List<StaffChat.Line> newest = StaffChat.window(all, 0, 10);
		assertEquals(10, newest.size());
		assertEquals("line 15", newest.getFirst().message());
		assertEquals("line 24", newest.getLast().message());
		List<StaffChat.Line> back = StaffChat.window(all, 5, 10);
		assertEquals("line 10", back.getFirst().message());
		assertEquals("line 19", back.getLast().message());
		assertEquals(3, StaffChat.window(all.subList(0, 3), 0, 10).size()); // fewer kept than fit
		assertTrue(StaffChat.window(List.of(), 0, 10).isEmpty());
	}
}
