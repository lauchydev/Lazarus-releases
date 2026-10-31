package dev.lauchy.lazarus.feature.slayer;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Drops;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
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
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voidgloom and Revenant slayer bosses: kills by tier, failed quests, every drop with how
 * often it comes, and how many bosses since each rare drop. Only your own quest prints
 * "SLAYER BOSS SLAIN", so every kill counted is yours. Announced drops are the boss's when
 * a staff roll names it, or they land within a moment of the kill; the rewards the server
 * puts straight into your inventory with no chat line (Null Ovoid, Revenant Viscera) are
 * counted from what you carry. {@code /voidgloom} and {@code /revenant} list it all.
 */
public final class SlayerTracker extends Feature {
	public enum Boss { VOIDGLOOM, REVENANT }

	/** The quest's boss as the sidebar, its tag or a staff roll names it; Riftborn is Voidgloom's T5, Atoned Horror Revenant's. */
	private static final Pattern NAME = Pattern.compile("(Voidgloom Seraph|Riftborn Seraph|Revenant Horror|Atoned Horror)(?:\\s+(V|IV|III|II|I)\\b)?");
	private static final Pattern TARGET = Pattern.compile("Slay [\\d,]+ Combat XP worth of (Zombies|Endermen)");
	private static final String SLAIN = "SLAYER BOSS SLAIN", FAILED = "SLAYER QUEST FAILED", STARTED = "SLAYER QUEST STARTED";
	/** Drops a little before the SLAIN line (same tick, other order) and a few seconds after count. */
	public static final long BEFORE_MS = 1_500, AFTER_MS = 5_000;
	/** How often what you carry is snapshotted while a quest is on; the reward window only needs a second's grain. */
	private static final long SNAP_MS = 500;
	private static final List<String> TIERS = List.of("V", "IV", "III", "II", "I");

	private record Pending(Drops.Drop drop, long at) {
	}

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every boss so far.", Period.SESSION));
	private final TextSetting voidRares = add(new TextSetting("voidRares", "Voidgloom rare drops",
			"Comma-separated drops to count bosses since, with a toast when one lands.",
			"Judgement Core, Void Upgrade Stone, High Class Archfiend Dice, Etherwarp Conduit, Etherwarp Merger, Transmission Tuner", 300));
	private final TextSetting revRares = add(new TextSetting("revRares", "Revenant rare drops",
			"Comma-separated drops to count bosses since, with a toast when one lands.",
			"Scythe Blade, Shard of the Shredded, Beheaded Horror, Revenant Catalyst, Undead Catalyst", 300));
	private final TextSetting silent = add(new TextSetting("silent", "Silent rewards",
			"Comma-separated rewards the server gives with no chat line, counted from your inventory after each kill.",
			"Null Ovoid, Null Atom, Revenant Viscera, Foul Flesh", 300));

	private final BoolSetting since = add(new BoolSetting("sinceDrop", "Bosses since last drop", "Show how many bosses since each rare drop.", false));
	private final BoolSetting rates = add(new BoolSetting("rates", "Drop rates", "Show how often each drop comes, like 1 in 12 or 2.5 per kill.", true));
	private final Tallies tallies = new Tallies(Config.dir().resolve("slayers.json"));
	private final Deque<Pending> recent = new ArrayDeque<>();
	private final RewardWindow rewards = new RewardWindow();
	private Boss boss;
	private String tier = "";
	private long slainAt;
	private Boss slainBoss;
	private static long lastKillAt;
	private long snapAt;

	public SlayerTracker() {
		super("slayerTracker", "Slayer Tracker", "Voidgloom and Revenant kills, drops and bosses since each rare drop; /voidgloom and /revenant list them.", Category.SLAYERS, true);
	}

	/** When your last slayer boss died, in ms; the zealot tracker leaves drops from that moment to the boss. */
	public static long lastKillAt() {
		return lastKillAt;
	}

