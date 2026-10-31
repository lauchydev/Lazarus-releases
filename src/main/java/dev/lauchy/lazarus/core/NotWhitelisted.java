package dev.lauchy.lazarus.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * What starts for a player who has the mod but isn't whitelisted: the command to redeem an invite, the live
 * connection, and the cosmetics everyone with the mod sees, so they can tell who uses Lazarus. Nothing else
 * starts for them. Pure, so it can be checked on its own.
 */
public final class NotWhitelisted {
	private NotWhitelisted() {
	}

	/** One part of Lazarus that can start for a player who isn't whitelisted. */
	public enum Part {
		/** {@code /lazarus redeem <code>} to get in with an invite, and {@code /lazarus discord}: their only commands. */
		REDEEM_COMMAND,
		/** The live connection to the Lazarus server, which says who's online with the mod and their badges and capes. */
		LIVE_CONNECTION,
		/** The name badges on everyone online with the mod, their own in grey. */
		BADGES,
		/** Other players' capes, drawn as the live connection says. */
		CAPES,
		/** A cape of their own: picked in the menu, saved, and shared with everyone. */
		OWN_CAPE
	}

	/** The parts that start for a player who isn't whitelisted. */
	public static Set<Part> starts() {
		return EnumSet.of(Part.REDEEM_COMMAND, Part.LIVE_CONNECTION, Part.BADGES, Part.CAPES);
	}
}
