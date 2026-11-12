package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.TextSetting;

import java.util.Arrays;
import java.util.List;

/**
 * The staff drawer on a profile: ban, mute, kick, teleport, tunnel, send, captcha, history, alts and refund,
 * with one-click duration and reason chips and the punishment ladder: pick a rule and the offence, and the
 * duration and reason fill in. The Profile Viewer opens the drawer when this is on and the
 * player's dragsim.co rank is a staff one ({@link dev.lauchy.lazarus.core.Staff}). The online roster
 * ({@link OnlineRoster}) goes by the same switch, with its own key.
 */
public final class StaffTools extends Feature {
	public static StaffTools INSTANCE;

	private final TextSetting durations = add(new TextSetting("durations", "Duration chips",
			"One-click durations on the ban and mute card, comma separated.", "1h, 1d, 7d, 14d, 30d, perm", 120));
	private final TextSetting reasons = add(new TextSetting("reasons", "Reason chips",
			"One-click reasons on the kick card, and on ban and mute when their ladder is empty, comma separated.", "Cheating, Macroing, Chat abuse, Scamming, Alt abuse, Inappropriate name", 300));
	/** Dragsim's ban ladder as it stands (1 Nov 2026); the one before it, so a saved copy of it moves on. */
	static final String BAN_RULES = "1.0 Cheating Through Unfair Advantages: 7d/30d/90d/1y; 1.1 Dishonest Trading: 30d/90d/365d; 1.2 Mass Rule Breaking: set; "
			+ "1.3 Punishment Evasion: set; 1.4 Alt Abuse: 14d/90d/1y; 1.5 Bug Abuse: 30d/1y; 1.6 Duping: perm; 2.3 Advertising: perm";
	private static final String OLD_BAN_RULES = BAN_RULES.replace("1.4 Alt Abuse: 14d/90d/1y", "1.4 Alt Abuse: 30d/1y");
	private final TextSetting banLadder = add(new TextSetting("banLadder", "Ban rules",
			"The rules on the ban card as rule name: 1st/2nd/3rd offence, separated by semicolons. \"set\" leaves the time to you.",
			BAN_RULES, 1000));
	private final TextSetting muteLadder = add(new TextSetting("muteLadder", "Mute rules",
			"The rules on the mute card, written the same way as Ban rules.",
			"2.0 Chat Abuse: 30m/12h/7d; 2.1 Begging: 30m/12h/7d; 2.2 Provocation: 6h/24h/30d; 2.4 Harassing: set; 2.5 Extreme Behaviour: 7d/30d/90d", 1000));

	/** The key that opens the staff desk, unbound until set. */
	private final KeySetting rosterKey = add(new KeySetting("rosterKey", "Staff desk key", "Opens the staff desk: everyone online, the flagged first, and their dossier and actions."));

	public StaffTools() {
		super("staffTools", "Staff Tools", "Ban, mute, kick, teleport and other staff buttons beside a profile.", Category.STAFF, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		// The ladder is saved with the settings, so a changed default would never reach anyone who hasn't edited theirs.
		if (banLadder.get().equals(OLD_BAN_RULES)) banLadder.set(BAN_RULES);
		OnlineRoster.init(rosterKey);
	}

	/** Whether the drawer is wanted at all; the rank check is the Profile Viewer's. */
	public static boolean on() {
		return INSTANCE != null && INSTANCE.enabled();
	}


	public List<String> durations() {
		return chips(durations);
	}

	public List<String> reasons() {
		return chips(reasons);
	}

	/** The ban rules of the punishment ladder, in the order written. */
	public List<Ladder.Rule> banRules() {
		return Ladder.parse(banLadder.get());
	}

	/** The mute rules of the punishment ladder, in the order written. */
	public List<Ladder.Rule> muteRules() {
		return Ladder.parse(muteLadder.get());
	}

	private static List<String> chips(TextSetting s) {
		return Arrays.stream(s.get().split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
	}
}
