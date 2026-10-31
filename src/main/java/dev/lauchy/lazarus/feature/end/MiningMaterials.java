package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Text;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * What the Mining Profit tracker counts: the End's mined materials, each a ladder of tiers
 * (Obsidian, Enchanted Obsidian, Refined Obsidian), all measured in raw blocks so the Personal
 * Compactor turning 160 Obsidian into one Enchanted Obsidian changes nothing. No game types, so
 * the counting and pricing are tested on their own.
 */
public final class MiningMaterials {
	/** One step of a material's ladder: its item name and how many raw blocks one of it holds. */
	public record Tier(String name, long units) {
		/** The catalogue id of the tier: its name in lower case with underscores. */
		String id() {
			return key(name);
		}
	}

	/** A material you mine in the End, its tiers from raw up, and the colour its row is drawn in. */
	public enum Material {
		END_STONE("End Stone", "§e", ladder("End Stone", "Enchanted End Stone", "Refined End Stone")),
		OBSIDIAN("Obsidian", "§5", ladder("Obsidian", "Enchanted Obsidian", "Refined Obsidian")),
		CRYING_OBSIDIAN("Crying Obsidian", "§d", ladder("Crying Obsidian", "Enchanted Crying Obsidian", "Refined Crying Obsidian")),
		// Each gem tier is 80 of the one below, and a Perfect is five Flawless (from the NPC prices).
		AMETHYST("Amethyst", "§d", List.of(new Tier("Rough Amethyst", 1), new Tier("Flawed Amethyst", 80),
				new Tier("Fine Amethyst", 6_400), new Tier("Flawless Amethyst", 512_000), new Tier("Perfect Amethyst", 2_560_000)));

		/** The name its row shows. */
		public final String label;
		/** The colour code its row is drawn in. */
		public final String colour;
		/** Its tiers, raw first. */
		public final List<Tier> tiers;

		Material(String label, String colour, List<Tier> tiers) {
			this.label = label;
			this.colour = colour;
			this.tiers = tiers;
		}
	}

	/** Which tier the readout counts mined material in. */
	public enum Units {
		RAW, ENCHANTED, REFINED
	}

	/** One stack's worth: which material, and how many raw blocks it holds. */
	public record Piece(Material material, long units, boolean compacted) {
	}

	private MiningMaterials() {
	}

	/** A raw block, its Enchanted form (160) and its Refined form (16 Enchanted), as the NPC prices bear out. */
	private static List<Tier> ladder(String raw, String enchanted, String refined) {
		return List.of(new Tier(raw, 1), new Tier(enchanted, 160), new Tier(refined, 2_560));
	}

