package dev.lauchy.lazarus.feature.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftEffectsTest {
	@Test
	void bothEffectsComeOff() {
		assertTrue(RiftEffects.removes("darkness", true, true));
		assertTrue(RiftEffects.removes("nausea", true, true));
	}

	@Test
	void eachEffectHasItsOwnSwitch() {
		assertFalse(RiftEffects.removes("darkness", false, true));
		assertFalse(RiftEffects.removes("nausea", true, false));
	}

	@Test
	void otherEffectsStay() {
		assertFalse(RiftEffects.removes("blindness", true, true));
		assertFalse(RiftEffects.removes("slowness", true, true));
	}
}
