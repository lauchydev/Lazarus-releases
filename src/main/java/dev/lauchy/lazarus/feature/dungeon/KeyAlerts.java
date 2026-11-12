package dev.lauchy.lazarus.feature.dungeon;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
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
 *
 * <p>When someone picks a key up ("Steve has obtained Wither Key!") their name goes big across the screen. When
 * it's you (the server then tells you, and only you, to right-click the door), a trail leads to the nearest door
 * that key opens (a wither door is coal blocks, the blood door red terracotta), until it's opened.
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
	private final BoolSetting pickup = add(new BoolSetting("pickup", "Pickup alert", "Who picked a key up, big across the screen.", true));
	private final ColorSetting witherLine = add(new ColorSetting("witherLine", "Wither door line", "The colour of a line that leads to a wither door.", 0xFF101010));
	private final ColorSetting bloodLine = add(new ColorSetting("bloodLine", "Blood door line", "The colour of a line that leads to the blood door.", 0xFFFF5555));
	private final BoolSetting doorTrail = add(new BoolSetting("doorTrail", "Trail to the door", "When you pick a key up, a line to the door it opens. A trail, so it needs Lazarus+.", true)).plusOnly();

	/** "Steve has obtained Wither Key!", with a rank before the name or not. */
	private static final Pattern OBTAINED = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w{1,16}) has obtained (Wither|Blood) Key!.*");
	/** How far round where the map puts a door its blocks are looked for. */
	private static final int DOOR_SCAN = 7;

	/** The key you're carrying, as the server last said; its trail; and the map pixel of the door it leads to. */
	private static DungeonLayout.Lock held = DungeonLayout.Lock.NONE;
	private final Trail.Search trail = new Trail.Search();
	private Object heldIn;
	/** The door the trail leads to, as the map has it. */
	private DungeonLayout.Door doorOnMap;

	private static KeyAlerts instance;

	/** The colour of a line that leads to a locked door of this kind: its own, so it stands apart from the lines to rooms. */
	static int doorColour(DungeonLayout.Lock lock) {
		KeyAlerts k = instance;
		if (lock == DungeonLayout.Lock.BLOOD) return k == null ? 0xFFFF5555 : k.bloodLine.argb();
		return k == null ? 0xFF101010 : k.witherLine.argb();
	}

	/** Whether the key's trail to its door is showing: the room trails give way to it. */
	private static volatile boolean trailing;

	static boolean trailing() {
		return trailing;
	}

	/** The key you're carrying. */
	static DungeonLayout.Lock held() {
		return held;
	}

	/** Carrier entity id to the key it holds, refreshed four times a second. */
	private Map<Integer, Key> keys = new HashMap<>();
	private int ticks;

	public KeyAlerts() {
		super("keyAlerts", "Key Alerts", "Alerts when a Wither or Blood Key drops and outlines it.", Category.DUNGEONS, true);
		instance = this;
	}

	@Override
	public void init() {
		Dungeon.init();
		Chat.listen(l -> {
			if (!enabled() || !Area.inDungeon()) return;
			String line = l.text();
			Minecraft mc = Minecraft.getInstance();
			Matcher m = OBTAINED.matcher(line);
			if (m.matches() && pickup.on()) {
				Key k = m.group(2).equals("Blood") ? Key.BLOOD : Key.WITHER;
				mc.gui.setTimes(5, 50, 15);
				mc.gui.setSubtitle(Component.literal("§f" + m.group(1) + " §7has it"));
				mc.gui.setTitle(Component.literal(k.alert));
			}
			// Only whoever holds the key is told how to use it.
			if (line.startsWith("RIGHT CLICK on a WITHER door")) hold(mc, DungeonLayout.Lock.WITHER);
			else if (line.startsWith("RIGHT CLICK on the BLOOD DOOR")) hold(mc, DungeonLayout.Lock.BLOOD);
			else if (line.contains("BLOOD DOOR has been opened") && held == DungeonLayout.Lock.BLOOD) held = DungeonLayout.Lock.NONE;
			else if (line.contains("opened a WITHER door") && held == DungeonLayout.Lock.WITHER
					&& (line.startsWith(mc.getUser().getName() + " ") || line.startsWith(Players.me() + " "))) held = DungeonLayout.Lock.NONE;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (held != DungeonLayout.Lock.NONE && (mc.level != heldIn || !Area.inDungeon())) held = DungeonLayout.Lock.NONE;
			if (held == DungeonLayout.Lock.NONE || !enabled() || !doorTrail.on() || !Plus.unlocked() || !Dungeon.placed()) {
				trail.clear();
				trailing = false;
				return;
			}
			if (mc.player.tickCount % 2 != 0) return;
			Vec3 door = door(mc);
			// No door of its kind left on the map: it's been opened.
			if (door == null) held = DungeonLayout.Lock.NONE;
			// Only from a room the door is in: from further off the room trails lead the way.
			DungeonLayout.Room here = Dungeon.roomHere();
			DungeonLayout.Door on = doorOnMap;
			trailing = door != null && on != null && here != null && (on.a() == here.id() || on.b() == here.id());
			trail.ask(mc, !trailing ? List.of() : List.of(new Trail.Goal(List.of(Trail.Via.door(Dungeon.layout(), Dungeon.fit(), on)), doorColour(held), false)));
		});
		World.paint(() -> {
			if (enabled() && Plus.unlocked()) Trail.draw(trail.found(), Trail.WIDTH, true);
		});
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

	private void hold(Minecraft mc, DungeonLayout.Lock lock) {
		held = lock;
		heldIn = mc.level;
	}

	/**
	 * The nearest door your key opens: where the map draws it, set right by the door's own blocks when they're in
	 * sight (coal for a wither door, red terracotta for the blood door). Null when the map shows none left.
	 */
	private Vec3 door(Minecraft mc) {
		DungeonLayout layout = Dungeon.layout();
		MapFit fit = Dungeon.fit();
		double mx = fit.mapX(mc.player.getX()), my = fit.mapY(mc.player.getZ());
		DungeonLayout.Door best = null;
		double bestD = Double.MAX_VALUE;
		for (DungeonLayout.Door d : layout.doors) {
			if (d.lock() != held) continue;
			double dd = Math.pow(d.x() - mx, 2) + Math.pow(d.y() - my, 2);
			if (dd < bestD) {
				bestD = dd;
				best = d;
			}
		}
		doorOnMap = best;
		if (best == null) return null;
		int[] at = fit.door(layout, best);
		BlockPos about = new BlockPos(at[0], mc.player.getBlockY(), at[1]);
		var block = held == DungeonLayout.Lock.BLOOD ? Blocks.RED_TERRACOTTA : Blocks.COAL_BLOCK;
		var box = Dungeon.doorBox(mc.level, about, block);
		return box == null ? Vec3.atCenterOf(about) : new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
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
