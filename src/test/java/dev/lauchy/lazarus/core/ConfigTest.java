package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
	@Test
	void theFeaturesThatStartedOnAreSwitchedOffOnceAndThenLeftAsYouSetThem() {
		// A settings file saved by 1.42.0 carries no mark: its new features were on without anyone choosing them.
		JsonObject before = new JsonObject();
		before.add("features", new JsonObject());
		assertTrue(NewFeatures.toSwitchOff(before));
		// Once saved by a version that starts new features off it's marked, and what's switched on in it stays on.
		before.addProperty(NewFeatures.MARK, true);
		assertFalse(NewFeatures.toSwitchOff(before));
	}

	@Test
	void theFeaturesSwitchedOffAreTheOnesThatStartedOn() {
		assertTrue(NewFeatures.STARTED_ON.contains("roomTrails"));
		assertTrue(NewFeatures.STARTED_ON.contains("dragonHitbox"));
		assertFalse(NewFeatures.STARTED_ON.contains("terminals"), "an older feature keeps its switch");
		assertFalse(NewFeatures.STARTED_ON.contains("breakerPath"), "already off from the start");
	}
}
