package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Who's online with the mod and which badge each shows, as the Lazarus server's live connection tells
 * it: a snapshot of everyone on connecting, then joins, leaves, and badge and cape changes; or, while the
 * connection is down, the answer to each check-in. The player's own Lazarus+ (in the snapshot, each check-in
 * and each update) is handed to {@link #onPlus}'s listener, and every answer to {@link #onContact}'s. Messages of kinds this
 * doesn't know are passed over, so the server can add more. Safe to read from any thread.
 */
public final class Live {
	/** The badge before a name: none for someone not online with the mod. */
	public enum Badge { NONE, GREY, PURPLE, GOLD }

	/** One player online with the mod, and the cape they wear ("" for none). */
	private record Online(String name, Badge badge, String cape) {
	}

	private volatile Map<UUID, Online> online = Map.of();
	/** The Lazarus users among them by lower-case name; nobody grey, whose name nothing vouches for. */
	private volatile Map<String, UUID> names = Map.of();
	/** Who hears this player's own Lazarus+ updates. */
	private volatile Consumer<JsonObject> plus = o -> {
	};
	/** Who hears that the server answered. */
	private volatile Runnable contact = () -> {
	};

	/**
	 * Runs on each update about this player's own Lazarus+ (a purchase, a refund, the owner's command),
	 * with the same fields as {@code /plus/me}: the signed receipt and any messages to show.
	 */
	public void onPlus(Consumer<JsonObject> listener) {
		plus = listener;
	}

	/** Runs whenever the server is heard from (any message, a "pong", a check-in's answer), which keeps Lazarus+ in touch. */
	public void onContact(Runnable listener) {
		contact = listener;
	}

	/** The server answered with nothing to apply, such as a "pong". */
	public void reached() {
		contact.run();
	}

	/** Applies one message from the server; false when it isn't one this knows. */
	public synchronized boolean apply(String message) {
		JsonObject o = parse(message);
		if (o == null) return false;
		reached();
		return apply(o);
	}

	/**
	 * Applies the answer to a check-in, sent while the connection is down: everyone online and, for a
	 * Lazarus user, their own Lazarus+, just as the snapshot on connecting. False when it isn't one.
	 */
	public synchronized boolean checkedIn(String answer) {
		JsonObject o = parse(answer);
		if (o == null) return false;
		reached();
		o.addProperty("type", "snapshot");
		return apply(o);
	}

	private static JsonObject parse(String message) {
		try {
			return JsonParser.parseString(message).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private boolean apply(JsonObject o) {
		if (string(o, "type").equals("plus")) {
			plus.accept(o);
			return true;
		}
		Map<UUID, Online> next = new HashMap<>(online);
		switch (string(o, "type")) {
			case "snapshot" -> {
				next.clear();
				if (o.has("online") && o.get("online").isJsonArray()) {
					for (JsonElement e : o.getAsJsonArray("online")) if (e.isJsonObject()) add(next, e.getAsJsonObject());
				}
				// A Lazarus user's own receipt and messages, so a change missed while out of touch arrives.
				if (o.has("plusReceipt") || o.has("plusPass") || o.has("messages")) plus.accept(o);
			}
			case "join" -> add(next, o);
			case "leave" -> next.remove(id(string(o, "id")));
			case "badge" -> {
				UUID id = id(string(o, "id"));
				Online was = next.get(id);
				if (was != null) next.put(id, new Online(was.name(), badge(string(o, "kind")), was.cape()));
			}
			case "cape" -> {
				UUID id = id(string(o, "id"));
				Online was = next.get(id);
				if (was != null) next.put(id, new Online(was.name(), was.badge(), string(o, "cape")));
			}
			default -> {
				return false;
			}
		}
		Map<String, UUID> byName = new HashMap<>();
		// Mojang signs the account but not the name, so someone grey could claim a name: a Lazarus user's name wins
		// over a grey one, and a grey name only ever earns a grey mark.
		next.forEach((id, p) -> {
			if (p.name().isEmpty()) return;
			String key = p.name().toLowerCase(Locale.ROOT);
			UUID held = byName.get(key);
			if (held == null || (p.badge() != Badge.GREY && badge(next, held) == Badge.GREY)) byName.put(key, id);
		});
		online = Map.copyOf(next);
		names = Map.copyOf(byName);
		return true;
	}

	private static Badge badge(Map<UUID, Online> in, UUID id) {
		Online p = in.get(id);
		return p == null ? Badge.NONE : p.badge();
	}

	/** The badge this account shows now. */
	public Badge badge(UUID id) {
		Online p = id == null ? null : online.get(id);
		return p == null ? Badge.NONE : p.badge();
	}

	/** The cape this account wears now: "" for none, or for someone not online with the mod. */
	public String cape(UUID id) {
		Online p = id == null ? null : online.get(id);
		return p == null ? "" : p.cape();
	}

	/** Who is online with the mod under this name, in any case; null when nobody is. A Lazarus user wins the name over someone grey claiming it. */
	public UUID named(String name) {
		return names.get(name.toLowerCase(Locale.ROOT));
	}

	private static void add(Map<UUID, Online> to, JsonObject entry) {
		UUID id = id(string(entry, "id"));
		if (id != null) to.put(id, new Online(string(entry, "name"), badge(string(entry, "kind")), string(entry, "cape")));
	}

	/** A kind this doesn't know still shows the person is online with the mod. */
	private static Badge badge(String kind) {
		return switch (kind) {
			case "purple" -> Badge.PURPLE;
			case "gold" -> Badge.GOLD;
			default -> Badge.GREY;
		};
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** The server's UUIDs come without dashes; null for anything else. */
	public static UUID id(String s) {
		if (s.length() != 32) return null;
		try {
			return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16), 16));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
