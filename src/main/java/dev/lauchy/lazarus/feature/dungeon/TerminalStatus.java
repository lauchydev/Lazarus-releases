package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.feature.dungeon.TerminalProgress.Kind;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Floor VII's terminals, levers and devices as they're done: each one named across the screen with a sound, how long
 * your own terminal took (from its window opening to the server saying it's activated), and a panel with how many of
 * each are done at the gate you're on. All from the server's own lines ({@link TerminalProgress}).
 */
public final class TerminalStatus extends Feature {
	private final BoolSetting titles = add(new BoolSetting("titles", "Titles", "Each terminal, lever and device done, across the screen.", true));
	private final BoolSetting sound = add(new BoolSetting("sound", "Sound", "A note when one is done.", true));
	private final BoolSetting times = add(new BoolSetting("times", "Your terminal times", "How long each terminal took you, in chat.", true));

	/** How many of each are done at this gate, as last said: {done, of}. */
	private final Map<Kind, int[]> progress = new EnumMap<>(Kind.class);
	private Object level;
	/** When the terminal window you have open (or last had) opened. */
	private static long openedAt;

	/** A terminal's window has opened without being shown (Terminal aura): its time is counted from now. */
	static void opened() {
		openedAt = Util.getMillis();
	}

	public TerminalStatus() {
		super("terminalStatus", "Terminal Status", "Titles, sounds and times for Floor VII's terminals, and how many are left.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> cs && Terminals.titled(dev.lauchy.lazarus.core.Text.plain(cs.getTitle()))) openedAt = Util.getMillis();
		});
		Chat.listen(l -> {
			if (!enabled() || !Area.inDungeon()) return;
			TerminalProgress.Done d = TerminalProgress.read(l.text());
			if (d == null) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != level) {
				level = mc.level;
				progress.clear();
			}
			// A count starting over is the next gate.
			if (d.done() == 1) progress.clear();
			progress.put(d.kind(), new int[]{d.done(), d.of()});
			if (titles.on()) {
				mc.gui.setTimes(0, 30, 10);
				mc.gui.setSubtitle(Component.literal("§7" + d.who()));
				mc.gui.setTitle(Component.literal("§a" + d.kind().label + " §f" + d.done() + "§7/§f" + d.of()));
			}
			if (sound.on() && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f);
			boolean mine = d.who().equals(mc.getUser().getName()) || d.who().equals(Players.me());
			if (mine && d.kind() == Kind.TERMINAL && times.on() && openedAt > 0) {
				say().info("Terminal done in ").highlight(String.format(Locale.ROOT, "%.2fs", (Util.getMillis() - openedAt) / 1000.0)).info(".").send();
				openedAt = 0;
			}
		});
		Hud.register(new HudElement(this, "terminalStatus", "Terminal Status", 0.40f, 0.20f) {
			@Override
			protected boolean visible() {
				return Area.inDungeon() && !progress.isEmpty() && Minecraft.getInstance().level == level;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = new ArrayList<>();
				if (sample) rows.addAll(List.of("§7Terminals\t§f3§7/§f7", "§7Levers\t§f1§7/§f2", "§7Devices\t§a1§7/§a1"));
				else for (var e : progress.entrySet()) {
					String c = e.getValue()[0] >= e.getValue()[1] ? "§a" : "§f";
					rows.add("§7" + e.getKey().label + "s\t" + c + e.getValue()[0] + "§7/" + c + e.getValue()[1]);
				}
				return Draw.panel(g, font, rows);
			}
		});
	}
}
