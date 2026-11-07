package dev.lauchy.lazarus.feature.profile;

import java.util.Locale;

/**
 * A pet's level from its XP, worked out the way dragsim.co does: a pet that doesn't level sits
 * at 100, and a Golden Dragon climbs from 101 to 200 (XP saved on the old curve converted
 * first). Profiles carry only the XP. Pure, so it is tested without the game.
 */
final class PetLevel {
	private PetLevel() {
	}

	/** {@code levels} and {@code maxLevel} come from the catalogue's pet entry (0 for no max given). */
	static int of(String type, String rarity, double totalXp, int xpCurve, boolean levels, int maxLevel) {
		boolean golden = "golden_dragon".equalsIgnoreCase(type);
		int max = golden ? (maxLevel > 0 ? maxLevel : 200) : 100;
		if (!golden && !levels) return max;
		String r = rarity == null ? "" : rarity.toUpperCase(Locale.ROOT);
		int base = switch (r) {
			case "UNCOMMON" -> 175;
			case "RARE" -> 275;
			case "EPIC" -> 400;
			case "LEGENDARY" -> 600;
			case "MYTHIC" -> 800;
			default -> 100;
		};
		int step = switch (r) {
			case "UNCOMMON" -> 125;
			case "RARE" -> 175;
			case "EPIC" -> 250;
			case "LEGENDARY" -> 350;
			case "MYTHIC" -> 500;
			default -> 75;
		};
		double xp = Math.max(0, totalXp);
		if (golden && xpCurve < 2) {
			int f = 1;
			double left = xp;
			while (f < max && left >= need(base, step, f)) left -= need(base, step, f++);
			double converted = 0;
			int upTo = Math.max(101, f);
			for (int p = 101; p < upTo; p++) converted += golden(p);
			if (upTo < max && f >= 101) converted += Math.floor(left / need(base, step, f) * golden(upTo));
			xp = converted;
		}
		int level = golden ? 101 : 1;
		while (level < max && xp >= (golden ? golden(level) : need(base, step, level))) xp -= golden ? golden(level++) : need(base, step, level++);
		return level;
	}

	/** XP from {@code level} to the next on the ordinary curve. */
	private static double need(int base, int step, int level) {
		return Math.floor((base + step * level) * (1 + Math.pow(level / 100.0, 2) * 3));
	}

	/** XP from {@code level} to the next for a Golden Dragon past 100. */
	private static double golden(int level) {
		return level == 101 ? 650 : 204_075;
	}
}
