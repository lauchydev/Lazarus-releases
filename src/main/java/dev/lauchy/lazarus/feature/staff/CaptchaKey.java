package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A captcha with one key: look at a player and press it, and the server's {@code /captcha} goes out
 * for them, its answer coming back as a toast.
 */
public final class CaptchaKey extends Feature {
	/** The result, minutes later: "[Captcha] Lauchy passed the check Lauchy sent, in 8s." */
	static final Pattern RESULT = Pattern.compile("^\\[Captcha\\]\\s+(\\w{1,16}) (passed|failed) the check (\\w{1,16}) sent(?:,? in (\\d+)s)?\\.?$");

	private final BoolSetting quiet = add(new BoolSetting("quiet", "Reply as a toast", "The server's reply as a toast instead of in chat.", true));

	public CaptchaKey() {
		super("captchaKey", "Captcha Key", "One key sends a captcha to the player you're looking at.", Category.STAFF, true);
	}

	/** The toast for a result line, or null when the line isn't one: "Steve passed the check in 8s". */
	static String result(String line) {
		Matcher m = RESULT.matcher(line.trim());
		if (!m.matches()) return null;
		boolean passed = m.group(2).equals("passed");
		return (passed ? "§a" : "§c") + m.group(1) + " " + m.group(2) + " the check" + (m.group(4) == null ? "" : " §7in " + m.group(4) + "s");
	}

	@Override
	public void init() {
		Chat.listen(line -> {
			if (!enabled()) return;
			String toast = result(line.text());
			if (toast != null) Toast.show(toast, toast.startsWith("§a") ? Theme.GOOD : Theme.BAD);
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("captcha")
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					send(StringArgumentType.getString(c, "player"));
					return 1;
				}))));
	}

	/** Sends the captcha to a player, the server's reply as a toast when wanted. */
	private void send(String target) {
		if (!enabled()) return;
		if (Reply.busy()) {
			Toast.show("Still waiting on the server", Theme.MUTED);
			return;
		}
		Toast.show("§7Captcha: §f" + target, Theme.ACCENT);
		Reply.ask("captcha " + target, target, quiet.on(), lines -> {
			if (!quiet.on()) return;
			Toast.show(lines.isEmpty() ? "§7Captcha sent to §f" + target : "§7Captcha: §f" + lines.getFirst(), lines.isEmpty() ? Theme.GOOD : Theme.ACCENT);
		});
	}
}
