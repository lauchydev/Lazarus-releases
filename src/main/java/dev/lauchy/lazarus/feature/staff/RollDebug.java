package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The staff-only "[Debug]" roll lines as a HUD readout instead of chat spam: the last few rolls, each
 * as the mob, what it rolled for, the roll out of 100 and whether it landed, with another player's roll
 * carrying their name. The lines can be kept out of chat altogether.
 */
public final class RollDebug extends Feature {
	/** One roll: whose it was ("" for yours), the mob, the kind (Roll, Table, Spawn), the roll, what was rolled for, and whether it hit. */
	public record Roll(String player, String mob, String kind, double roll, double outOf, String item, boolean hit, long at) {
		/** The HUD row: "Golden Dragon · Haunted Candy  78.0  HIT". */
		public String row() {
			String who = player.isEmpty() ? "" : "§5" + player + " §8· ";
			String what = item.isEmpty() ? kind : item;
			String n = String.format(Locale.ROOT, roll < 10 ? "%.2f" : "%.1f", roll);
			String end = kind.equals("Spawn") || kind.equals("Table") ? "" : hit ? "  §aHIT" : "  §cmiss";
			return who + "§7" + (mob.isEmpty() ? "" : mob + " §8· ") + "§f" + what + "  §e" + n + end;
		}
	}

	/** "[RANK] Name: " before another player's roll, then "[Debug] mob · Roll N/100 · segments", the mob being optional. */
	private static final Pattern LINE = Pattern.compile("^(?:\\[[^\\]]{1,20}\\]\\s+)?(?:(\\w{1,16}): )?\\[Debug\\]\\s+(?:(.+?)\\s+·\\s+)?(Roll|Table|Spawn)\\s+([\\d.]+)/(\\d+)\\s*·?\\s*(.*)$");
	/** A segment that is a number about the roll rather than what was rolled for. */
	private static final Pattern STAT = Pattern.compile("(?i)^(MF|PL|Luck|Chance|Base|Range|Armor range|Odds)\\b.*|^Golden\\s+\\d.*|^\\d+ item odds.*");

	private final IntSetting lines = add(new IntSetting("lines", "Rolls shown", "How many of the latest rolls stay on the readout.", 5, 1, 12, ""));
	private final BoolSetting others = add(new BoolSetting("others", "Other players' rolls", "Show rolls the server reports for other players too.", true));
	private final BoolSetting spawns = add(new BoolSetting("spawns", "Spawn rolls", "Show which dragon or boss a spawn roll chose.", true));
	private final BoolSetting hideChat = add(new BoolSetting("hideChat", "Hide from chat", "Keep the [Debug] lines out of chat; the readout has them.", true));
	private final IntSetting linger = add(new IntSetting("linger", "Shown for", "How long a roll stays on the readout.", 30, 5, 300, "s"));

	private final ArrayDeque<Roll> recent = new ArrayDeque<>();

	public RollDebug() {
		super("rollDebug", "Roll Readout", "The [Debug] roll lines as a HUD readout, and out of chat.", Category.STAFF, true);
	}

	@Override
	public void init() {
		Chat.hide(line -> {
			if (!enabled()) return false;
			Roll r = parse(line.text(), Util.getMillis());
			if (r == null) return false;
			if ((!r.player().isEmpty() && !others.on()) || (r.kind().equals("Spawn") && !spawns.on())) return hideChat.on();
			recent.addLast(r);
			while (recent.size() > 12) recent.pollFirst();
			return hideChat.on();
		});
		Hud.register(new HudElement(this, "rolls", "Roll readout", 0.80f, 0.30f) {
			@Override
			protected boolean visible() {
				return !shown().isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = new ArrayList<>();
				rows.add("§5§lRolls");
				if (sample) {
					rows.add(new Roll("", "Golden Dragon", "Roll", 78.0, 100, "Haunted Candy", true, 0).row());
					rows.add(new Roll("Steve", "Special Zealot", "Roll", 3.81, 100, "Golden Eye", false, 0).row());
				} else {
					for (Roll r : shown()) rows.add(r.row());
				}
				return Draw.panel(g, font, rows);
			}
		});
	}

	/** The rolls young enough to show, oldest first, at most the setting's count. */
	private List<Roll> shown() {
		long now = Util.getMillis();
		List<Roll> out = new ArrayList<>();
		for (Roll r : recent) if (now - r.at() < linger.get() * 1000L) out.add(r);
		while (out.size() > lines.get()) out.removeFirst();
		return out;
	}

	/**
	 * The roll a line is, or null when it isn't one. A roll that names an item landed on it, since the
	 * server names the item a roll chose: "HIT" only marks fixed-odds items, and only "miss" means the
	 * roll gave nothing. Notes after ";" are dropped.
	 */
	public static Roll parse(String line, long now) {
		Matcher m = LINE.matcher(line.trim());
		if (!m.matches()) return null;
		String player = m.group(1) == null ? "" : m.group(1);
		String mob = m.group(2) == null ? "" : m.group(2).trim();
		String kind = m.group(3);
		double roll, outOf;
		try {
			roll = Double.parseDouble(m.group(4));
			outOf = Double.parseDouble(m.group(5));
		} catch (NumberFormatException e) {
			return null;
		}
		String item = "";
		boolean missed = false;
		for (String segment : m.group(6).split("\\s+·\\s+")) {
			String s = segment.trim();
			if (s.isEmpty() || STAT.matcher(s).matches()) continue;
			int semi = s.indexOf(';');
			if (semi >= 0) s = s.substring(0, semi).trim();
			if (s.endsWith(" HIT")) {
				s = s.substring(0, s.length() - 4).trim();
			} else if (s.endsWith(" miss")) {
				missed = true;
				s = s.substring(0, s.length() - 5).trim();
			}
			if (!s.isEmpty() && item.isEmpty()) item = s;
		}
		boolean hit = !item.isEmpty() && !missed;
		// A spawn or table roll names what it chose, which is the thing rolled for; a spawn with no name chose nothing yet.
		if (kind.equals("Spawn") && item.equals(mob)) item = "";
		return new Roll(player, mob, kind, roll, outOf, item, hit, now);
	}
}
