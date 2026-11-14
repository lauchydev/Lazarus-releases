package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Alt accounts flagged as you work: opening a profile with the staff drawer asks the server's
 * {@code /alts} quietly, and the answer comes back as a toast and a card whose every name is a button
 * to that player's profile. {@code /lazarus alts <player>} asks for anyone.
 */
public final class AltFlag extends Feature {
	static AltFlag INSTANCE;
	private static final long KEEP_MS = 10 * 60_000L;
	/** The server's header, "———— Alts of Lauchy (2) ————": the names follow, one a line. */
	private static final Pattern HEADER = Pattern.compile("^[^A-Za-z]*Alts of (\\w{1,16})\\s*\\((\\d+)\\)[^A-Za-z]*$");

	private final BoolSetting onProfile = add(new BoolSetting("onProfile", "On profile open", "Ask for alts whenever you open a profile with the staff drawer.", true));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "A toast with the count when an answer comes.", true));

	private final Map<String, Long> askedAt = new HashMap<>();

	public AltFlag() {
		super("altFlag", "Alt Flag", "Opening a profile asks the server for the player's alts, shown as a card of profile buttons.", Category.STAFF, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("alts")
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					ask(StringArgumentType.getString(c, "player"), true);
					return 1;
				}))));
	}

	/** A profile with the staff drawer was opened: the alts are asked for, once every few minutes per player. */
	public static void profileOpened(String player) {
		AltFlag f = INSTANCE;
		if (f == null || !f.enabled() || !f.onProfile.on()) return;
		Long last = f.askedAt.get(player.toLowerCase(Locale.ROOT));
		if (last != null && Util.getMillis() - last < KEEP_MS) return;
		f.ask(player, false);
	}

	private void ask(String player, boolean always) {
		if (Reply.busy()) {
			if (always) Toast.show("Still waiting on the server", Theme.MUTED);
			return;
		}
		askedAt.put(player.toLowerCase(Locale.ROOT), Util.getMillis());
		lookup(player, lines -> {
			List<String> alts = names(lines, player);
			if (toast.on() || always) {
				Toast.show(lines.isEmpty() ? "§7Alts: §fno answer" : alts.isEmpty() ? "§7Alts: §fnone found" : "§7Alts: §f" + alts.size(),
						alts.isEmpty() ? Theme.MUTED : Theme.WARN);
			}
			if (always || !alts.isEmpty()) card(player, lines, alts).send();
		});
	}

	/** Whether a server line is part of an alts answer: the "Alts of" header or a bare name under it. */
	static boolean looksLike(String text) {
		return HEADER.matcher(text).matches() || Players.NAME.matcher(text).matches();
	}

	/** Asks the server's {@code /alts} quietly for another feature; {@code then} gets the lines (empty when it stayed quiet). */
	public static void lookup(String player, java.util.function.Consumer<List<String>> then) {
		Reply.ask("alts " + player, player, true, AltFlag::looksLike, then);
	}

	/** The names in an answer, in order: under the "Alts of" header, each bare name on its own line. Without the header there are none. */
	public static List<String> names(List<String> lines, String player) {
		Set<String> out = new LinkedHashSet<>();
		boolean listed = false;
		for (String l : lines) {
			String t = l.trim();
			if (HEADER.matcher(t).matches()) {
				listed = true;
				continue;
			}
			if (listed && Players.NAME.matcher(t).matches() && !t.equalsIgnoreCase(player)) out.add(t);
		}
		return new ArrayList<>(out);
	}

	/** The answer as one card: the server's lines, then a Profile button per name. */
	static Message card(String player, List<String> lines, List<String> alts) {
		Message m = Message.lazarus().info("Alts · " + player).muted(alts.isEmpty() ? "" : "  " + alts.size());
		if (lines.isEmpty()) m.line().warning("The server didn't answer in time.");
		else if (alts.isEmpty()) {
			for (String l : lines) m.line().info(l);
		} else {
			for (String a : alts) m.line().player(a).info("  ").danger("Staff", "Click for staff options on " + a, "/lazarus staff " + a);
		}
		return m;
	}
}
