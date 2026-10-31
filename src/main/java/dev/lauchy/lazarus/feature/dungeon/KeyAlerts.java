package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Wither or Blood Key lying in the dungeon: a toast and sound when one appears, and a
 * box on it until someone picks it up. The server says nothing when a key drops, so it
 * is found by name in the world, usually a label over an invisible stand wearing it. The
 * box is hidden by walls like any block: easier to see, not seen through the dungeon.
 */
public final class KeyAlerts extends Feature {
	enum Key {
		WITHER("Wither Key", 0xFF202020, "§8§lWITHER KEY"), BLOOD("Blood Key", 0xFFFF3030, "§c§lBLOOD KEY");

		final String name;
		final int color;
		final String alert;

		Key(String name, int color, String alert) {
			this.name = name;
			this.color = color;
			this.alert = alert;
		}
	}

	private final BoolSetting alert = add(new BoolSetting("alert", "Alert", "Toast and sound when a key appears.", true));
	private final BoolSetting outline = add(new BoolSetting("outline", "Outline keys", "Box the key so it is easier to spot.", true));

	/** Carrier entity id to the key it holds, refreshed four times a second. */
	private Map<Integer, Key> keys = new HashMap<>();
	private int ticks;

	public KeyAlerts() {
		super("keyAlerts", "Key Alerts", "Alerts when a Wither or Blood Key drops and outlines it.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || mc.level == null || !Area.inDungeon()) {
				keys.clear();
				return;
			}
			if (++ticks % 5 != 0) return;
			Map<Integer, Key> now = new HashMap<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				String n = Entities.name(e);
				for (Key k : Key.values()) {
					if (n.contains(k.name) && !n.contains(":")) now.putIfAbsent(carrier(mc, e).getId(), k);
				}
			}
			Set<Key> before = EnumSet.noneOf(Key.class);
			keys.values().forEach(before::add);
			for (Key k : now.values()) {
				if (!before.contains(k) && alert.on()) {
					Toast.show(k.alert + " §7dropped", k.color | 0xFF000000);
					if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
					before.add(k);
				}
			}
			keys = now;
		});
		World.paint(() -> {
			if (!enabled() || !outline.on() || keys.isEmpty()) return;
			Minecraft mc = Minecraft.getInstance();
			keys.forEach((id, k) -> {
				Entity e = mc.level.getEntity(id);
				if (e != null) World.box(keyBox(e), k.color, true, true, false);
			});
		});
	}

	/** What to box: the stand or item under a floating label, or the labelled thing itself. */
	private static Entity carrier(Minecraft mc, Entity labelled) {
		if (!(labelled instanceof Display)) return labelled;
		Entity best = labelled;
		double bestD = 4;
		for (Entity e : mc.level.getEntities(labelled, labelled.getBoundingBox().inflate(1, 3, 1))) {
			if (!(e instanceof ArmorStand) && !(e instanceof ItemEntity) && !(e instanceof Display.ItemDisplay)) continue;
			double d = e.distanceToSqr(labelled);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** A stand wears the key as its head, so only the top of a stand is boxed. */
	private static AABB keyBox(Entity e) {
		AABB b = e.getBoundingBox();
		if (e instanceof ArmorStand) return new AABB(e.getX() - 0.3, b.maxY - 0.6, e.getZ() - 0.3, e.getX() + 0.3, b.maxY, e.getZ() + 0.3);
		if (b.getSize() < 0.1) return b.inflate(0.3);
		return b.inflate(0.05);
	}
}
