package dev.lauchy.lazarus.feature.chat;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ability spam, one line instead of forty: repeated cooldown refusals stack into a single
 * line with a count, or vanish. Optionally any server line repeated back to back stacks
 * too, and the Goop boss's narration can be hidden while its results and loot stay.
 */
public final class ChatCleanup extends Feature {
	public enum Mode { STACK, HIDE }

	private static final String COOLDOWN = "This ability is on cooldown";
	private static final String SALVATION_MISS = "Salvation found nothing in its path.";

	private final ChoiceSetting<Mode> mode = add(new ChoiceSetting<>("mode", "Ability messages", "Stack into one line with a count, or hide.", Mode.STACK));
	private final BoolSetting stackAll = add(new BoolSetting("stackAll", "Stack any repeat", "Stack every server line repeated back to back, not just ability ones.", true));
	private final BoolSetting goop = add(new BoolSetting("goop", "Hide Goop boss chatter", "Hide the boss's dialogue and warnings; keep who placed it, results and loot.", false));

	private String lastKey, lastShownText;
	private Component lastShown;
	private int count;

	public ChatCleanup() {
		super("chatCleanup", "Ability Spam Cleanup", "Stack repeated ability messages and quiet the Goop boss.", Category.CHAT, true);
	}

	@Override
	public void init() {
		Chat.hide(l -> enabled() && goop.on() && Goop.hide(l.text()));
		Chat.hide(l -> {
			String line = l.text();
			if (!enabled() || line.length() < 3) return false;
			boolean ability = line.startsWith(COOLDOWN) || line.equals(SALVATION_MISS);
			if (ability && mode.get() == Mode.HIDE) return true;
			// Abilities stack by kind (the seconds left change every time); others only exact repeats.
			String key = ability ? line.replaceAll("[\\d.]+s", "") : line;
			if (!ability && !stackAll.on()) return false;
			if (key.equals(lastKey) && newestIs(lastShown)) {
				replaceNewest(l.component(), ++count);
				return true;
			}
			count = 1;
			lastKey = key;
			if (ability) { // shown by us from the start, so the next refusal can replace it
				replaceNewest(l.component(), 1);
				return true;
			}
			lastShown = null; // vanilla adds it; matched by text next time
			lastShownText = line;
			return false;
		});
	}

	/** True when the newest chat line is the one we last showed (or, for vanilla's, has its text). */
	private boolean newestIs(Component shown) {
		List<GuiMessage> all = Minecraft.getInstance().gui.getChat().allMessages;
		if (all.isEmpty()) return false;
		Component newest = all.getFirst().content();
		return shown != null ? newest == shown : lastShownText != null && Text.plain(newest).equals(lastShownText);
	}

	private void replaceNewest(Component message, int n) {
		ChatComponent chat = Minecraft.getInstance().gui.getChat();
		if (n > 1 && !chat.allMessages.isEmpty()) {
			chat.allMessages.removeFirst();
			chat.refreshTrimmedMessages();
		}
		MutableComponent line = Component.empty().append(message);
		if (n > 1) line.append(Component.literal("  x" + n).withStyle(ChatFormatting.GRAY));
		lastShown = line;
		lastShownText = null;
		chat.addServerSystemMessage(line);
	}

	/** The Goop encounter's own lines; the placer announcement is kept. */
	static final class Goop {
		private static final Pattern PLACER = Pattern.compile("(squeezes the goop into the altar\\.|has woken something under the altar\\.\\.\\.)$");
		private static final List<String> SPEAKERS = List.of("Rift Slime", "Rift Colony", "Rift Corruptor", "Rift Devourer", "The Disturbed");
		private static final Set<String> NARRATION = Set.of(
				"The ground begins to shake...", "The ground is shaking...", "The rift throws you skyward!",
				"It bursts into pieces! Kill every last one of them.", "It pulls itself back together...",
				"??? ...under you...", "IT RISES under you!", "The slime hurls you into the sky!",
				"The corruption begins. The sky is not empty any more.", "The island itself starts to come apart.",
				"What was left of it dissolves.", "It stops moving.", "The rift swallows everything.",
				"The rift falls silent.", "The island pulls itself back together.", "It drags you under...",
				"The lance runs you through.", "It sheds a whole layer of itself!", "The slime sinks into the island...");
		private static final List<Pattern> COUNTERS = List.of(
				Pattern.compile("It sheds a dreg\\. \\(\\d+ crawling\\)"),
				Pattern.compile("The rift slime swells\\.{3} \\(phase \\d+\\)"),
				Pattern.compile("Corruption rains from the sky! \\d+ revenants? fell\\."),
				Pattern.compile("\\d+ pieces? of it (?:is|are) still crawling around\\."),
				Pattern.compile("Your bowstring turns to smoke\\.{3} \\(bows are useless for [\\d.]+s\\)"),
				Pattern.compile("A dreg crawls back into it\\. \\+[\\d.]+% \\(it swells\\)"),
				Pattern.compile("A glob reaches the core and it re-forms\\. \\+[\\d.]+%"),
				Pattern.compile("The ground softens under \\w{1,16}\\.{3} \\(JUMP\\)"),
				Pattern.compile("The ground bulges under \\w{1,16}\\.{3}"),
				Pattern.compile("The corruption tide rolls in from the [A-Z]+\\. Find the gap\\."),
				Pattern.compile("GOO TIDE · seam [A-Z]+"));

		static boolean hide(String line) {
			if (PLACER.matcher(line).find()) return false;
			for (String s : SPEAKERS) if (line.startsWith(s + " ") || line.startsWith(s + ": ")) return true;
			return NARRATION.contains(line) || COUNTERS.stream().anyMatch(p -> p.matcher(line).matches());
		}
	}
}
