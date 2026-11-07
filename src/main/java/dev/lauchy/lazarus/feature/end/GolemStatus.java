package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lobby's Endstone Protector: resting, rising (tier and kills left to spawn), alive,
 * or turned Warden. Read from the sidebar or tab list status line, with chat
 * announcements filling in between reads.
 */
public final class GolemStatus extends Feature {
	public static GolemStatus INSTANCE;
	public enum State { UNKNOWN, RESTING, RISING, ALIVE, WARDEN }

	private static final Pattern RISING = Pattern.compile("(?i)^Protector:\\s*Rising\\s*\\(Tier\\s*(\\d+)\\s*/\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\)\\s*$");
	private static final Pattern RESTING = Pattern.compile("(?i)^Protector:\\s*Resting(?:\\s*\\((\\d+)\\))?\\s*$");
	private static final Pattern UP = Pattern.compile("(?i)^(Protector|Warden):\\s*(?:Spawned|Alive)\\s*$");
	private static final Pattern TIER_CHAT = Pattern.compile("Endstone Protector (?:is rising from|has emerged from) the ground! \\(Tier (\\d)/(\\d)\\)");
	private static final Pattern WARDEN_ACTIVE = Pattern.compile("The Warden (?:unleashes|summons)|The Warden's aura");
	private static final int KILL_GOAL = 200;

	private final BoolSetting alert = add(new BoolSetting("alert", "Spawn alert", "Toast and sound when the Protector spawns.", true));

	private State state = State.UNKNOWN;
	private int tier, tierMax = 5, left = -1, restSeconds = -1;
	private int ticks;
	private Object level;
	/**
	 * Whether this cycle's spawn was already announced. The chat line, the sidebar flicking
	 * through Rising and Alive, and a stale "Alive" read after the kill each looked like a
	 * new spawn. It used to re-arm when a rise was read, but the sidebar still shows the last
	 * "Rising" for a moment after the spawn line, which re-armed it and pinged again on
	 * "Alive"; now it re-arms only once the Protector is down or resting, or in a new world.
	 */
	private boolean alerted;
	/** When the last alert fired, so a flicker of the status can never ping twice in a row. */
	private long alertedAt;
	/** The shortest gap between two spawn alerts; a Protector never respawns this fast. */
	private static final long ALERT_GAP_MS = 60_000;

	public GolemStatus() {
		super("golemStatus", "Golem Status", "Protector stage, kills left to spawn, and Warden transformation.", Category.END, true);
		INSTANCE = this;
	}

	public State state() {
		return state;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) {
				level = mc.level;
				state = State.UNKNOWN;
				alerted = false;
			}
			if (!enabled() || mc.level == null || ++ticks % 10 != 0) return;
			for (String line : Area.sidebar()) if (status(line)) return;
			if (mc.getConnection() == null) return;
			List<String> tab = new ArrayList<>();
			for (PlayerInfo p : mc.getConnection().getListedOnlinePlayers()) {
				if (p.getTabListDisplayName() != null) tab.add(Text.plain(p.getTabListDisplayName()));
			}
			for (int i = 0; i < tab.size(); i++) {
				// The status can wrap over two or three tab cells.
				if (status(tab.get(i))) return;
				if (i + 1 < tab.size() && status(tab.get(i) + " " + tab.get(i + 1))) return;
				if (i + 2 < tab.size() && status(tab.get(i) + " " + tab.get(i + 1) + " " + tab.get(i + 2))) return;
			}
		});
		Chat.listen(l -> {
			String line = l.text();
			if (Chat.fromPlayer(line)) return;
			Matcher m = TIER_CHAT.matcher(line);
			if (m.find()) {
				tier = Integer.parseInt(m.group(1));
				tierMax = Integer.parseInt(m.group(2));
				state = State.RISING;
			} else if (line.contains("Endstone Protector has spawned")) {
				spawned();
			} else if (line.contains("The Endstone Protector has been defeated") || line.contains("The Warden has been defeated")
					|| line.contains("ENDSTONE PROTECTOR DOWN!") || line.contains("THE WARDEN DOWN!")) {
				state = State.RESTING;
				tier = 0;
				alerted = false;
			} else if (state == State.ALIVE && WARDEN_ACTIVE.matcher(line).find()) {
				state = State.WARDEN;
			}
		});
		Hud.register(new HudElement(this, "golemStatus", "Golem Status", 0.40f, 0.12f) {
			@Override
			protected boolean visible() {
				return Area.inEnd() && state != State.UNKNOWN;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				return Draw.panel(g, font, List.of(sample ? "§5Golem §eRising 4/5 §7· §f169/200" : line()));
			}
		});
	}

	private void spawned() {
		long now = System.currentTimeMillis();
		if (!alerted && now - alertedAt > ALERT_GAP_MS && alert.on() && enabled()) {
			alerted = true;
			alertedAt = now;
			Toast.show("§5Endstone Protector has spawned", Theme.ACCENT);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
		}
		state = State.ALIVE;
		tier = tierMax;
		left = 0;
	}

	private boolean status(String line) {
		Matcher m = RISING.matcher(line);
		if (m.matches()) {
			tier = Integer.parseInt(m.group(1));
			tierMax = Integer.parseInt(m.group(2));
			left = m.group(3) == null ? -1 : Integer.parseInt(m.group(3));
			state = State.RISING;
			return true;
		}
		if ((m = RESTING.matcher(line)).matches()) {
			state = State.RESTING;
			restSeconds = m.group(1) == null ? -1 : Integer.parseInt(m.group(1));
			alerted = false;
			return true;
		}
		if ((m = UP.matcher(line)).matches()) {
			if (m.group(1).equalsIgnoreCase("Warden")) state = State.WARDEN;
			else if (state != State.WARDEN) spawned();
			return true;
		}
		return false;
	}

	private String line() {
		return switch (state) {
			case RESTING -> "§5Golem §7Resting" + (restSeconds >= 0 ? " §f" + Text.clock(restSeconds) : "");
			case RISING -> "§5Golem §eRising " + tier + "/" + tierMax + (left >= 0 ? " §7· §f" + (KILL_GOAL - left) + "/" + KILL_GOAL : "");
			case ALIVE -> "§5Golem §aAlive";
			case WARDEN -> "§3Warden §aAlive";
			default -> "§5Golem §7?";
		};
	}
}
