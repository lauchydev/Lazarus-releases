package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Room Glow: in a dungeon, the mobs in the room you're in glow, and no others. The server lights mobs up through
 * walls from every room around, which shows a crowd you can't reach yet; this keeps the glow to the room you're
 * clearing. A room is told by the map's own squares, so one that bends round another (an L) doesn't take in the
 * room in its crook. Players are left as they are; the stands some mobs are built of lose their glow outside your
 * room and keep whatever the server gave them inside it.
 */
public final class RoomGlow extends Feature {
	private static RoomGlow instance;

	/** The room you're in, by one of its squares on the map ({column, row}): its number changes as the map is read again, its squares don't. Null when it can't be told. */
	private volatile int[] room;
	private int ticks;

	public RoomGlow() {
		super("roomGlow", "Room Glow", "Only the mobs in the room you're in glow.", Category.DUNGEONS, false);
		instance = this;
	}

	@Override
	public void init() {
		Dungeon.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || !Dungeon.placed()) {
				room = null;
				return;
			}
			if (++ticks % 5 != 0) return;
			DungeonLayout.Room here = Dungeon.roomHere();
			room = here == null ? null : here.cells().getFirst();
		});
	}

	/** Whether a thing should glow, by this feature's say: true or false for a mob in a dungeon whose room is known, null to leave it to the game. */
	public static Boolean glows(Entity e) {
		RoomGlow g = instance;
		int[] here = g == null ? null : g.room;
		if (here == null || !(e instanceof LivingEntity) || e instanceof Player || !Dungeon.placed()) return null;
		Vec3 at = e.position();
		DungeonLayout.Room in = Dungeon.layout().roomAt(Dungeon.fit().mapX(at.x), Dungeon.fit().mapY(at.z));
		boolean inside = false;
		if (in != null) for (int[] c : in.cells()) inside |= c[0] == here[0] && c[1] == here[1];
		// A stand is never made to glow (most only hold a name tag), but one in another room doesn't keep the server's glow.
		if (e instanceof ArmorStand) return inside ? null : Boolean.FALSE;
		return inside;
	}
}
