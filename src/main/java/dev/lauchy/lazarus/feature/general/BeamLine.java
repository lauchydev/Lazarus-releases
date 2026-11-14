package dev.lauchy.lazarus.feature.general;

import java.util.List;

/**
 * Where a beam of particles runs: the two particles farthest apart, when most of the rest lie
 * along the line between them. Other particles of the same kind nearby (dust from elsewhere)
 * scatter instead, and draw nothing. Pure, so it is tested without the game.
 */
final class BeamLine {
	/** The most particles one tick keeps; a beam is a few dozen. */
	static final int MAX_POINTS = 400;
	/** How far from the line a particle may sit and still be part of the beam, the beam's shortest length, and the share that must lie along it. */
	private static final double WIDTH = 0.6, SHORTEST = 1.5, ALONG = 0.7;

	private BeamLine() {
	}

	/** The beam's two ends as {x, y, z}, or null when the particles don't make one. */
	static double[][] ends(List<double[]> points) {
		if (points.size() < 3) return null;
		double[] a = null, b = null;
		double far = 0;
		for (int i = 0; i < points.size(); i++) {
			for (int j = i + 1; j < points.size(); j++) {
				double d = dist2(points.get(i), points.get(j));
				if (d > far) {
					far = d;
					a = points.get(i);
					b = points.get(j);
				}
			}
		}
		if (a == null || far < SHORTEST * SHORTEST) return null;
		int along = 0;
		for (double[] p : points) if (fromLine(p, a, b) <= WIDTH) along++;
		return along >= ALONG * points.size() ? new double[][]{a, b} : null;
	}

	private static double dist2(double[] p, double[] q) {
		double dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
		return dx * dx + dy * dy + dz * dz;
	}

	/** How far {@code p} is from the segment a-b. */
	private static double fromLine(double[] p, double[] a, double[] b) {
		double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
		double len2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
		double t = len2 == 0 ? 0 : Math.clamp(((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1] + (p[2] - a[2]) * ab[2]) / len2, 0, 1);
		double[] c = {a[0] + ab[0] * t, a[1] + ab[1] * t, a[2] + ab[2] * t};
		return Math.sqrt(dist2(p, c));
	}
}
