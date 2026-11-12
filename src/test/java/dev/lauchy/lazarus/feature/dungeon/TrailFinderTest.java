package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailFinderTest {
	/** A flat floor at y = 0 with a wall along x = 5 from z = -10 to z = 3, four blocks high. */
	private static final TrailFinder.Solid WALLED = (x, y, z) -> y == 0 || x == 5 && z >= -10 && z <= 3 && y <= 4;

	private static List<Spot> way(TrailFinder.Solid solid, Spot from, Spot to) {
		TrailFinder.Found found = TrailFinder.walk(solid, from, to, 0.5, 12, 8000);
		assertTrue(found.reached(), "reached: " + found.path());
		return found.path();
	}

	@Test
	void aTrailOnOpenFloorRunsStraight() {
		assertEquals(List.of(new Spot(0, 1, 0), new Spot(8, 1, 0)), way((x, y, z) -> y == 0, new Spot(0, 1, 0), new Spot(8, 1, 0)));
		assertEquals(List.of(new Spot(0, 1, 0), new Spot(9, 1, 4)), way((x, y, z) -> y == 0, new Spot(0, 1, 0), new Spot(9, 1, 4)), "one line, not a zigzag");
	}

	@Test
	void aTrailGoesRoundAWallNotThroughIt() {
		List<Spot> trail = way(WALLED, new Spot(0, 1, 0), new Spot(10, 1, 0));
		for (Spot s : trail) assertTrue(!(s.x() == 5 && s.z() <= 3 && s.y() <= 4), "through the wall at " + s);
		assertTrue(trail.stream().anyMatch(s -> s.z() >= 4), "round its end: " + trail);
		assertTrue(trail.stream().allMatch(s -> s.y() == 1), "level, not over its top: " + trail);
	}

	@Test
	void aTrailNeedsNoFloorUnderIt() {
		// A floor with a 4-wide pit right across it: the line goes straight over.
		TrailFinder.Solid pit = (x, y, z) -> y == 0 && !(x >= 4 && x <= 7);
		assertEquals(List.of(new Spot(0, 1, 0), new Spot(12, 1, 0)), way(pit, new Spot(0, 1, 0), new Spot(12, 1, 0)));
	}

	@Test
	void aTrailRisesOnlyToGetPastWhatIsInTheWay() {
		// A wall right across the way, three blocks high, with open air above it: the only way is over.
		TrailFinder.Solid wall = (x, y, z) -> y <= 0 || x == 5 && y <= 3;
		List<Spot> trail = way(wall, new Spot(0, 1, 0), new Spot(10, 1, 0));
		assertEquals(4, trail.stream().mapToInt(Spot::y).max().orElse(0), "just over the top: " + trail);
	}

	@Test
	void aTrailIsAFewLongLinesNotManyShortTurns() {
		// Round the end of a wall and up onto a ledge beyond it: a handful of long lines, however many blocks the search stepped through.
		TrailFinder.Solid room = (x, y, z) -> y <= 0 || x == 10 && z <= 8 && y <= 12 || x >= 20 && y <= 3;
		List<Spot> trail = TrailFinder.walk(room, new Spot(0, 1, 0), new Spot(26, 4, 0), 0.5, 12, 20000).path();
		assertEquals(new Spot(26, 4, 0), trail.getLast());
		assertTrue(trail.size() <= 5, "start, either side of the wall's end, and the top of the ledge at most: " + trail);
	}

	@Test
	void aPlannedTrailStaysPutAndOnlyTheWayBackToItIsFound() {
		TrailFinder.Solid floor = (x, y, z) -> y <= 0;
		TrailPlan.Blocks blocks = new TrailPlan.Blocks() {
			public boolean solid(int x, int y, int z) {
				return floor.at(x, y, z);
			}

			public boolean shut(int x, int y, int z) {
				return floor.at(x, y, z);
			}

			public boolean loaded(int x, int z) {
				return true;
			}
		};
		// A trail planned across the room and round a corner: along z = 0 to x = 20, then up to z = 20.
		List<Spot> planned = List.of(new Spot(0, 1, 0), new Spot(20, 1, 0), new Spot(20, 1, 20));
		TrailPlan plan = new TrailPlan(blocks, new TrailPlan.Fix());
		// Part of the way along it: the same trail, less what's behind you.
		assertEquals(List.of(new Spot(8, 1, 1), new Spot(20, 1, 0), new Spot(20, 1, 20)), plan.follow(new Spot(8, 1, 1), planned));
		// Round the corner already: only the last run is left.
		assertEquals(List.of(new Spot(20, 1, 12), new Spot(20, 1, 20)), plan.follow(new Spot(20, 1, 12), planned));
		// Well off to one side: a way straight back to the nearest point of it, then the rest as planned.
		assertEquals(List.of(new Spot(8, 1, 9), new Spot(8, 1, 0), new Spot(20, 1, 0), new Spot(20, 1, 20)), plan.follow(new Spot(8, 1, 9), planned));
	}

	@Test
	void aDoorwayIsFoundByItsGapInTheWallNotWhereTheMapRoughlyPutsIt() {
		// A wall three thick (x = 29 to 31) with a doorway three wide (z = 14 to 16) and three high; the map puts the door two blocks off each way.
		TrailFinder.Solid wall = (x, y, z) -> y <= 0 || x >= 29 && x <= 31 && !(z >= 14 && z <= 16 && y <= 3);
		TrailFinder.Doorway found = TrailFinder.doorway(wall, wall, new Spot(28, 1, 17), true, 7, 3);
		assertTrue(found.sure(), "a tunnel through a thick wall is a door between rooms");
		assertEquals(15, found.spot().z());
		assertEquals(1, found.spot().y());
		assertTrue(found.spot().x() >= 29 && found.spot().x() <= 31, "in the wall: " + found);
		// Seen from a ledge four blocks up, it's still the doorway on the floor.
		assertEquals(new Spot(found.spot().x(), 1, 15), TrailFinder.doorway(wall, wall, new Spot(28, 5, 17), true, 7, 3).spot());
		// No wall at all: no doorway.
		org.junit.jupiter.api.Assertions.assertNull(TrailFinder.doorway((x, y, z) -> y <= 0, (x, y, z) -> y <= 0, new Spot(28, 1, 17), true, 7, 3));
	}

	@Test
	void anArchInAThinWallIsNotTakenForADoorBetweenRooms() {
		// A thin wall inside the room (x = 26) with an arch right where the map puts the door, and the real doorway through the thick wall further along.
		TrailFinder.Solid room = (x, y, z) -> y <= 0 || x == 26 && !(z >= 16 && z <= 18 && y <= 3) || x >= 29 && x <= 31 && !(z >= 10 && z <= 12 && y <= 3);
		TrailFinder.Doorway found = TrailFinder.doorway(room, room, new Spot(28, 1, 17), true, 7, 3);
		assertTrue(found.sure());
		assertEquals(11, found.spot().z(), "the tunnel, though the arch is nearer: " + found);
		// With only the arch there, it's found but not sure: the planner won't end a trail at it.
		TrailFinder.Solid arch = (x, y, z) -> y <= 0 || x == 26 && !(z >= 16 && z <= 18 && y <= 3);
		assertTrue(!TrailFinder.doorway(arch, arch, new Spot(28, 1, 17), true, 7, 3).sure());
	}

	@Test
	void aShutDoorIsSureAndTeachesWhereTheMapIsOutForTheNextDoor() {
		// Two doors in thin walls. The first is shut (bars); the map puts both four blocks along from where they are.
		TrailFinder.Solid solid = (x, y, z) -> y <= 0 || x == 30 && !(z >= 14 && z <= 16 && y <= 3 && false) && !(z >= 46 && z <= 48 && y <= 3) || x == 30 && z >= 14 && z <= 16 && y <= 3;
		TrailFinder.Solid shut = (x, y, z) -> solid.at(x, y, z) && !(x == 30 && z >= 14 && z <= 16 && y >= 1 && y <= 3);
		TrailPlan.Blocks blocks = new TrailPlan.Blocks() {
			public boolean solid(int x, int y, int z) {
				return solid.at(x, y, z);
			}

			public boolean shut(int x, int y, int z) {
				return shut.at(x, y, z);
			}

			public boolean loaded(int x, int z) {
				return true;
			}
		};
		TrailPlan.Fix fix = new TrailPlan.Fix();
		TrailPlan plan = new TrailPlan(blocks, fix);
		TrailPlan.Stop open = new TrailPlan.Stop(30, 51, 'x'), barred = new TrailPlan.Stop(30, 19, 'x');
		org.junit.jupiter.api.Assertions.assertNull(plan.place(open, 1), "an unsure gap, and nothing yet says where the map is out");
		assertEquals(new Spot(30, 1, 15), plan.place(barred, 1), "the shut door's middle");
		assertEquals(new Spot(30, 1, 47), new TrailPlan(blocks, fix).place(open, 1), "now looked for four blocks back, and taken");
		// The shut door is solid through and through: a trail to it ends up against its near face, with no marker.
		TrailPlan.Route toShut = new TrailPlan(blocks, fix).route(new Spot(5, 1, 5), List.of(barred), false);
		assertTrue(toShut.line().size() > 1 && toShut.line().getLast().x() < 30, "up to the door on your side: " + toShut);
		org.junit.jupiter.api.Assertions.assertNull(toShut.marker());
		// A door with no doorway to be found gets a marker where the map puts it, and no line.
		TrailPlan.Route none = new TrailPlan(blocks, new TrailPlan.Fix()).route(new Spot(5, 1, 5), List.of(open), false);
		assertTrue(none.line().isEmpty());
		assertEquals(new Spot(30, 1, 51), none.marker());
		// One that's found gets a line to its middle, and no marker.
		TrailPlan.Route found = new TrailPlan(blocks, fix).route(new Spot(5, 1, 40), List.of(open), false);
		assertEquals(new Spot(30, 1, 47), found.line().getLast());
		org.junit.jupiter.api.Assertions.assertNull(found.marker());
	}

	@Test
	void aPlaceOutOfReachGetsATrailAsNearAsItCanGetNotOneThroughTheWall() {
		// The goal is sealed in a box.
		TrailFinder.Solid boxed = (x, y, z) -> y == 0 || Math.abs(x - 20) <= 2 && Math.abs(z) <= 2 && y <= 6 && !(x == 20 && z == 0 && (y == 1 || y == 2));
		TrailFinder.Found found = TrailFinder.walk(boxed, new Spot(0, 1, 0), new Spot(20, 1, 0), 0.5, 12, 3000);
		assertTrue(!found.reached());
		assertEquals(List.of(new Spot(0, 1, 0), new Spot(17, 1, 0)), found.path(), "up to the box's wall, and no further");
	}

	@Test
	void aRoomAwayThroughADoorwayIsReached() {
		// Two 30-block rooms side by side, walled to the ceiling, joined by one doorway at the far end of the wall.
		TrailFinder.Solid rooms = (x, y, z) -> y == 0 || y == 12 || x == 30 && !(z == 28 && y <= 2) || x < 0 || x > 60 || z < 0 || z > 30;
		List<Spot> trail = TrailFinder.walk(rooms, new Spot(5, 1, 3), new Spot(55, 1, 3), 1.5, 12, 8000).path();
		assertTrue(trail.getLast().across(new Spot(55, 1, 3)) <= 1.5, "reached: " + trail);
		for (int i = 0; i + 1 < trail.size(); i++) {
			Spot a = trail.get(i), b = trail.get(i + 1);
			assertTrue(!(Math.min(a.x(), b.x()) < 30 && Math.max(a.x(), b.x()) > 30) || a.z() >= 27 && b.z() >= 27, "crosses the wall only at the doorway: " + trail);
		}
	}

	@Test
	void theMapIsPlacedOnTheWorldByYourOwnMarker() {
		MapFit fit = new MapFit();
		fit.pitch(20); // 16-pixel rooms: 1.6 blocks a pixel
		// You stand at (-120, -40); the marker is drawn to the half pixel, so sightings wobble.
		fit.saw(50, 30, -120, -40);
		fit.saw(50.5, 30, -120, -40);
		fit.saw(50, 30.5, -119, -40);
		assertTrue(fit.ready());
		assertEquals(-120, fit.worldX(50), 1.0);
		assertEquals(-40, fit.worldZ(30), 1.0);
		assertEquals(-120 + 32, fit.worldX(70), 1.0, "one cell on is 32 blocks on");
		assertEquals(50, fit.mapX(-120), 0.6);
	}

	@Test
	void aRoomsFrameIsTheTurnAndCornerThatPutItsBlocksWhereTheyStand() {
		// A room really at corner (100, 200), a quarter turn round: its lever at (20, 10) unturned stands at (120, 220).
		RoomFrame real = new RoomFrame(100, 200, 1);
		int[] lever = real.real(20, 10);
		org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{120, 220}, lever);
		// The map puts the corner a block out; the frame is found by where the lever and a second block are.
		int[] other = real.real(15, 5);
		RoomFrame found = RoomFrame.find(101, 199, f -> java.util.Arrays.equals(f.real(20, 10), lever) && java.util.Arrays.equals(f.real(15, 5), other));
		assertEquals(real, found);
		org.junit.jupiter.api.Assertions.assertNull(RoomFrame.find(150, 150, f -> java.util.Arrays.equals(f.real(20, 10), lever)));
		for (int turn = 0; turn < 4; turn++) {
			int[] corner = new RoomFrame(0, 0, turn).real(0, 0), far = new RoomFrame(0, 0, turn).real(30, 30);
			assertTrue(Math.abs(corner[0] - far[0]) == 30 && Math.abs(corner[1] - far[1]) == 30, "a turn keeps the room in its own square");
		}
	}
}
