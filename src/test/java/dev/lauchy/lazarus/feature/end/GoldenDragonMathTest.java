package dev.lauchy.lazarus.feature.end;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoldenDragonMathTest {
	@Test
	void readsTheDragonsQuestion() {
		String line = "[BOSS] Golden Dragon: You think you have the knowledge to defeat me? Find the answer to 94.3 + 59.0?";
		assertEquals("94.3 + 59.0", GoldenDragonMath.question(line));
		assertEquals("153.3", GoldenDragonMath.solve(GoldenDragonMath.question(line)));
	}

	@Test
	void skipsAPlayerTypingTheQuestion() {
		assertNull(GoldenDragonMath.question("[DRAGON] Finnzius: Find the answer to 1 + 1?"));
		assertNull(GoldenDragonMath.question("[BOSS] Golden Dragon: Type your answer in chat! Choose wisely, or else.."));
	}

	@Test
	void solvesWithPrecedence() {
		assertEquals("95.8", GoldenDragonMath.solve("76.2 + 19.6"));
		assertEquals("14", GoldenDragonMath.solve("2 + 3 x 4"));
	}
}
