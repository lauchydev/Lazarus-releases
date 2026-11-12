package dev.lauchy.lazarus.feature.chat;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A sound when a private message arrives: the server's "From [RANK] Name: text" lines (and "To Name: text"
 * when wanted). Lines staff see of other people's messages (chat spy) start differently and never count.
 */
public final class MessageSound extends Feature {
	public enum Sound { PLING, BELL, CHIME, NONE }

	/** "From [MVP+] Steve: hi" / "To Steve: hi": the server's own private message lines, nothing in front of them. */
	static final Pattern DM = Pattern.compile("^(From|To)\\s+(?:\\[[^\\]]{1,20}\\]\\s+)?(\\w{1,16}):\\s(.*)$");
	private static final long SOUND_GAP_MS = 1000;

	private final ChoiceSetting<Sound> sound = add(new ChoiceSetting<>("sound", "Sound", "Played when a private message comes in.", Sound.PLING));
	private final BoolSetting sent = add(new BoolSetting("sent", "For sent messages too", "Play it for the \"To Name:\" line of your own messages as well.", false));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "A toast saying who messaged you.", false));

	private long lastSound;

	public MessageSound() {
		super("messageSound", "Message Sound", "A sound when someone sends you a private message.", Category.CHAT, true);
	}

	/** Who a private message line is from ("From" lines) or to ("To" lines), or null when the line isn't one. */
	static Matcher dm(String line) {
		Matcher m = DM.matcher(line.trim());
		return m.matches() ? m : null;
	}

	@Override
	public void init() {
		Chat.listen(line -> {
			if (!enabled()) return;
			Matcher m = dm(line.text());
			if (m == null) return;
			boolean incoming = m.group(1).equals("From");
			if (!incoming && !sent.on()) return;
			if (incoming && toast.on()) Toast.show("§7Message from §f" + m.group(2), Theme.ACCENT);
			chime();
		});
	}

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
		mc.player.playSound(s, 0.7f, 1.2f);
	}
}
