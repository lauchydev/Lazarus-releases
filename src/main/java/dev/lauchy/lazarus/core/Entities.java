package dev.lauchy.lazarus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/** Finding Dragsim mobs by their name tags. */
public final class Entities {
	private Entities() {
	}

	/** An entity's shown name: its custom name, or a text display's text. Plain text. */
	public static String name(Entity e) {
		if (e instanceof Display.TextDisplay td) return Text.plain(td.getText());
		Component c = e.getCustomName();
		return c == null ? "" : Text.plain(c);
	}

	/**
	 * The mob a name tag belongs to: the tagged entity itself if it's a mob of the wanted
	 * kind, else the nearest such mob just below the tag (servers float tags on stands).
	 */
	public static Entity owner(Entity tag, Predicate<Entity> kind) {
		if (kind.test(tag)) return tag;
		Minecraft mc = Minecraft.getInstance();
		Entity best = null;
		double bestD = 9;
		for (Entity e : mc.level.getEntities(tag, new AABB(tag.position(), tag.position()).inflate(2, 4, 2))) {
			if (!kind.test(e)) continue;
			double d = e.position().distanceToSqr(tag.position());
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Nearest non-player entity whose name contains {@code text} and passes {@code also}, or null. */
	public static Entity nearestNamed(String text, Predicate<String> also, double range) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		Entity best = null;
		double bestD = range * range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Player) continue;
			String n = name(e);
			if (n.isEmpty() || !n.contains(text) || !also.test(n)) continue;
			double d = e.distanceToSqr(mc.player);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}
}
