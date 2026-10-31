package dev.lauchy.lazarus.feature.slayer;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Particles;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Where your slayer boss is about to land. "SLAYER BOSS SPAWNING" is followed within a
 * fraction of a second by a burst of enchant, portal and witch particles at the exact
 * spot; the densest cluster of that burst near you is the landing point. A ring and a
 * countdown sit there until the boss's name appears on it.
 */
public final class SpawnMarker extends Feature {
	public static SpawnMarker INSTANCE;

	private static final long LISTEN_MS = 200, SHOW_MS = 1_500;
	private static final double RANGE = 30, CLUSTER = 2.3;

	private final BoolSetting hideBurst = add(new BoolSetting("hideBurst", "Hide spawn burst", "Hide the burst particles so the marker stays clear.", true));
	private final ColorSetting color = add(new ColorSetting("color", "Colour", "Marker colour.", 0xFFB46CFF));

	private record Sample(int kind, Vec3 at) {
	}

	private final List<Sample> samples = new ArrayList<>();
	private final List<Consumer<Vec3>> listeners = new ArrayList<>();
	private long armedAt, shownAt;
	private Vec3 spot;

	public SpawnMarker() {
		super("spawnMarker", "Slayer Spawn Marker", "Marks where your slayer boss is about to spawn, with a countdown.", Category.SLAYERS, true);
		INSTANCE = this;
	}

	/** Called with the landing point of each of your bosses (even with the marker hidden). */
	public void onSpot(Consumer<Vec3> listener) {
		listeners.add(listener);
	}

	/** Your boss's landing point if it spawned in the last few seconds, else null. */
	public Vec3 recentSpot(long withinMs) {
		return spot != null && System.currentTimeMillis() - shownAt <= withinMs ? spot : null;
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (!Chat.fromPlayer(l.text()) && l.text().contains("SLAYER BOSS SPAWNING")) {
				armedAt = System.currentTimeMillis();
				samples.clear();
			}
		});
		Particles.register((particle, x, y, z) -> {
			int kind = particle.getType() == ParticleTypes.ENCHANT ? 0 : particle.getType() == ParticleTypes.PORTAL ? 1
					: particle.getType() == ParticleTypes.WITCH ? 2 : -1;
			if (kind < 0) return false;
			long now = System.currentTimeMillis();
			Minecraft mc = Minecraft.getInstance();
			boolean listening = armedAt != 0 && now - armedAt <= LISTEN_MS;
			Vec3 at = new Vec3(x, y, z);
			if (listening && mc.player != null && mc.player.position().distanceTo(at) <= RANGE) {
				samples.add(new Sample(kind, at));
				return hideBurst.on() && enabled();
			}
			return hideBurst.on() && enabled() && spot != null && now - shownAt < SHOW_MS && horizontal(at, spot) < CLUSTER && Math.abs(y - spot.y) < 3.5;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long now = System.currentTimeMillis();
			if (armedAt != 0 && now - armedAt > LISTEN_MS) {
				armedAt = 0;
				Vec3 found = cluster();
				if (found != null) {
					spot = found;
					shownAt = now;
					for (Consumer<Vec3> l : listeners) l.accept(found);
				}
			}
			if (spot != null && now - shownAt < SHOW_MS && arrived()) shownAt = now - SHOW_MS;
		});
		World.paint(this::paint);
		Hud.register(new HudElement(this, "spawnMarker", "Slayer Spawn Countdown", 0.5f, 0.42f) {
			@Override
			protected boolean visible() {
				return showing();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				double left = sample ? 1.2 : (SHOW_MS - (System.currentTimeMillis() - shownAt)) / 1000.0;
				return Draw.panel(g, font, List.of("§5Boss in §f" + String.format(Locale.ROOT, "%.1fs", Math.max(0, left))));
			}
		});
	}

	private boolean showing() {
		return spot != null && System.currentTimeMillis() - shownAt < SHOW_MS;
	}

	/** The centre of the biggest cluster holding all three particle kinds, standing on the block below it. */
	private Vec3 cluster() {
		if (samples.size() < 12) return null;
		Vec3 best = null;
		int bestCount = 0;
		for (Sample c : samples) {
			int[] kinds = new int[3];
			int n = 0;
			double x = 0, y = 0, z = 0;
			for (Sample s : samples) {
				if (horizontal(s.at, c.at) > CLUSTER || Math.abs(s.at.y - c.at.y) > 2.5) continue;
				kinds[s.kind]++;
				n++;
				x += s.at.x;
				y += s.at.y;
				z += s.at.z;
			}
			if (n > bestCount && kinds[0] >= 3 && kinds[1] >= 3 && kinds[2] >= 3) {
				bestCount = n;
				best = new Vec3(x / n, Math.round(y / n - 1.0), z / n);
			}
		}
		return best;
	}

	private boolean arrived() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.position().distanceToSqr(spot) > 9) continue;
			String n = Entities.name(e);
			if (n.contains("Horror") || n.contains("Seraph")) return true;
		}
		return false;
	}

	private void paint() {
		if (!enabled() || !showing()) return;
		int c = color.argb();
		float t = 1f - (System.currentTimeMillis() - shownAt) / (float) SHOW_MS;
		double r = 0.6 + 0.9 * t;
		Vec3 last = null;
		for (int i = 0; i <= 32; i++) {
			double a = Math.PI * 2 * i / 32;
			Vec3 p = spot.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
			if (last != null) World.line(last, p, c, 3f, true);
			last = p;
		}
		World.line(spot, spot.add(0, 2.5, 0), c, 3f, true);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
