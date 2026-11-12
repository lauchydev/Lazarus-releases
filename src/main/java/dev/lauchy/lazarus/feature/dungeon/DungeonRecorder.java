package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Entities;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves the dungeon round you to a file ({@link DungeonSnap}) when you run {@code /lazarus snap}: for when a trail
 * or a solver gets a real room wrong, so it can be put right against that very room. Nothing is sent anywhere; the
 * file stays in the mod's own folder.
 */
public final class DungeonRecorder {
	/** How far round you blocks are saved: across, down and up. */
	private static final int REACH = 96, BELOW = 16, ABOVE = 24;
	/** The sidebar's place for this dungeon: "11/11/26 dungeons-1 690,-456". */
	private static final Pattern ORIGIN = Pattern.compile("(-?\\d+),(-?\\d+)\\s*$");

	private DungeonRecorder() {
	}

	/** Saves a snap of where you stand and says where it went; null when there's no world to save. */
	public static Path snap(Minecraft mc) throws IOException {
		if (mc.level == null || mc.player == null) return null;
		DungeonSnap snap = new DungeonSnap();
		Vec3 at = mc.player.position();
		snap.x = at.x;
		snap.y = at.y;
		snap.z = at.z;
		snap.yaw = mc.player.getYRot();
		for (String line : Area.sidebar()) {
			Matcher m = ORIGIN.matcher(line);
			if (line.contains("dungeon") && m.find()) snap.origin = m.group(1) + "," + m.group(2);
		}
		MapItemSavedData data = Dungeon.data(mc);
		if (data != null) {
			snap.map = new int[DungeonLayout.SIZE * DungeonLayout.SIZE];
			for (int i = 0; i < snap.map.length && i < data.colors.length; i++) {
				int packed = data.colors[i] & 0xFF;
				snap.map[i] = packed / 4 == 0 ? 0 : MapColor.getColorFromPackedId(packed) | 0xFF000000;
			}
		}
		if (Dungeon.fit().ready()) {
			snap.scale = Dungeon.fit().scale();
			snap.offsetX = Dungeon.fit().offset(0);
			snap.offsetZ = Dungeon.fit().offset(1);
		}
		BlockPos feet = mc.player.blockPosition();
		snap.minX = feet.getX() - REACH;
		snap.minY = feet.getY() - BELOW;
		snap.minZ = feet.getZ() - REACH;
		snap.sizeX = snap.sizeZ = REACH * 2 + 1;
		snap.sizeY = BELOW + ABOVE + 1;
		int[] kinds = new int[snap.sizeX * snap.sizeY * snap.sizeZ];
		int unloaded = snap.kindOf(DungeonSnap.UNLOADED, false);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < snap.sizeY; y++) {
			for (int z = 0; z < snap.sizeZ; z++) {
				for (int x = 0; x < snap.sizeX; x++) {
					p.set(snap.minX + x, snap.minY + y, snap.minZ + z);
					if (!mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
						kinds[i++] = unloaded;
						continue;
					}
					BlockState s = mc.level.getBlockState(p);
					kinds[i++] = snap.kindOf(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), !s.isAir() && !s.getCollisionShape(mc.level, p).isEmpty());
				}
			}
		}
		snap.pack(kinds);
		for (Entity e : mc.level.entitiesForRendering()) {
			if (Math.abs(e.getX() - at.x) > REACH || Math.abs(e.getZ() - at.z) > REACH || e == mc.player) continue;
			DungeonSnap.Thing t = new DungeonSnap.Thing();
			t.type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
			t.name = e instanceof Player ? "" : Entities.name(e); // a player's name stays out of the file
			t.x = e.getX();
			t.y = e.getY();
			t.z = e.getZ();
			snap.things.add(t);
		}
		for (Trail.Search search : Trail.searches()) {
			for (Trail.Goal g : search.asked()) {
				DungeonSnap.Shown shown = new DungeonSnap.Shown();
				shown.colour = g.colour();
				shown.whole = g.whole();
				for (Trail.Via v : g.via()) shown.stops.add(new int[]{(int) Math.floor(v.at().x), (int) Math.floor(v.at().z), v.runs()});
				for (Trail.Line l : search.lines()) {
					if (l.colour() != g.colour()) continue;
					for (Vec3 v : l.points()) shown.points.add(new double[]{v.x, v.y, v.z});
				}
				snap.trails.add(shown);
			}
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve(Lazarus.ID).resolve("dungeon-snaps").resolve("snap-" + System.currentTimeMillis() + ".json.gz");
		snap.write(file);
		return file;
	}
}
