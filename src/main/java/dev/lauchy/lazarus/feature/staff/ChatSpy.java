package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat spy DMs on their own panel, like the Staff Chat Panel: the "[Spy DM] A → B: message"
 * lines the server shows staff stay on the HUD, wrapped to the panel's width, optionally leaving
 * the main chat. Opening the chat box brings back the latest even after they've faded, and the
 * wheel over the panel scrolls back through the last hundred.
 */
public final class ChatSpy extends Feature {
	/** "[Spy DM] oYuvin_ → shotgvnz: watchu say", as Dragsim sends it (seen in Lauchlan's log, 2026-10-30). */
	private static final Pattern SPY = Pattern.compile("^\\[Spy DM]\\s+(\\S+)\\s+→\\s+(\\S+?):\\s?(.*)$");
	/** How many spy lines are kept to scroll back through. */
	private static final int KEPT = 100;
	private static final String HUD_ID = "chatSpy";

	/** One spied DM: who sent it, to whom, what it said, and when it arrived. */
	public record Line(String from, String to, String message, long at) {
		/** The panel row: sender → receiver in white, the message after. */
		public String row() {
			return "§f" + from + " §7→ §f" + to + "§7: §f" + message;
		}
	}

	private final IntSetting lines = add(new IntSetting("lines", "Lines shown", "How many spied DMs stay on the panel.", 6, 1, 15, ""));
	private final IntSetting openLines = add(new IntSetting("openLines", "Lines with chat open", "How many spied DMs the panel shows while the chat box is open, faded or not.", 10, 1, 30, ""));
	private final IntSetting linger = add(new IntSetting("linger", "Shown for", "How long a DM stays on the panel.", 120, 10, 600, "s"));
	private final IntSetting width = add(new IntSetting("width", "Panel width", "How wide the panel is before a line wraps onto the next.", 240, 120, 600, "px"));
	private final BoolSetting hideChat = add(new BoolSetting("hideChat", "Hide from chat", "Keep spied DMs out of the main chat; the panel has them.", true));

	private final ArrayDeque<Line> recent = new ArrayDeque<>();
	/** How many lines back from the newest the panel is scrolled; 0 shows the latest as usual. */
	private int scroll;

	public ChatSpy() {
		super("chatSpy", "Chat Spy Panel", "Chat spy DMs on their own HUD panel, out of the main chat.", Category.STAFF, true);
	}

	@Override
	public void init() {
		Chat.hide(line -> {
			if (!enabled()) return false;
			Line l = parse(line.text(), Util.getMillis());
			if (l == null) return false;
			recent.addLast(l);
			while (recent.size() > KEPT) recent.pollFirst();
			return hideChat.on();
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) return;
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, dx, dy) -> {
				if (!enabled() || recent.isEmpty() || !Hud.over(HUD_ID, mx, my)) return true;
				scroll = Math.clamp(scroll + (int) Math.signum(dy), 0, Math.max(0, recent.size() - openLines.get()));
				return false; // the wheel moved the panel, not the chat
			});
			ScreenEvents.remove(screen).register(s -> scroll = 0);
		});
		Hud.register(new HudElement(this, HUD_ID, "Chat spy", 0.01f, 0.32f) {
			@Override
			protected boolean visible() {
				return !shown().isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = new ArrayList<>();
				rows.add(scroll > 0 ? "§5§lSpy §r§7↑ " + scroll : "§5§lSpy");
				if (sample) {
					rows.add(new Line("Steve", "Alex", "selling a hype?", 0).row());
					rows.add(new Line("Alex", "Steve", "how much", 0).row());
				} else {
					for (Line l : shown()) rows.add(l.row());
				}
				return Draw.panel(g, font, Draw.wrap(font, rows, width.get() - 10));
			}
		});
	}

	/** The spied DM a chat line is, or null. */
	public static Line parse(String text, long now) {
		Matcher m = SPY.matcher(text.trim());
		return m.matches() ? new Line(m.group(1), m.group(2), m.group(3), now) : null;
	}

	/** The lines on the panel: the newest that haven't faded, or, with the chat box open, a window of everything kept. */
	private List<Line> shown() {
		if (Minecraft.getInstance().screen instanceof ChatScreen) return StaffChat.window(new ArrayList<>(recent), scroll, openLines.get());
		List<Line> out = new ArrayList<>();
		long now = Util.getMillis();
		for (Line l : recent) if (now - l.at() < linger.get() * 1000L) out.add(l);
		while (out.size() > lines.get()) out.removeFirst();
		return out;
	}
}
