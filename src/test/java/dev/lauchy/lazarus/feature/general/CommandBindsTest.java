package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandBindsTest {
	@Test
	void anEmptyLookUpCommandOpensTheStaffPanelForStaffAndTheProfileForEveryoneElse() {
		assertEquals("/lazarus staff %p", CommandBinds.lookup("", true));
		assertEquals("/l %p", CommandBinds.lookup("  ", false));
		assertEquals("/l %p", CommandBinds.lookup("/l %p", true)); // what's set is what runs
	}
}
