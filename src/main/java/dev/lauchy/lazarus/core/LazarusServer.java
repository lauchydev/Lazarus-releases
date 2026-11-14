package dev.lauchy.lazarus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The Lazarus worker (tools/lazarus-server): who's online with the mod, capes, Lazarus+, shared data and staff
 * sharing. Each call that names you is signed with this install's own key ({@link InstallKey}); the worker ties
 * your account to the first key it hears from, so no other install can speak for you. Nothing from the Minecraft
 * session is used.
 */
public final class LazarusServer {
	/** Where the worker is deployed, tried in order; while empty, capes stay on your screen. */
	private static final List<String> URLS = List.of("https://mod.lazaruss.co");
	/** The worker's {@code /key}: only the worker holds the private half, so only it can sign a Lazarus+ receipt this accepts. */
	private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAIx5UJ3ZtbzcwlSQ4WOsiQEOD9EMCCEk0+kq2CD8EOmY=";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	/**
	 * Whether the worker said this account is one of the owner's. The worker holds that list and
	 * enforces it, so it isn't built into the jar; this only hides the owner's commands from others.
	 */
	private static volatile boolean admin;
	/** Whether the worker lets this account set prices ({@code /lazarus price set}) without being the owner. */
	private static volatile boolean pricer;

	private LazarusServer() {
	}

	public static boolean admin() {
		return admin;
	}

	/** Whether this account may set prices: the owner, or a player the owner lets. */
	public static boolean pricer() {
		return admin || pricer;
	}

	/** Reads the owner, pricer and Discord flags from the worker's {@code /hello} answer. */
	static void admin(JsonObject answer) {
		admin = answer.has("admin") && answer.get("admin").getAsBoolean();
		pricer = answer.has("pricer") && answer.get("pricer").getAsBoolean();
		discordLinked = answer.has("discord") ? answer.get("discord").getAsBoolean() : null;
	}

	/** Whether this account has a Discord account linked; null while the worker's bot isn't set up. */
	private static volatile Boolean discordLinked;

	public static Boolean discordLinked() {
		return discordLinked;
	}

