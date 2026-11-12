package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of the server's map markers is which teammate. The map carries a marker for everyone in the run but no
 * names, so a marker is told by where it is: the ones at players your game can see (you among them) are theirs,
 * and what's left belongs to the teammates out of sight, each taking the marker nearest where they were last
 * drawn. That's how a teammate three rooms away keeps moving on the map. No Minecraft types, so it's tested on
 * its own.
 */
final class MapMarkers {
	/** How far, in map pixels, a server marker may sit from a player in sight and still be theirs: it's sent a few times a second, so it trails a runner. */
	static final double NEAR = 8;

	private MapMarkers() {
	}

	/**
	 * Where each teammate out of sight is, by the server's markers.
	 *
	 * @param markers the server's markers, each {x, y, facing}, in map pixels and degrees
	 * @param inSight where each player your game can see is on the map, {x, y} each: their markers are spoken for
	 * @param outOfSight each teammate your game can't see, by name, with where they were last drawn ({x, y}) or
	 *                   null when they never were
	 * @return a marker for each teammate one was left for; a name without one keeps whatever it had
	 */
	static Map<String, double[]> place(List<double[]> markers, List<double[]> inSight, Map<String, double[]> outOfSight) {
		List<double[]> left = new ArrayList<>(markers);
		for (double[] p : inSight) {
			int at = nearest(left, p);
			if (at >= 0 && far(left.get(at), p) <= NEAR * NEAR) left.remove(at);
		}
		Map<String, double[]> out = new LinkedHashMap<>();
		List<String> names = new ArrayList<>(outOfSight.keySet());
		// The closest pair of teammate and marker first, so two teammates out of sight don't swap.
		while (!left.isEmpty()) {
			String who = null;
			int which = -1;
			double best = Double.MAX_VALUE;
			for (String name : names) {
				double[] last = outOfSight.get(name);
				if (last == null) continue;
				int at = nearest(left, last);
				if (far(left.get(at), last) < best) {
					best = far(left.get(at), last);
					who = name;
					which = at;
				}
			}
			if (who == null) break;
			out.put(who, left.remove(which));
			names.remove(who);
		}
		// Teammates never drawn yet take what's left, in order.
		for (String name : names) {
			if (left.isEmpty()) break;
			if (outOfSight.get(name) == null) out.put(name, left.removeFirst());
		}
		return out;
	}

	private static int nearest(List<double[]> markers, double[] to) {
		int at = -1;
		for (int i = 0; i < markers.size(); i++) if (at < 0 || far(markers.get(i), to) < far(markers.get(at), to)) at = i;
		return at;
	}

	private static double far(double[] a, double[] b) {
		return (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]);
	}
}
