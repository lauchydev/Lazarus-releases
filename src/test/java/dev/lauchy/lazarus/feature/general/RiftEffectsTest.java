package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Area.Place;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftEffectsTest {
	@Test
	void inTheRiftBothEffectsComeOff() {
		assertTrue(RiftEffects.removes("darkness", Place.RIFT, true, true, true));
		assertTrue(RiftEffects.removes("nausea", Place.RIFT, true, true, true));
	}

	@Test
	void eachEffectHasItsOwnSwitch() {
		assertFalse(RiftEffects.removes("darkness", Place.RIFT, false, true, true));
		assertFalse(RiftEffects.removes("nausea", Place.RIFT, true, false, true));
	}

	@Test
	void otherEffectsStay() {
		assertFalse(RiftEffects.removes("blindness", Place.RIFT, true, true, true));
		assertFalse(RiftEffects.removes("slowness", Place.RIFT, true, true, false));
	}

	@Test
	void outsideTheRiftOnlyWhenRiftOnlyIsOff() {
		assertFalse(RiftEffects.removes("darkness", Place.END, true, true, true));
		assertFalse(RiftEffects.removes("nausea", Place.UNKNOWN, true, true, true));
		assertTrue(RiftEffects.removes("darkness", Place.END, true, true, false));
		assertTrue(RiftEffects.removes("nausea", Place.UNKNOWN, true, true, false));
	}
}
