package dev.lauchy.lazarus.feature.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Room;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boulder: a grid of boulders between you and the chest, pushed by clicking the sign on one. The room comes in a
 * handful of layouts, each with a known shortest set of pushes (the table is Odin's, see
 * assets/lazarus/puzzles/ODIN-LICENSE.txt): the layout is read off the grid, and the boulder to push next is boxed,
 * moving on as you click each.
 */
public final class Boulder extends Feature {
	private static final Map<String, List<int[]>> SOLUTIONS = load();

	private final BoolSetting all = add(new BoolSetting("all", "Show every push", "Box every boulder to push, not only the next.", false));

	/** One push: the boulder's box, and the block to click to make it. */
	private record Push(AABB box, BlockPos click) {
	}

	private final List<Push> pushes = new ArrayList<>();
	private String solvedFor;
	private int ticks;

	public Boulder() {
		super("boulder", "Boulder", "Boxes the boulders to push, in order, to reach the chest.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null) {
				pushes.clear();
				solvedFor = null;
				return;
			}
			if (++ticks % 20 != 0) return;
			Room here = Dungeon.roomHere();
			if (here == null || here.kind() != Kind.PUZZLE) return;
			String key = Dungeon.key(here);
			if (key.equals(solvedFor)) return;
			double[] b = Dungeon.bounds(here);
			RoomFrame[] found = new RoomFrame[1];
			List<int[]>[] solution = new List[1];
			found[0] = RoomFrame.find((int) Math.round(b[0]), (int) Math.round(b[1]), f -> (solution[0] = SOLUTIONS.get(grid(mc, f))) != null);
			if (found[0] == null) return;
			solvedFor = key;
			Dungeon.name(here, "Boulder");
			pushes.clear();
			for (int[] s : solution[0]) {
				int[] box = found[0].real(s[0], s[1]), click = found[0].real(s[2], s[3]);
				pushes.add(new Push(new AABB(new BlockPos(box[0], 65, box[1])), new BlockPos(click[0], 65, click[1])));
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && !pushes.isEmpty()) pushes.removeIf(p -> p.click().equals(hit.getBlockPos()));
			return InteractionResult.PASS;
		});
		World.paint(() -> {
			if (!enabled() || pushes.isEmpty()) return;
			for (int i = 0; i < (all.on() ? pushes.size() : 1); i++) World.box(pushes.get(i).box(), i == 0 ? 0xFF55FF55 : 0xFF55FFFF, true, true, true);
		});
	}

	/** The grid as the table keys it: a 1 for each boulder standing, a 0 for each gap, far row first. */
	private static String grid(Minecraft mc, RoomFrame f) {
		StringBuilder s = new StringBuilder();
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) {
				int[] at = f.real(x, z);
				s.append(mc.level.getBlockState(new BlockPos(at[0], 66, at[1])).isAir() ? '0' : '1');
			}
		}
		return s.toString();
	}

	/** The table: each layout's pushes as {boulder x, boulder z, click x, click z} in the unturned room. */
	private static Map<String, List<int[]>> load() {
		Map<String, List<int[]>> out = new HashMap<>();
		try (var in = Boulder.class.getResourceAsStream("/assets/lazarus/puzzles/boulder-solutions.json")) {
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.entrySet()) {
				List<int[]> pushes = new ArrayList<>();
				for (JsonElement p : e.getValue().getAsJsonArray()) {
					JsonArray a = p.getAsJsonArray();
					pushes.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()});
				}
				out.put(e.getKey(), pushes);
			}
		} catch (Exception e) {
			dev.lauchy.lazarus.Lazarus.LOG.error("Couldn't read the boulder solutions", e);
		}
		return out;
	}
}
