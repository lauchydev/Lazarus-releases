package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizChatTest {
	/** A question as Dragsim sent it (2026-11), colours gone. */
	private static QuizChat asked(String question, String a, String b, String c) {
		QuizChat quiz = new QuizChat();
		List<String> lines = List.of("", "                                Question #2", "            " + question, "", "     ⓐ " + a, "     ⓑ " + b);
		for (String line : lines) assertFalse(quiz.read(line));
		assertTrue(quiz.read("     ⓒ " + c), "the third answer completes it");
		return quiz;
	}

	@Test
	void theQuestionAndItsThreeAnswersAreReadFromChat() {
		QuizChat quiz = asked("What is the name of the person that upgrades pets?", "Elle", "Kat", "Jamie");
		assertEquals("what is the name of the person that upgrades pets", quiz.question);
		assertEquals(Map.of("ⓐ", "Elle", "ⓑ", "Kat", "ⓒ", "Jamie"), quiz.options);
		assertEquals("Kat", quiz.known());
		assertEquals("ⓑ", quiz.letter("Kat"));
	}

	@Test
	void aKnownAnswerIsGivenOnlyWhenJustOneOfThemIsOnOffer() {
		assertEquals("5 Fairy Souls", asked("How many Fairy Souls are there in Jerry's Workshop?", "5 Fairy Souls", "7 Fairy Souls", "3 Fairy Souls").known());
		assertNull(asked("How many Fairy Souls are there in Jerry's Workshop?", "6 Fairy Souls", "7 Fairy Souls", "3 Fairy Souls").known(), "this server counts differently");
		assertNull(asked("What SkyBlock year is it?", "Year 526", "Year 524", "Year 525").known(), "not a question it knows");
	}

	@Test
	void anAnswersLabelInTheRoomIsTheSameAnswerAsInChat() {
		// The label over the button reads "ⓒ 12 Fairy Souls." where chat said "ⓒ 12 Fairy Souls" (Dragsim, 2026-11).
		assertEquals("12 Fairy Souls", QuizChat.answer("12 Fairy Souls."));
		assertEquals("Kat", QuizChat.answer(" Kat "));
		assertEquals(QuizChat.answer("12 Fairy Souls."), asked("How many Fairy Souls are there in The End?", "14 Fairy Souls", "10 Fairy Souls", "12 Fairy Souls").options.get("ⓒ"));
	}

	@Test
	void theNextQuestionStartsAfresh() {
		QuizChat quiz = asked("What SkyBlock year is it?", "Year 526", "Year 524", "Year 525");
		quiz.read("                                Question #3");
		assertNull(quiz.question);
		assertTrue(quiz.options.isEmpty());
	}

	@Test
	void whoAnsweredRightIsReadFromTheStatue() {
		assertEquals("sStepbr0", QuizChat.right("[STATUE] Oruo the Omniscient: sStepbr0 answered Question #1 correctly!"));
		assertEquals("Gurrnos", QuizChat.right("[STATUE] Oruo the Omniscient: Gurrnos answered the final question correctly!"));
		assertNull(QuizChat.right("[STATUE] Oruo the Omniscient: One more question!"));
	}
}
