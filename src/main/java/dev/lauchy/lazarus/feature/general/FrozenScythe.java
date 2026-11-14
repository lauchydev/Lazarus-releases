package dev.lauchy.lazarus.feature.general;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Particles;
import dev.lauchy.lazarus.core.Sounds;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hides the Frozen Scythe's ice trail and its sound, from anyone's scythe. Which particles and
 * sounds count is a list in its settings; {@code /lazarus effects} names the ones playing near
 * you for a few seconds, each with a button that adds it to the list. With "Beam as a line" on,
 * the hidden particles of each tick are drawn as one thin line from end to end instead, when
 * they lie along one ({@link BeamLine}).
 */
public final class FrozenScythe extends Feature {
	/** How long {@code /lazarus effects} listens. */
	private static final long LISTEN_MS = 5_000;
	/** How near a particle or sound must be for {@code /lazarus effects} to name it. */
	private static final double NEAR = 16;
	/** How near a beam must start to be drawn as a line, how long the line shows, and its colour (ice blue). */
	private static final double NEAR_LINE = 64;
	private static final int LINE_MS = 300, LINE_COLOUR = 0xFFA8E4FF;

	private final BoolSetting particles = add(new BoolSetting("particles", "Hide particles", "Hide the scythe's ice trail.", true));
	private final BoolSetting sounds = add(new BoolSetting("sounds", "Hide sound", "Mute the scythe's sound.", true));
	private final TextSetting particleIds = add(new TextSetting("hiddenParticles", "Particles", "The particle ids hidden, comma separated. /lazarus effects names the ones near you.", "snowflake, item_snowball, dust", 300));
	private final BoolSetting line = add(new BoolSetting("line", "Beam as a line", "Draw the scythe's beam as one thin line where its particles were.", false));
	private final TextSetting soundIds = add(new TextSetting("mutedSounds", "Sounds", "The sound ids muted, comma separated. /lazarus effects names the ones near you.", "entity.player.hurt_freeze, entity.bat.takeoff", 300));

	/** When {@code /lazarus effects} stops listening, and what it has named so far. */
	private long listenUntil;
	private final Set<String> named = new LinkedHashSet<>();
	/** The hidden particles of this tick, for the line: x, y, z each. */
	private final List<double[]> hiddenAt = new ArrayList<>();

	public FrozenScythe() {
		super("frozenScythe", "Frozen Scythe Effects", "Hides the Frozen Scythe's ice particles and sound.", Category.GENERAL, true);
	}

	@Override
	public void init() {
		Particles.register((p, x, y, z) -> {
			String id = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(p.getType()));
			heard("particle", id, x, y, z);
			boolean hide = enabled() && particles.on() && listed(particleIds.get(), id);
			if (hide && line.on() && hiddenAt.size() < BeamLine.MAX_POINTS) hiddenAt.add(new double[]{x, y, z});
			return hide;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (hiddenAt.isEmpty()) return;
			double[][] ends = BeamLine.ends(hiddenAt);
			hiddenAt.clear();
			if (ends == null || mc.player == null || mc.player.distanceToSqr(ends[0][0], ends[0][1], ends[0][2]) > NEAR_LINE * NEAR_LINE) return;
			try {
				// Ticks run with the game's gizmo collector open, so the line is drawn like any debug line, fading out.
				Gizmos.line(new Vec3(ends[0][0], ends[0][1], ends[0][2]), new Vec3(ends[1][0], ends[1][1], ends[1][2]), LINE_COLOUR, 2f)
						.persistForMillis(LINE_MS).fadeOut();
			} catch (IllegalStateException e) {
				// no collector open: skip this beam
			}
		});
		Sounds.register((id, x, y, z) -> {
			heard("sound", id, x, y, z);
			return enabled() && sounds.on() && listed(soundIds.get(), id);
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("effects").executes(c -> {
					named.clear();
					listenUntil = Util.getMillis() + LISTEN_MS;
					Message.lazarus().info("Listening for 5 seconds. Swing the scythe now.").send();
					return 1;
				})
				.then(ClientCommands.literal("hide").then(ClientCommands.argument("kind", StringArgumentType.word())
						.then(ClientCommands.argument("id", StringArgumentType.greedyString()).executes(c -> {
							hide(StringArgumentType.getString(c, "kind"), StringArgumentType.getString(c, "id"));
							return 1;
						}))))));
	}

	/** While {@code /lazarus effects} listens, names each new particle or sound near you once, with a button to hide it. */
	private void heard(String kind, String id, double x, double y, double z) {
		if (Util.getMillis() > listenUntil || !named.add(kind + " " + id)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.distanceToSqr(x, y, z) > NEAR * NEAR) {
			named.remove(kind + " " + id);
			return;
		}
		TextSetting list = kind.equals("sound") ? soundIds : particleIds;
		boolean hidden = listed(list.get(), id);
		mc.execute(() -> {
			Message m = Message.lazarus().muted(kind + " ").highlight(id);
			if (hidden) m.muted(" · hidden");
			else m.info(" ").command("Hide", "Add it to the Frozen Scythe Effects list", "/lazarus effects hide " + kind + " " + id);
			m.send();
		});
	}

	/** Adds an id to the particles or sounds list, from the buttons {@code /lazarus effects} shows. */
	private void hide(String kind, String id) {
		TextSetting list = kind.equals("sound") ? soundIds : particleIds;
		if (!listed(list.get(), id)) {
			list.set(list.get().isBlank() ? id : list.get() + ", " + id);
			Config.save();
		}
		Message.lazarus().highlight(id).info(" is hidden now.").send();
	}

	/**
	 * Whether {@code id} ("minecraft:snowflake") is in a comma separated list; entries without a
	 * namespace are Minecraft's own ("snowflake").
	 */
	static boolean listed(String list, String id) {
		String full = id.toLowerCase(Locale.ROOT);
		return Arrays.stream(list.split(","))
				.map(s -> s.trim().toLowerCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.anyMatch(s -> full.equals(s.contains(":") ? s : "minecraft:" + s));
	}
}
