package dev.lauchy.lazarus.feature.dungeon;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * How each item a reward chest can hold ({@link ChestPools}) is named and looks, as dragsim.co/wiki has it (read
 * 2026-11-12): its name, rarity, the game item it's drawn as, a leather colour and a head's texture. The mod's own
 * item list knows many of these by name and price alone, which is no picture to put on a card. No Minecraft types,
 * so it's tested on its own.
 */
final class ChestLooks {
	/** One item's look. {@code colour} is "r,g,b" for dyed leather, {@code texture} the id of a head's skin; either may be empty. */
	record Look(String name, String rarity, String material, String colour, String texture) {
		/** The head's texture as the game takes it (base64 of where the skin is), or empty when it isn't a head with one. */
		String skin() {
			if (texture.isEmpty()) return "";
			String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture + "\"}}}";
			return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static final Map<String, Look> LOOKS = Map.ofEntries(
			Map.entry("superboom_tnt", new Look("Superboom TNT", "RARE", "tnt", "", "")),
			Map.entry("spirit_leap", new Look("Spirit Leap", "RARE", "ender_pearl", "", "")),
			Map.entry("revive_stone", new Look("Revive Stone", "LEGENDARY", "player_head", "", "b6a76cc22e7c2ab9c540d1244eadba581f5dd9e18f9adacf05280a5b48b8f618")),
			Map.entry("trap_defusal_kit", new Look("Trap Defusal Kit", "RARE", "player_head", "", "a223e36ac13f0f71abcfbf0c96fdc2010cc3e11ff2b0d8112d0e63f4b4aaa0de")),
			Map.entry("soul_ration", new Look("Soul Ration", "RARE", "player_head", "", "4a61b4f8b070e4bc30a86b2290db6e57e2681c44e0250d1906a89adb8fc455b1")),
			Map.entry("mana_draught", new Look("Mana Draught", "RARE", "player_head", "", "f7efbdfecf720c80a0bbc5d71200454bdce113071398b7308977508c25ab2f6b")),
			Map.entry("ward_beacon", new Look("Ward Beacon", "RARE", "player_head", "", "d4d2c8100c0e6d38910e24efa50438cdf7bf0aaebc471d4fa163af2d218a3395")),
			Map.entry("catacomb_decoy", new Look("Catacomb Decoy", "RARE", "player_head", "", "1880725ac40d46542bf7c819801872e0f01b7f9a52a4c48a98d7c94874484c0f")),
			Map.entry("cryptguard_helmet", new Look("Cryptguard Helmet", "RARE", "leather_helmet", "70,85,95", "")),
			Map.entry("cryptguard_chestplate", new Look("Cryptguard Chestplate", "RARE", "leather_chestplate", "70,85,95", "")),
			Map.entry("cryptguard_leggings", new Look("Cryptguard Leggings", "RARE", "leather_leggings", "70,85,95", "")),
			Map.entry("cryptguard_boots", new Look("Cryptguard Boots", "RARE", "leather_boots", "70,85,95", "")),
			Map.entry("cartographers_charm", new Look("Cartographer's Charm", "RARE", "player_head", "", "616f4ac8a0698ab07bb0a5ef69dc08daedd8f5972e7a1237c8c56361e3f204ca")),
			Map.entry("reinforced_plating", new Look("Reinforced Plating", "RARE", "player_head", "", "8df232457030a6667e4c65ff92e93c6ceffb20c133110844012e0a90fba786b4")),
			Map.entry("dungeon_sack", new Look("Dungeon Sack", "RARE", "player_head", "", "fb96c585ccd35f073da38d165cb9bb18ff136f1a184eee3f44725354640ebbd4")),
			Map.entry("recall_totem", new Look("Recall Totem", "RARE", "player_head", "", "4d359da4f70f1bcbe81545fc3a97b65c1d70f3f90198d757845aab198b6e0059")),
			Map.entry("dungeonbreaker_pickaxe", new Look("Dungeonbreaker", "SPECIAL", "diamond_pickaxe", "", "")),
			Map.entry("oathguard_helmet", new Look("Oathguard Helmet", "EPIC", "leather_helmet", "221,201,115", "")),
			Map.entry("oathguard_boots", new Look("Oathguard Boots", "EPIC", "leather_boots", "221,201,115", "")),
			Map.entry("catacombs_expert_ring", new Look("Catacombs Expert Ring", "EPIC", "player_head", "", "dfcc213d3369b5570edd3549d22277fc496bfce539d19f7583d6d64bb1649cb3")),
			Map.entry("warrior_soul", new Look("Warrior Rune", "RARE", "player_head", "", "468c1fa4a21ba4f27dcb30ab3bfab0dc22a1e4fcf9ed12c1d7a70feb07f8d7ae")),
			Map.entry("oathguard_leggings", new Look("Oathguard Leggings", "EPIC", "leather_leggings", "221,201,115", "")),
			Map.entry("excavators_heart", new Look("Excavator's Heart", "EPIC", "player_head", "", "5e0aa40856ae0b41e4c84ff656139a7ce8f43e9eb77b805c7b9c04d9d4b7ba7")),
			Map.entry("awakened_sanctifier", new Look("Dawnbreaker", "LEGENDARY", "netherite_sword", "", "")),
			Map.entry("oathguard_chestplate", new Look("Oathguard Chestplate", "EPIC", "leather_chestplate", "221,201,115", "")),
			Map.entry("wither_artifact", new Look("Wither Artifact", "EPIC", "player_head", "", "17b8ef7e8e0af19399cc32485f2ee47b2bb4d5802c71b3021f4d5d6b5f3afe03")),
			Map.entry("shadow_assassin_helmet", new Look("Shadow Assassin Helmet", "EPIC", "leather_helmet", "35,26,49", "")),
			Map.entry("shadow_assassin_boots", new Look("Shadow Assassin Boots", "EPIC", "leather_boots", "35,26,49", "")),
			Map.entry("scarf_studies", new Look("Scarf's Studies", "RARE", "player_head", "", "33453d6a5aaca67affa01f432afd9c4cfeff4582d7251cca33c534e782c03ab1")),
			Map.entry("mage_soul", new Look("Mage Rune", "RARE", "player_head", "", "82ada1c7fcc8cf35defeb944a4f8ffa9a9d260560fc7f5f5826de8085435967c")),
			Map.entry("shadow_assassin_leggings", new Look("Shadow Assassin Leggings", "EPIC", "leather_leggings", "35,26,49", "")),
			Map.entry("gravekeepers_locket", new Look("Gravekeeper's Locket", "EPIC", "player_head", "", "770d1599eefd5621d10ab69963d2a9b1b0c9a1e8999ea88a59772445ed2f0396")),
			Map.entry("shadow_fury", new Look("Shadow Fury", "LEGENDARY", "iron_sword", "", "")),
			Map.entry("awakened_duskblade", new Look("Nightfall", "LEGENDARY", "netherite_sword", "", "")),
			Map.entry("shadow_assassin_chestplate", new Look("Shadow Assassin Chestplate", "EPIC", "leather_chestplate", "0,0,0", "")),
			Map.entry("soul_amplifier", new Look("Soul Amplifier", "EPIC", "player_head", "", "3b0433f1c2279608f7bd2ceceb98cd175cbaadc3f6299ae3ccae257dc22a55b2")),
			Map.entry("necromancer_lord_helmet", new Look("Necromancer Lord Helmet", "LEGENDARY", "player_head", "", "7dade8bae567a0670fbeb025e63506a06e4010ef6644ff7b556bdf93d2608954")),
			Map.entry("necromancer_lord_boots", new Look("Necromancer Lord Boots", "LEGENDARY", "leather_boots", "64,36,79", "")),
			Map.entry("wisp_soul", new Look("Wisp Rune", "EPIC", "player_head", "", "c94d0a068ea50a9de2cef23a92bca4b3678d12ca8a1815d13beac94dfd557125")),
			Map.entry("necromancer_lord_leggings", new Look("Necromancer Lord Leggings", "LEGENDARY", "leather_leggings", "64,36,79", "")),
			Map.entry("blood_lust_talisman", new Look("Blood Lust Talisman", "EPIC", "player_head", "", "4463e5334ecd9b3b986f6e9cb04dba3b643a63f39e934ead76ece518f82f865")),
			Map.entry("bloody_recombobulator", new Look("Bloodstone", "LEGENDARY", "player_head", "", "57ccd36dc8f72adcb1f8c8e61ee82cd96ead140cf2a16a1366be9b5a8e3cc3fc")),
			Map.entry("reaper_soul", new Look("Reaper Rune", "EPIC", "player_head", "", "3a7cda9004fc197d66afbc2b003a5b9ef13cf4600b1fc7490067590f047181e")),
			Map.entry("warbound_stone", new Look("Warbound Stone", "EPIC", "player_head", "", "76e9b960dad4b0d330e9d876699862eed6ba388dd5f37209ec000ab3e9277979")),
			Map.entry("wither_lens", new Look("Wither Lens", "EPIC", "player_head", "", "5ee4bb4821d0f5ed865c21090a80b5ee7d52268476ee25d389710f7cc9f110d6")),
			Map.entry("awakened_necrostaff", new Look("Gravebinder", "LEGENDARY", "blaze_rod", "", "")),
			Map.entry("necromancer_lord_chestplate", new Look("Necromancer Lord Chestplate", "LEGENDARY", "leather_chestplate", "0,0,0", "")),
			Map.entry("colossus_helmet", new Look("Colossus Helmet", "LEGENDARY", "diamond_helmet", "", "")),
			Map.entry("colossus_boots", new Look("Colossus Boots", "LEGENDARY", "iron_boots", "", "")),
			Map.entry("frostbound_helmet", new Look("Frostbound Helmet", "LEGENDARY", "leather_helmet", "110,180,210", "")),
			Map.entry("frostbound_boots", new Look("Frostbound Boots", "LEGENDARY", "leather_boots", "110,180,210", "")),
			Map.entry("colossus_leggings", new Look("Colossus Leggings", "LEGENDARY", "iron_leggings", "", "")),
			Map.entry("frostbound_leggings", new Look("Frostbound Leggings", "LEGENDARY", "leather_leggings", "110,180,210", "")),
			Map.entry("boneguard_emblem", new Look("Boneguard Emblem", "EPIC", "player_head", "", "246d0d3d1069165a4523a858058a090c70b7429929abc869009cb6ebfcde7506")),
			Map.entry("knight_soul", new Look("Knight Rune", "EPIC", "player_head", "", "1fc0184473fe882d2895ce7cbc8197bd40ff70bf10d3745de97b6c2a9c5fc78f")),
			Map.entry("mortuary_stone", new Look("Mortuary Stone", "EPIC", "player_head", "", "2b055c810bddfd16264ec8d439c43283e35bca71a50983e15e364cd8ab7c668f")),
			Map.entry("colossus_chestplate", new Look("Colossus Chestplate", "LEGENDARY", "iron_chestplate", "", "")),
			Map.entry("frostbound_chestplate", new Look("Frostbound Chestplate", "LEGENDARY", "leather_chestplate", "110,180,210", "")),
			Map.entry("overlord_star", new Look("Overlord Star", "EPIC", "player_head", "", "d71b954e89aed156f90fafce6dc7788ac76b6466cecca3a4f4ec1e463eb914a1")),
			Map.entry("skeletal_lens", new Look("Skeletal Lens", "LEGENDARY", "player_head", "", "3464874df452c1d717eddd0fb3b848202ad15571245af6fade2ecf514f3c80bb")),
			Map.entry("colossus_blade", new Look("Overlord's Edge", "LEGENDARY", "iron_sword", "", "")),
			Map.entry("colossus_maul", new Look("Ossuary Maul", "LEGENDARY", "iron_axe", "", "")),
			Map.entry("soulfire_scepter", new Look("Soulfire Scepter", "LEGENDARY", "blaze_rod", "", "")),
			Map.entry("soulpiercer", new Look("Soulpiercer", "LEGENDARY", "bow", "", "")),
			Map.entry("shadow_soul", new Look("Shadow Rune", "RARE", "player_head", "", "20de5e8974940375934d32f71c91ad2d5728d38e51647dcc8f39206c099a54c2")),
			Map.entry("starward_signet", new Look("Starward Signet", "LEGENDARY", "player_head", "", "313384a293cfbba3489b483ebc1de7584ca2726d7f5c3a620513474925e87b97")),
			Map.entry("eclipse_star", new Look("Eclipse Star", "EPIC", "player_head", "", "71c8042527f81c8e293e2810113489f4394cc6efe15d1afac431571c73b62dc4")),
			Map.entry("awakened_dragonlance", new Look("Dragonheart Lance", "LEGENDARY", "netherite_sword", "", "")),
			Map.entry("awakened_necromancer_helmet", new Look("Gravebinder Helmet", "LEGENDARY", "leather_helmet", "64,36,79", "")),
			Map.entry("awakened_necromancer_chestplate", new Look("Gravebinder Chestplate", "LEGENDARY", "leather_chestplate", "64,36,79", "")),
			Map.entry("awakened_necromancer_leggings", new Look("Gravebinder Leggings", "LEGENDARY", "leather_leggings", "64,36,79", "")),
			Map.entry("awakened_necromancer_boots", new Look("Gravebinder Boots", "LEGENDARY", "leather_boots", "64,36,79", "")),
			Map.entry("awakened_paladin_helmet", new Look("Dawnwarden Helmet", "LEGENDARY", "leather_helmet", "221,201,115", "")),
			Map.entry("awakened_paladin_chestplate", new Look("Dawnwarden Chestplate", "LEGENDARY", "leather_chestplate", "221,201,115", "")),
			Map.entry("awakened_paladin_leggings", new Look("Dawnwarden Leggings", "LEGENDARY", "leather_leggings", "221,201,115", "")),
			Map.entry("awakened_paladin_boots", new Look("Dawnwarden Boots", "LEGENDARY", "leather_boots", "221,201,115", "")),
			Map.entry("awakened_assassin_helmet", new Look("Nightfall Helmet", "LEGENDARY", "leather_helmet", "35,26,49", "")),
			Map.entry("awakened_assassin_chestplate", new Look("Nightfall Chestplate", "LEGENDARY", "leather_chestplate", "35,26,49", "")),
			Map.entry("awakened_assassin_leggings", new Look("Nightfall Leggings", "LEGENDARY", "leather_leggings", "35,26,49", "")),
			Map.entry("awakened_assassin_boots", new Look("Nightfall Boots", "LEGENDARY", "leather_boots", "35,26,49", "")),
			Map.entry("heart_of_the_abyss", new Look("Heart of the Abyss", "MYTHIC", "player_head", "", "79c08e7447aecda5c9ab6113eb0efbabfa1bd9f811811208d89875c146ee8844")),
			Map.entry("abyssal_seal", new Look("Abyssal Seal", "LEGENDARY", "player_head", "", "7ab83858ebc8ee85c3e54ab13aabfcc1ef2ad446d6a900e471c3f33b78906a5b")),
			Map.entry("wither_catalyst", new Look("Wither Catalyst", "RARE", "wither_skeleton_skull", "", "")),
			Map.entry("withered_orb", new Look("Withered Orb", "EPIC", "ender_eye", "", "")),
			Map.entry("arcane_blood", new Look("Arcane Blood", "EPIC", "player_head", "", "bf5d20e0600174dcbab7745d944832fb2063c2bd1490c6505901b28bfaacd8e5")),
			Map.entry("auto_recombobulator", new Look("Auto Recombobulator", "LEGENDARY", "player_head", "", "5dff8dbbab15bfbb11e23b1f50b34ef548ad9832c0bd7f5a13791adad0057e1b")),
			Map.entry("wither_cloak_sword", new Look("Wither Cloak Sword", "EPIC", "stone_sword", "", "")),
			Map.entry("first_dungeon_star", new Look("First Dungeon Star", "EPIC", "nether_star", "", "")),
			Map.entry("wither_core", new Look("Wither Core", "LEGENDARY", "nether_star", "", "")),
			Map.entry("hardened_clay", new Look("Hardened Clay", "MYTHIC", "terracotta", "", "")));

	private ChestLooks() {
	}

	/** An item's look by its id, or null when the wiki has none for it. */
	static Look of(String id) {
		return LOOKS.get(id);
	}
}
