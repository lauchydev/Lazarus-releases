package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Staff chat mode that follows you between Dragsim's servers. Each server forgets which channel you were
 * in when you move to another, so when you were in staff chat, the switch is sent again soon after you
 * arrive. Which channel you're in comes from the server's "You are now in the STAFF channel" line, so it
 * doesn't matter how you got there or left. Arriving is any new world: another server, another Den, or
 * logging back in. If the server turns out to have kept you in staff chat and the switch took you out,
 * it's sent once more.
 */
public final class StaffChatKeep extends Feature {
	/** How long after sending the switch the server's answer still counts as answering it. */
	private static final int ANSWER_TICKS = 5 * 20;

	private final TextSetting toggle = add(new TextSetting("toggle", "Toggle command", "The command that switches you into staff chat, sent with nothing after it.", "/sc", 20));
	private final TextSetting channelLine = add(new TextSetting("channelLine", "Server says channel", "Pattern of the server's line when your chat channel changes; its first group is the channel.", "(?i)^You are now (?:in|chatting in|talking in) the (\\S+) channel", 120));
	private final TextSetting staffChannel = add(new TextSetting("staffChannel", "Staff channel", "The channel's name in that line when you're in staff chat.", "STAFF", 20));
	private final IntSetting delay = add(new IntSetting("delay", "Wait after arriving", "How long after reaching a server the switch is sent again.", 2, 1, 10, "s"));
	private final BoolSetting rejoin = add(new BoolSetting("rejoin", "After reconnecting too", "Also switch back after logging back in during the same game session.", true));

	/** Whether you're in staff chat mode, as far as the server's lines (or, before any, your toggles) say. */
	private boolean in;
	/** The world you're in; a new one means a new server, Den or connection. */
	private ClientLevel level;
	/** The server last seen on the sidebar, a second way to notice a move that keeps the world. */
	private String server;
	/** Ticks until the switch is sent again; 0 when nothing is due. */
	private int sendIn;
	/** Ticks the server's answer to the switch just sent is still awaited; 0 when nothing was sent. */
	private int answerIn;
	/** Whether the one resend for a switch that took you out has been used. */
	private boolean resent;
	/** Set by a fresh connection, until its first world. */
	private boolean joined;
	/** True while this sends the toggle itself, so its own command doesn't read as yours. */
	private boolean sending;

	public StaffChatKeep() {
		super("staffChatKeep", "Stay in Staff Chat", "When you were in staff chat and change server, you're put back in it on arrival.", Category.STAFF, true);
	}

	@Override
	public void init() {
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			// A guess until the server answers: the answer below always wins.
			if (enabled() && !sending && isToggle(command, toggle.get())) in = !in;
			return true;
		});
		Chat.listen(line -> {
			if (!enabled()) return;
			Boolean staff = inStaff(line.text(), channelLine.get(), staffChannel.get());
			if (staff == null) return;
			if (!staff && answerIn > 0 && !resent) {
				// The server had kept you in staff chat and the switch toggled you out: switch back.
				resent = true;
				answerIn = 0;
				send(Minecraft.getInstance());
				return;
			}
			in = staff;
			if (staff) answerIn = 0;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> joined = true);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			level = null;
			server = null;
			sendIn = 0;
			answerIn = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.level == null) return;
			if (answerIn > 0) answerIn--;
			boolean arrived = false;
			if (mc.level != level) {
				arrived = level != null || !joined || rejoin.on();
				level = mc.level;
				joined = false;
			}
			String now = Area.server();
			if (now != null && !now.equals(server)) {
				arrived |= server != null;
				server = now;
			}
			// Every arrival restarts the wait, so the switch goes once the move has settled.
			if (arrived && enabled() && in) sendIn = delay.get() * 20;
			if (sendIn > 0 && --sendIn == 0 && enabled() && in) {
				resent = false;
				send(mc);
			}
		});
	}

	private void send(Minecraft mc) {
		if (mc.player == null) return;
		String command = toggle.get().trim();
		if (command.startsWith("/")) command = command.substring(1);
		if (command.isEmpty()) return;
		sending = true;
		try {
			mc.player.connection.sendCommand(command);
			answerIn = ANSWER_TICKS;
		} finally {
			sending = false;
		}
	}

	/** Whether a command you sent (without its slash) is the bare toggle: "sc", not "sc hello". */
	static boolean isToggle(String command, String toggle) {
		String t = toggle.trim().toLowerCase(Locale.ROOT);
		if (t.startsWith("/")) t = t.substring(1);
		return !t.isEmpty() && command.trim().toLowerCase(Locale.ROOT).equals(t);
	}

	/**
	 * What a server line says about your chat channel: TRUE when it's now the staff channel, FALSE when it's
	 * now another, null when it isn't about that. Something a player said never counts, whatever words it holds.
	 */
	static Boolean inStaff(String line, String pattern, String staff) {
		if (pattern.isBlank() || Chat.speaker(line) != null) return null;
		try {
			Matcher m = Pattern.compile(pattern).matcher(line.trim());
			if (!m.find()) return null;
			// A pattern with no group names no channel, so it can't say which one you're in.
			if (m.groupCount() < 1 || m.group(1) == null) return null;
			return Objects.equals(m.group(1).toLowerCase(Locale.ROOT), staff.trim().toLowerCase(Locale.ROOT));
		} catch (PatternSyntaxException e) {
			return null;
		}
	}
}
