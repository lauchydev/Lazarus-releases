package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import net.minecraft.client.Minecraft;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

/**
 * What Lazarus users learn in game and share through the Lazarus server ({@code /shared/<kind>}),
 * so each starts from everyone's findings instead of only their own: item prices, recipes, quiz
 * answers, the blood room's wait, Revenant spawn tiles. Each kind is fetched now and again, and
 * what this player finds is sent a few seconds later, batched with whatever else turns up by then.
 * The server merges findings by each kind's rule and takes them only from whitelisted players.
 */
public final class Shared {
	/** How long a finding waits to be sent, so those that turn up together go together. */
	private static final long SEND_AFTER_S = 10;
	/** How many findings one upload carries; the server takes up to 200. */
	private static final int PER_POST = 200;
	/** The one thread that fetches and sends, off the game thread. */
	private static final ScheduledExecutorService RUNNER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Lazarus shared data");
		t.setDaemon(true);
		return t;
	});
	/** Findings waiting to be sent, by kind, then key. */
	private static final Map<String, Map<String, JsonObject>> pending = new ConcurrentHashMap<>();

	private Shared() {
	}

	/** One shared entry: its value and when it last changed (ms). */
	public record Entry(JsonObject value, long at) {
	}

	/**
	 * Fetches every entry of {@code kind} now and then every {@code every}, handing them by key
	 * to {@code use} on the game thread. Nothing is handed over when the server can't be reached.
	 */
	public static void follow(String kind, Duration every, Consumer<Map<String, Entry>> use) {
		every(every, () -> {
			Map<String, Entry> got = fetch(kind);
			if (got != null) Minecraft.getInstance().execute(() -> use.accept(got));
		});
	}

	/** Sends one finding; a later one for the same key before it goes replaces it. */
	public static void send(String kind, String key, JsonObject value) {
		send(kind, key, value, (old, now) -> now);
	}

	/** Sends one finding; {@code merge} combines it with one for the same key still waiting to go. */
	public static void send(String kind, String key, JsonObject value, BinaryOperator<JsonObject> merge) {
		if (!LazarusServer.configured() || key == null || key.isEmpty()) return;
		Map<String, JsonObject> waiting = pending.computeIfAbsent(kind, k -> new ConcurrentHashMap<>());
		boolean first = waiting.isEmpty();
		waiting.merge(key, value, merge);
		if (first) later(SEND_AFTER_S, () -> flush(kind));
	}

	/** Every entry of {@code kind} on the server, or null when it can't be reached. */
	private static Map<String, Entry> fetch(String kind) {
		if (!LazarusServer.configured()) return null;
		try {
			HttpResponse<String> r = LazarusServer.get("/shared/" + kind);
			if (r.statusCode() != 200) return null;
			Map<String, Entry> out = new HashMap<>();
			for (var e : JsonParser.parseString(r.body()).getAsJsonObject().entrySet()) {
				if (e.getValue() instanceof JsonObject o && o.get("v") instanceof JsonObject v) {
					out.put(e.getKey(), new Entry(v, o.has("at") ? o.get("at").getAsLong() : 0));
				}
			}
			return out;
		} catch (Exception e) {
			Lazarus.LOG.debug("Could not fetch shared {}: {}", kind, e.getMessage());
			return null;
		}
	}

	/** Sends the findings of {@code kind} waiting to go, and schedules the rest when there are too many for one upload. */
	private static void flush(String kind) {
		Map<String, JsonObject> waiting = pending.get(kind);
		if (waiting == null) return;
		JsonObject entries = new JsonObject();
		for (String key : waiting.keySet()) {
			if (entries.size() >= PER_POST) break;
			JsonObject v = waiting.remove(key);
			if (v != null) entries.add(key, v);
		}
		if (!waiting.isEmpty()) later(SEND_AFTER_S, () -> flush(kind));
		if (entries.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.add("entries", entries);
		try {
			HttpResponse<String> r = LazarusServer.post("/shared/" + kind, body);
			if (r.statusCode() != 200) Lazarus.LOG.debug("The server didn't take shared {}: {}", kind, r.statusCode());
		} catch (Exception e) {
			Lazarus.LOG.debug("Could not send shared {}: {}", kind, e.getMessage());
		}
	}

	/** A JSON body read from the server, for callers with their own paths ({@code /items}); null when it can't be read. */
	static JsonElement getJson(String path) {
		try {
			HttpResponse<String> r = LazarusServer.get(path);
			return r.statusCode() == 200 ? JsonParser.parseString(r.body()) : null;
		} catch (Exception e) {
			Lazarus.LOG.debug("Could not fetch {}: {}", path, e.getMessage());
			return null;
		}
	}

	/** Runs {@code task} on the shared data thread, now and then every {@code every}. */
	static void every(Duration every, Runnable task) {
		RUNNER.scheduleWithFixedDelay(() -> safely(task), 5, every.toSeconds(), TimeUnit.SECONDS);
	}

	/** Runs {@code task} on the shared data thread {@code seconds} from now. */
	static void later(long seconds, Runnable task) {
		RUNNER.schedule(() -> safely(task), seconds, TimeUnit.SECONDS);
	}

	/** Runs {@code task}, logging what it throws: a scheduled task that throws is never run again. */
	private static void safely(Runnable task) {
		try {
			task.run();
		} catch (RuntimeException e) {
			Lazarus.LOG.warn("Shared data task failed", e);
		}
	}
}