	/**
	 * {@code /lazarus discord}: a code, click to copy, that links the Discord account it's entered on and gives it
	 * its roles. Verifying on Discord changes nothing in the mod, which works the same either way.
	 */
	public static void discord() {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("name", mc.getUser().getName());
				HttpResponse<String> r = post("/discord/link", body);
				if (r.statusCode() == 200) {
					JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
					String code = o.get("code").getAsString();
					Message m = Message.lazarus().info("Your code is ").copy(code, "Click to copy", code)
							.info(". Enter it under Verify in the Lazarus Discord within " + o.get("minutes").getAsInt() + " minutes.");
					if (o.has("linked") && o.get("linked").getAsBoolean()) m.line().warning("This replaces your linked Discord account.");
					else m.line().line().info("Not in the Discord yet? ").link("Join it", "Click to join the Lazarus Discord", JoinMessages.DISCORD).info(" first.");
					said = m.build();
				} else {
					said = Message.lazarus().error(r.body()).build();
				}
			} catch (Exception e) {
				said = unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	/**
	 * {@code /lazarus access block|unblock|resetkey <player>}: blocks an account (its mod locks), lifts a block, or
	 * forgets the account's install key so its next install can claim it. Asks the worker, then says in chat what it did.
	 */
	public static void access(String action, String player) {
		if (!configured()) {
			Message.lazarus().error("The Lazarus server isn't set up yet.").send();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				// The worker can't ask Mojang (it turns Cloudflare away), so the account is looked up here.
				String[] account = account(player);
				if (account == null) throw new IllegalArgumentException("no Minecraft account " + player);
				JsonObject body = new JsonObject();
				body.addProperty("action", action);
				body.addProperty("player", account[0]);
				body.addProperty("playerName", account[1]);
				HttpResponse<String> r = post("/access", body);
				said = r.statusCode() == 200 ? accessReply(action, account[1]) : Message.lazarus().error(r.body()).build();
			} catch (IllegalArgumentException e) {
				said = Message.lazarus().error("No Minecraft account is called " + player + ".").build();
			} catch (Exception e) {
				said = unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	private static Component accessReply(String action, String name) {
		return switch (action) {
			case "block" -> Message.lazarus().success("Blocked ").highlight(name).success(". Their Lazarus locks the next time it starts.").build();
			case "unblock" -> Message.lazarus().success("Unblocked ").highlight(name).success(". Their Lazarus unlocks the next time it starts.").build();
			default -> Message.lazarus().success("Reset ").highlight(name).success("'s key. The next install they start claims it.").build();
		};
	}

	/** {uuid, current name} for a name or a UUID, from Mojang; null when there's no such account. */
	public static String[] account(String who) throws Exception {
		String id = who.replace("-", "").toLowerCase();
		boolean uuid = id.matches("[0-9a-f]{32}");
		HttpRequest request = HttpRequest.newBuilder(URI.create(uuid
						? "https://sessionserver.mojang.com/session/minecraft/profile/" + id
						: "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(who, StandardCharsets.UTF_8)))
				.timeout(Duration.ofSeconds(8)).header("User-Agent", "Lazarus").GET().build();
		HttpResponse<String> r = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) return null;
		JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
		return new String[]{o.get("id").getAsString(), o.get("name").getAsString()};
	}

	/** The reply when the worker can't be asked: what to do, with the technical reason in grey after it. */
	public static Component unreachable(Exception e) {
		return Message.lazarus().error("Lazarus couldn't reach its server. Try again in a moment.").muted(" " + e.getMessage()).build();
	}

	public static boolean configured() {
		return !URLS.isEmpty();
	}

	/** GETs one of the worker's public paths (the item index, shared data). Blocks; throws when it can't be reached. */
	public static HttpResponse<String> get(String path) throws Exception {
		return send(url -> HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Lazarus").GET().build());
	}

	/**
	 * POSTs the body signed with this install's key. Blocks; throws when either side can't be
	 * reached. Turned away as stale (this PC's clock is off), it is signed again by the worker's
	 * clock, which the answer just gave, and sent once more (staff calls answer that 401, the rest 403).
	 */
	public static HttpResponse<String> post(String path, JsonObject body) throws Exception {
		HttpResponse<String> r = signedPost(path, body);
		if ((r.statusCode() == 403 || r.statusCode() == 401) && r.body().contains("stale request")) r = signedPost(path, body);
		return r;
	}

	private static HttpResponse<String> signedPost(String path, JsonObject body) throws Exception {
		String text = body.toString();
		String auth = auth(path, text).toString();
		return send(url -> HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Lazarus").header("Content-Type", "application/json").header("x-lazarus-auth", auth)
				.POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8)).build());
	}

	/** The live connection's address: the first worker address, as a WebSocket. */
	static URI liveAddress() {
		return URI.create(URLS.getFirst().replaceFirst("^http", "ws") + "/live");
	}

	/** The live connection's x-lazarus-auth header: signed like a POST, with the body inside it, since an upgrade has none. */
	static String liveAuth(JsonObject body) throws Exception {
		String text = body.toString();
		JsonObject auth = auth("/live", text);
		auth.addProperty("body", text);
		return auth.toString();
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * The x-lazarus-auth header's fields for a request to {@code path} with body {@code text}: the account's UUID (public,
	 * from the game), this install's public key, and "lazarus|path|now|nonce|SHA-256 of the body" signed with the install's
	 * key. The worker takes each signature once and checks the body against its hash, so a request seen in passing can be
	 * neither sent again nor changed.
	 */
	private static JsonObject auth(String path, String text) throws Exception {
		return auth(path, text, Minecraft.getInstance().getUser().getProfileId().toString(), InstallKey.publicKey(), InstallKey::sign, ServerClock.now());
	}

	/** Signs the challenge text: the install key's {@link InstallKey#sign}, or a test's key. */
	interface Signer {
		String sign(String text) throws Exception;
	}

	/** {@link #auth(String, String)} for any account, key and time, with nothing from the game: tested on its own. */
	static JsonObject auth(String path, String text, String uuid, String publicKey, Signer signer, long now) throws Exception {
		String digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		byte[] nonce = new byte[12];
		RANDOM.nextBytes(nonce);
		String challenge = "lazarus|" + path + "|" + now + "|" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "|" + digest;
		JsonObject auth = new JsonObject();
		auth.addProperty("uuid", uuid);
		auth.addProperty("key", publicKey);
		auth.addProperty("challenge", challenge);
		auth.addProperty("signature", signer.sign(challenge));
		return auth;
	}


	/**
	 * The first answer that came from the worker, trying each address in turn: one that can't
	 * be reached, fails, or answers with a web page (a domain not pointed at it) is skipped.
	 */
	private static HttpResponse<String> send(Function<String, HttpRequest> request) throws Exception {
		Exception failed = null;
		HttpResponse<String> last = null;
		for (String url : URLS) {
			try {
				long sent = System.currentTimeMillis();
				last = CLIENT.send(request.apply(url), HttpResponse.BodyHandlers.ofString());
				ServerClock.heard(last.headers().firstValue("Date").orElse(null), sent);
				boolean page = last.headers().firstValue("Content-Type").orElse("").contains("text/html");
				if (last.statusCode() < 500 && !page) return last;
			} catch (InterruptedException e) {
				throw e;
			} catch (Exception e) {
				failed = e;
			}
		}
		if (last != null) return last;
		throw failed;
	}

	/** The membership receipt in a Plus pass the worker signed for this account (end date, and whether it's frozen); null when it isn't one. */
	static Membership.Receipt plusReceipt(String pass, String uuid) {
		if (pass == null || uuid == null || PUBLIC_KEY.isEmpty()) return null;
		int dot = pass.lastIndexOf('.');
		if (dot < 0) return null;
		String payload = pass.substring(0, dot);
		String[] parts = payload.split("\\|");
		try {
			// "<uuid>|plus|<end ms>|frozen" or "|running"; older servers sent the first three parts only.
			if (parts.length < 3 || parts.length > 4 || !parts[0].equals(uuid.replace("-", "").toLowerCase()) || !parts[1].equals("plus")) return null;
			if (parts.length == 4 && !parts[3].equals("frozen") && !parts[3].equals("running")) return null;
			PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
			Signature sig = Signature.getInstance("Ed25519");
			sig.initVerify(key);
			sig.update(payload.getBytes(StandardCharsets.UTF_8));
			if (!sig.verify(Base64.getDecoder().decode(pass.substring(dot + 1)))) return null;
			long until = Long.parseLong(parts[2]);
			return until < 0 ? null : new Membership.Receipt(until, parts.length == 4 && parts[3].equals("frozen"));
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * {@code /lazarus plus grant|remove|revoke|lookup|giftall|unblacklist|unblacklistemail}: the owner's Lazarus+ tools,
	 * answered in chat. For unblacklistEmail, {@code player} is the email to lift; for giftAll, it's the gift ID.
	 */
	public static void plusAdmin(String action, String player, int days) {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("action", action);
				String name = "";
				if (action.equals("unblacklistEmail")) {
					body.addProperty("email", player.trim());
					name = player.trim();
				} else if (action.equals("giftAll")) {
					body.addProperty("gift", player);
					name = player;
				} else {
					String[] account = account(player);
					if (account == null) throw new IllegalArgumentException("no Minecraft account " + player);
					body.addProperty("player", account[0]);
					body.addProperty("playerName", account[1]);
					name = account[1];
				}
				body.addProperty("days", days);
				HttpResponse<String> r = post("/plus/admin", body);
				said = r.statusCode() == 200 ? plusAdminReply(action, name, JsonParser.parseString(r.body()).getAsJsonObject()) : Message.plus().error(r.body()).build();
			} catch (IllegalArgumentException e) {
				said = Message.plus().error("No Minecraft account is called " + player + ".").build();
			} catch (Exception e) {
				said = Message.plus().error("Lazarus couldn't reach its server. Try again in a moment.").muted(" " + e.getMessage()).build();
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	private static Component plusAdminReply(String action, String name, JsonObject o) {
		java.time.format.DateTimeFormatter date = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH);
		java.util.function.LongFunction<String> when = ms -> date.format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()));
		Message m = Message.plus();
		if (action.equals("giftAll")) {
			int count = o.get("count").getAsInt();
			// A re-run of the same gift only reaches those it missed, so none left means it's done.
			return (count == 0 ? m.info("Everyone the gift ").highlight(name).info(" covers already has it.")
					: m.success("Gift ").highlight(name).success(": gave ").highlight(count + " Lazarus users ").highlight(o.get("days").getAsInt() + " days")
							.success(" of Lazarus+ each.")).build();
		}
		if (action.equals("unblacklist") || action.equals("unblacklistEmail")) {
			return (o.get("lifted").getAsBoolean() ? m.success("Lifted the blacklist on ").highlight(name).success(".")
					: m.highlight(name).info(" wasn't blacklisted.")).build();
		}
		long until = o.get("until").getAsLong();
		return switch (action) {
			case "grant" -> m.success("Gave ").highlight(name).success(" Lazarus+ until ").highlight(when.apply(until)).success(".").build();
			case "remove" -> m.success("Removed days. ").highlight(name).success(" now has Lazarus+ until ").highlight(when.apply(until)).success(".").build();
			case "revoke" -> m.success("Ended ").highlight(name).success("'s Lazarus+.").build();
			default -> plusLookup(m, name, o, when);
		};
	}

	/** The owner's lookup in chat: the end date, any state holding the pass off, then the whole history. */
	private static Component plusLookup(Message m, String name, JsonObject o, java.util.function.LongFunction<String> when) {
		long until = o.get("until").getAsLong();
		if (until > System.currentTimeMillis()) m.highlight(name).info(" has Lazarus+ until ").highlight(when.apply(until)).info(".");
		else m.highlight(name).info(" doesn't have Lazarus+" + (until > 0 ? " (it ended " + when.apply(until) + ")" : "") + ".");
		// Each state the worker reports as true gets a line; a state it doesn't send yet is skipped.
		String[][] states = {{"frozen", "Frozen: a payment is being charged back."}, {"blacklisted", "Blacklisted: they can't buy Lazarus+."}};
		for (String[] state : states) {
			if (o.has(state[0]) && o.get(state[0]).getAsBoolean()) m.line().warning(state[1]);
		}
		JsonArray history = o.has("history") ? o.getAsJsonArray("history") : new JsonArray();
		m.line().info(history.isEmpty() ? "No payments or gifts yet." : "Payments and gifts, newest first:");
		for (var item : history) {
			JsonObject p = item.getAsJsonObject();
			String kind = p.get("kind").getAsString();
			int days = p.get("days").getAsInt();
			m.line().info(when.apply(p.get("at").getAsLong()) + "  ");
			switch (kind) {
				case "gift" -> m.success("Gift ").highlight("+" + days + " days");
				case "remove" -> m.error("Removed ").highlight("-" + days + " days");
				case "revoke" -> m.error("Pass ended");
				default -> {
					int amount = p.get("amount").getAsInt(), refunded = p.get("refunded").getAsInt();
					String currency = p.get("currency").isJsonNull() ? "" : p.get("currency").getAsString().toUpperCase(java.util.Locale.ROOT);
					m.success("Paid ").highlight(String.format(java.util.Locale.ROOT, "%.2f %s", amount / 100.0, currency)).info(" (+" + days + " days)");
					if (p.get("disputed").getAsBoolean()) m.error(" being charged back");
					else if (refunded > 0 && refunded >= amount) m.error(" refunded");
					else if (refunded > 0) m.error(" refunded " + String.format(java.util.Locale.ROOT, "%.2f", refunded / 100.0));
				}
			}
			m.muted("  " + p.get("id").getAsString());
		}
		return m.build();
	}
}
