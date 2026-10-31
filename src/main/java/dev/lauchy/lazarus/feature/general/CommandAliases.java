package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.Locale;

/**
 * Short commands that stand for longer ones: typing /w runs /wardrobe. Anything typed after
 * the alias is passed on, so an alias for "warp" turns "/wp end" into "/warp end". Each alias
 * expands once, so aliases can't loop.
 */
public final class CommandAliases extends Feature {
	private static final int SLOTS = 8;
	private final TextSetting[] aliases = new TextSetting[SLOTS];
	private final TextSetting[] targets = new TextSetting[SLOTS];

	public CommandAliases() {
		super("commandAliases", "Command Aliases", "Type a short command like /w to run a longer one like /wardrobe.", Category.GENERAL, true);
		for (int i = 0; i < SLOTS; i++) {
			String alias = i == 0 ? "w" : "", target = i == 0 ? "wardrobe" : "";
			aliases[i] = add(new TextSetting("alias" + i, "Alias " + (i + 1), "What you type, without the /. Leave empty to turn this alias off.", alias, 32));
			targets[i] = add(new TextSetting("target" + i, "Alias " + (i + 1) + " runs", "The command it runs, with or without the /.", target, 100));
		}
	}

	@Override
	public void init() {
		ClientSendMessageEvents.MODIFY_COMMAND.register(command -> enabled() ? expand(command) : command);
	}

	private String expand(String command) {
		int space = command.indexOf(' ');
		String head = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
		String rest = space < 0 ? "" : command.substring(space);
		for (int i = 0; i < SLOTS; i++) {
			String alias = aliases[i].get().trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
			String target = targets[i].get().trim().replaceFirst("^/", "");
			if (!alias.isEmpty() && !target.isEmpty() && alias.equals(head)) return target + rest;
		}
		return command;
	}
}
