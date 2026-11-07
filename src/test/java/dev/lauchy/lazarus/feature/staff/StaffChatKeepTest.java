package dev.lauchy.lazarus.feature.staff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffChatKeepTest {
	private static final String LINE = "(?i)^You are now (?:in|chatting in|talking in) the (\\S+) channel";

	@Test
	void theBareCommandIsTheToggleAndAMessageInStaffChatIsNot() {
		assertTrue(StaffChatKeep.isToggle("sc", "/sc"));
		assertTrue(StaffChatKeep.isToggle("SC ", "/sc"));
		assertTrue(StaffChatKeep.isToggle("staffchat", "staffchat"));
		assertFalse(StaffChatKeep.isToggle("sc hello", "/sc"));
		assertFalse(StaffChatKeep.isToggle("send Lauchy end", "/sc"));
		assertFalse(StaffChatKeep.isToggle("sc", "/"));
	}

	@Test
	void theServersChannelLineSaysWhetherYoureInStaffChat() {
		assertEquals(Boolean.TRUE, StaffChatKeep.inStaff("You are now in the STAFF channel", LINE, "STAFF")); // Dragsim's real line
		assertEquals(Boolean.TRUE, StaffChatKeep.inStaff("You are now in the staff channel", LINE, "Staff"));
		assertEquals(Boolean.FALSE, StaffChatKeep.inStaff("You are now in the GLOBAL channel", LINE, "STAFF"));
		assertEquals(Boolean.FALSE, StaffChatKeep.inStaff("You are now in the ALL channel", LINE, "STAFF"));
		assertNull(StaffChatKeep.inStaff("Staff > [ADMIN] Lauchy: You are now in the STAFF channel", LINE, "STAFF")); // someone talking
		assertNull(StaffChatKeep.inStaff("You are now in the STAFF channel", "(?i)^You are now in the STAFF channel", "STAFF")); // no group names no channel
		assertNull(StaffChatKeep.inStaff("Sending you to Dragon's Den #1...", LINE, "STAFF"));
	}

	@Test
	void aBrokenPatternMatchesNothing() {
		assertNull(StaffChatKeep.inStaff("You are now in the STAFF channel", "(", "STAFF"));
		assertNull(StaffChatKeep.inStaff("You are now in the STAFF channel", "", "STAFF"));
		assertNull(StaffChatKeep.inStaff("You are now in the STAFF channel", "You are now", "STAFF")); // no group, no channel
	}
}
