package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;
import net.minecraft.util.debugchart.LocalSampleLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * FPS, ping and TPS. Dragsim reports zero latency in the tab list, so ping is measured
 * with the same request the F3 chart sends. TPS comes from the time packets the server
 * sends every 20 of its ticks: their spacing in real time is the tick rate.
 */
public final class ServerStats extends Feature {
	public static ServerStats INSTANCE;

	private final BoolSetting fps = add(new BoolSetting("fps", "FPS", "Frames per second.", true));
	private final BoolSetting ping = add(new BoolSetting("ping", "Ping", "Round trip to the server.", true));
	private final BoolSetting tps = add(new BoolSetting("tps", "TPS", "Server ticks per second.", true));

	private final ArrayDeque<Long> timePackets = new ArrayDeque<>();
	private long lastPingMs;

	public ServerStats() {
		super("stats", "Ping / TPS", "FPS, ping and server TPS in one compact readout.", Category.GENERAL, false);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) {
				timePackets.clear();
				return;
			}
			if (!enabled() || !ping.on() || mc.getConnection() == null) return;
			long now = Util.getMillis();
			if (now - lastPingMs >= 1000) {
				lastPingMs = now;
				mc.getConnection().send(new ServerboundPingRequestPacket(now));
			}
		});
		Hud.register(new HudElement(this, "stats", "Ping / TPS", 0.01f, 0.01f) {
			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				return drawStats(g, font, sample);
			}
		});
	}

	/** Called from the time packet handler. */
	public void onTimePacket() {
		long now = Util.getMillis();
		timePackets.addLast(now);
		while (timePackets.size() > 11) timePackets.pollFirst();
	}

	double tps() {
		if (timePackets.size() < 3) return 20;
		long span = timePackets.peekLast() - timePackets.peekFirst();
		if (Util.getMillis() - timePackets.peekLast() > 3000) return 20.0 * 1000 / Math.max(1000, Util.getMillis() - timePackets.peekLast());
		double perPacket = span / 1000.0 / (timePackets.size() - 1);
		return Math.min(20, 20 / perPacket);
	}

	private int latency(Minecraft mc) {
		LocalSampleLogger log = mc.getDebugOverlay().getPingLogger();
		return log.size() > 0 ? (int) log.get(log.size() - 1) : -1;
	}

	private int[] drawStats(GuiGraphicsExtractor g, Font font, boolean sample) {
		Minecraft mc = Minecraft.getInstance();
		List<String> parts = new ArrayList<>();
		if (fps.on()) parts.add("§7FPS §f" + (sample ? 144 : mc.getFps()));
		if (ping.on()) {
			int ms = sample ? 42 : latency(mc);
			parts.add("§7Ping " + (ms > 250 ? "§c" : "§f") + (ms < 0 ? "-" : ms + "ms"));
		}
		if (tps.on()) {
			double t = sample ? 20 : tps();
			parts.add("§7TPS " + (t < 17 ? "§c" : t < 19.5 ? "§e" : "§a") + String.format("%.1f", t));
		}
		if (parts.isEmpty()) return null;
		String line = String.join("  ", parts);
		int w = font.width(line) + 10, h = 13;
		if (g != null) {
			Draw.rect(g, 0, 0, w, h, Theme.HUD_BG);
			Draw.text(g, font, line, 5, 3, Theme.TEXT);
		}
		return new int[]{w, h};
	}
}
