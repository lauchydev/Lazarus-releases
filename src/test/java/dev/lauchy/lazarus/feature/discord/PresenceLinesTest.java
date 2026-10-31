package dev.lauchy.lazarus.feature.discord;

import dev.lauchy.lazarus.core.Area.Place;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PresenceLinesTest {
	private static Map<String, String> values(String location, String purse, String held) {
		Map<String, String> v = new HashMap<>();
		v.put("location", location);
		v.put("purse", purse);
		v.put("held", held);
		v.put("name", "Lauchy");
		return v;
	}

	@Test
	void placeholdersAreFilledIn() {
		assertEquals("Dragon's Den", PresenceLines.fill("{location}", values("Dragon's Den", "12M", "")));
		assertEquals("Purse: 12M · Lauchy", PresenceLines.fill("Purse: {purse} · {name}", values(null, "12M", "")));
	}

	@Test
	void aLineWithNothingToShowIsLeftOff() {
		assertNull(PresenceLines.fill("Holding {held}", values("Hub", "12M", "")));
		assertNull(PresenceLines.fill("{location}", values(null, "12M", "")));
		assertNull(PresenceLines.fill("", values("Hub", "12M", "")));
		assertNull(PresenceLines.fill("x", values("Hub", "12M", "")));
	}

	@Test
	void plainTextAndUnknownPlaceholdersStayAsTyped() {
		assertEquals("Grinding dragons", PresenceLines.fill("Grinding dragons", values(null, null, null)));
		assertEquals("{pet} in Hub", PresenceLines.fill("{pet} in {location}", values("Hub", null, null)));
	}

	@Test
	void longLinesAreCutToWhatDiscordShows() {
		String line = PresenceLines.fill("{held}", values(null, null, "a".repeat(200)));
		assertEquals(PresenceLines.MAX, line.length());
	}

	@Test
	void thePurseIsReadFromTheSidebar() {
		assertEquals("12.35M", PresenceLines.purse("Purse: 12,345,678"));
		assertEquals("950", PresenceLines.purse("Purse: 950"));
		assertNull(PresenceLines.purse("Bits: 400"));
	}

	@Test
	void everyKnownPlaceHasAName() {
		assertEquals("Dragon's Den", RichPresence.location(Place.END));
		assertEquals("The Rift", RichPresence.location(Place.RIFT));
		assertNull(RichPresence.location(Place.UNKNOWN));
	}

	@Test
	void framesAreTheKindAndLengthThenTheJson() {
		byte[] frame = DiscordIpc.encode(DiscordIpc.FRAME, "{\"a\":1}");
		ByteBuffer b = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(1, b.getInt());
		assertEquals(7, b.getInt());
		assertEquals("{\"a\":1}", new String(frame, 8, 7, StandardCharsets.UTF_8));
	}
}
