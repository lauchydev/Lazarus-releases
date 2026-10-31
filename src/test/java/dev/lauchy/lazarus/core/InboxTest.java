package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboxTest {
	private static final Inbox.Message WELCOME = new Inbox.Message("welcome_cs_1", "welcome", "Welcome to Lazarus+", "Thank you!");
	private static final Inbox.Message OTHER = new Inbox.Message("welcome_cs_2", "welcome", "Welcome to Lazarus+", "Thank you!");

	@Test
	void showsWhatTheServerSent() {
		Inbox inbox = new Inbox();
		inbox.offer(List.of(WELCOME, OTHER));
		assertEquals(List.of(WELCOME, OTHER), inbox.take());
	}

	@Test
	void showsEachMessageOnceWhenTheServerSendsItAgainBeforeHearingItWasSeen() {
		Inbox inbox = new Inbox();
		inbox.offer(List.of(WELCOME));
		inbox.take();
		inbox.offer(List.of(WELCOME, OTHER));
		assertEquals(List.of(OTHER), inbox.take());
	}

	@Test
	void showsNothingWhileNothingIsWaiting() {
		Inbox inbox = new Inbox();
		inbox.offer(List.of(WELCOME));
		inbox.take();
		assertEquals(List.of(), inbox.take());
	}
}
