package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Room;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The room tracker's lines: a summary (puzzles done of those seen, and how much of the dungeon is cleared), then a
 * row for each special room the map shows (fairy, each puzzle by name once known, mini boss, trap, blood), in its
 * map colour with where it stands. No Minecraft types, so it's tested on its own.
 */
final class TrackerRows {
	private static final List<Kind> ORDER = List.of(Kind.FAIRY, Kind.PUZZLE, Kind.MINIBOSS, Kind.TRAP, Kind.BLOOD);

	private TrackerRows() {
	}

	/**
	 * The panel's lines. {@code puzzle} names a puzzle room once someone has been in it (null before); {@code cleared}
	 * is the sidebar's own figure ("70%"), "" when it shows none; without {@code detail} only the summary is given.
	 */
	static List<String> rows(DungeonLayout layout, Function<Room, String> puzzle, String cleared, boolean detail) {
		List<Room> special = layout.rooms.stream().filter(r -> r.kind().special())
				.sorted(Comparator.comparingInt((Room r) -> ORDER.indexOf(r.kind())).thenComparingInt(r -> r.cells().getFirst()[0]).thenComparingInt(r -> r.cells().getFirst()[1])).toList();
		long puzzles = special.stream().filter(r -> r.kind() == Kind.PUZZLE).count();
		long solved = special.stream().filter(r -> r.kind() == Kind.PUZZLE && r.state() == State.CLEARED).count();
		long rooms = layout.rooms.stream().filter(r -> r.kind() != Kind.ENTRANCE && r.kind() != Kind.BLOOD).count();
		long done = layout.rooms.stream().filter(r -> r.kind() != Kind.ENTRANCE && r.kind() != Kind.BLOOD && r.state() == State.CLEARED).count();
		List<String> out = new ArrayList<>();
		out.add("§7Puzzles §f" + solved + "/" + puzzles + " §8· §7Rooms §f" + done + "/" + rooms + (cleared.isEmpty() ? "" : " §8· §7Cleared §f" + cleared));
		if (!detail) return out;
		for (Room r : special) {
			String name = r.kind() == Kind.PUZZLE ? (puzzle.apply(r) == null ? "Puzzle ?" : puzzle.apply(r)) : r.kind().label;
			out.add(code(r.kind()) + name + "\t" + switch (r.state()) {
				case CLEARED -> "§a✔";
				case FAILED -> "§c✖ failed";
				default -> r.kind() == Kind.BLOOD ? "§7closed" : "§eto do";
			});
		}
		return out;
	}

	/** The chat colour nearest a room's map colour. */
	private static String code(Kind k) {
		return switch (k) {
			case FAIRY -> "§d";
			case PUZZLE -> "§5";
			case MINIBOSS -> "§e";
			case TRAP -> "§6";
			case BLOOD -> "§c";
			default -> "§f";
		};
	}
}
