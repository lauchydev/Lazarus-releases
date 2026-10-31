package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Drops;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Period;
import dev.lauchy.lazarus.core.Tallies;
import dev.lauchy.lazarus.core.Tally;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Endstone Protector (golem) and Warden loot on its own: fights, where you placed, your
 * damage, every drop with how often it comes, and how many golems since each rare drop.
 * Fights and drops come from {@link BossDrops}, which decides what loot is yours; it reads golem
 * fights even while its own readout is switched off. {@code /golem} lists it all; add "session"
 * for this session.
 */
public final class GolemLoot extends Feature {
	private static final int PAID_PLACES = 3;

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every golem so far.", Period.SESSION));
	private final TextSetting rares = add(new TextSetting("rares", "Rare drops",
			"Comma-separated drops to count golems since, with a toast when one lands.",
			"Tier Boost Core, Golem, Warden Heart, Warden Core", 200));

	private final BoolSetting since = add(new BoolSetting("sinceDrop", "Kills since last drop", "Show how many kills since each rare drop.", false));
	private final BoolSetting rates = add(new BoolSetting("rates", "Drop rates", "Show how often each drop comes, like 1 in 12 or 2.5 per kill.", true));

	private final Tallies tallies = new Tallies(Config.dir().resolve("golem.json"));

	public GolemLoot() {
		super("golemLoot", "Golem Loot", "Protector and Warden fights, placings, damage and drops; /golem lists them.", Category.END, true);
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		BossDrops.listen(new BossDrops.GolemListener() {
			@Override
			public void fight(boolean warden, int place, String damage) {
				if (enabled()) fought(warden, place, Text.number(damage));
			}

			@Override
			public void drop(boolean warden, Drops.Drop drop) {
				if (enabled()) dropped(drop);
			}
		});
		Hud.register(new HudElement(this, "golemLoot", "Golem Loot", 0.006f, 0.50f) {
			@Override
			protected boolean visible() {
				return Area.inEnd() && tallies.shown(period.get()).count("fights") > 0;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return Draw.panel(g, font, List.of("§5§lGolem Loot §7Session", "§7Kills §f14 §8+3 Warden",
						"§7Avg place §f#1.6 §8· §f8 §7firsts", "§7Avg damage §f2.4M",
						"§f9x §fEnder Pearl §8(1 in 2)\t§a90", "§f1x §5Tier Boost Core §8(1 in 17)\t§a1.1M", "§7Value\t§a1.1M"));
				return Draw.panel(g, font, rows(period.get(), 5));
			}
		});
		Commands.add(() -> ClientCommands.literal("golem")
				.executes(c -> list(Period.ALL_TIME))
				.then(ClientCommands.literal("session").executes(c -> list(Period.SESSION)))
				.then(ClientCommands.literal("reset").executes(c -> {
					tallies.resetSession();
					Message.lazarus().success("Golem loot session reset. All-time totals are kept.").send();
					return 1;
				})));
	}

	private void fought(boolean warden, int place, double damage) {
		tallies.both(t -> {
			t.add("fights", 1);
			t.add("placeSum", place);
			t.add("damageSum", Math.max(0, damage));
			if (place == 1) t.add("firsts", 1);
			if (place > PAID_PLACES) return;
			t.add(warden ? "wardens" : "kills", 1);
			// A Warden rare (Warden Heart, Warden Core) counts Wardens; the rest count Protectors.
			for (String rare : LootRows.names(rares.get())) {
				if (wardenRare(rare) == warden) t.miss(rare);
			}
		});
	}

	private void dropped(Drops.Drop d) {
		String rare = LootRows.rareFor(d.item(), LootRows.names(rares.get()));
		tallies.both(t -> {
			t.drop(d.item(), d.count());
			if (rare == null) return;
			int after = t.hit(rare);
			if (t == tallies.total("")) {
				String what = wardenRare(rare) ? "Warden" : "golem";
				Toast.show(LootRows.colour(d.item()) + d.item() + " §7after §f" + after + " §7" + what + (after == 1 ? "" : "s"), Theme.ACCENT);
			}
		});
	}

	private static boolean wardenRare(String rare) {
		return rare.toLowerCase(Locale.ROOT).contains("warden");
	}

	private List<String> rows(Period p, int limit) {
		Tally t = tallies.shown(p);
		int fights = t.count("fights"), kills = t.count("kills"), wardens = t.count("wardens"), firsts = t.count("firsts");
		List<String> out = new ArrayList<>();
		out.add("§5§lGolem Loot §7" + p.label());
		out.add("§7Kills §f" + kills + (wardens > 0 ? " §8+" + wardens + " Warden" : "")
				+ (fights > kills + wardens ? " §8· " + (fights - kills - wardens) + " unpaid" : ""));
		if (fights > 0) {
			out.add("§7Avg place §f#" + String.format(Locale.ROOT, "%.1f", t.count("placeSum") / (double) fights)
					+ (firsts > 0 ? " §8· §f" + firsts + " §7first" + (firsts == 1 ? "" : "s") : ""));
			out.add("§7Avg damage §f" + Text.compact(t.sum("damageSum") / fights));
		}
		if (since.on()) out.addAll(LootRows.since(t.since(), LootRows.names(rares.get()), "Kills since last drop"));
		int golems = kills + wardens;
		out.addAll(LootRows.drops(t.drops(), rates.on() ? golems : 0, "kill", limit, PriceDatabase::worth));
		if (!t.drops().isEmpty() && golems > 0) out.add("§7Per golem\t§a" + Text.compact(LootRows.total(t.drops(), PriceDatabase::worth) / (double) golems));
		return out;
	}

	private int list(Period p) {
		if (tallies.shown(p).count("fights") == 0) {
			Message.lazarus().info(p == Period.SESSION ? "No golem fights this session." : "No golem fights recorded yet.").send();
			return 1;
		}
		Message.panel(rows(p, Integer.MAX_VALUE)).send();
		return 1;
	}
}
