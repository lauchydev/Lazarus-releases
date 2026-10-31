package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
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
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Times the Goop Bait you drink: its "The bait sinks in." line says how long it lasts, a readout
 * counts that down, and a toast, sound and chat line say when it wears off. Drinking another
 * starts the count again from its own line.
 */
public final class GoopBait extends Feature {
	/** "The bait sinks in. Rift Obsidian Defenders and Watchers turn into Goop 50% more often for 10 minutes." */
	private static final Pattern BAIT = Pattern.compile("^The bait sinks in\\..*\\bfor (\\d+) (minute|second)s?\\b");

	private final BoolSetting readout = add(new BoolSetting("readout", "On-screen readout", "Time left on your bait, with a bar running down.", true));
	private final BoolSetting chat = add(new BoolSetting("chat", "Chat message", "Say in chat when the bait wears off, as well as the toast.", true));
	private final BoolSetting sound = add(new BoolSetting("sound", "Sound", "Play a sound when the bait wears off.", true));

	/** When your bait was drunk and when it wears off, or 0 when none is working. */
	private long startedAt, endsAt;

	public GoopBait() {
		super("goopBait", "Goop Bait Timer", "Counts down your Goop Bait and tells you when it wears off.", Category.END, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (!enabled() || Chat.fromPlayer(l.text())) return;
			long ms = lasts(l.text());
			if (ms <= 0) return;
			startedAt = System.currentTimeMillis();
			endsAt = startedAt + ms;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (endsAt == 0 || System.currentTimeMillis() < endsAt) return;
			endsAt = 0;
			if (enabled()) wornOff(mc);
		});
		Hud.register(new HudElement(this, "goopBait", "Goop Bait", 0.5f, 0.3f) {
			@Override
			protected boolean visible() {
				return readout.on() && endsAt != 0;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				long now = System.currentTimeMillis();
				long left = sample ? 372_000 : Math.max(0, endsAt - now);
				float share = sample ? 0.6f : left / (float) Math.max(1, endsAt - startedAt);
				int[] size = Draw.panel(g, font, List.of("§aGoop Bait §8· §f" + clock(left)));
				if (g != null) Draw.rect(g, 1, size[1] - 1, Math.round((size[0] - 2) * share), 1, Draw.alpha(Theme.GOOD, 0.6f));
				return size;
			}
		});
	}

	/** The toast, sound and chat line for a bait running out. */
	private void wornOff(Minecraft mc) {
		Toast.show("§aGoop Bait §7has worn off", Theme.GOOD);
		if (sound.on() && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
		if (chat.on()) Message.send(Message.lazarus().info("Your Goop Bait has worn off.").build());
	}

	/** How long the bait in this chat line lasts, in milliseconds, or 0 when the line isn't the bait's. */
	static long lasts(String line) {
		Matcher m = BAIT.matcher(line);
		if (!m.find()) return 0;
		long n = Long.parseLong(m.group(1));
		return m.group(2).equals("minute") ? n * 60_000 : n * 1_000;
	}

	/** Time left as m:ss, rounded up so the readout shows 0:00 only as it ends. */
	static String clock(long ms) {
		long s = (ms + 999) / 1000;
		return s / 60 + ":" + String.format("%02d", s % 60);
	}
}
