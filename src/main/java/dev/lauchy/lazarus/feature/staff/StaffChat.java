package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Ranks;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff chat on its own panel: the last few "Staff >" lines stay on the HUD where public chat can't
 * push them away, wrapped to the panel's width, optionally leaving the main chat, with a sound when
 * another staff member talks, and one key opens the chat box with the staff chat command already typed.
 * Like the main chat, opening the chat box brings back the latest staff lines even after they've faded,
 * and the wheel over the panel scrolls back through the last hundred (only the panel: the main chat stays
 * where it is); closing the chat box returns to the newest.
 */
public final class StaffChat extends Feature {
	public enum Sound { PLING, BELL, CHIME, NONE }

	/** A run of staff lines sounds once, not over itself. */
	private static final long SOUND_GAP_MS = 1000;
	/** How many staff lines are kept to scroll back through. */
	private static final int KEPT = 100;
	private static final String HUD_ID = "staffChat";

	/** One staff line. */
	public record Line(String rank, String name, String message, long at) {
		/** The panel row: the rank in its own colour (a staff rank's from {@link Ranks}, any other in Violet), then the name and message. */
		public String row() {
			if (rank.isEmpty()) return "§f" + name + "§7: §f" + message;
			String tag = Ranks.tag(rank);
			return (tag == null ? "§5" + rank : tag) + " §f" + name + "§7: §f" + message;
		}
	}

	private final IntSetting lines = add(new IntSetting("lines", "Lines shown", "How many staff lines stay on the panel.", 6, 1, 15, ""));
	private final IntSetting openLines = add(new IntSetting("openLines", "Lines with chat open", "How many staff lines the panel shows while the chat box is open, faded or not.", 10, 1, 30, ""));
	private final IntSetting linger = add(new IntSetting("linger", "Shown for", "How long a line stays on the panel.", 120, 10, 600, "s"));
	private final IntSetting width = add(new IntSetting("width", "Panel width", "How wide the panel is before a line wraps onto the next.", 240, 120, 600, "px"));
	private final ChoiceSetting<Sound> sound = add(new ChoiceSetting<>("sound", "Sound", "Played when another staff member talks in staff chat.", Sound.PLING));
	private final BoolSetting ownSound = add(new BoolSetting("ownSound", "Sound for your own", "Play it for your own staff chat lines too.", false));
	private final BoolSetting hideChat = add(new BoolSetting("hideChat", "Hide from chat", "Keep staff chat out of the main chat; the panel has it.", false));
	private final KeySetting key = add(new KeySetting("key", "Talk key", "Opens the chat box with the staff chat command typed for you."));
	private final TextSetting command = add(new TextSetting("command", "Staff chat command", "What the server's staff chat command is, typed before your message.", "/sc ", 20));

	private final ArrayDeque<Line> recent = new ArrayDeque<>();
	private boolean held;
	private long lastSound;
	/** How many lines back from the newest the panel is scrolled; 0 shows the latest as usual. */
	private int scroll;

	public StaffChat() {
		super("staffChat", "Staff Chat Panel", "Staff chat on its own HUD panel, with a key that opens the chat box ready to talk in it.", Category.STAFF, true);
	}

	@Override
	public void init() {
		Chat.hide(line -> {
			if (!enabled()) return false;
			Line l = parse(line.text(), Util.getMillis());
			if (l == null) return false;
			recent.addLast(l);
			while (recent.size() > KEPT) recent.pollFirst();
			if (ownSound.on() || !l.name().equalsIgnoreCase(Players.me())) chime();
			return hideChat.on();
		});
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (enabled() && mc.screen == null) Keys.suppressVanilla(key);
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean down = enabled() && mc.screen == null && mc.player != null && Keys.down(key);
			if (down && !held) mc.setScreen(new ChatScreen(command.get(), false));
			held = down;
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
		Hud.register(new HudElement(this, HUD_ID, "Staff chat", 0.01f, 0.55f) {
			@Override
			protected boolean visible() {
				return !shown().isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = new ArrayList<>();
				rows.add(scroll > 0 ? "§5§lStaff §r§7↑ " + scroll : "§5§lStaff");
				if (sample) {
					rows.add(new Line("ADMIN", "Lauchy", "anyone free for a captcha?", 0).row());
					rows.add(new Line("MOD", "Steve", "on it", 0).row());
				} else {
					for (Line l : shown()) rows.add(l.row());
				}
				return Draw.panel(g, font, Draw.wrap(font, rows, width.get() - 10));
			}
		});
	}

	/** The staff line a chat line is, or null. */
	private void chime() {
		Minecraft mc = Minecraft.getInstance();
		SoundEvent s = switch (sound.get()) {
			case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
			case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
			case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case NONE -> null;
		};
		long now = Util.getMillis();
		if (s == null || mc.player == null || now - lastSound < SOUND_GAP_MS) return;
		lastSound = now;
		mc.player.playSound(s, 0.7f, 1.4f);
	}

	public static Line parse(String text, long now) {
		Chat.Speaker s = Chat.speaker(text);
		if (s == null || !s.staffChat()) return null;
		return new Line(s.rank(), s.name(), s.message(), now);
	}

	/** The lines on the panel: the newest that haven't faded, or, with the chat box open, a window of everything kept. */
	private List<Line> shown() {
		if (Minecraft.getInstance().screen instanceof ChatScreen) return window(new ArrayList<>(recent), scroll, openLines.get());
		List<Line> out = new ArrayList<>();
		long now = Util.getMillis();
		for (Line l : recent) if (now - l.at() < linger.get() * 1000L) out.add(l);
		while (out.size() > lines.get()) out.removeFirst();
		return out;
	}

	/** Up to {@code count} lines ending {@code scroll} lines back from the newest, oldest first. */
	static <T> List<T> window(List<T> all, int scroll, int count) {
		int end = Math.max(0, all.size() - scroll), start = Math.max(0, end - count);
		return new ArrayList<>(all.subList(start, end));
	}
}
