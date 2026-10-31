package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** A tall beam (and box) over the Endstone Protector and the Warden, visible through walls. */
public final class BossBeacon extends Feature {
	private final BoolSetting protector = add(new BoolSetting("protector", "Protector", "Beam over the Endstone Protector.", true));
	private final BoolSetting warden = add(new BoolSetting("warden", "Warden", "Beam over the Warden.", true));
	private final ColorSetting color = add(new ColorSetting("color", "Colour", "Beam colour.", 0xFFAA00AA));
	private final IntSetting height = add(new IntSetting("height", "Beam height", "How far the beam rises.", 60, 8, 128, "m"));
	private final BoolSetting tracer = add(new BoolSetting("tracer", "Tracer", "A line from your crosshair to the boss.", false));

	private Entity cached;
	private long cachedAt;

	public BossBeacon() {
		super("bossBeacon", "Boss Beacon", "A beam over the Protector and the Warden so you can find them.", Category.END, true);
	}

	@Override
	public void init() {
		World.paint(this::paint);
	}

	private void paint() {
		if (!enabled() || !Area.inEnd()) return;
		GolemStatus.State s = GolemStatus.INSTANCE == null ? GolemStatus.State.UNKNOWN : GolemStatus.INSTANCE.state();
		boolean wantProtector = protector.on() && s != GolemStatus.State.WARDEN;
		boolean wantWarden = warden.on() && s == GolemStatus.State.WARDEN;
		if (!wantProtector && !wantWarden) return;
		long now = Util.getMillis();
		if (now - cachedAt > 250 || cached != null && cached.isRemoved()) {
			cachedAt = now;
			cached = find(wantWarden ? "Warden" : "Endstone Protector", wantWarden);
		}
		if (cached == null) return;
		float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 pos = cached.getPosition(pt);
		AABB box = cached.getBoundingBox().move(pos.subtract(cached.position()));
		int c = color.argb();
		World.box(box, c, true, false, true);
		Vec3 top = new Vec3(pos.x, box.maxY, pos.z);
		World.line(top, top.add(0, height.get(), 0), (c & 0xFFFFFF) | 0x50000000, 14f, true);
		World.line(top, top.add(0, height.get(), 0), c, 4f, true);
		if (tracer.on()) World.tracer(top, c);
	}

	private static Entity find(String name, boolean warden) {
		Entity tag = Entities.nearestNamed(name, n -> {
			String s = n.toLowerCase(Locale.ROOT);
			return !s.contains("[lvl") && !s.contains("'s ") && !s.contains(" pet");
		}, 256);
		if (tag == null) return null;
		Entity body = Entities.owner(tag, e -> warden ? e instanceof Warden : e instanceof IronGolem);
		return body != null ? body : tag;
	}
}
