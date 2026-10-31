package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dragon cycle from the server's announcements: eyes placed, the dragon that spawned
 * and how long it has been up, and the countdown to the next egg.
 */
public final class DragonTimer extends Feature {
	public static DragonTimer INSTANCE;

	static final Pattern EYE = Pattern.compile("» (.+?) placed an? (\\w+ Eye)! \\((\\d+)/(\\d+)\\)");
	static final Pattern SPAWNED = Pattern.compile("The (\\w+ Dragon) has spawned!");
	static final Pattern DESPAWNED = Pattern.compile("The (\\w+ Dragon) has de-spawned\\.");
	static final Pattern EGG_WAIT = Pattern.compile("(?:The )?Dragon Egg is respawning in (\\d+) (seconds?|minutes?)\\.", Pattern.CASE_INSENSITIVE);
	private static final long DEFAULT_EGG_MS = 10_000;

	private final BoolSetting placers = add(new BoolSetting("placers", "Show placers", "List who placed this cycle's eyes.", false));
	private final BoolSetting history = add(new BoolSetting("history", "Last dragon", "Show the last dragon type and fight length.", true));

	private int eyes, eyesNeeded = 8, myEyes;
	private final Set<Integer> countedEyes = new HashSet<>();
	private final Map<String, Integer> placed = new LinkedHashMap<>();
	private String dragon, lastDragon;
	private long dragonSince, lastFightMs, eggAt;
	private boolean eggReady;
	private Object level;
	private Runnable onMyEye = () -> {
	};

	public DragonTimer() {
		super("dragonTimer", "Dragon Timer", "Eye count, dragon fight time and egg respawn countdown.", Category.END, true);
		INSTANCE = this;
	}

	/** Lets Protect Placed Eyes re-arm when the server confirms one of your placements. */
	public void onMyEye(Runnable r) {
		onMyEye = r;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) { // a lobby switch swaps the world without a new join
				level = mc.level;
				reset();
			}
		});
		Chat.listen(l -> {
			String line = l.text();
			if (line.startsWith("Switched to ") || line.startsWith("Sending you to ")) reset();
			else if (!Chat.fromPlayer(line) || line.startsWith("»")) read(line);
		});
		Hud.register(new HudElement(this, "dragonTimer", "Dragon Timer", 0.40f, 0.04f) {
			@Override
			protected boolean visible() {
				return Area.inEnd() && (eyes > 0 || dragon != null || eggAt > 0 || eggReady);
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				return Draw.panel(g, font, lines(sample));
			}
		});
	}

	void read(String line) {
		Matcher m;
		if ((m = EYE.matcher(line)).find()) {
			int index = Integer.parseInt(m.group(3));
			eyesNeeded = Integer.parseInt(m.group(4));
			eyes = Math.max(eyes, index);
			// Your own placement is announced twice ("You placed..." and with your name).
			if (!countedEyes.add(index)) return;
			String who = m.group(1).replaceFirst("^\\[[^]]*]\\s*", "").trim();
			Minecraft mc = Minecraft.getInstance();
			String me = mc.player == null ? "" : mc.player.getName().getString();
			if (who.equalsIgnoreCase("You")) who = me;
			placed.merge(who, 1, Integer::sum);
			if (who.equalsIgnoreCase(me)) {
				myEyes++;
				onMyEye.run();
			}
		} else if ((m = SPAWNED.matcher(line)).find()) {
			dragon = m.group(1);
			dragonSince = Util.getMillis();
			eggAt = 0;
			eggReady = false;
		} else if ((m = DESPAWNED.matcher(line)).find()) {
			lastDragon = m.group(1);
			lastFightMs = dragonSince > 0 ? Util.getMillis() - dragonSince : 0;
			dragon = null;
			eggAt = Util.getMillis() + DEFAULT_EGG_MS;
			newCycle();
		} else if ((m = EGG_WAIT.matcher(line)).find()) {
			long n = Long.parseLong(m.group(1));
			eggAt = Util.getMillis() + n * (m.group(2).toLowerCase().startsWith("minute") ? 60_000 : 1000);
			eggReady = false;
			newCycle();
		} else if (line.contains("The Egg has Spawned") || line.contains("Dragon Egg has respawned")) {
			eggAt = 0;
			eggReady = true;
			newCycle();
		} else if (line.contains("Sleeping Eyes have been awoken")) {
			eyes = eyesNeeded;
		}
	}

	private void newCycle() {
		eyes = 0;
		myEyes = 0;
		countedEyes.clear();
		placed.clear();
	}

	private void reset() {
		newCycle();
		dragon = null;
		eggAt = 0;
		eggReady = false;
	}

	private List<String> lines(boolean sample) {
		List<String> out = new ArrayList<>();
		if (sample) {
			out.add("§5Superior Dragon §f2:14");
			out.add("§7Eyes §f8/8 §7(yours §a2§7)");
			return out;
		}
		long now = Util.getMillis();
		if (dragon != null) out.add("§5" + dragon + " §f" + Text.clock((now - dragonSince) / 1000));
		else if (eggAt > now) out.add("§7Egg in §e" + Text.clock((eggAt - now + 999) / 1000));
		else if (eggAt > 0 || eggReady) out.add("§aEgg is up");
		if (dragon == null) out.add("§7Eyes §f" + eyes + "/" + eyesNeeded + (myEyes > 0 ? " §7(yours §a" + myEyes + "§7)" : ""));
		if (placers.on()) placed.forEach((who, n) -> out.add(" §8- §7" + who + " §f" + n));
		if (history.on() && lastDragon != null && dragon == null) out.add("§8Last: " + lastDragon + " " + Text.clock(lastFightMs / 1000));
		return out;
	}
}
