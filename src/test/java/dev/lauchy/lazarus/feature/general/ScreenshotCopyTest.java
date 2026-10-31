package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotCopyTest {
	@Test
	void eachSystemUsesItsOwnClipboardTool() {
		assertEquals("powershell", ScreenshotCopy.command("Windows 11", false).getFirst());
		assertEquals("osascript", ScreenshotCopy.command("Mac OS X", false).getFirst());
		assertTrue(ScreenshotCopy.command("Linux", true).getLast().startsWith("wl-copy"));
		assertTrue(ScreenshotCopy.command("Linux", false).getLast().startsWith("xclip"));
	}

	@Test
	void thePathComesFromTheEnvironmentNotTheScript() {
		for (String os : new String[] {"Windows 10", "Mac OS X", "Linux"}) {
			String script = ScreenshotCopy.command(os, false).getLast();
			assertTrue(script.contains(ScreenshotCopy.PATH_VAR), os);
			assertFalse(script.contains("screenshots"), os);
		}
	}
}
