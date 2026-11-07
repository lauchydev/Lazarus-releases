package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.setting.BoolSetting;

/**
 * The anticheat's lines ("[FLAG REVIEW] … priority score", "DragSim AC » … position was updated", "… joined
 * using …", "[DragSim AC] … Click rate: CPS=14.5 (review only)") out of chat and into the staff desk: each is
 * shared with every staff member through the Lazarus server, where it becomes a reason on the roster (a priority
 * score, a run of setbacks, a check the player was caught on) or the client a player joined with.
 */
public final class AcFeed extends Feature {
	private final BoolSetting hide = add(new BoolSetting("hide", "Keep out of chat", "The anticheat's lines go to the staff desk instead of chat.", true));

	public AcFeed() {
		super("acFeed", "Anticheat Lines", "The anticheat's priority scores, setbacks, detections and client lines go to the staff desk instead of chat.", Category.STAFF, true);
	}

	@Override
	public void init() {
		Chat.hide(line -> {
			if (!enabled() || !Staff.is()) return false;
			for (String part : line.text().split("\\R")) {
				AcLine.Line read = AcLine.parse(part);
				if (read != null) FlagSync.anticheat(read);
			}
			return hide.on() && AcLine.isAnticheat(line.text());
		});
	}
}
