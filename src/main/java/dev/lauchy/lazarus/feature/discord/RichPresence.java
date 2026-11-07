package dev.lauchy.lazarus.feature.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.JoinMessages;
import dev.lauchy.lazarus.feature.general.UpdateNotice;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Shows what you're doing on Dragsim on your Discord profile, through the Discord app on this
 * computer. The two lines are yours to write, with placeholders for your location, purse, held
 * item and name. The status is worked out on the game's thread once a second and sent from a
 * background thread, at most once every five seconds (Discord's own limit), so a closed or slow
 * Discord never holds up the game; it reconnects by itself when Discord is opened.
 */
public final class RichPresence extends Feature {
	/** The Lazarus application on Discord, whose name shows as the game being played. */
	private static final String APP_ID = "1564256234800947260";
	/** The Lazarus mark, shown as the status's picture. */
	private static final String LOGO = "https://plus.lazaruss.co/assets/icon-512.png";
	/** The quickest Discord takes a new status; it drops ones sent faster. */
	private static final long SEND_MS = 5_000;
	/** How long to wait before looking for Discord again once it's closed. */
	private static final long RETRY_MS = 15_000;

	private final TextSetting top = add(new TextSetting("top", "Top line",
			"What your status says first. Placeholders: " + PresenceLines.PLACEHOLDERS + ". A line whose placeholder has nothing to show is left off.", "{location}", 100));
	private final TextSetting bottom = add(new TextSetting("bottom", "Bottom line",
			"What your status says under it, with the same placeholders: " + PresenceLines.PLACEHOLDERS + ".", "Purse: {purse}", 100));
	private final BoolSetting time = add(new BoolSetting("time", "Time played", "Show how long you've been on Dragsim.", true));
	private final BoolSetting head = add(new BoolSetting("head", "Your head", "Show your Minecraft head in the corner of the picture.", true));
	private final BoolSetting button = add(new BoolSetting("button", "Lazarus button", "Add a button that opens the Lazarus Discord.", true));
	private final BoolSetting dragsimOnly = add(new BoolSetting("dragsimOnly", "Only on Dragsim", "Switch off to show Lazarus on other servers too.", true));

	/** The status to show, worked out on the game's thread; null clears it. */
	private volatile JsonObject wanted;
	/** When you joined, for the time played. */
	private long joined;
	/** Counts ticks so the status is worked out once a second, not every tick. */
	private int ticks;

	public RichPresence() {
		super("richPresence", "Rich Presence", "Shows where you are, your purse or what you're holding on your Discord profile.", Category.DISCORD, true);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.level == null) {
				joined = 0;
				wanted = null;
				return;
			}
			if (joined == 0) joined = System.currentTimeMillis() / 1000;
			if (++ticks % 20 != 0) return;
			wanted = enabled() && !APP_ID.isEmpty() && (!dragsimOnly.on() || onDragsim(mc)) ? activity(mc) : null;
		});
		Thread t = new Thread(this::send, "Lazarus Discord presence");
		t.setDaemon(true);
		t.start();
	}

	/** The status as Discord takes it, from the player's lines and switches. */
	private JsonObject activity(Minecraft mc) {
		Map<String, String> values = new HashMap<>();
		values.put("location", location(Area.place()));
		values.put("purse", Area.sidebar().stream().map(PresenceLines::purse).filter(Objects::nonNull).findFirst().orElse(null));
		values.put("held", Items.name(mc.player.getMainHandItem()));
		values.put("name", mc.getUser().getName());

		JsonObject a = new JsonObject();
		String details = PresenceLines.fill(top.get(), values);
		String state = PresenceLines.fill(bottom.get(), values);
		if (details != null) a.addProperty("details", details);
		if (state != null) a.addProperty("state", state);
		if (time.on()) {
			JsonObject timestamps = new JsonObject();
			timestamps.addProperty("start", joined);
			a.add("timestamps", timestamps);
		}
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LOGO);
		assets.addProperty("large_text", "Lazarus " + UpdateNotice.version());
		if (head.on()) {
			assets.addProperty("small_image", "https://mc-heads.net/avatar/" + mc.getUser().getProfileId() + "/64");
			assets.addProperty("small_text", mc.getUser().getName());
		}
		a.add("assets", assets);
		if (button.on()) {
			JsonObject link = new JsonObject();
			link.addProperty("label", "Get Lazarus");
			link.addProperty("url", JoinMessages.DISCORD);
			JsonArray buttons = new JsonArray();
			buttons.add(link);
			a.add("buttons", buttons);
		}
		return a;
	}

	/** Keeps Discord showing the wanted status: connects when there is one, sends each change, and lets go when there's none. */
	private void send() {
		DiscordIpc ipc = null;
		JsonObject sent = null;
		while (true) {
			JsonObject next = wanted;
			try {
				if (next == null) {
					// Dropping the connection clears the status on Discord.
					if (ipc != null) ipc.close();
					ipc = null;
					sent = null;
				} else if (!next.equals(sent)) {
					if (ipc == null) ipc = DiscordIpc.connect(APP_ID);
					ipc.activity(next);
					sent = next;
				}
				pause(SEND_MS);
			} catch (IOException | RuntimeException e) {
				Lazarus.LOG.debug("Discord presence: {}", e.getMessage());
				if (ipc != null) ipc.close();
				ipc = null;
				sent = null;
				pause(RETRY_MS);
			}
		}
	}

	/** Whether the server you're on is Dragsim, by its address. */
	private static boolean onDragsim(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip.toLowerCase(Locale.ROOT).contains("dragsim");
	}

	/** The place's name as players say it, or null when the sidebar doesn't say. */
	static String location(Area.Place place) {
		return switch (place) {
			case END -> "Dragon's Den";
			case VOID_SEPULTURE -> "Void Sepulture";
			case CRYPTS -> "Crypts";
			case DUNGEON -> "The Catacombs";
			case HUB -> "Hub";
			case RIFT -> "The Rift";
			case UNKNOWN -> null;
		};
	}

	private static void pause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