	/** "Enchanted Obsidian" or "enchanted_obsidian" -> "enchanted_obsidian". */
	static String key(String nameOrId) {
		return nameOrId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	/**
	 * What {@code count} of an item are as mined material, or null when it isn't one. Only exact
	 * names count, so an Obsidian Pickaxe or an Amethyst Chestplate is never material.
	 */
	public static Piece piece(String nameOrId, int count) {
		if (nameOrId == null || count <= 0) return null;
		String key = key(nameOrId);
		for (Material m : Material.values()) {
			for (int i = 0; i < m.tiers.size(); i++) {
				Tier t = m.tiers.get(i);
				if (t.id().equals(key)) return new Piece(m, t.units() * count, i > 0);
			}
		}
		return null;
	}

	/**
	 * What {@code units} raw blocks of {@code m} are worth, counted the way the bag would hold them:
	 * as many of the top tier as fit, then the next, down to raw. {@code price} gives one item's
	 * worth by name, or -1 (or 0) when unknown; an unpriced tier is skipped and its blocks valued
	 * in the tiers below, and blocks left with no priced tier under them go at the rate of the
	 * lowest priced tier above.
	 */
	public static long value(Material m, long units, ToLongFunction<String> price) {
		if (units <= 0) return 0;
		long out = 0, left = units;
		double lowestRate = -1;
		for (int i = m.tiers.size() - 1; i >= 0; i--) {
			Tier t = m.tiers.get(i);
			long each = price.applyAsLong(t.name());
			if (each <= 0) continue;
			out += left / t.units() * each;
			left %= t.units();
			lowestRate = each / (double) t.units();
		}
		if (left > 0 && lowestRate > 0) out += Math.round(left * lowestRate);
		return out;
	}

	/**
	 * {@code units} raw blocks as the readout says them in {@code shown}: "48 Ench", "12 Fine",
	 * "3.2k". Less than one of the chosen tier is said in the tier below, since a fraction of a
	 * Refined is not something anyone holds.
	 */
	public static String amount(Material m, long units, Units shown) {
		int tier = Math.min(shown.ordinal(), m.tiers.size() - 1);
		while (tier > 0 && units < m.tiers.get(tier).units()) tier--;
		long n = Math.max(0, units) / m.tiers.get(tier).units();
		return Text.compact(n) + (tier == 0 ? "" : " " + shortName(m, tier));
	}

	/** "Ench" and "Ref" for blocks; the gem's own tier word ("Flawed", "Fine") for Amethyst. */
	private static String shortName(Material m, int tier) {
		if (m == Material.AMETHYST) return m.tiers.get(tier).name().split(" ")[0];
		return tier == 1 ? "Ench" : "Ref";
	}

	/** Mining time as the readout says it: 754000 -> "12m 34s", 3900000 -> "1h 5m". */
	public static String time(long ms) {
		long s = Math.max(0, ms / 1000), h = s / 3600, m = s / 60 % 60;
		if (h > 0) return h + "h " + m + "m";
		if (m > 0) return m + "m " + s % 60 + "s";
		return s + "s";
	}


	/**
	 * What came into the bag between two looks at it, per material, so compaction is never counted
	 * as mining. Material counts go up and down with the bag, never below zero. The one case that
	 * needs memory is the Personal Compactor taking material you brought with you (80 Rough
	 * Amethyst) and handing back its compacted form (1 Flawed) a tick later: the removal could not
	 * be taken off a count already at zero, so the return must not be counted as mined either.
	 */
	public static final class Gains {
		/** How long a removal the count could not absorb waits for its compacted form to come back. */
		static final long CARRY_MS = 750;

		/** Per material: blocks removed but not taken off the count, and when. */
		private final Map<Material, long[]> owed = new EnumMap<>(Material.class);

		/**
		 * How much the mined count of {@code m} changes, now at {@code before}, when the bag's
		 * raw blocks of it changed by {@code change} and its compacted forms rose by
		 * {@code compactedRise} (raw blocks), at {@code now}.
		 */
		public long apply(Material m, long before, long change, long compactedRise, long now) {
			before = Math.max(0, before);
			long[] debt = owed.get(m);
			if (debt != null && (now < debt[1] || now - debt[1] > CARRY_MS)) {
				owed.remove(m);
				debt = null;
			}
			if (change < 0) {
				long removed = -change;
				long taken = Math.min(before, removed);
				long unpaid = removed - taken;
				// A second removal in the same window adds to the debt but keeps its deadline.
				if (unpaid > 0) owed.put(m, debt == null ? new long[]{unpaid, now} : new long[]{debt[0] + unpaid, debt[1]});
				return -taken;
			}
			// Freshly mined raw blocks can't settle a debt; only the compacted form coming back can.
			if (change == 0 || debt == null || compactedRise <= 0) return change;
			long cancelled = Math.min(change, Math.min(compactedRise, debt[0]));
			if (debt[0] == cancelled) owed.remove(m);
			else owed.put(m, new long[]{debt[0] - cancelled, debt[1]});
			return change - cancelled;
		}

		/** Forgets every debt: a reset, a world change or a menu opening. */
		public void clear() {
			owed.clear();
		}
	}
}
