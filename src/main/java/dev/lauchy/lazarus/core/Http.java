package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

/** GETs against dragsim.co's public JSON, off the game thread. */
public final class Http {
	public static final String SITE = "https://dragsim.co";
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final String AGENT = "Lazarus/" + FabricLoader.getInstance().getModContainer(Lazarus.ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");

	private Http() {
	}

	/** The parsed body of {@code SITE + path}; fails on anything but a 200. */
	public static CompletableFuture<JsonElement> json(String path) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + path))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", AGENT)
				.header("Accept", "application/json")
				.GET().build();
		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException(path + " returned HTTP " + r.statusCode());
			return JsonParser.parseString(r.body());
		});
	}

	public static String encode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** Epoch milliseconds from a JSON time that may be epoch seconds, epoch millis or ISO-8601; 0 if none. */
	public static long time(JsonElement e) {
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return 0;
		String s = e.getAsString().trim().replace(' ', 'T');
		if (e.getAsJsonPrimitive().isNumber() || s.matches("\\d+(\\.\\d+)?")) {
			double v = Double.parseDouble(s);
			return (long) (v < 1e11 ? v * 1000 : v);
		}
		try {
			return Instant.parse(s).toEpochMilli();
		} catch (DateTimeParseException ex) {
			try { // no zone given: the site's times are UTC
				return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli();
			} catch (DateTimeParseException ex2) {
				return 0;
			}
		}
	}
}
