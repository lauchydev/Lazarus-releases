package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Drops;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.LootFeed;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Period;
import dev.lauchy.lazarus.core.Tallies;
import dev.lauchy.lazarus.core.Tally;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Goop boss in the Rift: someone squeezes goop into the altar, it rises through its
 * forms (Rift Slime, Colony, Corruptor, Devourer, then The Disturbed), and "THE RIFT
 * SLEEPS" ends it. While a fight runs the HUD shows its form and time; after, it keeps
 * fights, goops you placed, your share of the damage and place, whether you were paid
 * (top three, the placer, or 3%+), and the loot. {@code /goop} lists it all.
 */
public final class GoopTracker extends Feature {
	/** "Name squeezes the goop into the altar." starts a fight; "has woken something under the altar..." is the rift opening, not the Goop. */
	private static final Pattern PLACER = Pattern.compile("^(?:✦ )?(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16}) squeezes the goop into the altar\\.$");
	private static final List<String> FORMS = List.of("Rift Slime", "Rift Colony", "Rift Corruptor", "Rift Devourer", "The Disturbed");
	private static final String END = "THE RIFT SLEEPS";
	private static final Pattern MY_SHARE = Pattern.compile("^You did ([\\d.]+)% of the damage\\.");
	private static final Pattern POSITION = Pattern.compile("Your Damage: [\\d,.]+[KMB]? \\((?:[\\d.]+%, )?Position #(\\d+)\\)");
	private static final Pattern SHARE_IN_BOX = Pattern.compile("Your Damage: [\\d,.]+[KMB]? \\(([\\d.]+)%, Position #\\d+\\)");
	private static final Pattern RANKED = Pattern.compile("^(\\d+)(?:st|nd|rd|th) Damager (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16}) - [\\d,]+");
	private static final String DROPPED = "THE DISTURBED DROPPED";
	private static final Pattern REWARD_LINE = Pattern.compile("^\\+(.+?)(?:\\s*\\([^)]*\\))?$");
	private static final Pattern COUNTED = Pattern.compile("^([\\d,]+)\\s+(.+)$");
	/** Loot lines arrive within a few seconds of the end. */
	private static final long LOOT_MS = 8_000;

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every fight so far.", Period.SESSION));
	private final BoolSetting rates = add(new BoolSetting("rates", "Drop rates", "Show how often each drop comes, like 1 in 12 or 2.5 per kill.", true));

	private final Tallies tallies = new Tallies(Config.dir().resolve("goop.json"));

	/** The running fight: when it started, its current form, and whether you placed it. */
	private long startedAt, endedAt;
	private String form;
	private boolean mine;
	/** Read from the end box and share line, then settled once the loot window closes. */
	private int place = -1;
	private double share = -1;
	private boolean inDropList, settled = true;
	/** Whether you took part: placed it, got a place or share, or got loot. Others' fights in the lobby don't count. */
	private boolean involved;

	public GoopTracker() {
		super("goopTracker", "Goop Tracker", "The Rift's Goop boss: its form and time, your damage and place, and loot; /goop lists them.", Category.END, true);
	}

