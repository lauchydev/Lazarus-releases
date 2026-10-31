package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The mod's one open connection to the Lazarus server (the worker's {@code /live}), for every player
 * with the mod, whitelisted or not. What it hears goes into {@link #LIVE}, which the badges and capes read. When
 * it drops it connects again, waiting longer after each failure (up to a minute), and meanwhile checks in
 * every minute so badges, capes and the player's own Lazarus+ stay current. {@link LivePolicy} decides all of
 * that; this does what it says.
 */
public final class LiveConnection {
	/** Who's online with the mod and their badges, as the connection last told it. */
	public static final Live LIVE = new Live();
	/** How often the policy is asked what's due. */
	private static final long TICK = 1_000;
	/** Makes the WebSocket connections. */
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	/** The one thread that ticks the policy; nothing on it waits on the network. */
	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Lazarus live connection");
		t.setDaemon(true);
		return t;
	});

	/** What the connection does and when; made when it starts. */
	private static volatile LivePolicy policy;
	/** Whether {@link #start} has run. */
	private static boolean started;

	private LiveConnection() {
	}

	/** Connects, once the game has started; does nothing when called again. */
	public static synchronized void start() {
		if (started || !LazarusServer.configured()) return;
		started = true;
		long start = System.nanoTime();
		policy = new LivePolicy(new Wiring(), () -> (System.nanoTime() - start) / 1_000_000, ThreadLocalRandom.current().nextLong(1000));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> policy.stop());
		TIMER.scheduleAtFixedRate(LiveConnection::tick, 0, TICK, TimeUnit.MILLISECONDS);
	}

	/** Asks the policy what's due; a tick that throws mustn't stop the ones after it. */
	private static void tick() {
		try {
			policy.tick();
		} catch (RuntimeException e) {
			Lazarus.LOG.warn("The live connection to the Lazarus server tripped up", e);
		}
	}

	/** Does what the policy asks in the game: the network calls, and handing what's heard to {@link #LIVE}. */
	private static final class Wiring implements LivePolicy.Actions {
		@Override
		public LivePolicy.Socket connect() {
			Connection c = new Connection();
			CompletableFuture.runAsync(() -> {
				try {
					JsonObject body = new JsonObject();
					body.addProperty("name", Minecraft.getInstance().getUser().getName());
					String auth = LazarusServer.signed("/live", body).toString();
					CLIENT.newWebSocketBuilder().header("x-lazarus-auth", auth).connectTimeout(Duration.ofSeconds(10))
							.buildAsync(LazarusServer.liveAddress(), c)
							.whenComplete((ws, e) -> {
								if (e != null) c.gone("Could not connect to the Lazarus server: " + e);
							});
				} catch (Exception e) {
					c.gone("Could not connect to the Lazarus server: " + e);
				}
			});
			return c;
		}

		/**
		 * Asks the server what the connection would have said ({@code /live/checkin}): everyone online and this
		 * player's own Lazarus+. That also keeps this player online with the mod for everyone else.
		 */
		@Override
		public void checkIn() {
			CompletableFuture.runAsync(() -> {
				try {
					JsonObject body = new JsonObject();
					body.addProperty("name", Minecraft.getInstance().getUser().getName());
					HttpResponse<String> r = LazarusServer.post("/live/checkin", body);
					if (r.statusCode() == 200 && !policy.connected()) LIVE.checkedIn(r.body());
				} catch (Exception e) {
					Lazarus.LOG.info("Could not check in with the Lazarus server: {}", e.toString());
				}
			});
		}

		@Override
		public void apply(String message) {
			LIVE.apply(message);
		}

		@Override
		public void reached() {
			LIVE.reached();
		}
	}

	/** One connection to the server, from the handshake on: it tells the policy what happens to it, and collects each message (which can come in parts). */
	private static final class Connection implements LivePolicy.Socket, WebSocket.Listener {
		/** The message coming in, so far. */
		private final StringBuilder parts = new StringBuilder();
		/** The open connection; null until the handshake finishes. */
		private volatile WebSocket ws;
		/** Whether the policy closed it, perhaps before it opened. */
		private volatile boolean closing;
		/** The last message sent, which the next waits for: a WebSocket sends one at a time. */
		private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

		@Override
		public synchronized void send(String text) {
			WebSocket w = ws;
			if (w == null || w.isOutputClosed()) return;
			sending = sending.handle((x, e) -> null).thenCompose(x -> w.sendText(text, true));
		}

		/** Says goodbye and drops the connection without waiting for the server's answer, which a dead connection never sends. */
		@Override
		public void close() {
			closing = true;
			WebSocket w = ws;
			if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(5, TimeUnit.SECONDS).whenComplete((x, e) -> w.abort());
		}

		@Override
		public void onOpen(WebSocket ws) {
			this.ws = ws;
			if (closing) {
				close();
				return;
			}
			policy.opened(this);
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			parts.append(data);
			if (last) {
				String message = parts.toString();
				parts.setLength(0);
				policy.heard(this, message);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
			gone("The Lazarus server closed the live connection (" + status + ")");
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			gone("The live connection to the Lazarus server failed: " + error);
		}

		/** The connection closed, failed or couldn't be made. */
		void gone(String why) {
			Lazarus.LOG.info(why);
			policy.closed(this);
		}
	}
}
