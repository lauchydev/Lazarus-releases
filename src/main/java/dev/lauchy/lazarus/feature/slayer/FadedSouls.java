package dev.lauchy.lazarus.feature.slayer;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Raised souls ("[Lv100] Voidling Extremist (Name's soul)") drawn see-through, the way
 * the game draws an invisible teammate, so they stop hiding the boss behind them. The
 * tag rides a stand above the body, so each tag is matched to the body under it.
 */
public final class FadedSouls extends Feature {
	public static FadedSouls INSTANCE;
	private static final Pattern SOUL = Pattern.compile("\\(\\S+['’]s soul\\)");

	private final BoolSetting hideTags = add(new BoolSetting("hideTags", "Hide soul tags", "Hide the souls' name tags too.", true));

	private final Set<Integer> bodies = new HashSet<>(), tags = new HashSet<>();

	public FadedSouls() {
		super("fadedSouls", "Faded Souls", "Draws raised souls see-through so they don't block your view.", Category.SLAYERS, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			bodies.clear();
			tags.clear();
			if (!enabled() || mc.level == null) return;
			List<Entity> loose = new ArrayList<>();
			List<LivingEntity> candidates = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				boolean body = e instanceof LivingEntity && !(e instanceof ArmorStand) && !(e instanceof Player) && !e.isInvisible();
				if (SOUL.matcher(Entities.name(e)).find()) {
					tags.add(e.getId());
					if (body) bodies.add(e.getId());
					else if (e.getVehicle() instanceof LivingEntity v && !(v instanceof Player)) bodies.add(v.getId());
					else loose.add(e);
				} else if (body) {
					candidates.add((LivingEntity) e);
				}
			}
			for (Entity t : loose) {
				for (LivingEntity b : candidates) {
					double dx = b.getX() - t.getX(), dz = b.getZ() - t.getZ(), dy = t.getY() - b.getY();
					if (dx * dx + dz * dz <= 0.75 * 0.75 && dy >= -0.5 && dy <= 4.5) {
						bodies.add(b.getId());
						break;
					}
				}
			}
		});
	}

	public boolean faded(Entity e) {
		return enabled() && bodies.contains(e.getId());
	}

	public boolean tagHidden(Entity e) {
		return enabled() && hideTags.on() && tags.contains(e.getId());
	}
}
