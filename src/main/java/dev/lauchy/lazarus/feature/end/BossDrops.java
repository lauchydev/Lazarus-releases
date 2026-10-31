package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kills and loot from dragons (each kind on its own), the Endstone Protector and the
 * Warden, this session and all time. A dragon is yours when you put an eye into it or the
 * server sends you its loot summary; a Protector or Warden when its damage box ranks you in
 * the top three, or a staff roll names it as what dropped. {@code /drops} lists everything;
 * add "session" for this session only.
 * <p>
 * Protector and Warden fights are read even while this is switched off, because Golem Loot
 * counts them through here: closing the Dragon Drops readout must not stop golem tracking.
 */
public final class BossDrops extends Feature {
	private static final String PROTECTOR = "Endstone Protector", WARDEN = "Warden";
	private static final Pattern MY_EYE = Pattern.compile("^(?:» )?You placed an? (?:Summoning|Golden) Eye");
	private static final Pattern DRAGON_DEAD = Pattern.compile("^(?:☠ )?The (?:(\\w+) )?Dragon has de-spawned");
	/** The dragon's damage box, sent to everyone who hurt it; it names the dragon when the de-spawn line was missed. */
	private static final Pattern DRAGON_BOX = Pattern.compile("([A-Z]+) DRAGON DOWN!", Pattern.DOTALL);
	private static final String SUMMARY = "Here is your loot from the last dragon:";
	private static final Pattern SUMMARY_LINE = Pattern.compile("^-\\s*(.+)$");
	private static final Pattern BOX = Pattern.compile("(THE WARDEN|ENDSTONE PROTECTOR) DOWN!.*?Your Damage: ([\\d,.]+[KMB]?) \\((?:[\\d.]+%, )?Position #(\\d+)\\)", Pattern.DOTALL);
	/** What the nest beside the fight drops: never a boss's, whatever the timing says. */
	private static final Set<String> NEST = Set.of("summoning eye", "enderman", "null ovoid", "ender pearl", "spicy wart",
			"warped stone", "infinieye™", "wolf", "golden eye", "rift pass", "warden catalyst");
	/** A boss's drops land inside a second or two either side of its summary box. */
	private static final long BEFORE_MS = 2_000, AFTER_MS = 2_500, SUMMARY_MS = 1_500;
	/** A loot summary this long after the last credited kill is for a new dragon, even if its de-spawn line was missed. */
	private static final long NEW_DRAGON_MS = 60_000;
	private static final int PAID_PLACES = 3;

	private record Seen(long at, Drops.Drop drop) {
	}

	/** Hears each Protector or Warden fight you finish and each drop credited to one (the golem loot tracker). */
	public interface GolemListener {
		/** A fight's summary box: your place, and damage as the box writes it ("1.2M"). Paid when place is 3 or better. */
		void fight(boolean warden, int place, String damage);

		void drop(boolean warden, Drops.Drop drop);
	}

	private static final List<GolemListener> LISTENERS = new ArrayList<>();

	public static void listen(GolemListener l) {
		LISTENERS.add(l);
	}

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every kill so far.", Period.SESSION));
	private final BoolSetting rates = add(new BoolSetting("rates", "Drop rates", "Show how often each drop comes, like 1 in 12 or 2.5 per kill.", true));

	private final Tallies tallies = new Tallies(Config.dir().resolve("drops.json"));
	private final Deque<Seen> recent = new ArrayDeque<>();
	private boolean myEye;
	/** The current dragon's kill is already counted, so its loot summary doesn't count it again. */
	private boolean credited;
	private long creditedAt;
	private String lastDragon = "Dragon";
	/** The dragon the HUD shows: golems have their own HUD (Golem Loot), so they never take it over. */
	private String latest;
	private Object level;
	private String openBoss;
	private long openUntil, summaryUntil;

