package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonObject;
import dev.lauchy.lazarus.setting.KeySetting;
import dev.lauchy.lazarus.setting.Setting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotBindsTest {
	/** The feature's settings, as the binds hand them over. */
	private final List<Setting<?>> settings = new ArrayList<>();
	private final SlotBinds binds = new SlotBinds("pet", "Pet bind", "", "", settings::add);

	/** Reads a saved file into the binds as the config does: the binds it holds are added, then every value is read. */
	private void load(JsonObject file) {
		binds.loading(file);
		for (Setting<?> s : settings) if (file.has(s.key)) s.load(file.get(s.key));
	}

	private Setting<?> setting(String key) {
		return settings.stream().filter(s -> s.key.equals(key)).findFirst().orElseThrow();
	}

	@Test
	void eachBindIsAKeyAndASlotThatStartsAtItsOwnNumber() {
		assertEquals(1, binds.size());
		assertEquals(List.of("pet1Key", "pet1Slot"), settings.stream().map(s -> s.key).toList());
		assertEquals("Pet bind 1 key", settings.get(0).name);
		assertEquals("Pet bind 1 slot", settings.get(1).name);
		assertEquals(1, binds.slot(0));
		binds.keys().getFirst().set(KeySetting.of(74, 0));
		binds.follow();
		assertEquals(2, binds.size(), "the key just set brings the next bind");
		assertEquals(2, binds.slot(1));
		binds.follow();
		assertEquals(2, binds.size(), "an empty bind brings no more");
	}

	@Test
	void aBindSavedBeforeSlotsPicksTheSlotItAlwaysDid() {
		JsonObject file = new JsonObject();
		file.addProperty("enabled", true);
		file.addProperty("pet1Key", -1);
		file.addProperty("pet2Key", -1);
		file.addProperty("pet3Key", 74);
		for (int n = 4; n <= 45; n++) file.addProperty("pet" + n + "Key", -1);
		load(file);
		assertEquals(4, binds.size(), "up to the last one set, then one empty");
		assertTrue(binds.keys().get(2).bound());
		assertEquals(3, binds.slot(2));
	}

	@Test
	void aBindPicksTheSlotSavedForIt() {
		JsonObject file = new JsonObject();
		file.addProperty("pet1Key", 74);
		file.addProperty("pet1Slot", 4);
		file.addProperty("pet2Key", -1);
		file.addProperty("pet2Slot", 99);
		load(file);
		assertEquals(4, binds.slot(0), "J on slot 4, with no binds for 1 to 3");
		assertEquals(SlotBinds.MOST, binds.slot(1), "a slot past the most is held to it");
	}

	@Test
	void theFreeAllowanceIsCountedByBindWhateverSlotItPicks() {
		JsonObject file = new JsonObject();
		file.addProperty("pet1Key", 74);
		file.addProperty("pet1Slot", 4);
		file.addProperty("pet2Key", 75);
		file.addProperty("pet2Slot", 9);
		load(file);
		assertEquals(3, binds.size());
		assertFalse(setting("pet1Key").plus());
		assertFalse(setting("pet1Slot").plus());
		assertFalse(setting("pet2Slot").plus());
		assertTrue(setting("pet3Key").plus());
		assertTrue(setting("pet3Slot").plus());
	}

	@Test
	void holdingAKeyPicksOnce() {
		assertTrue(binds.pressed(0, true));
		assertFalse(binds.pressed(0, true));
		assertFalse(binds.pressed(0, false));
		assertTrue(binds.pressed(0, true));
	}
}
