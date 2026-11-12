package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandsTest {
	@Test
	void aShortNameIsLowerCaseWithoutTheSlash() {
		assertEquals("lz", Commands.aliasName("lz"));
		assertEquals("lz", Commands.aliasName("/LZ "));
		assertEquals("laz-2", Commands.aliasName("laz-2"));
	}

	@Test
	void nothingUsableGivesNoAlias() {
		assertEquals("", Commands.aliasName(""));
		assertEquals("", Commands.aliasName(null));
		assertEquals("", Commands.aliasName("la zarus"), "a space can't be in a command name");
		assertEquals("", Commands.aliasName("lazarus"), "the command itself isn't an alias");
		assertEquals("", Commands.aliasName("l".repeat(17)), "too long to be a short name");
		assertEquals("", Commands.aliasName("lz!"));
	}
}
