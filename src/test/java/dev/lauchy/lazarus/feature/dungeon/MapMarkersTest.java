package dev.lauchy.lazarus.feature.dungeon;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMarkersTest {
	private static double[] at(double x, double y) {
		return new double[]{x, y, 90};
	}

	@Test
	void aTeammateOutOfSightMovesToTheServersMarker() {
		// You're in the bottom room; your teammate was last seen by the blood door and has since gone into the blood room.
		Map<String, double[]> placed = MapMarkers.place(List.of(at(30, 110), at(112, 60)), List.of(new double[]{31, 109}), Map.of("Gurrnos", new double[]{95, 62}));
		assertArrayEquals(at(112, 60), placed.get("Gurrnos"));
	}

	@Test
	void aMarkerAtAPlayerInSightIsNotGivenToSomeoneElse() {
		// Two markers: yours and a teammate standing beside you, in sight. The one out of sight has none, so stays put.
		Map<String, double[]> placed = MapMarkers.place(List.of(at(30, 110), at(34, 108)), List.of(new double[]{30, 110}, new double[]{35, 108}), Map.of("Far", new double[]{90, 20}));
		assertTrue(placed.isEmpty());
	}

	@Test
	void twoTeammatesOutOfSightEachTakeTheNearerMarker() {
		Map<String, double[]> last = new HashMap<>();
		last.put("A", new double[]{20, 20});
		last.put("B", new double[]{100, 100});
		Map<String, double[]> placed = MapMarkers.place(List.of(at(96, 104), at(24, 18), at(60, 60)), List.of(new double[]{60, 61}), last);
		assertArrayEquals(at(24, 18), placed.get("A"));
		assertArrayEquals(at(96, 104), placed.get("B"));
	}

	@Test
	void aTeammateNeverSeenTakesTheMarkerLeftOver() {
		Map<String, double[]> last = new HashMap<>();
		last.put("New", null);
		Map<String, double[]> placed = MapMarkers.place(List.of(at(10, 10), at(70, 40)), List.of(new double[]{10, 11}), last);
		assertArrayEquals(at(70, 40), placed.get("New"));
		assertEquals(1, placed.size());
	}
}