	public BossDrops() {
		super("bossDrops", "Boss Drops", "Dragon, Protector and Warden kills and loot; /drops lists them.", Category.END, true);
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		Chat.listen(l -> {
			String full = l.text();
			Matcher box = BOX.matcher(full);
			if (box.find()) {
				String boss = box.group(1).equals("THE WARDEN") ? WARDEN : PROTECTOR;
				int place = Integer.parseInt(box.group(3));
				// Logged so a fight that was counted wrong can be matched to its box afterwards.
				dev.lauchy.lazarus.Lazarus.LOG.info("Golem fight: {} at place {}", boss, place);
				for (GolemListener g : LISTENERS) g.fight(boss.equals(WARDEN), place, box.group(2));
				boss(boss, place);
				return;
			}
			if (!enabled()) return; // the rest is dragons, which only this counts
			if (full.contains("DOWN!") && !full.contains("DRAGON DOWN!")) dev.lauchy.lazarus.Lazarus.LOG.info("Boss Drops: a DOWN! box it couldn't read: {}", full);
			Matcher dragon = DRAGON_BOX.matcher(full);
			if (dragon.find()) {
				String kind = dragon.group(1);
				lastDragon = kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT) + " Dragon";
				return;
			}
			for (String line : full.split("\\R")) line(line.trim());
		});
		LootFeed.listen(this::loot);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) { // a new lobby: an eye placed in the last one isn't in this fight
				level = mc.level;
				myEye = false;
				credited = false;
				summaryUntil = 0;
				openBoss = null;
				recent.clear();
			}
			long now = System.currentTimeMillis();
			while (!recent.isEmpty() && now - recent.peekFirst().at > BEFORE_MS) recent.removeFirst();
		});
		Hud.register(new HudElement(this, "bossDrops", "Dragon Drops", 0.006f, 0.70f) {
			@Override
			protected boolean visible() {
				return latest != null && Area.inEnd();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return Draw.panel(g, font, List.of("§5§lGolden Dragon §7Session", "§7Kills §f6",
						"§f2x §7Golden Dragon Fragment", "§f1x §6Golden Dragon Helmet", "§7Value §a3.2M"));
				return Draw.panel(g, font, rows(latest, period.get(), 5));
			}
		});
		Commands.add(() -> ClientCommands.literal("drops")
				.executes(c -> list(Period.ALL_TIME))
				.then(ClientCommands.literal("session").executes(c -> list(Period.SESSION)))
				.then(ClientCommands.literal("reset").executes(c -> {
					tallies.resetSession();
					Message.lazarus().success("Boss drop session reset. All-time totals are kept.").send();
					return 1;
				})));
	}

	private void line(String line) {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		Matcher m;
		if (MY_EYE.matcher(line).find()) {
			myEye = true;
			return;
		}
		if ((m = DragonTimer.EYE.matcher(line)).find() && mc.player != null && m.group(1).equals(mc.player.getName().getString())) {
			myEye = true;
			return;
		}
		if ((m = DRAGON_DEAD.matcher(line)).find()) {
			lastDragon = m.group(1) == null ? "Dragon" : m.group(1) + " Dragon";
			credited = false;
			if (myEye) {
				kill(lastDragon);
				credited = true;
				creditedAt = now;
			}
			myEye = false;
			return;
		}
		if (line.equals(SUMMARY)) {
			// The server only sends a summary to players it paid, so the summary itself proves the kill.
			summaryUntil = now + SUMMARY_MS;
			if (!credited || now - creditedAt > NEW_DRAGON_MS) kill(lastDragon);
			credited = true;
			creditedAt = now;
			return;
		}
		if (now < summaryUntil && (m = SUMMARY_LINE.matcher(line)).matches()) drop(lastDragon, Drops.item(m.group(1), Drops.Source.SUMMARY));
	}

	/**
	 * A drop from the loot feed. A staff roll that names the Protector or Warden settles it; one
	 * naming anything else (a zealot, a slayer boss) rules it out. Without a roll, a drop that
	 * isn't the nest's and lands around a boss's summary box is that boss's.
	 */
	private void loot(LootFeed.Loot l) {
		Drops.Drop d = l.drop();
		if (l.mob() != null) {
			if (l.mob().equals(PROTECTOR) || l.mob().equals(WARDEN)) drop(l.mob(), d);
			return;
		}
		if (NEST.contains(d.item().toLowerCase(Locale.ROOT))) return;
		if (openBoss != null && l.at() < openUntil) drop(openBoss, d);
		else recent.addLast(new Seen(l.at(), d));
	}

	/** A Protector or Warden summary: yours if you placed high enough, along with the drops just before it. */
	private void boss(String boss, int place) {
		if (place > PAID_PLACES) {
			recent.clear();
			return;
		}
		kill(boss);
		for (Seen s : recent) drop(boss, s.drop);
		recent.clear();
		openBoss = boss;
		openUntil = System.currentTimeMillis() + AFTER_MS;
	}

	private void kill(String boss) {
		if (!enabled()) return;
		tallies.both(boss, t -> t.add("kills", 1));
		if (!golem(boss)) latest = boss;
	}

	/** Counts a drop here when switched on, and always passes a golem's on to Golem Loot. */
	private void drop(String boss, Drops.Drop d) {
		if (d == null) return;
		if (enabled()) tallies.both(boss, t -> t.drop(d.item(), d.count()));
		if (golem(boss)) dev.lauchy.lazarus.Lazarus.LOG.info("Golem drop: {}x {} from the {}", d.count(), d.item(), boss);
		if (!golem(boss)) latest = boss;
		else for (GolemListener g : LISTENERS) g.drop(boss.equals(WARDEN), d);
	}

	private static boolean golem(String boss) {
		return boss.equals(PROTECTOR) || boss.equals(WARDEN);
	}

	private List<String> rows(String boss, Period p, int limit) {
		Tally t = tallies.shown(boss, p);
		List<String> out = new ArrayList<>();
		out.add("§5§l" + boss + " §7" + p.label());
		out.add("§7Kills §f" + t.count("kills"));
		out.addAll(LootRows.drops(t.drops(), rates.on() ? t.count("kills") : 0, "kill", limit, PriceDatabase::worth));
		return out;
	}

	private int list(Period p) {
		List<String> bosses = tallies.keys(p);
		if (bosses.isEmpty()) {
			Message.lazarus().info(p == Period.SESSION ? "No boss kills this session." : "No boss kills recorded yet.").send();
			return 1;
		}
		Message m = Message.lazarus().heading("Boss drops").muted(" · " + p.label());
		for (String boss : bosses) {
			List<String> rows = rows(boss, p, Integer.MAX_VALUE);
			m.line().line().heading(rows.getFirst()).rows(rows.subList(1, rows.size()));
		}
		m.send();
		return 1;
	}
}