	/** True when a drop with no roll at {@code at} falls in the window this tracker books to your last slayer boss. */
	public static boolean claims(long at) {
		return lastKillAt > 0 && at - lastKillAt <= AFTER_MS && lastKillAt - at <= BEFORE_MS;
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		Chat.listen(l -> {
			if (enabled()) line(l.text());
		});
		LootFeed.listen(this::loot);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || mc.player == null) return;
			for (String s : Area.sidebar()) {
				Matcher m = NAME.matcher(s);
				if (m.find()) {
					named(m);
					break;
				}
			}
			long now = System.currentTimeMillis();
			// What you carry of each silent reward, every tick while a quest is on, so the kill has a baseline.
			if (boss != null && now - snapAt >= SNAP_MS) {
				snapAt = now;
				rewards.seen(now, carried(mc.player.getInventory()));
			}
			if (rewards.due(now) && slainBoss != null) {
				for (var e : rewards.close().entrySet()) dropped(slainBoss, new Drops.Drop(e.getKey(), e.getValue(), Drops.Source.INVENTORY));
			}
		});
		for (Boss b : Boss.values()) {
			Hud.register(new HudElement(this, b.name().toLowerCase(Locale.ROOT) + "Tracker", label(b) + " Tracker", 0.006f, 0.30f) {
				@Override
				protected boolean visible() {
					Tally t = shown(b);
					return boss == b && t.count("kills") + t.count("fails") > 0;
				}

				@Override
				public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
					if (sample) return Draw.panel(g, font, List.of("§5§l" + label(b) + " §7Session", "§7Kills §f42 §8· V 30 · IV 12",
							"§f3x §6" + rareNames(b).getFirst() + " §8(1 in 14)\t§a1.5M", "§7Value\t§a1.5M"));
					return Draw.panel(g, font, rows(b, period.get(), 5));
				}
			});
			String cmd = b.name().toLowerCase(Locale.ROOT);
			Commands.add(() -> ClientCommands.literal(cmd)
					.executes(c -> list(b, Period.ALL_TIME))
					.then(ClientCommands.literal("session").executes(c -> list(b, Period.SESSION)))
					.then(ClientCommands.literal("reset").executes(c -> {
						tallies.resetSession(key(b));
						Message.lazarus().success(label(b) + " session reset. All-time totals are kept.").send();
						return 1;
					})));
		}
	}

	private void line(String line) {
		if (Chat.fromPlayer(line)) return;
		long now = System.currentTimeMillis();
		if (line.contains(STARTED)) tier = "";
		Matcher target = TARGET.matcher(line);
		if (target.find()) boss = target.group(1).equals("Zombies") ? Boss.REVENANT : Boss.VOIDGLOOM;
		Matcher name = NAME.matcher(line);
		if (name.find()) named(name);
		if (line.contains(SLAIN) && boss != null) killed(boss, now);
		else if (line.contains(FAILED) && boss != null) tallies.both(key(boss), t -> t.add("fails", 1));
	}

	/**
	 * A drop from the loot feed: the boss's when a staff roll names a slayer boss, never when it
	 * names something else, and otherwise when it lands within a few seconds after the kill (or
	 * just before, kept until the SLAIN line says whose it was).
	 */
	private void loot(LootFeed.Loot l) {
		if (!enabled()) return;
		if (l.mob() != null) {
			Matcher m = NAME.matcher(l.mob());
			if (!m.find()) return;
			Boss b = m.group(1).endsWith("Seraph") ? Boss.VOIDGLOOM : Boss.REVENANT;
			dropped(b, l.drop());
			return;
		}
		if (slainBoss != null && l.at() - slainAt <= AFTER_MS) dropped(slainBoss, l.drop());
		else {
			recent.addLast(new Pending(l.drop(), l.at()));
			while (recent.size() > 20) recent.removeFirst();
		}
	}

	private void named(Matcher m) {
		String n = m.group(1);
		boss = n.endsWith("Seraph") ? Boss.VOIDGLOOM : Boss.REVENANT;
		if (n.startsWith("Riftborn") || n.startsWith("Atoned")) tier = "V";
		else if (m.group(2) != null) tier = m.group(2);
	}

	private void killed(Boss b, long now) {
		String t = tier;
		tallies.both(key(b), x -> {
			x.add("kills", 1);
			if (!t.isEmpty()) x.add("tiers." + t, 1);
			for (String rare : rareNames(b)) x.miss(rare);
		});
		slainAt = lastKillAt = now;
		slainBoss = b;
		rewards.open(now);
		// Drops that landed just before the SLAIN line belong to this kill.
		for (Pending p : recent) if (now - p.at <= BEFORE_MS) dropped(b, p.drop);
		recent.clear();
	}

	private void dropped(Boss b, Drops.Drop d) {
		if (d.source() != Drops.Source.INVENTORY && rewards.watching(System.currentTimeMillis())) rewards.announced(d.item(), d.count());
		String rare = LootRows.rareFor(d.item(), rareNames(b));
		tallies.both(key(b), t -> {
			t.drop(d.item(), d.count());
			if (rare == null) return;
			// The kill that dropped it is already counted, so the dry streak it ends is one less.
			int after = Math.max(0, t.hit(rare) - 1);
			if (t == tallies.total(key(b))) Toast.show(LootRows.colour(d.item()) + d.item() + " §7after §f" + after + " §7" + label(b) + (after == 1 ? "" : "s"), Theme.ACCENT);
		});
	}

	/** How many of each silent reward you carry, by the item's name. */
	private Map<String, Integer> carried(Inventory inv) {
		List<String> watched = LootRows.names(silent.get());
		Map<String, Integer> out = new LinkedHashMap<>();
		for (String w : watched) out.put(w, 0);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String name = Items.name(stack);
			for (String w : watched) if (w.equalsIgnoreCase(name)) out.merge(w, stack.getCount(), Integer::sum);
		}
		return out;
	}

	private Tally shown(Boss b) {
		return tallies.shown(key(b), period.get());
	}

	private static String key(Boss b) {
		return b.name().toLowerCase(Locale.ROOT);
	}

	private static String label(Boss b) {
		return b == Boss.VOIDGLOOM ? "Voidgloom" : "Revenant";
	}

	private List<String> rareNames(Boss b) {
		List<String> out = LootRows.names((b == Boss.VOIDGLOOM ? voidRares : revRares).get());
		return out.isEmpty() ? List.of("Rare drop") : out;
	}

	private List<String> rows(Boss b, Period p, int limit) {
		Tally t = tallies.shown(key(b), p);
		int kills = t.count("kills"), fails = t.count("fails");
		List<String> out = new ArrayList<>();
		out.add("§5§l" + label(b) + " §7" + p.label());
		StringBuilder tiers = new StringBuilder();
		Map<String, Integer> byTier = t.counts("tiers.");
		for (String tier : TIERS) if (byTier.containsKey(tier)) tiers.append(" · ").append(tier).append(' ').append(byTier.get(tier));
		out.add("§7Kills §f" + kills + (tiers.isEmpty() ? "" : " §8" + tiers) + (fails > 0 ? " §8· §c" + fails + " §7failed" : ""));
		if (since.on()) out.addAll(LootRows.since(t.since(), rareNames(b), "Bosses since last drop"));
		out.addAll(LootRows.drops(t.drops(), rates.on() ? kills : 0, "kill", limit, PriceDatabase::worth));
		if (!t.drops().isEmpty() && kills > 0) out.add("§7Per boss\t§a" + Text.compact(LootRows.total(t.drops(), PriceDatabase::worth) / (double) kills));
		return out;
	}

	private int list(Boss b, Period p) {
		Tally t = tallies.shown(key(b), p);
		if (t.count("kills") + t.count("fails") == 0) {
			Message.lazarus().info(p == Period.SESSION ? "No " + label(b) + " bosses this session." : "No " + label(b) + " bosses recorded yet.").send();
			return 1;
		}
		Message.panel(rows(b, p, Integer.MAX_VALUE)).send();
		return 1;
	}
}
