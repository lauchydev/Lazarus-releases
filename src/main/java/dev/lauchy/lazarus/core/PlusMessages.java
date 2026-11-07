package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.Component;

/** The words of every Lazarus+ notice, kept apart from {@link Plus} so they can be tested without the game. */
final class PlusMessages {
	private PlusMessages() {
	}

	/** Shown once when Lazarus+ arrives on this account, bought, gifted or granted. */
	static Component welcome() {
		return Message.plus().info("Welcome to Lazarus+. Everything is unlocked.").build();
	}

	/**
	 * A notice the server keeps for this account, in Lazarus's own words for the kinds it knows (the plan
	 * ending in 7 days or tomorrow, ended, a late payment refunded); any other kind is said as the server
	 * wrote it. A notice about the plan ending carries one button: extend a plan that's still running (warn7,
	 * warn1), or renew one that has ended.
	 */
	static Component notice(String kind, String title, String text, int days) {
		Message m = Message.plus();
		switch (kind) {
			case "warn7" -> m.warning("Your Lazarus+ plan ends in 7 days.");
			case "warn1" -> m.warning("Your Lazarus+ plan ends tomorrow.");
			case "ended" -> m.info("Your Lazarus+ plan has ended.");
			case "late" -> m.info("Your Lazarus+ payment arrived too late and was refunded in full.");
			default -> {
				m.info(title);
				if (!text.isBlank()) m.line().muted(text);
			}
		}
		boolean running = kind.startsWith("warn");
		if (running || kind.equals("ended")) m.line().command(running ? "Extend now" : "Renew now",
				"Click to " + (running ? "add" : "get") + " " + days + " days of Lazarus+", "/lazarus upgrade");
		return m.build();
	}
}
