package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Room;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.State;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * Trails to what's left of the dungeon: a line from you to the middle of the doorway to leave your room by, one for
 * each way out that leads somewhere worth going. Special rooms still to do (puzzles, mini boss, trap, fairy) are
 * always pointed to, wherever they are; plain rooms only when they're next door and your own room is cleared. Once
 * every room is ticked, one red line leads back to the blood room. A line is in the colour of the room it leads to
 * A special room's line runs the whole way to it, through every doorway; a plain room's stops at the doorway out
 * (a setting draws it the whole way too). They give way to a key's own trail to its door
 * ({@link KeyAlerts}) while that shows. Lazarus+.
 */
public final class RoomTrails extends Feature {
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Draw the trails over walls, so the way on shows from inside a room.", true));

	private final IntSetting width = add(new IntSetting("bandWidth", "Line width", "How wide the trails are drawn, in tenths of a block.", (int) Trail.WIDTH, 1, 20, ""));
	private final BoolSetting whole = add(new BoolSetting("whole", "Whole route", "Draw the lines to plain rooms the whole way too, through every doorway, not only to the doorway out of the room you're in. Special rooms' lines always run the whole way.", false));

	private final Trail.Search search = new Trail.Search();
	private int ticks;

	public RoomTrails() {
		super("roomTrails", "Room Trails", "Lines to the doorway that leads to each special room, and to the rooms next door still to clear.", Category.DUNGEONS, false);
	}

	@Override
	public boolean plus() {
		return true;
	}

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || !Dungeon.placed() || KeyAlerts.trailing()) {
				search.clear();
				return;
			}
			if (++ticks % 2 == 0) search.ask(mc, goals(mc));
		});
		World.paint(() -> {
			if (enabled()) Trail.draw(search.found(), width.get(), through.on());
		});
	}

	/** Where the trails lead from the room you're in: one for each doorway out that's the way to a room worth going to. */
	private List<Trail.Goal> goals(Minecraft mc) {
		DungeonLayout layout = Dungeon.layout();
		Room here = Dungeon.roomHere();
		if (here == null || mc.player == null) return List.of();
		List<Trail.Goal> out = new ArrayList<>();
		Set<Trail.Via> exits = new HashSet<>();
		for (Room r : layout.targets(here.id())) {
			List<Trail.Via> via = Trail.via(layout, Dungeon.fit(), here, r);
			if (via == null || via.isEmpty()) continue;
			// A special room's line runs the whole way to it, from wherever you are; a plain room's only to the doorway
			// out, and plain rooms reached by the same doorway share one line.
			Kind kind = layout.kindOf(r);
			boolean all = whole.on() || kind.special();
			if (!all && !exits.add(via.getFirst())) continue;
			// A way that ends at a door still locked is a line to that door, in the door's colour, not the room's beyond it.
			DungeonLayout.Lock lock = Trail.lockOnWay(layout, here, r);
			out.add(new Trail.Goal(via, lock == DungeonLayout.Lock.NONE ? kind.colour : KeyAlerts.doorColour(lock), all));
		}
		return out;
	}
}
