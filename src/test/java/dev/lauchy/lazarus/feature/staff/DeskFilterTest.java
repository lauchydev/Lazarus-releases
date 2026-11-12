package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeskFilterTest {
	@Test
	void thePicksComeBackAsTheyWereSaved() {
		DeskFilter.Picks picks = new DeskFilter.Picks(new LinkedHashSet<>(List.of("", "VIP")), EnumSet.of(Roster.Source.REPORT, Roster.Source.ALT));
		DeskFilter.Picks back = DeskFilter.read(JsonParser.parseString(DeskFilter.write(picks).toString()));
		assertEquals(picks.ranks(), back.ranks());
		assertEquals(picks.reasons(), back.reasons());
	}

	@Test
	void noFileOrAReasonThisVersionDoesntKnowPicksNothingOfIt() {
		DeskFilter.Picks none = DeskFilter.read(null);
		assertTrue(none.ranks().isEmpty() && none.reasons().isEmpty());
		DeskFilter.Picks some = DeskFilter.read(JsonParser.parseString("{\"ranks\":[\"vip\"],\"reasons\":[\"REPORT\",\"SOMETHING_NEW\"]}"));
		assertEquals(Set.of("VIP"), some.ranks());
		assertEquals(EnumSet.of(Roster.Source.REPORT), some.reasons());
	}
}
