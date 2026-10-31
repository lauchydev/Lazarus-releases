package dev.lauchy.lazarus.feature.slayer;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Your Voidgloom (or Riftborn) Seraph: shield hits left or health on screen, a box and
 * optional tracer on the boss, and the Yang Glyph beacon marked so you can reach it in
 * time.
 * <p>
 * Finding your boss works the way SkyblockAddons does it where it can: a "Spawned by: you"
 * line floating with a Seraph makes it yours. Dragsim's tags carry no owner, so otherwise the
 * boss is claimed by time and place: the first Seraph near you (or near where the spawn marker
 * saw it land) in the half minute after your own "SLAYER BOSS SPAWNING" line. What's kept is
 * the boss's body (the Enderman, which can be named "Dinnerbone" with its tag riding it as a
 * text display), and every tick its health is read from all the Seraph tags over it, the lowest
 * winning, because a second tag can sit at full health for the whole fight.
 */
public final class VoidgloomHelper extends Feature {
	/** How long after your spawn line a Seraph can be claimed, and how far from you it may be. */
	private static final long CLAIM_MS = 30_000;
	private static final double CLAIM_RANGE = 10;
	private static final int GLYPH_RANGE = 12;
	/** How long Broken Heart Radiation (the beams) lasts from its chat line. */
	private static final long BEAMS_MS = 8_200;
	/** How long after the beams line the boss's tag gets to show "Radiation" before a tag without it ends the timer early. */
	private static final long BEAMS_GRACE_MS = 2_000;
	/** How thick the beams bar is, and the gap between it and the readout. */
	private static final int BAR = 3, BAR_GAP = 2;

	private final BoolSetting readout = add(new BoolSetting("readout", "On-screen readout", "Shield hits left, then health.", true));
	private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight boss", "Box around your boss.", true));
	private final BoolSetting tracer = add(new BoolSetting("tracer", "Tracer", "A line to your boss and the glyph.", false));
	private final BoolSetting glyph = add(new BoolSetting("glyph", "Highlight Yang Glyph", "Box the beacon the boss drops at your feet.", true));
	private final BoolSetting beams = add(new BoolSetting("beams", "Beams alert", "Warn when Broken Heart Radiation starts.", true));
	private final BoolSetting beamTimer = add(new BoolSetting("beamTimer", "Beams timer", "A bar under the readout runs down the time left on the beams.", true));
	private final ColorSetting color = add(new ColorSetting("color", "Colour", "Highlight colour.", 0xFFAA00AA));

	/** Your boss's body (or its tag when no body can be found), where it was last seen, and what its tags last said. */
	private Entity tag;
	private Vec3 lastPos;
	private SeraphTag shown;
	private final List<BlockPos> glyphs = new ArrayList<>();
	private int ticks;
	private long claimUntil;
	/** When your boss's beams started, or 0 when they aren't up. */
	private long beamsAt;

