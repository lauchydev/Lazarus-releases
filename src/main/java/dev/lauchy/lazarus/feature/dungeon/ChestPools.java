package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a dungeon reward chest can hold, by floor and chest, as dragsim.co/wiki lists it (its floor pages, read
 * 2026-11-12): the supplies and Cryptguard pieces every chest draws from, the rare items of the floor, and each
 * chest's own drops. Essence is in every chest and is left out: it's never the prize. Only Floor I is open, so
 * later floors have their rare items alone. No Minecraft types, so it's tested on its own.
 */
final class ChestPools {
	/** The chests, cheapest first. */
	private static final List<String> CHESTS = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock");
	/** "The Catacombs (F1)" on the sidebar. */
	private static final Pattern FLOOR = Pattern.compile("\\(F(\\d)\\)");

	/** In every chest of every floor: one bundle of supplies, or rarely a Cryptguard piece. */
	private static final List<String> EVERY = List.of("superboom_tnt", "spirit_leap", "revive_stone", "trap_defusal_kit", "soul_ration", "mana_draught",
			"ward_beacon", "catacomb_decoy", "cryptguard_helmet", "cryptguard_chestplate", "cryptguard_leggings", "cryptguard_boots");

	/** A floor's rare items, each with the cheapest chest it shows in, and each chest's own drops. */
	private record Rare(int floor, String from, List<String> ids) {
	}

	private record Drops(int floor, String chest, List<String> ids) {
	}

	private static final List<Rare> RARE = List.of(
			new Rare(1, "Gold", List.of("cartographers_charm", "reinforced_plating", "dungeon_sack", "recall_totem")),
			new Rare(1, "Diamond", List.of("dungeonbreaker_pickaxe", "oathguard_helmet", "oathguard_boots", "catacombs_expert_ring", "warrior_soul")),
			new Rare(1, "Emerald", List.of("oathguard_leggings", "excavators_heart")),
			new Rare(1, "Obsidian", List.of("awakened_sanctifier", "oathguard_chestplate", "wither_artifact")),
			new Rare(2, "Diamond", List.of("shadow_assassin_helmet", "shadow_assassin_boots", "scarf_studies", "mage_soul")),
			new Rare(2, "Emerald", List.of("shadow_assassin_leggings", "gravekeepers_locket")),
			new Rare(2, "Obsidian", List.of("shadow_fury", "awakened_duskblade", "shadow_assassin_chestplate", "soul_amplifier")),
			new Rare(3, "Diamond", List.of("necromancer_lord_helmet", "necromancer_lord_boots", "wisp_soul")),
			new Rare(3, "Emerald", List.of("necromancer_lord_leggings", "blood_lust_talisman", "bloody_recombobulator", "reaper_soul", "warbound_stone", "wither_lens")),
			new Rare(3, "Obsidian", List.of("awakened_necrostaff", "necromancer_lord_chestplate")),
			new Rare(4, "Diamond", List.of("colossus_helmet", "colossus_boots", "frostbound_helmet", "frostbound_boots")),
			new Rare(4, "Emerald", List.of("colossus_leggings", "frostbound_leggings", "boneguard_emblem", "knight_soul", "mortuary_stone")),
			new Rare(4, "Obsidian", List.of("colossus_chestplate", "frostbound_chestplate", "overlord_star", "skeletal_lens", "colossus_blade", "colossus_maul", "soulfire_scepter", "soulpiercer")),
			new Rare(5, "Emerald", List.of("shadow_soul")),
			new Rare(5, "Obsidian", List.of("starward_signet", "eclipse_star")),
			new Rare(5, "Bedrock", List.of("awakened_dragonlance", "awakened_necromancer_helmet", "awakened_necromancer_chestplate", "awakened_necromancer_leggings",
					"awakened_necromancer_boots", "awakened_paladin_helmet", "awakened_paladin_chestplate", "awakened_paladin_leggings", "awakened_paladin_boots",
					"awakened_assassin_helmet", "awakened_assassin_chestplate", "awakened_assassin_leggings", "awakened_assassin_boots", "heart_of_the_abyss", "abyssal_seal")));

	private static final List<Drops> DROPS = List.of(
			new Drops(1, "Diamond", List.of("wither_catalyst")),
			new Drops(1, "Emerald", List.of("wither_catalyst", "withered_orb", "arcane_blood")),
			new Drops(1, "Obsidian", List.of("wither_catalyst", "withered_orb", "auto_recombobulator", "arcane_blood", "wither_cloak_sword", "wither_artifact", "first_dungeon_star")),
			new Drops(1, "Bedrock", List.of("wither_catalyst", "withered_orb", "auto_recombobulator", "arcane_blood", "wither_cloak_sword", "wither_artifact", "wither_core",
					"hardened_clay", "first_dungeon_star")));

	private ChestPools() {
	}

	/** The floor the sidebar names ("The Catacombs (F1)"); 1 when it names none. */
	static int floor(List<String> sidebar) {
		for (String line : sidebar) {
			Matcher m = FLOOR.matcher(line);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return 1;
	}

	/**
	 * The ids of everything a chest of this material can hold on a floor, essence aside. A rare item shows in the
	 * chest the wiki names for it and in the dearer ones (an Oathguard piece listed for Diamond has turned up in
	 * Bedrock). Empty for a material that isn't a chest.
	 */
	static List<String> of(int floor, String material) {
		int chest = CHESTS.indexOf(material);
		if (chest < 0) return List.of();
		Set<String> out = new LinkedHashSet<>(EVERY);
		for (Rare r : RARE) if (r.floor == floor && CHESTS.indexOf(r.from) <= chest) out.addAll(r.ids);
		for (Drops d : DROPS) if (d.floor == floor && d.chest.equals(material)) out.addAll(d.ids);
		return new ArrayList<>(out);
	}
}
