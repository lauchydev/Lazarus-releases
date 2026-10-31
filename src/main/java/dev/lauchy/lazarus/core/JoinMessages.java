package dev.lauchy.lazarus.core;

import net.minecraft.network.chat.Component;

/** What Lazarus says in chat on the first join of each game start, built without the game so it can be tested. */
public final class JoinMessages {
	/** The Lazarus Discord, where players verify and ask for help. */
	public static final String DISCORD = "https://discord.gg/FBpjcYGNKW";

	private JoinMessages() {
	}

	/** For an account that isn't whitelisted (inactive ones too): the two ways in, and a button to link Discord. */
	public static Component notWhitelisted() {
		return Message.lazarus().info("Welcome to Lazarus.").line().line()
				.info("Lazarus is invite only. Redeem an invite with /lazarus redeem <code>, or ask Lauchy on the ")
				.link("Lazarus Discord", "Click to join the Lazarus Discord", DISCORD).info(".").line().line()
				.command("Link Discord", "Click to get your linking code", "/lazarus discord").build();
	}

	/**
	 * For a whitelisted account: a greeting, the key that opens the menu, where Lazarus+ stands and,
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
