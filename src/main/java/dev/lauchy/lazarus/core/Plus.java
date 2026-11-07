package dev.lauchy.lazarus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Lazarus+: a 30-day pass bought on plus.lazaruss.co, which never renews. The Lazarus worker
 * holds the end date (only Stripe's word that a payment went through moves it); this keeps the
 * last one it said, from {@code /verify} at start and from the live connection whenever it changes
 * (a purchase, a refund, the owner's command), along with the worker's signed copy so Plus also
 * works on an offline start. Counting down is local, so nothing waits on the network. Offline, the
 * saved pass counts only while the server was reached in the last 7 days (the live connection staying
 * open counts) and the clock hasn't gone back; while Plus is paused for that, the next word from the
 * server (the connection, or the check-in every minute while it's down) brings it back.
 */
public final class Plus {
	public static final String SITE = "https://plus.lazaruss.co";
	public static final String PRICE = "US$4.99";
	public static final int DAYS = 30;
	/** The worker won't sell more while this many days are left. */
	public static final int EXTEND_WITHIN_DAYS = Membership.EXTEND_WITHIN_DAYS;
	private static final long DAY = 86_400_000L;
	private static final Path PASS = Config.dir().resolve("plus.txt");
	/** When the server last answered and the latest time the mod saw, for the offline rules. */
	private static final Path CLOCK = Config.dir().resolve("plus-clock.json");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	/** The last membership receipt the Lazarus server signed for this account, and what it means now. */
	private static final Membership MEMBERSHIP = new Membership();
	/** The server's messages for this account, shown once each in a world. */
	private static final Inbox INBOX = new Inbox();
	/** When Plus last switched on while playing, for the menu's one-time rise of the +. */
	private static long welcomedAt;
	/** Whether Plus was unlocked the last time anyone looked, so listeners hear only real changes. */
	private static boolean wasUnlocked;
	/** Why Plus is paused, once the player has been told; null while it isn't. */
	private static Membership.Reason toldPaused;
	/** The latest time seen as last saved, so it's written about once a minute rather than every tick. */
	private static long savedSeen;
	private static int ticks;
	private static final List<Runnable> CHANGED = new ArrayList<>();

	private Plus() {
	}

	/** Lazarus+ right now, by the computer's clock (which the offline rules also note down). */
	private static Membership.Decision decision() {
		long now = System.currentTimeMillis();
		MEMBERSHIP.saw(now);
		return MEMBERSHIP.decide(now);
	}

	public static boolean active() {
		return decision().reason() == Membership.Reason.ACTIVE;
	}

	/** Lazarus+ features run: this account has Lazarus+ and the mod can trust its end date. */
	public static boolean unlocked() {
		return decision().unlocked();
	}

	/** The pass hasn't ended, but Plus is off until the server answers: offline too long, or the clock went back. */
	public static boolean paused() {
		return paused(decision().reason());
	}

	private static boolean paused(Membership.Reason reason) {
		return reason == Membership.Reason.OUT_OF_TOUCH || reason == Membership.Reason.CLOCK_BACK;
	}

	/** A chargeback is open on one of this account's payments, so the server has the pass frozen until it closes. */
	public static boolean frozen() {
		return decision().reason() == Membership.Reason.FROZEN;
	}

	/** Why Plus is paused, in a sentence for the player; null while it isn't. */
	public static String pausedWhy() {
		return switch (decision().reason()) {
			case OUT_OF_TOUCH -> "Lazarus+ is paused until Lazarus reaches its server again.";
			case CLOCK_BACK -> "Lazarus+ is paused until Lazarus reaches its server again. Your computer's clock went back.";
			default -> null;
		};
	}

	/** When Lazarus+ ends (or ended), in ms; 0 when this account never had it. */
	public static long until() {
		return MEMBERSHIP.until();
	}

	public static long left() {
		return Math.max(0, until() - System.currentTimeMillis());
	}

	/** The share of a 30-day pass still left, 0 to 1, for the menu's bar. */
	public static float leftShare() {
		return Math.min(1f, left() / (float) (DAYS * DAY));
	}

	public static boolean canExtend() {
		return left() <= EXTEND_WITHIN_DAYS * DAY;
	}

	/** The pass has more than {@link #EXTEND_WITHIN_DAYS} days left, so the worker won't sell another yet. */
	private static boolean tooMuchLeft() {
		return MEMBERSHIP.tooMuchLeft(System.currentTimeMillis());
	}

	/** What /lazarus upgrade says while the pass has too much left to extend. */
	private static Component tooMuchLeftText() {
		return Message.plus().info("Your Lazarus+ plan runs until ").highlight(endDate()).info(". Extending opens in the last " + EXTEND_WITHIN_DAYS + " days.").build();
	}

	public static long welcomedAt() {
		return welcomedAt;
	}

	/** "23 days, 4 hours", "5 hours, 12 minutes", "40 minutes". */
	public static String leftText() {
		long m = left() / 60_000, d = m / 1440, h = m / 60 % 24, min = m % 60;
		if (d > 0) return plural(d, "day") + (h > 0 ? ", " + plural(h, "hour") : "");
		if (h > 0) return plural(h, "hour") + (min > 0 ? ", " + plural(min, "minute") : "");
		return min > 0 ? plural(min, "minute") : "less than a minute";
	}

	/** "18 Nov 2026", in the player's own time zone. */
	public static String endDate() {
		return DATE.format(Instant.ofEpochMilli(until()).atZone(ZoneId.systemDefault()));
	}

	private static String plural(long n, String word) {
		return n + " " + word + (n == 1 ? "" : "s");
	}

	/** Takes the Plus fields of a {@code /verify} or {@code /plus/me} answer, if the worker's signature checks out. */
	static void read(JsonObject answer, String uuid) {
		if (answer.get("messages") instanceof JsonArray a) {
			List<Inbox.Message> messages = new ArrayList<>();
			for (JsonElement e : a) {
				if (!(e instanceof JsonObject m) || !m.has("id")) continue;
				messages.add(new Inbox.Message(m.get("id").getAsString(), text(m, "kind"), text(m, "title"), text(m, "text")));
			}
			INBOX.offer(messages);
		}
		// plusReceipt also says whether a chargeback has the pass frozen; plusPass is for older mods and servers.
		String field = answer.has("plusReceipt") ? "plusReceipt" : "plusPass";
		if (!answer.has(field)) return;
		String pass = answer.get(field).getAsString();
		Membership.Receipt receipt = LazarusServer.plusReceipt(pass, uuid);
		if (receipt == null) return;
		MEMBERSHIP.heard(receipt, System.currentTimeMillis());
		saveClock();
		try {
			Files.createDirectories(PASS.getParent());
			Files.writeString(PASS, pass);
		} catch (IOException e) {
			Lazarus.LOG.warn("Could not keep the Lazarus+ pass: {}", e.toString());
		}
		check();
	}

	private static String text(JsonObject o, String key) {
		return o.get(key) instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** Shows the server's messages waiting for this account once the player is in a world, then tells the server they were seen. */
	private static void showMessages() {
		List<Inbox.Message> messages = INBOX.take();
		if (messages.isEmpty()) return;
		JsonArray ids = new JsonArray();
		for (Inbox.Message m : messages) {
			show(m);
			ids.add(m.id());
		}
		JsonObject body = new JsonObject();
		body.add("ids", ids);
		// Not heard back: the server keeps them, so they show once more at the next start.
		CompletableFuture.runAsync(() -> {
			try {
				LazarusServer.post("/plus/seen", body);
			} catch (Exception e) {
				Lazarus.LOG.info("Could not tell the Lazarus server its messages were shown: {}", e.toString());
			}
		});
	}

	/** One message from the server: a toast with its title, and its text in chat. */
	private static void show(Inbox.Message m) {
		boolean welcome = m.kind().equals("welcome");
		// Halo means Plus arriving; anything else is a notice, in the accent.
		Toast.show(m.title(), welcome ? Theme.HALO : Theme.ACCENT);
		if (welcome) {
			// The same words on every computer, whatever the server's message says.
			welcomedAt = Util.getMillis();
			Message.send(PlusMessages.welcome());
			return;
		}
		// The mod words the notices it knows (ending, ended, a late refund), so the voice stays its own.
		Message.send(PlusMessages.notice(m.kind(), m.title(), m.text(), DAYS));
	}

	/** When the worker can't be reached: the last end date it signed for this account. */
	static void offline(String uuid) {
		try {
			if (Files.exists(PASS)) {
				Membership.Receipt receipt = LazarusServer.plusReceipt(Files.readString(PASS).trim(), uuid);
				if (receipt == null) return;
				long contact = 0, seen = 0;
				if (Config.readJson(CLOCK) instanceof JsonObject o) {
					if (o.has("contact")) contact = o.get("contact").getAsLong();
					if (o.has("seen")) seen = o.get("seen").getAsLong();
				}
				MEMBERSHIP.restored(receipt, contact, seen);
				savedSeen = seen;
				check();
			}
		} catch (IOException ignored) {
		}
	}

	/** Keeps when the server last answered and the latest time seen, so the offline rules hold across restarts. */
	private static void saveClock() {
		JsonObject o = new JsonObject();
		o.addProperty("contact", MEMBERSHIP.contact());
		o.addProperty("seen", MEMBERSHIP.seen());
		Config.writeJson(CLOCK, o);
		savedSeen = MEMBERSHIP.seen();
	}

	/** Tells listeners when Plus switches on or off, and tells the player when it pauses and comes back. */
	private static void check() {
		Membership.Decision d = decision();
		if (d.unlocked() != wasUnlocked) {
			wasUnlocked = d.unlocked();
			changed();
		}
		Minecraft mc = Minecraft.getInstance();
		// Said once in a world, so a pause found at an offline start isn't lost before the player joins.
		if (mc == null || mc.player == null) return;
		if (paused(d.reason())) {
			if (toldPaused == d.reason()) return;
			toldPaused = d.reason();
			Toast.show("Lazarus+ is paused", Theme.ACCENT);
			Message.plus().warning(pausedWhy()).send();
		} else if (toldPaused != null) {
			toldPaused = null;
			if (d.unlocked()) Message.plus().success("Lazarus+ is active again.").send();
		}
	}

	/** Runs whenever Lazarus+ switches on or off (bought, ended, refunded). */
	public static void onChange(Runnable listener) {
		CHANGED.add(listener);
	}

	private static void changed() {
		for (Runnable r : CHANGED) r.run();
	}

	public static void init() {
		Minecraft game = Minecraft.getInstance();
		String uuid = String.valueOf(game.getUser().getProfileId());
		// Every change to this account's Lazarus+ arrives on the live connection (or the check-in while it's down,
		// which also brings what changed while out of touch), so the menu locks or unlocks at once.
		LiveConnection.LIVE.onPlus(o -> game.execute(() -> read(o, uuid)));
		// Hearing from the server at all keeps Plus in touch, so a game connected for weeks with no news never pauses it.
		LiveConnection.LIVE.onContact(() -> game.execute(Plus::reached));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++ticks % 20 == 0) {
				check();
				if (MEMBERSHIP.seen() - savedSeen >= 60_000) saveClock();
			}
			if (mc.level != null && mc.player != null && ticks % 20 == 0) showMessages();
		});
	}

	/** The server was heard from with no news about Lazarus+: the saved receipt counts from now (saved with the clock about once a minute). */
	private static void reached() {
		MEMBERSHIP.reached(System.currentTimeMillis());
		check();
	}

	/** {@code /lazarus upgrade}: opens the site to buy (or extend) Lazarus+ for this account; the payment arrives on the live connection. */
	public static void upgrade() {
		if (tooMuchLeft()) {
			Message.send(tooMuchLeftText());
			return;
		}
		link("/buy", true);
	}

	/** {@code /lazarus plus manage}: the site's page with your end date and payment history. */
	public static void manage() {
		link("/manage", false);
	}

	/**
	 * Opens the site for this account. Buying asks for a ticket for the player's own account, so the
	 * buy page opens with their name filled in and already checked; managing gets a one-time link.
	 */
	private static void link(String page, boolean buying) {
		Minecraft mc = Minecraft.getInstance();
		String name = mc.getUser().getName();
		CompletableFuture.runAsync(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("name", name);
				HttpResponse<String> r = LazarusServer.post(buying ? "/plus/ticket" : "/plus/link", body);
				if (r.statusCode() == 404 || r.statusCode() == 503) {
					mc.execute(() -> Message.plus().info("Lazarus+ isn't available yet.").send());
					return;
				}
				if (r.statusCode() != 200) {
					mc.execute(() -> Message.plus().error(r.body()).send());
					return;
				}
				JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
				if (buying && !o.get("eligible").getAsBoolean()) {
					// The worker never says why. Time left is the one reason the player can be told; anything else
					// (a pass frozen by a chargeback, say) gets the same plain no.
					mc.execute(() -> Message.send(tooMuchLeft() ? tooMuchLeftText() : Message.plus().info("This account can't get Lazarus+ right now.").build()));
					return;
				}
				String url = buying
						? SITE + page + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&ticket=" + o.get("ticket").getAsString()
						: SITE + page + "?t=" + o.get("token").getAsString();
				mc.execute(() -> open(url, buying));
			} catch (Exception e) {
				mc.execute(() -> Message.plus().error("Lazarus couldn't reach its server. Try again in a moment.").muted(" " + e.getMessage()).send());
			}
		});
	}

	/** Opens the page in the browser, and puts the link in chat too in case the browser didn't come up. */
	private static void open(String url, boolean buying) {
		try {
			Util.getPlatform().openUri(URI.create(url));
		} catch (RuntimeException e) {
			Lazarus.LOG.warn("Could not open the browser: {}", e.toString());
		}
		// A manage link is used up once the page loads, so it only helps if the browser didn't open.
		Message.plus().info("Opening your browser. ")
				.link("Open again", "Click to open the page again. " + (buying ? "The link is yours alone and works for 30 minutes." : "The link is yours alone and opens once."), url).send();
	}

	/** {@code /lazarus plus}: where your Lazarus+ stands, in chat. */
	public static void status() {
		Message m = Message.plus();
		if (frozen()) {
			m.warning("Your Lazarus+ plan is paused while a payment dispute is open.");
		} else if (paused()) {
			m.warning(pausedWhy());
		} else if (active()) {
			m.success("Your Lazarus+ plan is active. ").highlight(leftText()).info(" left.");
		} else if (until() > 0) {
			m.info("Your Lazarus+ plan ended on ").highlight(endDate()).info(".");
		} else {
			m.info("Lazarus+ is ").highlight(PRICE).info(" for " + DAYS + " days.")
					.line().info("Every cape, exclusive features, and the gold plus beside your name.");
		}
		boolean buy = !active() || canExtend(), manage = until() > 0;
		if (buy || manage) m.line();
		// A paused or frozen plan with time left is still running, so its button extends rather than renews.
		boolean running = until() > System.currentTimeMillis();
		if (buy) m.command(running ? "Extend now" : until() > 0 ? "Renew now" : "Get Lazarus+", "Click to " + (running ? "add" : "get") + " " + DAYS + " days of Lazarus+", "/lazarus upgrade");
		if (buy && manage) m.info("  ");
		if (manage) m.command("Manage", "Click to see your end date and payments", "/lazarus plus manage");
		m.send();
	}
}
