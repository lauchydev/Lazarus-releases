package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Room;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The room tracker: a panel with a summary line (puzzles done, rooms cleared, the sidebar's cleared figure) and a
 * row for each special room on the map with where it stands ({@link TrackerRows}). A puzzle reads "Puzzle ?" until
 * it's close enough to see into: it takes the name the server announces ("PUZZLE! Quiz - ...") when it does, the
 * one a result gives away ("X was fooled by Y" is Three Weirdos, in the room X stands in), and otherwise is named
 * from what's inside (a silverfish on ice is Ice Path, blazes Higher Or Lower, and so on).
 */
public final class RoomTracker extends Feature {
	/** The panel's rows, worked out once a tick and drawn from that every frame. */
	private final dev.lauchy.lazarus.core.PerTick<java.util.List<String>> shownRows = new dev.lauchy.lazarus.core.PerTick<>();
	private static final Pattern CLEARED = Pattern.compile("Cleared:\\s*(\\d+%)");

	private final BoolSetting detail = add(new BoolSetting("rows", "A row for each room", "Off leaves only the summary line.", true));

	private int ticks, turn;

	public RoomTracker() {
		super("roomTracker", "Room Tracker", "A panel with each special room of the dungeon and whether it's done.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		Dungeon.init();
		Chat.listen(l -> {
			String name = enabled() && Area.inDungeon() ? Puzzles.named(l.text()) : null;
			Room here = name == null ? null : Dungeon.roomHere();
			if (here != null && here.kind() == Kind.PUZZLE) Dungeon.name(here, name);
			if (!enabled() || !Area.inDungeon() || !Dungeon.placed()) return;
			Minecraft mc = Minecraft.getInstance();
			Puzzles.Result result = Puzzles.result(l.text());
			if (result != null) {
				// The room whoever it names stands in; yours when they can't be seen and you're in a puzzle.
				Room room = roomOf(mc, result.player());
				if (room == null) room = Dungeon.roomHere();
				if (room != null && room.kind() == Kind.PUZZLE) Dungeon.name(room, result.puzzle());
			} else if (l.text().startsWith("[NPC] ")) {
				// Only one puzzle has people who talk to you.
				Room room = Dungeon.roomHere();
				if (room != null && room.kind() == Kind.PUZZLE && Dungeon.puzzle(room) == null) Dungeon.name(room, "Three Weirdos");
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !Area.inDungeon() || mc.level == null || mc.player == null || ++ticks % 20 != 0) return;
			if (!Dungeon.placed()) return;
			// Every puzzle not named yet, one a second in turn: a room near enough for the game to have its blocks is named before you step in.
			List<Room> unnamed = Dungeon.layout().rooms.stream().filter(r -> r.kind() == Kind.PUZZLE && Dungeon.puzzle(r) == null).toList();
			if (unnamed.isEmpty()) return;
			Room room = unnamed.get(turn++ % unnamed.size());
			String name = spot(mc, room);
			if (name != null) Dungeon.name(room, name);
		});
		Hud.register(new HudElement(this, "roomTracker", "Room Tracker", 0.006f, 0.34f) {
			@Override
			protected boolean visible() {
				return Area.inDungeon() && Dungeon.layout() != null;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = new ArrayList<>(List.of("§5§lDungeon"));
				if (sample) {
					rows.add("§7Puzzles §f1/3 §8· §7Rooms §f9/14 §8· §7Cleared §f70%");
					if (detail.on()) rows.addAll(List.of("§dFairy\t§a✔", "§5Three Weirdos\t§a✔", "§5Ice Fill\t§eto do", "§5Puzzle ?\t§eto do", "§eMini boss\t§eto do", "§6Trap\t§a✔", "§cBlood\t§7closed"));
				} else {
					rows.addAll(shownRows.get(() -> TrackerRows.rows(Dungeon.layout(), Dungeon::puzzle, cleared(), detail.on())));
				}
				return Draw.panel(g, font, rows);
			}
		});
	}

	/** The room a player stands in, or null when the game can't see them. */
	private static Room roomOf(Minecraft mc, String player) {
		if (mc.level == null || player.isEmpty()) return null;
		for (var p : mc.level.players()) {
			if (p.getName().getString().equals(player)) return Dungeon.layout().roomAt(Dungeon.fit().mapX(p.getX()), Dungeon.fit().mapY(p.getZ()));
		}
		return null;
	}

	/** The sidebar's own "Cleared: 70%" figure, "" when it shows none. */
	private static String cleared() {
		for (String line : Area.sidebar()) {
			Matcher m = CLEARED.matcher(line);
			if (m.find()) return m.group(1);
		}
		return "";
	}

	/** The puzzle a room is, from the mobs and blocks in it, or null when nothing tells yet. */
	private static String spot(Minecraft mc, Room room) {
		double[] b = Dungeon.bounds(room);
		int silverfish = 0, blazes = 0, creepers = 0, npcs = 0;
		boolean quiz = false;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getX() < b[0] || e.getZ() < b[1] || e.getX() > b[2] || e.getZ() > b[3]) continue;
			if (e instanceof Silverfish) silverfish++;
			else if (e instanceof Blaze) blazes++;
			else if (e instanceof Creeper) creepers++;
			String n = Entities.name(e);
			if (n.equals("CLICK")) npcs++;
			if (n.contains("Quiz") || n.contains("Oruo")) quiz = true;
		}
		int ice = 0, packed = 0, lanterns = 0, frames = 0, levers = 0, water = 0, chests = 0, boardButtons = 0, planks = 0;
		int y0 = mc.player.getBlockY() - 12, y1 = mc.player.getBlockY() + 20;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = (int) b[0]; x <= (int) b[2]; x++) {
			for (int z = (int) b[1]; z <= (int) b[3]; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockState s = mc.level.getBlockState(p.set(x, y, z));
					if (s.isAir()) continue;
					if (s.is(Blocks.ICE)) ice++;
					else if (s.is(Blocks.PACKED_ICE)) packed++;
					else if (s.is(Blocks.SEA_LANTERN)) lanterns++;
					else if (s.is(Blocks.END_PORTAL_FRAME)) frames++;
					else if (s.is(Blocks.LEVER)) levers++;
					else if (s.is(Blocks.WATER)) water++;
					else if (s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST)) chests++;
					else if (s.is(Blocks.BIRCH_PLANKS) || s.is(Blocks.JUNGLE_PLANKS)) planks++;
					else if (s.getBlock() instanceof ButtonBlock && ironBeside(mc, p.immutable())) boardButtons++;
				}
			}
		}
		if (silverfish > 0 && packed > 10) return "Ice Path";
		if (blazes >= 2) return "Higher Or Lower";
		if (creepers > 0 && lanterns >= 2) return "Creeper Beams";
		if (frames >= 8) return "Teleport Maze";
		if (npcs >= 3 && chests >= 3) return "Three Weirdos";
		if (ice + packed >= 20 && silverfish == 0) return "Ice Fill";
		if (boardButtons >= 1) return "Tic Tac Toe";
		if (quiz) return "Quiz";
		if (planks >= 60) return "Boulder";
		return null;
	}

	/** Whether a button hangs on an iron block: a free square of the Tic Tac Toe board. */
	private static boolean ironBeside(Minecraft mc, BlockPos at) {
		for (BlockPos n : List.of(at.north(), at.south(), at.east(), at.west())) if (mc.level.getBlockState(n).is(Blocks.IRON_BLOCK)) return true;
		return false;
	}
}