	public VoidgloomHelper() {
		super("voidgloomHelper", "Voidgloom Helper", "Hits and health readout, boss and Yang Glyph highlight.", Category.SLAYERS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			String line = l.text();
			if (!enabled() || Chat.fromPlayer(line)) return;
			if (line.contains("SLAYER BOSS SPAWNING")) {
				claimUntil = System.currentTimeMillis() + CLAIM_MS;
				tag = null;
				lastPos = null;
				shown = null;
				beamsAt = 0;
			} else if (line.contains("SLAYER BOSS SLAIN") || line.contains("SLAYER QUEST FAILED")) {
				tag = null;
				lastPos = null;
				shown = null;
				claimUntil = 0;
				beamsAt = 0;
				glyphs.clear();
			} else if (line.contains("BROKEN HEART RADIATION") && tag != null) {
				beamsAt = System.currentTimeMillis();
				if (beams.on()) {
					Toast.show("§5Beams §7· stay within 15 blocks", Theme.WARN);
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.8f);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		World.paint(this::paint);
		Hud.register(new HudElement(this, "voidgloom", "Voidgloom Boss", 0.5f, 0.2f) {
			@Override
			protected boolean visible() {
				return readout.on() && tag != null;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return beamBar(g, Draw.panel(g, font, List.of("§5§l57 HITS", "§7Seraph IV §c· beams §8· §f9m")), 0.6f);
				Minecraft mc = Minecraft.getInstance();
				if (shown == null) return Draw.panel(g, font, List.of("§7Seraph §8· §fwaiting for its tag"));
				String big = shown.hits().isEmpty() ? "§c" + shown.health() + " §7/ §c" + shown.maxHealth() : "§5§l" + shown.hits() + " HITS";
				int dist = mc.player == null ? 0 : (int) Math.round(mc.player.position().distanceTo(tag.position()));
				int[] size = Draw.panel(g, font, List.of(big, "§7" + shown.label() + (shown.radiating() ? " §c· beams" : "") + " §8· §f" + dist + "m"));
				float left = beamsLeft();
				return left > 0 && beamTimer.on() ? beamBar(g, size, left) : size;
			}
		});
	}

	/** How much of the beams phase is left, 1 at its start to 0 at its end; 0 when they aren't up. */
	private float beamsLeft() {
		if (beamsAt == 0) return 0;
		long age = System.currentTimeMillis() - beamsAt;
		// The tag says "Radiation" while the beams are up; once it stops, so does the timer.
		if (age >= BEAMS_MS || age > BEAMS_GRACE_MS && shown != null && !shown.radiating()) {
			beamsAt = 0;
			return 0;
		}
		return 1f - age / (float) BEAMS_MS;
	}

	/** A red bar under the readout, {@code left} of its width long over a dim track, as the toasts run down their time. */
	private static int[] beamBar(GuiGraphicsExtractor g, int[] size, float left) {
		if (g != null) {
			Draw.rect(g, 0, size[1] + BAR_GAP, size[0], BAR, Theme.HUD_BG);
			Draw.rect(g, 0, size[1] + BAR_GAP, Math.round(size[0] * left), BAR, Theme.BAD);
		}
		return new int[]{size[0], size[1] + BAR_GAP + BAR};
	}

	private void tick(Minecraft mc) {
		if (!enabled() || mc.level == null || mc.player == null) {
			tag = null;
			return;
		}
		String me = mc.player.getName().getString();
		// An owner line naming you settles it whenever one shows, as SkyblockAddons reads Hypixel's.
		Entity owned = ownedBoss(mc, me);
		if (owned != null) tag = owned;
		if (tag != null && (tag.isRemoved() || tag.level() != mc.level)) tag = lastPos == null ? null : claim(lastPos, 6); // tags and bodies get re-sent
		if (tag == null && System.currentTimeMillis() < claimUntil) {
			Vec3 spot = SpawnMarker.INSTANCE == null ? null : SpawnMarker.INSTANCE.recentSpot(CLAIM_MS);
			tag = claim(spot != null ? spot : mc.player.position(), CLAIM_RANGE);
			if (tag != null) claimUntil = 0;
		}
		if (tag == null) {
			glyphs.clear();
			return;
		}
		lastPos = tag.position();
		SeraphTag read = SeraphTag.live(tagsOver(mc, tag));
		if (read != null) shown = read;
		if (glyph.on() && ++ticks % 5 == 0) scanGlyphs(mc);
	}

	/** Every Seraph tag on or floating over a boss: its own name, a text display riding it, or one in the column above it. */
	private static List<SeraphTag> tagsOver(Minecraft mc, Entity body) {
		List<SeraphTag> out = new ArrayList<>();
		SeraphTag own = SeraphTag.read(Entities.name(body));
		if (own != null) out.add(own);
		for (Entity e : mc.level.getEntities(body, body.getBoundingBox().inflate(0.8, 4.5, 0.8))) {
			if (e instanceof EnderMan) continue; // another boss standing in this one
			SeraphTag t = SeraphTag.read(Entities.name(e));
			if (t != null) out.add(t);
		}
		return out;
	}

	/** The body of the Seraph nearest {@code near} within {@code range}, found through any of its tags, or null. */
	private static Entity claim(Vec3 near, double range) {
		Entity tagEntity = nearestSeraph(near, range);
		if (tagEntity == null) return null;
		Entity body = Entities.owner(tagEntity, e -> e instanceof EnderMan);
		return body != null ? body : tagEntity;
	}

	/** The body of the Seraph a "Spawned by: you" line floats with, or null when the server shows no such line. */
	private static Entity ownedBoss(Minecraft mc, String me) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!me.equals(SeraphTag.owner(Entities.name(e)))) continue;
			Entity tagEntity = nearestSeraph(e.position(), 4);
			if (tagEntity == null) continue;
			Entity body = Entities.owner(tagEntity, x -> x instanceof EnderMan);
			return body != null ? body : tagEntity;
		}
		return null;
	}

	private void scanGlyphs(Minecraft mc) {
		glyphs.clear();
		BlockPos me = mc.player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(me.offset(-GLYPH_RANGE, -4, -GLYPH_RANGE), me.offset(GLYPH_RANGE, 4, GLYPH_RANGE))) {
			if (mc.level.getBlockState(p).is(Blocks.BEACON)) glyphs.add(p.immutable());
		}
	}

	private static Entity nearestSeraph(Vec3 near, double range) {
		Minecraft mc = Minecraft.getInstance();
		Entity best = null;
		double bestD = range * range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (SeraphTag.read(Entities.name(e)) == null) continue;
			double d = e.position().distanceToSqr(near);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private void paint() {
		if (!enabled() || tag == null) return;
		int c = color.argb();
		if (highlight.on()) {
			Entity body = Entities.owner(tag, e -> e instanceof EnderMan);
			Entity boss = body != null ? body : tag;
			World.box(boss.getBoundingBox(), c, true, false, true);
			if (tracer.on()) World.tracer(boss.getBoundingBox().getCenter(), c);
		}
		for (BlockPos p : glyphs) {
			World.block(p, Theme.WARN, true, true);
			if (tracer.on()) World.tracer(p.getCenter(), Theme.WARN);
		}
	}
}
