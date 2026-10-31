package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Higher Or Lower: kill the blazes in order of health. A floating label beside the room's
 * title says which end goes first ("Highest health first."); each blaze's tag carries its
 * health ("[Lv100] Blaze 225000/225000❤"). Only the next blaze is boxed, ordered by its
 * full health so a damaged one doesn't jump the queue.
 */
public final class HigherOrLower extends Feature {
	private static final String PUZZLE = "Higher Or Lower";
	private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kKmMbB]?)\\s*/\\s*([\\d.,]+[kKmMbB]?)\\s*❤");
	private static final double RANGE = 30;

	private final BoolSetting through = add(new BoolSetting("walls", "Through walls", "Show the box through blocks.", true));

	private boolean active, said;
	private Vec3 origin;
	private Boolean highestFirst;
	private Entity next;

	public HigherOrLower() {
		super("higherOrLower", "Higher Or Lower", "Boxes the next blaze to kill, in the order the room asks for.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			String line = l.text();
			Minecraft mc = Minecraft.getInstance();
			if (Puzzles.entered(line, PUZZLE) && mc.player != null) {
				active = true;
				said = false;
				highestFirst = null;
				origin = mc.player.position();
			} else if (Puzzles.left(line, PUZZLE)) {
				active = false;
				next = null;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			next = null;
			if (!enabled() || !active || mc.level == null || origin == null) return;
			List<Entity> blazes = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e.position().distanceTo(origin) > RANGE) continue;
				String n = Entities.name(e);
				if (e instanceof Display.TextDisplay && highestFirst == null) {
					String t = n.toLowerCase(Locale.ROOT);
					if (t.contains("highest") || t.contains("strongest")) highestFirst = true;
					else if (t.contains("lowest") || t.contains("weakest")) highestFirst = false;
				}
				if (n.contains("Blaze") && HEALTH.matcher(n).find()) blazes.add(e);
			}
			if (highestFirst == null || blazes.isEmpty()) return;
			if (!said) {
				said = true;
				Message.lazarus().info("Higher Or Lower: ").highlight(highestFirst ? "highest" : "lowest").info(" health first.").send();
			}
			Comparator<Entity> order = Comparator.comparingDouble(HigherOrLower::maxHealth);
			if (highestFirst) order = order.reversed();
			next = blazes.stream().filter(e -> !(e instanceof Blaze b && b.isDeadOrDying())).min(order).map(HigherOrLower::body).orElse(null);
		});
		World.paint(() -> {
			if (next != null) World.box(next.getBoundingBox().inflate(0.1), 0xFF55FF55, true, false, through.on());
		});
	}

	private static double maxHealth(Entity e) {
		Matcher m = HEALTH.matcher(Entities.name(e));
		return m.find() ? Text.number(m.group(2)) : 0;
	}

	/** The blaze itself when the tag rides a stand above it. */
	private static Entity body(Entity tag) {
		Entity b = Entities.owner(tag, e -> e instanceof Blaze);
		return b != null ? b : tag;
	}
}
