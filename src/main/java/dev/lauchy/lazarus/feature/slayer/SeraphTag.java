package dev.lauchy.lazarus.feature.slayer;

import dev.lauchy.lazarus.core.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Voidgloom or Riftborn Seraph's name tag read into its parts, and the rules for picking
 * your boss's live tag among the ones floating over it. Pure, so it is tested without the game.
 * <p>
 * The tag is "☠ Voidgloom Seraph IV 817.8M/2.5B❤ 57 Hits", with "Radiation" after the
 * health while the beams are up. Riftborn (tier V) tags carry no tier. A boss can carry two
 * tags: one that keeps its full health and a live one that counts down, so the lowest health
 * among them is the one to show.
 */
public record SeraphTag(String family, String tier, String health, String maxHealth, double healthValue, String hits, boolean radiating) {
	private static final Pattern TAG = Pattern.compile("(Voidgloom|Riftborn) Seraph(?:\\s+([IVX]+))?\\s+([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)❤(?:\\s*(\\d+) Hits?)?");
	/** The owner line some servers float under a slayer boss, as SkyblockAddons reads it: "Spawned by: Name". */
	private static final Pattern OWNER = Pattern.compile("^Spawned by:?\\s+(\\w{1,16})$");

	/** The tag a name reads as, or null when the name isn't a Seraph's. */
	public static SeraphTag read(String name) {
		if (name == null) return null;
		Matcher m = TAG.matcher(Text.plain(name));
		if (!m.find()) return null;
		boolean rift = m.group(1).equals("Riftborn");
		String tier = m.group(2) != null ? m.group(2) : rift ? "V" : "";
		double hp = Text.number(m.group(3));
		if (hp < 0) return null;
		return new SeraphTag(m.group(1), tier, m.group(3), m.group(4), hp, m.group(5) == null ? "" : m.group(5),
				Text.plain(name).substring(m.end()).contains("Radiation"));
	}

	/** The player a "Spawned by: Name" line names, or null when the line isn't one. */
	public static String owner(String name) {
		if (name == null) return null;
		Matcher m = OWNER.matcher(Text.plain(name));
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * The tag to show among those over one boss: the lowest health, since a second tag can sit
	 * at full health all fight; the hit count comes from whichever tag carries one.
	 */
	public static SeraphTag live(List<SeraphTag> tags) {
		SeraphTag best = null;
		String hits = "";
		for (SeraphTag t : tags) {
			if (!t.hits.isEmpty()) hits = t.hits;
			if (best == null || t.healthValue < best.healthValue) best = t;
		}
		if (best == null || hits.equals(best.hits)) return best;
		return new SeraphTag(best.family, best.tier, best.health, best.maxHealth, best.healthValue, hits, best.radiating);
	}

	/** "Seraph IV", or "Riftborn Seraph" for tier V. */
	public String label() {
		return family.equals("Riftborn") ? "Riftborn Seraph" : "Seraph " + tier;
	}
}
