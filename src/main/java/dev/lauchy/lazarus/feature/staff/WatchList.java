package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Players to keep an eye on: {@code /lazarus watch add <player> [note]} puts someone on the list, and
 * from then on they carry a red ⚠ in the tab list, over their head and after their chat lines, you're
 * told when they join or leave your server, and any server line that names them is flagged. The list
 * is kept between sessions.
 */
public final class WatchList extends Feature {
	static final String MARK = " ⚠";
	private static final Pattern WORD = Pattern.compile("\\w{3,16}");
	public static WatchList INSTANCE;

	private final BoolSetting joins = add(new BoolSetting("joins", "Join and leave alerts", "A toast and a chat line when a watched player comes or goes.", true));
	private final BoolSetting chat = add(new BoolSetting("chat", "Flag their chat", "A red ⚠ after a watched player's chat lines.", true));
	private final BoolSetting mentions = add(new BoolSetting("mentions", "Flag server lines", "A red ⚠ on server lines that name a watched player.", true));
	private final BoolSetting names = add(new BoolSetting("names", "Mark in tab list and name tags", "A red ⚠ after a watched player's name.", true));

	/** Note by lowercase name. */
	private final Map<String, String> list = new TreeMap<>();
	private Set<String> online = new HashSet<>();
	private int ticks;
	private boolean dirty;

	public WatchList() {
		super("watchList", "Watch List", "Players you're watching are marked everywhere and announced when they join.", Category.STAFF, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		Config.saveOn(this::save);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> online = new HashSet<>());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || ++ticks % 20 != 0) return;
			Set<String> now = new HashSet<>();
			for (String n : Players.online()) now.add(n.toLowerCase(Locale.ROOT));
			if (enabled() && joins.on() && !online.isEmpty()) {
				for (String n : now) if (!online.contains(n) && list.containsKey(n)) came(n, true);
				for (String n : online) if (!now.contains(n) && list.containsKey(n)) came(n, false);
			}
			online = now;
		});
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay || !enabled() || list.isEmpty()) return message;
			String plain = Text.plain(message);
			Chat.Speaker who = Chat.speaker(plain);
			String hit = who != null ? (chat.on() && watched(who.name()) ? who.name() : null) : mentions.on() ? namedIn(plain) : null;
			if (hit == null) return message;
			return Component.empty().append(message).append(Message.mark(MARK, "Watched: " + note(hit), "/lazarus watch list"));
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("watch").executes(c -> {
			show();
			return 1;
		}).then(ClientCommands.literal("list").executes(c -> {
			show();
			return 1;
		})).then(ClientCommands.literal("add").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
			add(StringArgumentType.getString(c, "player"), "");
			return 1;
		}).then(ClientCommands.argument("note", StringArgumentType.greedyString()).executes(c -> {
			add(StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "note"));
			return 1;
		})))).then(ClientCommands.literal("remove").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
			remove(StringArgumentType.getString(c, "player"));
			return 1;
		})))));
	}

	/** Whether this name is on the list. */
	public boolean watched(String name) {
		return list.containsKey(name.toLowerCase(Locale.ROOT));
	}

	/** The note on a watched player ("" when none or not watched). */
	public String noteOf(String name) {
		return list.getOrDefault(name.toLowerCase(Locale.ROOT), "");
	}

	/** Puts a player on the list, or takes them off when they're on it, telling you which in chat. */
	public void toggle(String name) {
		if (watched(name)) remove(name);
		else add(name, "");
	}

	/** Whether watched names should be marked in the tab list and on name tags right now. */
	boolean marksNames() {
		return enabled() && names.on() && !list.isEmpty();
	}

	/** The first watched name a text mentions as a whole word, or null. */
	String namedIn(String plain) {
		if (list.isEmpty()) return null;
		Matcher m = WORD.matcher(plain);
		while (m.find()) if (watched(m.group())) return m.group();
		return null;
	}

	/** The note on a watched player, or their name when none was given. */
	private String note(String name) {
		String n = list.getOrDefault(name.toLowerCase(Locale.ROOT), "");
		return n.isEmpty() ? name : name + ", " + n;
	}

	private void came(String lower, boolean joined) {
		String name = shown(lower);
		Toast.show("§c⚠ §f" + name + " §7" + (joined ? "joined" : "left"), joined ? Theme.BAD : Theme.MUTED);
		Message m = Message.lazarus().warning("⚠ ").highlight(name).info(joined ? " joined." : " left.");
		String note = list.get(lower);
		if (!note.isEmpty()) m.muted("  " + note);
		if (joined) {
			m.line().danger("Staff", "Click for staff options on " + name, "/lazarus staff " + name).info("  ")
					.command("Teleport", "Click to teleport to " + name, "/tp " + name).info("  ")
					.player(name);
		}
		m.send();
	}

	/** The name as the player list spells it, or as it was added. */
	private String shown(String lower) {
		for (String n : Players.online()) if (n.equalsIgnoreCase(lower)) return n;
		return lower;
	}

	private void add(String player, String note) {
		if (!Players.NAME.matcher(player).matches()) {
			Message.lazarus().error("That isn't a player name.").send();
			return;
		}
		list.put(player.toLowerCase(Locale.ROOT), note.trim());
		dirty = true;
		Message.lazarus().success("Watching ").highlight(player).success(".").muted(note.isBlank() ? "" : "  " + note.trim()).send();
	}

	private void remove(String player) {
		if (list.remove(player.toLowerCase(Locale.ROOT)) == null) {
			Message.lazarus().error(player + " isn't on the watch list.").send();
			return;
		}
		dirty = true;
		Message.lazarus().success("No longer watching ").highlight(player).success(".").send();
	}

	private void show() {
		if (list.isEmpty()) {
			Message.lazarus().info("The watch list is empty.").line().muted("/lazarus watch add <player> [note]").send();
			return;
		}
		Message m = Message.lazarus().heading("Watching " + list.size());
		List<String> here = Players.online().stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();
		for (var e : list.entrySet()) {
			String name = shown(e.getKey());
			m.line().player(name).muted(here.contains(e.getKey()) ? "  online" : "").muted(e.getValue().isEmpty() ? "" : "  " + e.getValue()).info("  ")
					.danger("Staff", "Click for staff options on " + name, "/lazarus staff " + name).info("  ")
					.command("Remove", "Click to stop watching " + name, "/lazarus watch remove " + name);
		}
		m.send();
	}

	private static Path file() {
		return Config.dir().resolve("watchlist.json");
	}

	private void load() {
		JsonElement json = Config.readJson(file());
		if (json == null || !json.isJsonObject()) return;
		for (var e : json.getAsJsonObject().entrySet()) list.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "");
	}

	private void save() {
		if (!dirty) return;
		dirty = false;
		JsonObject o = new JsonObject();
		list.forEach(o::addProperty);
		Config.writeJson(file(), o);
	}
}
