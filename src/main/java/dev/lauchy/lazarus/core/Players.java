package dev.lauchy.lazarus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The other players on this server: who is on the list, and who the crosshair is on. */
public final class Players {
	/** A real account name. */
	public static final Pattern NAME = Pattern.compile("\\w{1,16}");

	private Players() {
	}

	/** Your own name, or "" outside a game. */
	public static String me() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? "" : mc.player.getName().getString();
	}

	/** The names on the server's player list right now, in no particular order; empty outside a server. */
	public static List<String> online() {
		Minecraft mc = Minecraft.getInstance();
		List<String> names = new ArrayList<>();
		if (mc.getConnection() == null) return names;
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (NAME.matcher(name).matches()) names.add(name);
		}
		return names;
	}

	/**
	 * The real player your crosshair is on, ignoring blocks, up to 512 blocks away, or null. Each
	 * hitbox is padded a little more the further it is, so distant players are easy to catch. NPCs
	 * aren't on the server's player list, so they are skipped.
	 */
	public static String lookedAt(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.getConnection() == null) return null;
		Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1f);
		Vec3 end = eye.add(dir.scale(512));
		String best = null;
		double bestD = Double.MAX_VALUE;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player || p.isSpectator() || mc.getConnection().getPlayerInfo(p.getUUID()) == null) continue;
			String name = p.getGameProfile().name();
			if (!NAME.matcher(name).matches()) continue;
			double d = eye.distanceTo(p.position());
			AABB box = p.getBoundingBox().inflate(0.2 + d * 0.008);
			var hit = box.clip(eye, end);
			if (hit.isEmpty() && !box.contains(eye)) continue;
			if (d < bestD) {
				bestD = d;
				best = name;
			}
		}
		return best;
	}
}
