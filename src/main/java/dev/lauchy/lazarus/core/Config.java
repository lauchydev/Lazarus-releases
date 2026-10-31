package dev.lauchy.lazarus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * config/lazarus/settings.json: every feature's settings plus HUD placement. Written to a
 * temp file and moved into place, so a crash mid-write never leaves a half file behind.
 */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(Lazarus.ID);
	private static final Path FILE = DIR.resolve("settings.json");

	private Config() {
	}

	public static Path dir() {
		return DIR;
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			JsonObject features = obj(root, "features");
			for (Feature f : Features.all()) {
				JsonObject saved = obj(features, f.id);
				loadInto(saved, f.enabledSetting());
				loadInto(saved, f.toggleKey());
				for (Setting<?> s : f.settings()) loadInto(saved, s);
			}
			Hud.load(obj(root, "hud"));
			Favourites.load(root.get("favourites"));
			Categories.load(root.get("categoriesOff"));
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not read {}, using defaults", FILE, e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		JsonObject features = new JsonObject();
		for (Feature f : Features.all()) {
			JsonObject out = new JsonObject();
			out.add(f.enabledSetting().key, f.enabledSetting().save());
			out.add(f.toggleKey().key, f.toggleKey().save());
			for (Setting<?> s : f.settings()) out.add(s.key, s.save());
			features.add(f.id, out);
		}
		root.add("features", features);
		root.add("hud", Hud.save());
		root.add("favourites", Favourites.save());
		root.add("categoriesOff", Categories.save());
		writeJson(FILE, root);
	}

	/**
	 * Runs a tracker's save once a minute as well as on quit, so a crash or a killed game
	 * loses at most a minute of all-time totals. The save itself skips when nothing changed.
	 */
	public static void autosave(Runnable save) {
		int[] ticks = {0};
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++ticks[0] % 1200 == 0) save.run();
		});
	}

	/**
	 * Runs a tracker's save whenever its totals could otherwise be lost: once a minute, on leaving
	 * a server, and when the game closes. The save itself skips when nothing changed.
	 */
	public static void saveOn(Runnable save) {
		autosave(save);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> save.run());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> save.run());
	}

	/** Shared by every store under config/lazarus. */
	public static void writeJson(Path file, JsonElement json) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(json));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Lazarus.LOG.warn("Could not write {}", file, e);
		}
	}

	public static JsonElement readJson(Path file) {
		try {
			return Files.exists(file) ? JsonParser.parseString(Files.readString(file)) : null;
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not read {}", file, e);
			return null;
		}
	}

	private static void loadInto(JsonObject saved, Setting<?> s) {
		JsonElement v = saved.get(s.key);
		if (v != null) s.load(v);
	}

	private static JsonObject obj(JsonObject parent, String key) {
		JsonElement e = parent.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}
}
