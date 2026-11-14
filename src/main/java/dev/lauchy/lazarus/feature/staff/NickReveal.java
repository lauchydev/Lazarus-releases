package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.profile.Nicks;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nicked staff shown by their real names: the server's {@code /nicklist} is asked quietly every few
 * minutes, and anyone on it gets "(RealName)" after their nick in the tab list, over their head and
 * in chat. {@code /lazarus nicks} lists who is nicked right now.
 */
public final class NickReveal extends Feature {
	private static final Pattern WORD = Pattern.compile("\\w{3,16}");
	static NickReveal INSTANCE;

	private final IntSetting every = add(new IntSetting("every", "Refresh every", "How often the server's nick list is asked for.", 5, 1, 30, "min"));
	private final BoolSetting chat = add(new BoolSetting("chat", "In chat", "The real name after a nicked player's name in chat.", true));
	private final BoolSetting names = add(new BoolSetting("names", "In tab list and name tags", "The real name after the nick.", true));

	private int ticks;

	public NickReveal() {
		super("nickReveal", "Nick Reveal", "Nicked staff show their real name in the tab list, over their head and in chat.", Category.STAFF, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.player == null || !enabled() || ++ticks % 20 != 0) return;
			if (Nicks.sinceAsked() > every.get() * 60_000L && !Reply.busy()) Nicks.refresh();
		});
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay || !enabled() || !chat.on()) return message;
			Chat.Speaker who = Chat.speaker(Text.plain(message));
			if (who == null) return message;
			String real = Nicks.realName(who.name());
			return real == null ? message : Component.empty().append(message).append(Component.literal(" (" + real + ")").withColor(0x8C8C9A));
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("nicks").executes(c -> {
			var all = Nicks.all();
			if (all.isEmpty()) Message.lazarus().info("Nobody is nicked" + (Nicks.answered() ? "." : ", as far as the server has said.")).send();
			else Message.lazarus().heading(all.size() + " nicked").rows(all.stream().map(l -> "§f" + l).toList()).send();
			Nicks.refresh();
			return 1;
		})));
	}

	/** Whether nicks should be revealed in the tab list and on name tags right now. */
	boolean revealsNames() {
		return enabled() && names.on();
	}

	/** The real name behind the first nick a text contains, or null. */
	static String realIn(String plain) {
		Matcher m = WORD.matcher(plain);
		while (m.find()) {
			String real = Nicks.realName(m.group());
			if (real != null) return real;
		}
		return null;
	}
}
