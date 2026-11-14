package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeamLineTest {
	@Test
	void aRowOfParticlesIsABeamFromEndToEnd() {
		List<double[]> beam = new ArrayList<>();
		for (int i = 0; i <= 10; i++) beam.add(new double[]{i, 64 + i * 0.1, 0.05 * (i % 2)});
		double[][] ends = BeamLine.ends(beam);
		assertNotNull(ends);
		assertArrayEquals(new double[]{0, 64, 0}, ends[0], 1e-9);
		assertArrayEquals(new double[]{10, 65, 0}, ends[1], 1e-9);
	}

	@Test
	void scatteredParticlesAreNoBeam() {
		List<double[]> dust = List.of(new double[]{0, 0, 0}, new double[]{5, 0, 0}, new double[]{2, 4, 3}, new double[]{-3, 2, 6}, new double[]{1, -4, -2});
		assertNull(BeamLine.ends(dust));
	}

	@Test
	void tooFewOrTooShortIsNoBeam() {
		assertNull(BeamLine.ends(List.of(new double[]{0, 0, 0}, new double[]{9, 0, 0})));
		assertNull(BeamLine.ends(List.of(new double[]{0, 0, 0}, new double[]{0.5, 0, 0}, new double[]{1, 0, 0})));
	}
}
