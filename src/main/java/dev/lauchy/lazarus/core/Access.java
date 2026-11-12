package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether Lazarus runs for this account. Everyone with the mod may use it; only an account the admins blocked
 * is turned away. {@link #blocked()} says hello to the Lazarus worker, which answers whether the account is
 * blocked along with its Lazarus+ and the owner's flags, and the answer is saved so the same holds while the
 * worker can't be reached. With no saved answer the mod runs. Dev workspaces (including CI's game test) always run.
 */
public final class Access {
	private Access() {
	}

	/** The worker's last word on this account: "open" or "blocked". */
	private static final Path SAVED = Config.dir().resolve("access.txt");
	/** Whether the worker turned this install's key away: the account's key belongs to another install. */
	private static volatile boolean keyTaken;

	/** Whether the worker refused this install's key because the account already has another; server features are off until an admin resets it. */
	public static boolean keyTaken() {
		return keyTaken;
	}

	/**
	 * Whether an admin blocked this account. Asks the worker (blocks briefly), and falls back to the last answer
	 * when it can't be reached, or to running when there has never been one. Runs once the client has started.
	 */
	public static boolean blocked() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false;
		String uuid = String.valueOf(Minecraft.getInstance().getUser().getProfileId());
		try {
			// The whitelist pass older versions saved means nothing now.
			Files.deleteIfExists(Config.dir().resolve("pass.txt"));
		} catch (IOException ignored) {
		}
		try {
			JsonObject body = new JsonObject();
			body.addProperty("name", Minecraft.getInstance().getUser().getName());
			HttpResponse<String> r = LazarusServer.post("/hello", body);
			if (r.statusCode() == 200) {
				JsonObject answer = JsonParser.parseString(r.body()).getAsJsonObject();
				boolean blocked = answer.has("blocked") && answer.get("blocked").getAsBoolean();
				save(blocked);
				if (!blocked) {
					Plus.read(answer, uuid);
					LazarusServer.admin(answer);
				}
				Lazarus.LOG.info("Lazarus: this account is {}", blocked ? "blocked" : "open");
				return blocked;
			}
			// The account already has a key from another install: the mod still runs, without what the server shares.
			if (r.statusCode() == 409) {
				keyTaken = true;
				Lazarus.LOG.info("Lazarus: this account's key belongs to another install: {}", r.body());
			} else {
				Lazarus.LOG.info("Lazarus: the server answered HTTP {} to hello: {}", r.statusCode(), r.body());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			Lazarus.LOG.info("Could not reach the Lazarus server, going by the last answer: {}", e.toString());
		}
		Plus.offline(uuid);
		return saved();
	}

	private static void save(boolean blocked) {
		try {
			Files.createDirectories(SAVED.getParent());
			Files.writeString(SAVED, blocked ? "blocked" : "open");
		} catch (IOException e) {
			Lazarus.LOG.warn("Could not save the Lazarus server's answer: {}", e.toString());
		}
	}

	/** The last answer saved; not blocked when there's none. */
	private static boolean saved() {
		try {
			return Files.exists(SAVED) && Files.readString(SAVED).trim().equals("blocked");
		} catch (IOException e) {
			return false;
		}
	}
}
