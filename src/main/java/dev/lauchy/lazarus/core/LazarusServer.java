package dev.lauchy.lazarus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The Lazarus worker (tools/lazarus-server): the whitelist and everyone's capes. Each call
 * that names you carries your Mojang player certificate (the key Mojang signs for your
 * account, which chat signing uses) and is signed with it, so the worker can tell it's you
 * without asking Mojang, and no one can pass as you.
 */
public final class LazarusServer {
	/** Where the worker is deployed, tried in order; while empty, the GitHub whitelist is used and capes stay on your screen. */
	private static final List<String> URLS = List.of("https://mod.lazaruss.co");
	/** The worker's {@code /key}: only the worker holds the private half, so only it can sign a pass this accepts. */
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

	/** Reads the owner flag from the worker's {@code /verify} answer. */
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

	/** {@code /lazarus discord}: a code, click to copy, that links the Discord account it's entered on and gives it its roles. */
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

	/** {@code /lazarus whitelist list|add|remove}: asks the worker, then says in chat what it did. */
	public static void whitelist(String action, String player) {
		if (!configured()) {
			Message.lazarus().error("The Lazarus server isn't set up yet.").send();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("action", action);
				if (!action.equals("list")) {
					// The worker can't ask Mojang (it turns Cloudflare away), so the account is looked up here.
					String[] account = account(player);
					if (account == null) throw new IllegalArgumentException("no Minecraft account " + player);
					body.addProperty("player", account[0]);
					body.addProperty("playerName", account[1]);
				}
				HttpResponse<String> r = post("/whitelist", body);
				said = r.statusCode() == 200 ? whitelistReply(JsonParser.parseString(r.body()).getAsJsonObject()) : Message.lazarus().error(r.body()).build();
			} catch (IllegalArgumentException e) {
				said = Message.lazarus().error("No Minecraft account is called " + player + ".").build();
			} catch (Exception e) {
				said = unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	/** {@code /lazarus invite}: a new invite code for one new player, click to copy. */
	public static void invite() {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				HttpResponse<String> r = post("/invite", new JsonObject());
				if (r.statusCode() == 200) {
					JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
					String code = o.get("code").getAsString();
					String redeem = "/lazarus redeem " + code;
					said = Message.lazarus().info("Your invite is ").copy(code, "Click to copy", code).info(". It works once.").line().line()
							.heading("How to invite someone").line()
							.info("1. Invite them to the Lazarus Discord: ").copy(JoinMessages.DISCORD, "Click to copy the Discord invite", JoinMessages.DISCORD).line()
							.info("2. Give them the code ").copy(code, "Click to copy", code).line()
							.info("3. They install Lazarus and type ").copy(redeem, "Click to copy", redeem).info(" in game.").build();
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

	/** {@code /lazarus invites [cancel <code>]}: the unused invite codes, each with a click to cancel. */
	public static void invites(String cancel) {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("action", cancel == null ? "list" : "cancel");
				if (cancel != null) body.addProperty("code", cancel);
				HttpResponse<String> r = post("/invites", body);
				if (r.statusCode() != 200) {
					said = Message.lazarus().error(r.body()).build();
				} else if (cancel != null) {
					JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
					said = Message.lazarus().success("Cancelled ").highlight(o.get("cancelled").getAsString()).success(". ")
							.highlight(o.get("byName").getAsString()).success(" can make a new one.").build();
				} else {
					JsonArray codes = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("codes");
					Message m = Message.lazarus().info(codes.isEmpty() ? "No unused invites." : "Unused invites (" + codes.size() + ")");
					for (JsonElement e : codes) {
						JsonObject c = e.getAsJsonObject();
						String code = c.get("code").getAsString();
						long minutes = (System.currentTimeMillis() - c.get("at").getAsLong()) / 60000;
						String age = minutes < 60 ? minutes + "m" : minutes < 1440 ? minutes / 60 + "h" : minutes / 1440 + "d";
						m.line().highlight(code).info(" by ").highlight(c.get("byName").getAsString()).info(", " + age + " ago ")
								.command("Cancel", "Click to cancel " + code, "/lazarus invites cancel " + code);
					}
					said = m.build();
				}
			} catch (Exception e) {
				said = unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	/** {@code /lazarus redeem <code>}: activates this account with an invite code; runs {@code activated} on the game thread if it worked. */
	public static void redeem(String code, Runnable activated) {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			boolean ok = false;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("code", code);
				body.addProperty("name", mc.getUser().getName());
				HttpResponse<String> r = post("/redeem", body);
				ok = r.statusCode() == 200 && Access.activate(JsonParser.parseString(r.body()).getAsJsonObject().get("pass").getAsString());
				said = ok ? Message.lazarus().success("You're in. Welcome to Lazarus.").build()
						: Message.lazarus().error(r.statusCode() == 200 ? "That reply couldn't be verified. Try again, or ask Lauchy." : r.body()).build();
			} catch (Exception e) {
				said = unreachable(e);
			}
			Component message = said;
			boolean worked = ok;
			mc.execute(() -> {
				Message.send(message);
				if (worked) activated.run();
			});
		});
	}

	/** {uuid, current name} for a name or a UUID, from Mojang; null when there's no such account. */
	private static String[] account(String who) throws Exception {
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

	/** The current name for each listed player (names change); the saved one when Mojang can't be asked. */
	private static void freshNames(JsonArray players) {
		for (JsonElement e : players) {
			JsonObject p = e.getAsJsonObject();
			try {
				String[] a = account(p.get("uuid").getAsString());
				if (a != null) p.addProperty("name", a[1]);
			} catch (Exception ignored) {
			}
		}
	}

	private static String name(JsonObject p) {
		return (p.has("name") && !p.get("name").isJsonNull() ? p.get("name") : p.get("uuid")).getAsString();
	}

	/** The reply when the worker can't be asked: what to do, with the technical reason in grey after it. */
	public static Component unreachable(Exception e) {
		return Message.lazarus().error("Lazarus couldn't reach its server. Try again in a moment.").muted(" " + e.getMessage()).build();
	}

	private static Component whitelistReply(JsonObject o) {
		if (o.has("added")) return Message.lazarus().success("Added ").highlight(o.getAsJsonObject("added").get("name").getAsString()).success(" to the whitelist.").build();
		if (o.has("removed")) {
			StringBuilder names = new StringBuilder();
			for (JsonElement e : o.getAsJsonArray("removed")) {
				JsonObject p = e.getAsJsonObject();
				names.append(names.isEmpty() ? "" : ", ").append(name(p));
			}
			return Message.lazarus().success("Removed ").highlight(names.toString()).success(" from the whitelist.").build();
		}
		JsonArray players = o.getAsJsonArray("players");
		freshNames(players);
		Message m = Message.lazarus().info("Whitelist (" + players.size() + (players.size() == 1 ? " player" : " players") + " and you)");
		for (JsonElement e : players) {
			JsonObject p = e.getAsJsonObject();
			m.line().highlight(name(p));
			if (p.has("invitedByName")) m.info(", invited by " + p.get("invitedByName").getAsString());
		}
		return m.build();
	}

	public static boolean configured() {
		return !URLS.isEmpty();
	}

	/** GETs one of the worker's public paths (the item index, shared data). Blocks; throws when it can't be reached. */
	public static HttpResponse<String> get(String path) throws Exception {
		return send(url -> HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Lazarus").GET().build());
	}

	/** POSTs the body with proof of the signed-in account. Blocks; throws when either side can't be reached. */
	public static HttpResponse<String> post(String path, JsonObject body) throws Exception {
		signed(path, body);
		return send(url -> HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Lazarus").header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
	}

	/** The live connection's address: the first worker address, as a WebSocket. */
	static URI liveAddress() {
		return URI.create(URLS.getFirst().replaceFirst("^http", "ws") + "/live");
	}

	/**
	 * Adds proof of the signed-in account to {@code body}, for {@code path}: the Mojang player certificate,
	 * and the request signed with it. Blocks while Minecraft fetches the certificate.
	 */
	static JsonObject signed(String path, JsonObject body) throws Exception {
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		ProfileKeyPair pair = mc.getProfileKeyPairManager().prepareKeyPair().get(15, TimeUnit.SECONDS)
				.orElseThrow(() -> new IllegalStateException("no Mojang player certificate for this account"));
		ProfilePublicKey.Data cert = pair.publicKey().data();
		String challenge = "lazarus|" + path + "|" + System.currentTimeMillis();
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(pair.privateKey());
		signer.update(challenge.getBytes(StandardCharsets.UTF_8));
		Base64.Encoder b64 = Base64.getEncoder();
		body.addProperty("uuid", user.getProfileId().toString());
		body.addProperty("expiresAt", cert.expiresAt().toEpochMilli());
		body.addProperty("publicKey", b64.encodeToString(cert.key().getEncoded()));
		body.addProperty("keySignature", b64.encodeToString(cert.keySignature()));
		body.addProperty("challenge", challenge);
		body.addProperty("signature", b64.encodeToString(signer.sign()));
		return body;
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
				last = CLIENT.send(request.apply(url), HttpResponse.BodyHandlers.ofString());
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

	static boolean validPass(String pass, String uuid) {
		if (pass == null || uuid == null || PUBLIC_KEY.isEmpty()) return false;
		int dot = pass.lastIndexOf('.');
		if (dot < 0) return false;
		String payload = pass.substring(0, dot);
		String[] parts = payload.split("\\|");
		try {
			if (parts.length != 2 || !parts[0].equals(uuid.replace("-", "").toLowerCase()) || Long.parseLong(parts[1]) < System.currentTimeMillis()) return false;
			PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
			Signature sig = Signature.getInstance("Ed25519");
			sig.initVerify(key);
			sig.update(payload.getBytes(StandardCharsets.UTF_8));
			return sig.verify(Base64.getDecoder().decode(pass.substring(dot + 1)));
		} catch (Exception e) {
			return false;
		}
	}
}
