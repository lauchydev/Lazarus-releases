package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.Live;
import dev.lauchy.lazarus.core.LiveConnection;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * A line a Lazarus+ member writes that other Lazarus users see in chat when the member joins, after their
 * name. The line is kept by the Lazarus server and comes with the member's join over the live connection;
 * writing one is Lazarus+, seeing them is everyone's. Letters, numbers and simple punctuation only, no links,
 * {@link #MAX} characters at most, and it goes up a moment after you stop typing.
 */
public final class JoinLines extends Feature {
	/** The longest line. */
	static final int MAX = 48;
	/** What a line may hold: letters, digits, spaces and simple punctuation. */
	private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .,!?'’:;()\\-]*$");
	/** A link, which a line may not carry. */
	private static final Pattern LINK = Pattern.compile("(?i)https?:|www\\.|discord\\.gg|\\.(?:com|net|org|gg|co|io)\\b");
	/** Ticks the line must sit unchanged before it's sent, so each keystroke isn't a request. */
	private static final int SETTLE_TICKS = 40;

	private final TextSetting line = add(new TextSetting("line", "Your join message", "What other Lazarus users see after your name when you join: letters, numbers and simple punctuation, no links, " + MAX + " characters at most.", "", MAX)).plusOnly();
	private final BoolSetting show = add(new BoolSetting("show", "Show others' join messages", "A line in chat when a Lazarus+ member with a join message joins.", true));

	/** The line as last seen in the setting, and ticks it has sat unchanged. */
	private String seen = "";
	private int quiet;
	/** The line the server last took, or null before it has. */
	private String sent;

	public JoinLines() {
		// Its home is the Lazarus+ section, where members set it up; it isn't a Lazarus+ feature, since everyone sees the lines.
		super("joinLines", "Join Messages", "Lazarus+ members write a line other Lazarus users see when they join.", Category.PLUS, true);
	}

	@Override
	public void init() {
		Minecraft mc = Minecraft.getInstance();
		LiveConnection.LIVE.onJoin(j -> mc.execute(() -> {
			if (!enabled() || !show.on() || j.line().isEmpty() || j.badge() != Live.Badge.GOLD || j.name().isEmpty()) return;
			if (mc.player != null && j.id().equals(mc.player.getUUID())) return;
			Message.lazarus().player(j.name()).muted(" · ").info(j.line()).send();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled() || !Plus.unlocked() || !LazarusServer.configured()) return;
			String now = tidy(line.get());
			if (!now.equals(seen)) {
				seen = now;
				quiet = 0;
				return;
			}
			// Sent once it has sat still, and only when it differs from what the server has.
			if (++quiet == SETTLE_TICKS && !now.equals(sent)) send(client, now);
		});
	}

	/**
	 * A line as the server keeps it: colour codes off, spaces collapsed, trimmed and cut to {@link #MAX}; "" when it
	 * holds a link or anything but letters, numbers and simple punctuation.
	 */
	static String tidy(String raw) {
		String t = raw.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
		if (t.length() > MAX) t = t.substring(0, MAX).trim();
		return ALLOWED.matcher(t).matches() && !LINK.matcher(t).find() ? t : "";
	}

	/** Sends the line to the Lazarus server, off the game thread; what it says comes back as a chat line only when it refused. */
	private void send(Minecraft mc, String text) {
		JsonObject body = new JsonObject();
		body.addProperty("line", text);
		CompletableFuture.runAsync(() -> {
			String refusal = null;
			try {
				HttpResponse<String> r = LazarusServer.post("/plus/joinline", body);
				if (r.statusCode() != 200) refusal = r.body();
			} catch (Exception e) {
				refusal = "Lazarus couldn't reach its server. Try again in a moment.";
			}
			String why = refusal;
			mc.execute(() -> {
				if (why == null) {
					sent = text;
					Message.lazarus().success(text.isEmpty() ? "Your join message is off." : "Your join message is set.").send();
				} else {
					Message.lazarus().error(why).send();
				}
			});
		});
	}
}
