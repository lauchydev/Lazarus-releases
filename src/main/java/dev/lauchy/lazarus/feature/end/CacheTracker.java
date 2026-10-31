package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Drops;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Period;
import dev.lauchy.lazarus.core.Tallies;
import dev.lauchy.lazarus.core.Tally;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Crypt Caches you open and everything they give. The server answers your own cache with
 * "The Crypt Cache rolled like ...!", then a "Crypt Cache" header and one indented line
 * per reward; only rows inside that block count. Totals are kept across sessions.
 */
public final class CacheTracker extends Feature {
	private static final Pattern PLACED = Pattern.compile("^(?:☠\\s+)?You set down a Crypt Cache\\. It opens in \\d+ seconds?\\.$");
	private static final Pattern ROLLED = Pattern.compile("^(?:☠\\s+)?The Crypt Cache rolled like (.+?)!\\s+Loot number:.*$");
	private static final Pattern HEADER = Pattern.compile("^(?:☠\\s+)?Crypt Cache$");
	private static final long HEADER_MS = 5_000, LOOT_MS = 3_000, SHOW_MS = 300_000;

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every cache you have opened.", Period.SESSION));
	private final BoolSetting compact = add(new BoolSetting("compact", "Compact", "Show the five most common rewards; /lazarus caches lists all.", true));
	private final BoolSetting rates = add(new BoolSetting("rates", "Drop rates", "Show how often each drop comes, like 1 in 12 or 2.5 per kill.", true));

	private final Tallies tallies = new Tallies(Config.dir().resolve("caches.json"));
	private int headers;
	private long rolledAt = -1, blockUntil = -1, lastSeen;

	public CacheTracker() {
		super("cacheTracker", "Cache Tracker", "Crypt Caches opened and every reward, per session and all time.", Category.END, true);
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		Chat.listen(l -> {
			if (!enabled()) return;
			// Rewards are indented, so lines are read untrimmed; one message can hold several.
			for (String raw : l.component().getString().split("\\R")) line(raw.replaceAll("§.", ""));
		});
		Hud.register(new HudElement(this, "cacheTracker", "Cache Tracker", 0.006f, 0.55f) {
			@Override
			protected boolean visible() {
				return tallies.shown(period.get()).count("opened") > 0 && (Area.inCrypts() || System.currentTimeMillis() - lastSeen < SHOW_MS);
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				return Draw.panel(g, font, sample ? List.of("§5§lCrypt Caches §7Session", "§7Opened §f12",
						"§f3x §9Revenant Viscera", "§f1x §6Zombie Talisman", "§7Value §a2.1M") : rows(compact.on() ? 5 : Integer.MAX_VALUE));
			}
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("caches")
				.executes(c -> {
					Message.panel(rows(Integer.MAX_VALUE)).send();
					return 1;
				})
				.then(ClientCommands.literal("reset").executes(c -> {
					tallies.resetSession();
					Message.lazarus().success("Cache session reset. All-time totals are kept.").send();
					return 1;
				}))));
	}

	private void line(String raw) {
		String line = raw.trim();
		long now = System.currentTimeMillis();
		if (PLACED.matcher(line).matches()) {
			lastSeen = now;
			return;
		}
		if (ROLLED.matcher(line).matches()) {
			if (now - rolledAt > HEADER_MS) headers = 0;
			headers++;
			rolledAt = lastSeen = now;
			tallies.both(t -> t.add("opened", 1));
			return;
		}
		if (HEADER.matcher(line).matches()) {
			if (headers > 0 && now - rolledAt <= HEADER_MS) {
				headers--;
				blockUntil = now + LOOT_MS;
			} else {
				blockUntil = -1;
			}
			return;
		}
		if (now > blockUntil) return;
		if (!raw.startsWith("  ")) { // anything unindented ends the reward block
			blockUntil = -1;
			return;
		}
		if (line.isEmpty() || line.matches("(?i)\\(?nothing\\)?[.!]?|no loot[.!]?")) return;
		Drops.Drop d = Drops.item(line);
		if (d != null) tallies.both(t -> t.drop(d.item(), d.count()));
	}

	private List<String> rows(int limit) {
		Tally t = tallies.shown(period.get());
		List<String> out = new ArrayList<>();
		out.add("§5§lCrypt Caches §7" + period.get().label());
		out.add("§7Opened §f" + t.count("opened"));
		out.addAll(LootRows.drops(t.drops(), rates.on() ? t.count("opened") : 0, "cache", limit, PriceDatabase::worth));
		return out;
	}
}
