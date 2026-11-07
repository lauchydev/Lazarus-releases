package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.Component;

/** What Lazarus says in chat on the first join of each game start, built without the game so it can be tested. */
public final class JoinMessages {
	/** The Lazarus Discord, where players verify and ask for help. */
	public static final String DISCORD = "https://discord.gg/FBpjcYGNKW";

	private JoinMessages() {
	}

	/** For an account the admins blocked: Lazarus doesn't run, and the Discord is where to ask why. */
	public static Component blocked() {
		return Message.lazarus().error("Lazarus is blocked for this account.").line().line()
				.info("Ask on the ").link("Lazarus Discord", "Click to join the Lazarus Discord", DISCORD).info(" if you think that's a mistake.").build();
	}

	/** When the Lazarus server already knows this account from another install: the mod runs, without what the server shares. */
	public static Component keyTaken() {
		return Message.lazarus().warning("This account's Lazarus key belongs to another install.").line()
				.info("Badges, capes, Lazarus+ and staff sharing are off here until an admin resets it. Ask on the ")
				.link("Lazarus Discord", "Click to join the Lazarus Discord", DISCORD).info(".").build();
	}

	/**
	 * For anyone who isn't blocked: a greeting, the key that opens the menu, where Lazarus+ stands and,
	 * while no Discord account is linked, a button to link one.
	 * {@code plusLeft} is the time left ("23 days, 4 hours") for a member, or null for anyone else.
	 */
	public static Component welcome(String name, String menuKey, String plusLeft, boolean discordUnlinked) {
		Message m = Message.lazarus().info("Welcome back, " + name + ".").line().line()
				.info("Press " + menuKey + " for the menu.").line();
		if (plusLeft != null) m.info("Lazarus+ · " + plusLeft + " left.");
		else m.info("Lazarus+ is off. ").link("Learn more", "Click to open plus.lazaruss.co", Plus.SITE);
		if (discordUnlinked) m.line().line().info("Get your Discord roles. ").command("Link Discord", "Click to link your Discord account", "/lazarus discord");
		return m.build();
	}
}
