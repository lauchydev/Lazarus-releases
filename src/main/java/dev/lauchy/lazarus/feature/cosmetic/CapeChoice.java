package dev.lauchy.lazarus.feature.cosmetic;

import dev.lauchy.lazarus.core.Live;

/**
 * Which cape a player shows, from what they chose and who they are: every Lazarus user wears the Lazarus
 * cape; a Lazarus+ member wears the Lazarus+ cape until they pick another, or none. No Minecraft types, so
 * it's tested on its own.
 */
public final class CapeChoice {
	/** The Lazarus cape every Lazarus user wears, the Lazarus+ cape a member wears until they pick another, and the key for wearing none. */
	public static final String LAZARUS = "lazarus", PLUS_CAPE = "lazarusPlus", NONE = "none";

	private CapeChoice() {
	}

	/**
	 * The key of the cape a player shows, "" for none: the one they chose, or none for {@link #NONE}; with
	 * nothing chosen, a member wears the Lazarus+ cape and every other Lazarus user the Lazarus cape. A chosen
	 * cape is a member's alone: without Lazarus+ it gives way to the Lazarus cape, though anyone may take theirs
	 * off. Someone who isn't a Lazarus user (no badge, or a grey one) wears nothing.
	 */
	public static String worn(Live.Badge badge, String chosen, boolean member) {
		if (chosen.equals(NONE)) return "";
		if (!member) return badge == Live.Badge.NONE || badge == Live.Badge.GREY ? "" : LAZARUS;
		return chosen.isEmpty() ? PLUS_CAPE : chosen;
	}

	/** Whether a player without Lazarus+ may pick this: the Lazarus cape, none, or nothing chosen; the rest is a member's. */
	public static boolean free(String key) {
		return key.isEmpty() || key.equals(LAZARUS) || key.equals(NONE);
	}
}
