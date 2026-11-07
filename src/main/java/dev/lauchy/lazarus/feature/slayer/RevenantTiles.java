package dev.lauchy.lazarus.feature.slayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Shared;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * The Crypts' Revenant spawn tiles, learned as you play. Each time your boss lands in the
 * Crypts the block it lands on is counted in config/lazarus/revenant_tiles.json; tiles
 * seen twice (or once, if you choose) are outlined whenever you are near them, so you
 * can stand where the next boss will appear. Sightings are shared through the Lazarus server,
 * so each tile counts every Lazarus user's.
 */
public final class RevenantTiles extends Feature {
	private static final Path FILE = Config.dir().resolve("revenant_tiles.json");

	private final IntSetting range = add(new IntSetting("range", "Show within", "Only outline tiles this close.", 16, 4, 64, " blocks"));
	private final BoolSetting single = add(new BoolSetting("single", "Show single sightings", "Also outline tiles seen only once.", false));
	private final BoolSetting walls = add(new BoolSetting("walls", "Through walls", "Draw tiles through blocks.", true));
	private final ColorSetting color = add(new ColorSetting("color", "Colour", "Tile colour.", 0xFF59FF00));

	private final Map<BlockPos, Integer> tiles = new HashMap<>();

	public RevenantTiles() {
		super("revenantTiles", "Revenant Spawn Tiles", "Outlines Crypts tiles where Revenant bosses have spawned before.", Category.SLAYERS, true);
	}

	@Override
	public void init() {
		load();
		Shared.follow("revenant", Duration.ofMinutes(10), got -> {
			got.forEach((key, e) -> {
				String[] xyz = key.split(",");
				if (xyz.length != 3 || !e.value().has("count")) return;
				try {
					BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
					tiles.merge(pos, e.value().get("count").getAsInt(), Math::max); // everyone's count includes yours once sent
				} catch (RuntimeException ignored) {
					// a malformed tile is skipped
				}
			});
			save();
		});
		SpawnMarker.INSTANCE.onSpot(spot -> {
			if (!enabled() || !Area.inCrypts()) return;
			BlockPos pos = BlockPos.containing(spot.x, spot.y - 1, spot.z); // the block under its feet
			tiles.merge(pos, 1, Integer::sum);
			save();
			JsonObject v = new JsonObject();
			v.addProperty("add", 1);
			Shared.send("revenant", pos.getX() + "," + pos.getY() + "," + pos.getZ(), v, (a, b) -> {
				a.addProperty("add", a.get("add").getAsInt() + b.get("add").getAsInt());
				return a;
			});
		});
		World.paint(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (!enabled() || !Area.inCrypts()) return;
			double max = range.get() * (double) range.get();
			Vec3 me = mc.player.position();
			int c = color.argb();
			for (var e : tiles.entrySet()) {
				if (e.getValue() < (single.on() ? 1 : 2) || e.getKey().getCenter().distanceToSqr(me) > max) continue;
				World.block(e.getKey(), e.getValue() >= 2 ? c : (c & 0xFFFFFF) | 0x80000000, true, walls.on());
			}
		});
	}

	private void load() {
		JsonElement json = Config.readJson(FILE);
		if (json == null || !json.isJsonArray()) return;
		for (JsonElement e : json.getAsJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			tiles.put(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()), a.get(3).getAsInt());
		}
	}

	private void save() {
		JsonArray out = new JsonArray();
		tiles.forEach((pos, n) -> {
			JsonArray a = new JsonArray();
			a.add(pos.getX());
			a.add(pos.getY());
			a.add(pos.getZ());
			a.add(n);
			out.add(a);
		});
		Config.writeJson(FILE, out);
	}
}
