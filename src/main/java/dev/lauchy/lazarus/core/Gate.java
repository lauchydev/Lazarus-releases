package dev.lauchy.lazarus.core;

import java.util.Locale;

/**
 * Which of the server's staff commands a rank may run, as the server's Staff Command Center states it: each is
 * MOD+ or ADMIN+. Helpers run none of them. No Minecraft types, so it's tested on its own.
 */
public final class Gate {
	/** How far up a rank reaches: none of the commands, the MOD+ ones, or every one. */
	public enum Tier {
		NONE, MOD, ADMIN;

		/** Whether this tier may run a command that needs {@code needed}. */
		public boolean allows(Tier needed) {
			return needed == NONE || compareTo(needed) >= 0 && this != NONE;
		}

		/** The tag a command needing this tier carries: "MOD+", "ADMIN+". */
		public String tag() {
			return this == NONE ? "" : name() + "+";
		}
	}

	private Gate() {
	}

	/** The tier a rank reaches ("OWNER", "[ADMIN]", "Mod"); an unknown or player rank reaches none. */
	public static Tier tier(String rank) {
		String r = rank == null ? "" : rank.replaceAll("§.", "").replaceAll("[\\[\\]]", "").trim().toUpperCase(Locale.ROOT);
		return switch (r) {
			case "OWNER", "MANAGER", "ADMIN" -> Tier.ADMIN;
			case "MOD", "MODERATOR" -> Tier.MOD;
			default -> Tier.NONE;
		};
	}
}
