package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Kind;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.Lock;
import dev.lauchy.lazarus.feature.dungeon.DungeonLayout.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonLayoutTest {
	@org.junit.jupiter.api.Test
	void theWayToARoomIsTheDoorsBetweenAndALockedOneOnlyWhenThereIsNoOther() {
		var open = DungeonLayout.Lock.NONE;
		DungeonLayout.Door ab = new DungeonLayout.Door(0, 1, open, 0, 0), bc = new DungeonLayout.Door(1, 2, open, 0, 0), cd = new DungeonLayout.Door(2, 3, open, 0, 0),
				ad = new DungeonLayout.Door(0, 3, DungeonLayout.Lock.WITHER, 0, 0), de = new DungeonLayout.Door(3, 4, DungeonLayout.Lock.BLOOD, 0, 0);
		java.util.List<DungeonLayout.Door> doors = java.util.List.of(ab, bc, cd, ad, de);
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(ab, bc), DungeonLayout.route(doors, 0, 2));
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(ab, bc, cd), DungeonLayout.route(doors, 0, 3), "round the locked door");
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(cd, de), DungeonLayout.route(doors, 2, 4), "through one when it's the only way");
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(), DungeonLayout.route(doors, 1, 1));
		org.junit.jupiter.api.Assertions.assertNull(DungeonLayout.route(doors, 0, 9));
	}

	@org.junit.jupiter.api.Test
	void theMapsMarkersAreTheTeammatesTheSidebarLists() {
		// The sidebar as Dragsim sent it (2026-11).
		java.util.List<String> sidebar = java.util.List.of("11/11/26 dungeons-1", "The Catacombs (F1)", "Time Elapsed: 56s", "Cleared: 43% (272)", "", "[M] Gurrnos 7,549❤", "[M] Lauchy 5,211❤", "[M] sStepbr0 7,244❤", "", "dragsim.co");
		org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("Gurrnos", "Lauchy", "sStepbr0"), DungeonMap.party(sidebar));
	}

	@org.junit.jupiter.api.Test
	void aColourPaneIsOnlyEverTurnedForward() {
		org.junit.jupiter.api.Assertions.assertEquals(4, Terminals.turn(1, 0), "one back is four on");
		org.junit.jupiter.api.Assertions.assertEquals(3, Terminals.turn(3, 1));
		org.junit.jupiter.api.Assertions.assertEquals(0, Terminals.turn(2, 2));
	}

	private static final int GREEN = 0xFF007C00, BROWN = 0xFF72431B, PURPLE = 0xFFB24CD8, RED = 0xFFFF0000, GREY = 0xFF414141, BLACK = 0xFF0D0D0D,
			ORANGE = 0xFFD87F33, PINK = 0xFFF27FA5, YELLOW = 0xFFE5E533, TICK = 0xFF006B00;
	private static final int ROOM = 16, PITCH = 20, OX = 6, OY = 10;

	/** A map being painted: rooms of 16 pixels, 4 apart, the grid starting at (6, 10). */
	private final int[] map = new int[128 * 128];

	private void rect(int x, int y, int w, int h, int colour) {
		for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) map[j * 128 + i] = colour;
	}

	private void room(int gx, int gy, int colour) {
		rect(OX + gx * PITCH, OY + gy * PITCH, ROOM, ROOM, colour);
	}

	/** A tick (or cross, or "?") drawn in the middle of a cell. */
	private void mark(int gx, int gy, int colour) {
		rect(OX + gx * PITCH + 5, OY + gy * PITCH + 5, 6, 6, colour);
	}

	/** A door to the right of a cell: a narrow strip across the gap. */
	private void doorRight(int gx, int gy, int colour) {
		rect(OX + gx * PITCH + ROOM, OY + gy * PITCH + 6, 4, 4, colour);
	}

	private void doorBelow(int gx, int gy, int colour) {
		rect(OX + gx * PITCH + 6, OY + gy * PITCH + ROOM, 4, 4, colour);
	}

	private DungeonLayout.Room at(DungeonLayout l, int gx, int gy) {
		return l.roomAt(OX + gx * PITCH + 8, OY + gy * PITCH + 8);
	}

	@Test
	void aMapWithNoEntranceYetIsNoDungeon() {
		assertNull(DungeonLayout.read(map));
		room(0, 0, BROWN);
		assertNull(DungeonLayout.read(map));
	}

	@Test
	void roomsTakeTheirKindFromTheirColourAndTheirStateFromTheirMark() {
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		mark(0, 4, TICK);
		room(1, 4, GREY);
		mark(1, 4, BLACK);
		room(1, 5, ORANGE);
		room(2, 5, PURPLE);
		mark(2, 5, RED);
		room(2, 4, PINK);
		room(3, 4, YELLOW);
		room(3, 5, RED);
		DungeonLayout l = DungeonLayout.read(map);
		assertNotNull(l);
		assertEquals(16, l.roomSize);
		assertEquals(Kind.ENTRANCE, at(l, 0, 5).kind());
		assertEquals(Kind.NORMAL, at(l, 0, 4).kind());
		assertEquals(State.CLEARED, at(l, 0, 4).state());
		assertEquals(Kind.UNKNOWN, at(l, 1, 4).kind());
		assertEquals(State.UNSEEN, at(l, 1, 4).state());
		assertEquals(Kind.TRAP, at(l, 1, 5).kind());
		assertEquals(State.OPEN, at(l, 1, 5).state());
		assertEquals(Kind.PUZZLE, at(l, 2, 5).kind());
		assertEquals(State.FAILED, at(l, 2, 5).state());
		assertEquals(Kind.FAIRY, at(l, 2, 4).kind());
		assertEquals(Kind.MINIBOSS, at(l, 3, 4).kind());
		assertEquals(Kind.BLOOD, at(l, 3, 5).kind());
		assertEquals(State.OPEN, at(l, 3, 5).state(), "the blood room's own red is no cross");
		assertNull(l.roomAt(OX + 4 * PITCH + 8, OY + 8));
	}

	@Test
	void cellsWithTheGapBetweenThemFilledAreOneRoomAndItsTickCountsForAllOfIt() {
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		room(1, 4, BROWN);
		rect(OX + ROOM, OY + 4 * PITCH, 4, ROOM, BROWN); // the gap filled edge to edge: a 2×1 room
		mark(1, 4, TICK);
		room(2, 4, BROWN);
		doorRight(1, 4, BROWN);
		DungeonLayout l = DungeonLayout.read(map);
		assertEquals(3, l.rooms.size());
		assertEquals(2, at(l, 0, 4).cells().size());
		assertEquals(at(l, 0, 4).id(), at(l, 1, 4).id());
		assertEquals(State.CLEARED, at(l, 0, 4).state());
		assertEquals(State.OPEN, at(l, 2, 4).state());
	}

	@Test
	void doorsJoinRoomsAndSayWhatKeyTheyNeed() {
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		room(1, 4, BROWN);
		room(1, 3, RED);
		doorBelow(0, 4, GREEN);
		doorRight(0, 4, BLACK);
		doorBelow(1, 3, RED);
		DungeonLayout l = DungeonLayout.read(map);
		assertEquals(3, l.doors.size());
		DungeonLayout.Door wither = l.doors.stream().filter(d -> d.lock() == Lock.WITHER).findFirst().orElseThrow();
		assertEquals(OX + ROOM + 2, wither.x(), 0.01);
		assertEquals(OY + 4 * PITCH + 8, wither.y(), 0.01);
		DungeonLayout.Door blood = l.doors.stream().filter(d -> d.lock() == Lock.BLOOD).findFirst().orElseThrow();
		assertTrue(l.room(blood.a()).kind() == Kind.BLOOD || l.room(blood.b()).kind() == Kind.BLOOD);
		assertEquals(State.OPEN, at(l, 1, 4).state(), "a blood door beside a room is not a cross on it");
	}

	@Test
	void theWayIsPointedToSpecialRoomsAnywhereAndPlainOnesOnlyNextDoorOnceYoursIsCleared() {
		// Entrance, then a row: plain (you), plain, puzzle; a plain room below yours; all joined by doors.
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		room(1, 4, BROWN);
		room(2, 4, PURPLE);
		room(0, 3, BROWN);
		doorBelow(0, 4, BROWN);
		doorRight(0, 4, BROWN);
		doorRight(1, 4, BROWN);
		doorBelow(0, 3, BROWN);
		DungeonLayout l = DungeonLayout.read(map);
		int here = at(l, 0, 4).id();
		assertEquals(java.util.List.of(at(l, 2, 4).id()), l.targets(here).stream().map(DungeonLayout.Room::id).toList(), "your room not cleared: the puzzle only");
		mark(0, 4, TICK);
		l = DungeonLayout.read(map);
		here = at(l, 0, 4).id();
		assertEquals(java.util.List.of(at(l, 2, 4).id(), at(l, 1, 4).id(), at(l, 0, 3).id()).stream().sorted().toList(), l.targets(here).stream().map(DungeonLayout.Room::id).sorted().toList(), "cleared: the rooms next door too");
		assertEquals(at(l, 2, 4).id(), l.targets(here).getFirst().id(), "special rooms first");
		// From the room next door nothing plain is next door, so the nearest plain room left is pointed to: the one below yours, two doors away.
		mark(1, 4, TICK);
		l = DungeonLayout.read(map);
		assertEquals(java.util.List.of(at(l, 2, 4).id(), at(l, 0, 3).id()), l.targets(at(l, 1, 4).id()).stream().map(DungeonLayout.Room::id).toList());
	}

	@Test
	void withNothingNextDoorLeftToClearTheNearestRoomThatIsGetsPointedTo() {
		// A row of four plain rooms off the entrance: yours and the next cleared, the two beyond not.
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		mark(0, 4, TICK);
		doorBelow(0, 4, GREEN);
		room(1, 4, BROWN);
		mark(1, 4, TICK);
		doorRight(0, 4, BROWN);
		room(2, 4, BROWN);
		doorRight(1, 4, BROWN);
		room(3, 4, BROWN);
		doorRight(2, 4, BROWN);
		DungeonLayout l = DungeonLayout.read(map);
		assertEquals(java.util.List.of(at(l, 2, 4).id()), l.targets(at(l, 0, 4).id()).stream().map(DungeonLayout.Room::id).toList(), "two doors away, and not the one three away");
		assertEquals(java.util.List.of(at(l, 2, 4).id()), l.targets(at(l, 1, 4).id()).stream().map(DungeonLayout.Room::id).toList(), "from next door to it, only that one");
	}

	@Test
	void aDoorsColourGivesAwayTheSpecialRoomBehindItBeforeItHasBeenSeenInto() {
		// Your room, with a "?" room beside it whose door is pink: a fairy room. Another "?" room below, its door plain.
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		doorBelow(0, 4, GREEN);
		room(1, 4, GREY);
		mark(1, 4, BLACK);
		doorRight(0, 4, PINK);
		room(0, 3, GREY);
		mark(0, 3, BLACK);
		doorBelow(0, 3, BROWN);
		DungeonLayout l = DungeonLayout.read(map);
		assertEquals(Kind.FAIRY, l.kindOf(at(l, 1, 4)));
		assertEquals(Kind.UNKNOWN, l.kindOf(at(l, 0, 3)));
		assertEquals(java.util.List.of(at(l, 1, 4).id()), l.targets(at(l, 0, 4).id()).stream().map(DungeonLayout.Room::id).toList(), "pointed to like any special room, your own not yet cleared");
	}

	@Test
	void theRunIsDoneWhenEveryRoomButTheBloodRoomIsTicked() {
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		mark(0, 4, TICK);
		room(1, 4, PURPLE);
		room(2, 4, RED);
		assertFalse(DungeonLayout.read(map).allCleared());
		mark(1, 4, 0xFFFFFFFF); // a white tick counts too
		assertTrue(DungeonLayout.read(map).allCleared());
	}

	@Test
	void theTrackerListsEachSpecialRoomWithWhereItStands() {
		room(0, 5, GREEN);
		room(0, 4, BROWN);
		mark(0, 4, TICK);
		room(1, 4, PURPLE);
		mark(1, 4, TICK);
		room(2, 4, PURPLE);
		room(3, 4, ORANGE);
		room(1, 5, PINK);
		mark(1, 5, TICK);
		room(3, 5, RED);
		DungeonLayout l = DungeonLayout.read(map);
		java.util.List<String> rows = TrackerRows.rows(l, r -> r.state() == State.CLEARED ? "Three Weirdos" : null, "70%", true);
		assertEquals(java.util.List.of(
				"§7Puzzles §f1/2 §8· §7Rooms §f3/5 §8· §7Cleared §f70%",
				"§dFairy	§a✔",
				"§5Three Weirdos	§a✔",
				"§5Puzzle ?	§eto do",
				"§6Trap	§eto do",
				"§cBlood	§7closed"), rows);
		assertEquals(1, TrackerRows.rows(l, r -> null, "", false).size(), "only the summary when the rows are off");
	}
}
