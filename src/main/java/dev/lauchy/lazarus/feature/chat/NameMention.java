package dev.lauchy.lazarus.feature.chat;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Recolour;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * When someone says your name in chat: the name is highlighted in their message, a sound
 * plays and a toast says who it was. Your own messages, and the part of a line before its
 * colon (where the sender's name sits), never count.
 */
public final class NameMention extends Feature {
	public enum Sound { WITHER_SPAWN, DRAGON_GROWL, ANVIL, PLING, NONE }

	private static final int HIGHLIGHT = 0xFFD54F;
	private static final long SOUND_GAP_MS = 1500;

	private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight", "Your name in gold and bold in the message.", true));
	private final ChoiceSetting<Sound> sound = add(new ChoiceSetting<>("sound", "Sound", "Played when you're mentioned.", Sound.WITHER_SPAWN));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "A toast saying who mentioned you.", true));
	private final TextSetting others = add(new TextSetting("names", "Other names", "Comma-separated nicknames that count as your name too.", "", 100));

	private long lastSound;

	public NameMention() {
		super("nameMention", "Name Mention", "Highlights your name when someone says it in chat, with a sound and a toast.", Category.CHAT, true);
	}

	@Override
	public void init() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay || !enabled() ? message : mention(message));
	}

	private Component mention(Component message) {
		String plain = Text.plain(message);
		int colon = plain.indexOf(':');
		if (!Chat.fromPlayer(plain)) return message;
		Pattern names = names();
		if (names == null) return message;
		// Before the colon is who sent it: your own messages mention you there.
		if (names.matcher(plain.substring(0, colon)).find() || !names.matcher(plain.substring(colon + 1)).find()) return message;
		notify(sender(plain.substring(0, colon)));
		return highlight.on() ? highlighted(message, names) : message;
	}

	/** Your name and any nicknames as whole words, any case; null when there's no name to look for. */
	private Pattern names() {
		List<String> all = new ArrayList<>();
		String me = Minecraft.getInstance().getUser().getName();
		if (me != null && !me.isBlank()) all.add(Pattern.quote(me));
		for (String n : others.get().split(",")) if (!n.isBlank()) all.add(Pattern.quote(n.trim()));
		if (all.isEmpty()) return null;
		return Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:" + String.join("|", all) + ")(?![A-Za-z0-9_])");
	}

	/** The same message with each mention after the colon recoloured, every other style kept. */
	private static Component highlighted(Component message, Pattern names) {
		return Recolour.afterColon(message, names, word -> style -> style.withColor(TextColor.fromRgb(HIGHLIGHT)).withBold(true));
	}

	/** The last word before the colon, past any rank in brackets: "[MVP+] Steve" gives Steve. */
	private static String sender(String head) {
		String[] words = head.trim().split("\\s+");
		return words.length == 0 ? "Someone" : words[words.length - 1];
	}

	private void notify(String who) {
		if (toast.on()) Toast.show("§6" + who + " §7mentioned you", Theme.ACCENT);
		long now = Util.getMillis();
		Minecraft mc = Minecraft.getInstance();
		SoundEvent s = switch (sound.get()) {
			case WITHER_SPAWN -> SoundEvents.WITHER_SPAWN;
			case DRAGON_GROWL -> SoundEvents.ENDER_DRAGON_GROWL;
			case ANVIL -> SoundEvents.ANVIL_LAND;
			case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
			case NONE -> null;
		};
		// A burst of mentions plays once, not over itself.
		if (s == null || mc.player == null || now - lastSound < SOUND_GAP_MS) return;
		lastSound = now;
		mc.player.playSound(s, 0.6f, 1f);
	}
}
