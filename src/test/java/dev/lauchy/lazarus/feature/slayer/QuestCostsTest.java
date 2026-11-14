package dev.lauchy.lazarus.feature.slayer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestCostsTest {
	@Test
	void thePriceListIsReadHoweverItIsWritten() {
		assertEquals(Map.of("I", 2_000L, "II", 7_500L, "III", 20_000L, "IV", 50_000L, "V", 150_000L), QuestCosts.parse("I 2000, II 7500, III 20000, IV 50000, V 150000"));
		assertEquals(Map.of("II", 7_500L, "V", 150_000L, "IV", 1_500_000L), QuestCosts.parse("V: 150k; II = 7,500 / IV 1.5m"));
		assertEquals(Map.of(), QuestCosts.parse("free"));
	}

	@Test
	void everyQuestStartedIsPaidForKilledOrFailed() {
		Map<String, Long> costs = QuestCosts.parse("IV 50000, V 150000");
		assertEquals(30 * 150_000L + 12 * 50_000L + 2 * 150_000L, QuestCosts.spent(Map.of("V", 30, "IV", 12), Map.of("V", 2), costs));
		assertEquals(0L, QuestCosts.spent(Map.of("III", 4), Map.of(), costs), "a tier with no price costs nothing");
	}
}
