package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Gate;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Staff desks filled with made-up players and the server's real answers (docs/dragsim/staff-replies.md) rather
 * than the live game, for the client game test to draw the desk's main screens: each tab, a detail panel and a dialog.
 */
public final class StaffDeskSamples {
	/** Every sample the game test draws. */
	public static final List<String> ALL = List.of("overview", "records", "account", "signal", "ban");

	private StaffDeskSamples() {
	}

	public static Screen desk(String which) {
		long now = System.currentTimeMillis();
		List<Roster.Online> online = new ArrayList<>(List.of(
				new Roster.Online("Avxyl", "", ""), new Roster.Online("JLLM", "", "MVP+"), new Roster.Online("Speedy_089", "", "VIP"),
				new Roster.Online("Ttv_Pierre", "", ""), new Roster.Online("Lauchy", "", "ADMIN"), new Roster.Online("Realname", "Disguise", "MOD"),
				new Roster.Online("_nephis", "", "DRAGON"), new Roster.Online("Audeu", "", "DRAGON"), new Roster.Online("chark001", "", "DRAGON"),
				new Roster.Online("KnightIris", "", "DRAGON"), new Roster.Online("YetiOwO", "", "DRAGON"), new Roster.Online("Finnzius", "", ""),
				new Roster.Online("GoopMe", "", "DRAGON")));
		StaffFlags.State state = new StaffFlags.State(
				List.of(new StaffFlags.Watch("Ttv_Pierre", "alt of a banned account", "Lauchy", now - 3_600_000)),
				List.of(new StaffFlags.Captcha("Speedy_089", false, "Lauchy", -1, now - 1_800_000), new StaffFlags.Captcha("Avxyl", true, "alotropia", 8, now - 7_200_000)),
				List.of(new StaffFlags.Report(4, "Avxyl", "Snitch", "flying around the altar", now - 3_600_000)),
				List.of(new StaffFlags.Flag("JLLM", "ANTICHEAT", "427 unreviewed · DragSim AC", 586.8, now - 8 * 3_600_000)),
				now - 480_000, List.of(new StaffFlags.Alt("Ttv_Pierre", "Linked to a banned account", "OtherAlt (banned), Speedy_089, OldPierre", now - 86_400_000)), 0,
				List.of(new StaffFlags.Priority("Avxyl", 67.5, now - 120_000, List.of(new StaffFlags.Reading(67.5, now - 120_000),
								new StaffFlags.Reading(47.3, now - 600_000), new StaffFlags.Reading(21.0, now - 3_600_000))),
						new StaffFlags.Priority("JLLM", 47.3, now - 600_000)),
				List.of(new StaffFlags.Setback("Avxyl", 11, "minestom-3ae7ecbe, -669, 10, -274", now - 1_200_000, now - 300_000)),
				List.of(new StaffFlags.Brand("Avxyl", "Lunarclient:v2.23.14-2645,fabric", now - 540_000)));
		Map<StaffActions, List<String>> records = Map.of(
				StaffActions.HISTORY, List.of("-------- History of Avxyl (page 1) --------",
						"#292 BAN Avxyl by Lauchy on Nov 7, 2026 05:02 - 1.0 - Cheating Through Unfair Advantages (active, 6d 23h left)",
						"#291 KICK Avxyl by Lauchy on Nov 7, 2026 05:02 - Lazarus test (undone)",
						"#290 MUTE Avxyl by alotropia on Nov 7, 2026 03:07 - 2.5 - Extreme Behaviour (undone by Porier)",
						"#289 WARN Avxyl by Porier on Nov 6, 2026 22:00 - Spamming the auction house (undone by Porier)"),
				StaffActions.WARNS, List.of("-------- Warnings of Avxyl (1) --------", "#293 WARN Avxyl by Porier on Nov 7, 2026 05:10 - Spam (active, Permanent left)"),
				StaffActions.ALTS, List.of("-------- Alts of Avxyl (2) --------", "Lauchy", "OtherAlt"));
		Map<StaffActions, List<String>> account = Map.of(
				StaffActions.LOOKUP, List.of("-----------------------------", "Lookup: Avxyl", "Rank: DEFAULT",
						"IP: /203.0.113.42:60418", "Balance: 512,771,693,499 coins", "Health: 3155", "Mana: 2279", "-----------------------------"),
				StaffActions.ACCOUNT_HISTORY, List.of("-------- Account history of Avxyl --------", "Names: Avxyl", "Addresses: 2 (hidden; use /alts)"),
				StaffActions.AC_INFO, List.of("[DragSim AC] Avxyl checks=176; vl=0; transaction=128ms; received=392282; sent=236118; alerts=false"),
				StaffActions.CAPTCHA_STATUS, List.of("Avxyl passed their last check 7 h ago, in 8 s (sent by JavaBasti)."));
		Map<StaffActions, List<String>> audit = Map.of(StaffActions.AUDIT_LOG, List.of(
				"50 transactions for Avxyl (last 7 days):",
				"[11/07 00:01:35] CREATE pet_item_lucky_clover (pet_take_out_equipped)",
				"[11/07 00:01:35] GIVE golden_dragon_pet_legendary (given)",
				"[11/07 00:01:38] TRANSFER golden_dragon_pet_legendary ↔ MaciekByr (trade)",
				"[11/07 00:01:38] GIVE golden_dragon_pet_legendary (given)",
				"[11/07 00:01:39] DESTROY golden_dragon_pet_legendary (pet_consume)",
				"[11/07 00:03:42] GIVE summoning_eye (loot_drop)",
				"[11/07 00:05:42] DESTROY defender_talisman (npc_sell)",
				"[11/07 00:05:52] CREATE warden_talisman (craft_create)",
				"[11/07 00:06:00] DESTROY warden_talisman (auction_list)",
				"[11/07 00:07:54] DESTROY summoning_eye x7 (npc_sell)",
				"[11/07 00:08:43] GIVE rift_goo x15 (loot_drop)",
				"[11/07 00:08:43] GIVE gelatinous_membrane x2 (loot_drop)"));
		Map<StaffActions, List<String>> everything = new java.util.EnumMap<>(StaffActions.class);
		everything.putAll(records);
		everything.putAll(account);
		everything.putAll(audit);
		return switch (which) {
			case "signal" -> desk(online, state, Gate.Tier.ADMIN, "Avxyl", StaffDeskScreen.Tab.OVERVIEW, everything).withSignalSheet();
			case "records" -> desk(online, state, Gate.Tier.ADMIN, "Avxyl", StaffDeskScreen.Tab.RECORDS, everything);
			case "account" -> desk(online, state, Gate.Tier.ADMIN, "Avxyl", StaffDeskScreen.Tab.ACCOUNT, everything);
			case "ban" -> desk(online, state, Gate.Tier.ADMIN, "Avxyl", StaffDeskScreen.Tab.OVERVIEW, everything).withDialog(DeskDialog.Kind.BAN, "1.0");
			default -> desk(online, state, Gate.Tier.ADMIN, "Avxyl", StaffDeskScreen.Tab.OVERVIEW, everything);
		};
	}

	private static StaffDeskScreen desk(List<Roster.Online> online, StaffFlags.State state, Gate.Tier tier, String who, StaffDeskScreen.Tab tab,
			Map<StaffActions, List<String>> replies) {
		return new StaffDeskScreen(new StaffDeskScreen.Sample(online, state, tier, who, tab, replies));
	}
}
