package dev.lauchy.lazarus.feature.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Shared;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The blood room's opening: from "The BLOOD DOOR has been opened!" the Watcher talks for
 * a few seconds before the first mob arrives. This counts that down and then names the
 * mob. The wait is learned: each run's real delay is kept (config/lazarus/blood.json) and
 * shared through the Lazarus server, and the countdown uses the median of everyone's last
 * hundred (your own while the server can't be reached), starting from 10 seconds.
 */
public final class BloodRoom extends Feature {
	private static final String DOOR = "The BLOOD DOOR has been opened!";
	/** "[BOSS] The Watcher: Let's see how you handle Jolly Pink Giant." The line two seconds before names nothing. */
	private static final Pattern SPAWN = Pattern.compile("^\\[BOSS] The Watcher: Let's see how you handle (.+?)\\.?$");
	private static final Path FILE = Config.dir().resolve("blood.json");
	private static final long DEFAULT_MS = 10_000, SHOW_MOB_MS = 5_000;
	private static final int KEEP = 25;

	private final List<Long> delays = new ArrayList<>();
	/** Every Lazarus user's last waits, from the server. */
	private List<Long> everyone = List.of();
	private long openedAt, spawnedAt;
	private String mob;

	public BloodRoom() {
		super("bloodRoom", "Blood Room", "Counts down to the first blood mob and names it.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		load();
		Shared.follow("blood", Duration.ofMinutes(30), got -> {
			Shared.Entry e = got.get("delay");
			if (e == null || !(e.value().get("ms") instanceof JsonArray ms)) return;
			List<Long> all = new ArrayList<>();
			for (JsonElement m : ms) all.add(m.getAsLong());
			everyone = all;
		});
		Chat.listen(l -> {
			String line = l.text();
			long now = System.currentTimeMillis();
			if (line.equals(DOOR)) {
				openedAt = now;
				mob = null;
				return;
			}
			Matcher m = SPAWN.matcher(line);
			if (m.matches() && !m.group(1).equalsIgnoreCase("this")) {
				if (openedAt != 0) {
					long took = now - openedAt;
					if (took > 1_000 && took < 60_000) learn(took);
				}
				openedAt = 0;
				mob = m.group(1);
				spawnedAt = now;
			}
		});
		Hud.register(new HudElement(this, "bloodRoom", "Blood Room", 0.5f, 0.35f) {
			@Override
			protected boolean visible() {
				long now = System.currentTimeMillis();
				if (openedAt != 0 && now - openedAt > 60_000) openedAt = 0;
				return openedAt != 0 || mob != null && now - spawnedAt < SHOW_MOB_MS;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return Draw.panel(g, font, List.of("§cBlood mob in §f6.4s"));
				if (openedAt != 0) {
					double left = Math.max(0, (expected() - (System.currentTimeMillis() - openedAt)) / 1000.0);
					return Draw.panel(g, font, List.of("§cBlood mob in §f" + String.format(Locale.ROOT, "%.1fs", left)));
				}
				return Draw.panel(g, font, List.of("§cSpawned: §f" + mob));
			}
		});
	}

	private long expected() {
		List<Long> sorted = new ArrayList<>(everyone.isEmpty() ? delays : everyone);
		if (sorted.isEmpty()) return DEFAULT_MS;
		sorted.sort(null);
		return sorted.get(sorted.size() / 2);
	}

	private void learn(long ms) {
		delays.add(ms);
		while (delays.size() > KEEP) delays.removeFirst();
		JsonArray out = new JsonArray();
		delays.forEach(out::add);
		Config.writeJson(FILE, out);
		JsonObject v = new JsonObject();
		JsonArray one = new JsonArray();
		one.add(ms);
		v.add("ms", one);
		Shared.send("blood", "delay", v, (a, b) -> {
			a.getAsJsonArray("ms").addAll(b.getAsJsonArray("ms"));
			return a;
		});
	}

	private void load() {
		JsonElement json = Config.readJson(FILE);
		if (json != null && json.isJsonArray()) for (JsonElement e : json.getAsJsonArray()) delays.add(e.getAsLong());
	}
}
