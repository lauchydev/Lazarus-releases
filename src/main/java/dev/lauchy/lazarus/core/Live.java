package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

	/** One player online with the mod, the cape they wear ("" for none) and their join line ("" for none). */
	private record Online(String name, Badge badge, String cape, String line) {
	}

	/** Someone who just connected with the mod: who, their badge, and the join line they wrote ("" for none). */
	public record Joined(UUID id, String name, Badge badge, String line) {
	}

	private volatile Map<UUID, Online> online = Map.of();
	/** Everyone online with the mod by lower-case name: a name is only what a mod said, so two can claim the same one. */
	private volatile Map<String, Set<UUID>> names = Map.of();
	/** Who hears this player's own Lazarus+ updates. */
	private volatile Consumer<JsonObject> plus = o -> {
	};
	/** Who hears that the server answered. */
	private volatile Runnable contact = () -> {
	};
	/** Who hears that someone connected with the mod. */
	private volatile Consumer<Joined> join = j -> {
	};

	/** Runs, on the connection's thread, whenever someone connects with the mod (not for those already on when this player connects). */
	public void onJoin(Consumer<Joined> listener) {
		join = listener;
	}

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
			case "join" -> {
				add(next, o);
				UUID id = id(string(o, "id"));
				if (id != null) join.accept(new Joined(id, string(o, "name"), badge(string(o, "kind")), string(o, "line")));
			}
			case "leave" -> next.remove(id(string(o, "id")));
			case "badge" -> {
				UUID id = id(string(o, "id"));
				Online was = next.get(id);
				if (was != null) next.put(id, new Online(was.name(), badge(string(o, "kind")), was.cape(), was.line()));
			}
			case "cape" -> {
				UUID id = id(string(o, "id"));
				Online was = next.get(id);
				if (was != null) next.put(id, new Online(was.name(), was.badge(), string(o, "cape"), was.line()));
			}
			default -> {
				return false;
			}
		}
		Map<String, Set<UUID>> byName = new HashMap<>();
		next.forEach((id, p) -> {
			if (!p.name().isEmpty()) byName.computeIfAbsent(p.name().toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(id);
		});
		byName.replaceAll((k, ids) -> Set.copyOf(ids));
		online = Map.copyOf(next);
		names = Map.copyOf(byName);
		return true;
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

	/**
	 * Who is online with the mod under this name, in any case, going only by a claim the game backs: the account must be one
	 * {@code tabIds} holds, the accounts the game's own tab list has under that name. Anyone can send any name with their own
	 * account, so a claim the tab list doesn't back is ignored. Null when no claim is backed.
	 */
	public UUID named(String name, Set<UUID> tabIds) {
		if (tabIds == null || tabIds.isEmpty()) return null;
		for (UUID id : names.getOrDefault(name.toLowerCase(Locale.ROOT), Set.of())) if (tabIds.contains(id)) return id;
		return null;
	}

	private static void add(Map<UUID, Online> to, JsonObject entry) {
		UUID id = id(string(entry, "id"));
		if (id != null) to.put(id, new Online(string(entry, "name"), badge(string(entry, "kind")), string(entry, "cape"), string(entry, "line")));
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
