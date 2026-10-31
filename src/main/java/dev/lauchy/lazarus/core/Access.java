package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.Lazarus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Who may use Lazarus. {@link #verified()} has Mojang confirm the signed-in account and the
 * Lazarus worker ({@link LazarusServer}) check its private list, so a faked UUID or name gets
 * nowhere. No list of players is built into the jar. Dev workspaces (including CI's game
 * test) are always allowed.
 */
public final class Access {
	private Access() {
	}

	private static final Path PASS = Config.dir().resolve("pass.txt");
	/** Why the last {@link #verified()} said no, for the message in game; empty when it didn't. */
	private static String reason = "";

	public static String reason() {
		return reason;
	}

	/** Keeps the pass the worker gave for redeeming an invite, if it's real; whether it was. */
	static boolean activate(String pass) throws IOException {
		if (!LazarusServer.validPass(pass, String.valueOf(Minecraft.getInstance().getUser().getProfileId()))) return false;
		Files.createDirectories(PASS.getParent());
		Files.writeString(PASS, pass);
		reason = "";
		return true;
	}

	/**
	 * Whether Mojang and the Lazarus worker agree the signed-in account is on the whitelist.
	 * When the worker can't be reached, a pass it signed for this account in the last few
	 * days still counts; a pass can't be forged, since only the worker holds the key. Needs
	 * Minecraft's signed-in user, so it runs once the client has started. Blocks briefly.
	 */
	public static boolean verified() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
		User user = Minecraft.getInstance().getUser();
		String uuid = String.valueOf(user.getProfileId());
		try {
			HttpResponse<String> r = LazarusServer.post("/verify", new JsonObject());
			if (r.statusCode() == 200) {
				JsonObject answer = JsonParser.parseString(r.body()).getAsJsonObject();
				String pass = answer.get("pass").getAsString();
				if (LazarusServer.validPass(pass, uuid)) {
					Files.createDirectories(PASS.getParent());
					Files.writeString(PASS, pass);
					Plus.read(answer, uuid);
					LazarusServer.admin(answer);
					Lazarus.LOG.info("Lazarus whitelist: {} ({}) is allowed", user.getName(), uuid);
					return true;
				}
				reason = "the server's pass didn't check out";
			} else if (r.statusCode() == 403) {
				reason = r.body();
				Files.deleteIfExists(PASS);
				Lazarus.LOG.info("Lazarus whitelist: {} ({}) is not allowed: {}", user.getName(), uuid, r.body());
				return false;
			} else {
				reason = "the Lazarus server answered HTTP " + r.statusCode();
			}
			Lazarus.LOG.info("Lazarus whitelist: {}", reason);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			reason = "could not reach the Lazarus server (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
			Lazarus.LOG.info("Could not reach the Lazarus server, checking the saved pass: {}", e.toString());
		}
		try {
			boolean ok = Files.exists(PASS) && LazarusServer.validPass(Files.readString(PASS).trim(), uuid);
			Lazarus.LOG.info("Lazarus whitelist: saved pass for {} is {}", user.getName(), ok ? "valid" : "missing or expired");
			if (ok) Plus.offline(uuid);
			return ok;
		} catch (IOException e) {
			return false;
		}
	}
}
