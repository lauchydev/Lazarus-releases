package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Ranks;
import dev.lauchy.lazarus.core.Recolour;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Staff names in chat take their rank's colour wherever a server line shows them (said by a player, in
 * a player list, in a join line): an admin's name in red, a mod's in green, the owner's in blue, bold,
 * the way your own name is picked out. Who is staff and which rank is learnt
 * from the rank in front of their own chat lines and, for everyone else online, from the dragsim.co
 * lookup the Profile Viewer uses, one player a second, remembered for a day.
 */
public final class RankColours extends Feature {
	/** What a lookup says about a player: their rank key ("" for none) and when it was learnt. */
	record Known(String rank, long at) {
	}

	private static final long KEEP_MS = 24 * 60 * 60_000L;

	private final TextSetting colours = add(new TextSetting("colours", "Rank colours",
			"Each staff rank's colour code: owner=9 is blue, admin=c red, manager=4 dark red, mod=2 green, helper=b aqua.",
			"owner=9, admin=c, manager=4, mod=2, helper=b", 120));
	private final BoolSetting lookup = add(new BoolSetting("lookup", "Look up players online", "Ask dragsim.co for the rank of each player on the list, one a second, so staff who haven't spoken are coloured too.", true));
	private final BoolSetting bold = add(new BoolSetting("bold", "Bold", "Staff names in bold as well as their colour.", true));

	/** Rank by lowercase name. */
	private final Map<String, Known> known = new HashMap<>();
	private Pattern staffNames;
	private boolean dirty, asking;
	private int ticks;
	private String lastColours = "";

	public RankColours() {
		super("rankColours", "Rank Colours in Chat", "Staff names said in chat take their rank's colour.", Category.STAFF, true);
	}

	@Override
	public void init() {
		load();
		Config.saveOn(this::save);
		Chat.listen(line -> {
			if (!enabled()) return;
			Chat.Speaker who = Chat.speaker(line.text());
			if (who != null && !who.rank().isEmpty()) learn(who.name(), who.rank());
		});
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay || !enabled()) return message;
			Pattern names = staffNames();
			if (names == null) return message;
			String plain = Text.plain(message);
			if (!names.matcher(plain).find()) return message;
			// In a player's line the sender's own name stays as the server styled it; any other line (a
			// player list, a join, a broadcast) is coloured throughout.
			return Chat.speaker(plain) != null ? Recolour.afterColon(message, names, word -> style -> styled(style, rankOf(word)))
					: Recolour.everywhere(message, names, word -> style -> styled(style, rankOf(word)));
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || !enabled()) return;
			if (!colours.get().equals(lastColours)) {
				lastColours = colours.get();
				Ranks.configure(lastColours);
			}
			if (++ticks % 20 == 0 && lookup.on() && !asking) ask(mc);
		});
	}

	/** The rank key a name was last seen or looked up with, or "". */
	String rankOf(String name) {
		Known k = known.get(name.toLowerCase(Locale.ROOT));
		return k == null ? "" : k.rank();
	}

	/** A player's rank as a chat line or the site spelt it; a non-staff rank is kept so they aren't looked up again. */
	void learn(String name, String rank) {
		String key = Staff.rankKey(rank);
		Known old = known.get(name.toLowerCase(Locale.ROOT));
		if (old != null && old.rank().equals(key) && Util.getMillis() - old.at() < KEEP_MS / 2) return;
		known.put(name.toLowerCase(Locale.ROOT), new Known(key, Util.getMillis()));
		staffNames = null;
		dirty = true;
	}

	/** The style for a staff name: the rank's colour, and bold when wanted. */
	private Style styled(Style style, String rank) {
		ChatFormatting f = ChatFormatting.getByCode(Ranks.code(rank));
		if (f == null) return style;
		return bold.on() ? style.withColor(f).withBold(true) : style.withColor(f);
	}

	/** Every staff name known, as one whole-word pattern, or null when none is. */
	private Pattern staffNames() {
		if (staffNames != null) return staffNames;
		List<String> names = new ArrayList<>();
		for (var e : known.entrySet()) if (Ranks.code(e.getValue().rank()) != 0) names.add(Pattern.quote(e.getKey()));
		staffNames = names.isEmpty() ? null : Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:" + String.join("|", names) + ")(?![A-Za-z0-9_])");
		return staffNames;
	}

	/** Looks up the first player online whose rank isn't known or is a day old. */
	private void ask(Minecraft mc) {
		long now = Util.getMillis();
		for (String name : Players.online()) {
			Known k = known.get(name.toLowerCase(Locale.ROOT));
			if (k != null && now - k.at() < KEEP_MS) continue;
			asking = true;
			Http.json("/api/player/" + Http.encode(name) + "?mode=main").whenComplete((json, error) -> mc.execute(() -> {
				asking = false;
				if (error != null || json == null || !json.isJsonObject()) {
					known.put(name.toLowerCase(Locale.ROOT), new Known("", now - KEEP_MS + 60_000L)); // unknown for now; again in a minute
					return;
				}
				JsonElement p = json.getAsJsonObject().get("player");
				JsonObject player = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
				String rank = player.has("rank") && player.get("rank").isJsonPrimitive() ? player.get("rank").getAsString() : "";
				learn(name, rank);
			}));
			return;
		}
	}

	private static Path file() {
		return Config.dir().resolve("ranks.json");
	}

	private void load() {
		JsonElement json = Config.readJson(file());
		if (json == null || !json.isJsonObject()) return;
		for (var e : json.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject o = e.getValue().getAsJsonObject();
			known.put(e.getKey(), new Known(o.get("rank").getAsString(), o.get("at").getAsLong()));
		}
	}

	private void save() {
		if (!dirty) return;
		dirty = false;
		JsonObject root = new JsonObject();
		known.forEach((name, k) -> {
			JsonObject o = new JsonObject();
			o.addProperty("rank", k.rank());
			o.addProperty("at", k.at());
			root.add(name, o);
		});
		Config.writeJson(file(), root);
	}
}