	@Override
	public void init() {
		tallies.load();
		// Before 1.17.5 a server line in the drop list ("OOPS! You don't have enough mana!") or a stat on the reward line ("+5 ☘ Mining Fortune") was booked as a drop.
		boolean[] cleaned = {false};
		tallies.both(t -> cleaned[0] |= t.drops().keySet().removeIf(Drops::notAnItem));
		if (cleaned[0]) tallies.save();
		Config.saveOn(() -> {
			settle();
			tallies.save();
		});
		Chat.listen(l -> {
			if (!enabled()) return;
			for (String line : l.text().split("\\R")) line(line.trim(), l.text());
		});
		LootFeed.listen(this::loot);
		// Books a finished fight once its loot window closes, not only when the next chat line comes.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!settled && System.currentTimeMillis() - endedAt > LOOT_MS) settle();
		});
		Hud.register(new HudElement(this, "goopTracker", "Goop Tracker", 0.006f, 0.42f) {
			@Override
			protected boolean visible() {
				return fighting() || System.currentTimeMillis() - endedAt < 120_000 && tallies.shown(period.get()).count("fights") > 0;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return Draw.panel(g, font, List.of("§5§lGoop §7· §fRift Corruptor §8(3/5)", "§7Time §f1:42",
						"§7Fights §f6 §8· §f5 §7paid · §f2 §7placed", "§7Avg share §f8.4% §8· place #2.3",
						"§f41x §9Rift Goo §8(6.8 per fight)\t§a41k", "§f2x §5Corrupted Core §8(1 in 3)\t§a2M", "§7Value\t§a2.04M"));
				return Draw.panel(g, font, rows(period.get(), 4, true));
			}
		});
		Commands.add(() -> ClientCommands.literal("goop")
				.executes(c -> list(Period.ALL_TIME))
				.then(ClientCommands.literal("session").executes(c -> list(Period.SESSION)))
				.then(ClientCommands.literal("reset").executes(c -> {
					tallies.resetSession();
					Message.lazarus().success("Goop session reset. All-time totals are kept.").send();
					return 1;
				})));
	}

	private boolean fighting() {
		return startedAt > 0 && endedAt < startedAt;
	}

	private void line(String line, String whole) {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player == null ? "" : mc.player.getName().getString();
		Matcher m;
		if ((m = PLACER.matcher(line)).matches()) {
			settle();
			startedAt = now;
			form = FORMS.getFirst();
			mine = m.group(1).equals(me);
			place = -1;
			share = -1;
			return;
		}
		if (Chat.fromPlayer(line) && !line.startsWith("You did")) return;
		if (fighting()) {
			for (String f : FORMS) if (line.startsWith(f + " ")) form = f;
		}
		if (line.contains(END)) {
			if (startedAt == 0) startedAt = now; // joined mid-fight
			endedAt = now;
			settled = false;
			involved = mine;
			// "Your Damage: 6,017,726,189 (13.8%, Position #3)": your place, and your share when the box carries it.
			Matcher p = POSITION.matcher(whole);
			if (p.find()) {
				place = Integer.parseInt(p.group(1));
				involved = true;
			}
			Matcher s = SHARE_IN_BOX.matcher(whole);
			if (s.find()) share = Double.parseDouble(s.group(1));
			return;
		}
		if (!settled && now - endedAt > LOOT_MS) settle();
		if (settled) return;
		if ((m = RANKED.matcher(line)).find() && m.group(2).equals(me)) {
			place = Integer.parseInt(m.group(1));
			involved = true;
		} else if ((m = MY_SHARE.matcher(line)).find()) {
			share = Double.parseDouble(m.group(1));
			involved = true;
		} else if (line.equals(DROPPED)) {
			inDropList = true;
		} else if ((m = REWARD_LINE.matcher(line)).matches()) {
			// "+1 Crypt Cache, 13 Rift Goo and 2 Gelatinous Membranes (top three)"
			for (String part : m.group(1).split(",\\s*|\\s+and\\s+")) {
				Matcher c = COUNTED.matcher(part.trim());
				// The reward line also carries stats ("+5 ☘ Mining Fortune"); only items count.
				if (c.matches() && !Drops.notAnItem(c.group(2)) && Catalog.id(c.group(2).trim()) != null) {
					add(c.group(2).trim(), Integer.parseInt(c.group(1).replace(",", "")));
				}
			}
		} else if (inDropList && !line.isEmpty()) {
			Drops.Drop d = Drops.listed(line, item -> Catalog.id(item) != null);
			if (d != null) add(d.item(), d.count());
		}
	}

	/** An announced drop is the Goop's when a staff roll names The Disturbed, or it lands in the loot window after a fight you were in. */
	private void loot(LootFeed.Loot l) {
		if (!enabled()) return;
		if (l.mob() != null) {
			if (l.mob().equals(FORMS.getLast())) add(l.drop().item(), l.drop().count());
			return;
		}
		if (!settled && l.at() - endedAt <= LOOT_MS) add(l.drop().item(), l.drop().count());
	}

	private void add(String item, int count) {
		involved = true;
		tallies.both(t -> t.drop(item, count));
	}

	/** Books the fight that just ended, once its loot window has passed or a new one starts. */
	private void settle() {
		if (settled) return;
		settled = true;
		inDropList = false;
		if (!involved) return;
		boolean paid = mine || place > 0 && place <= 3 || share >= 3;
		tallies.both(t -> {
			t.add("fights", 1);
			if (paid) t.add("paid", 1);
			if (mine) t.add("placed", 1);
			if (place > 0) {
				t.add("places", 1);
				t.add("placeSum", place);
			}
			if (share >= 0) {
				t.add("shares", 1);
				t.add("shareSum", share);
			}
		});
	}

	private List<String> rows(Period p, int limit, boolean live) {
		Tally t = tallies.shown(p);
		List<String> out = new ArrayList<>();
		if (live && fighting()) {
			int i = FORMS.indexOf(form);
			out.add("§5§lGoop §7· §f" + form + " §8(" + (i + 1) + "/" + FORMS.size() + ")" + (mine ? " §7· yours" : ""));
			out.add("§7Time §f" + Text.clock((System.currentTimeMillis() - startedAt) / 1000));
		} else {
			out.add("§5§lGoop §7" + p.label());
		}
		int fights = t.count("fights"), placed = t.count("placed"), shares = t.count("shares"), places = t.count("places");
		out.add("§7Fights §f" + fights + " §8· §f" + t.count("paid") + " §7paid" + (placed > 0 ? " · §f" + placed + " §7placed" : ""));
		if (shares > 0 || places > 0) {
			out.add("§7Avg share §f" + (shares > 0 ? String.format(Locale.ROOT, "%.1f%%", t.sum("shareSum") / shares) : "?")
					+ (places > 0 ? " §8· place #" + String.format(Locale.ROOT, "%.1f", t.count("placeSum") / (double) places) : ""));
		}
		out.addAll(LootRows.drops(t.drops(), rates.on() ? fights : 0, "fight", limit, PriceDatabase::worth));
		return out;
	}

	private int list(Period p) {
		if (tallies.shown(p).count("fights") == 0) {
			Message.lazarus().info(p == Period.SESSION ? "No Goop fights this session." : "No Goop fights recorded yet.").send();
			return 1;
		}
		Message.panel(rows(p, Integer.MAX_VALUE, false)).send();
		return 1;
	}
}
