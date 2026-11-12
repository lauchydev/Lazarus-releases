package dev.lauchy.lazarus.feature.inventory;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePagesTest {
	@Test
	void readsPageWindows() {
		assertEquals(3, StoragePages.pageOfWindow("Storage Page (3/8)"));
		assertEquals(8, StoragePages.countOfWindow("Storage Page (3 / 8)"));
		assertEquals(StoragePages.ENDER, StoragePages.pageOfWindow("Ender Chest"));
		assertEquals(-1, StoragePages.pageOfWindow("Storage"));
		assertEquals(-1, StoragePages.pageOfWindow("Storage Page (999/999)"));
		assertTrue(StoragePages.isRoot("Storage"));
	}

	@Test
	void readsStorageMenuButtons() {
		assertEquals(4, StoragePages.pageOfButton("Storage Page 4"));
		assertEquals(-1, StoragePages.pageOfButton("Locked Page"));
	}

	@Test
	void spotsLockedPages() {
		assertTrue(StoragePages.locked("minecraft:red_stained_glass_pane", List.of()));
		assertTrue(StoragePages.locked("minecraft:chest", List.of("Unlock this page in the store")));
		assertFalse(StoragePages.locked("minecraft:chest", List.of("Click to view")));
	}

	@Test
	void pagesAlwaysDropTheirButtonRow() {
		assertTrue(StoragePages.chromeRow(2, Collections.nCopies(9, "minecraft:chest"), Collections.nCopies(9, "Chest")));
	}

	@Test
	void enderChestKeepsItsTopRowUnlessItIsButtons() {
		List<String> items = Collections.nCopies(9, "minecraft:diamond");
		assertFalse(StoragePages.chromeRow(StoragePages.ENDER, items, Collections.nCopies(9, "Diamond")));
		assertTrue(StoragePages.chromeRow(StoragePages.ENDER, Collections.nCopies(9, "minecraft:black_stained_glass_pane"), Collections.nCopies(9, " ")));
		assertTrue(StoragePages.chromeRow(StoragePages.ENDER, List.of("minecraft:arrow"), List.of("Go Back")));
	}

	@Test
	void layoutFitsThreePageColumnsBesideTheInventoryAtGuiScaleTwo() {
		StoragePages.Layout l = StoragePages.layout(960, 540, 9, 5);
		assertEquals(3, l.cols());
		assertEquals(3, l.totalRows());
		assertTrue(l.x(2) + StoragePages.CARD_W <= 960);
		assertTrue(l.left() >= 12);
	}

	@Test
	void neverShowsMoreThanThreeRowsOfPages() {
		StoragePages.Layout l = StoragePages.layout(1920, 1080, 20, 5);
		assertEquals(3, l.cols());
		assertEquals(3, l.visibleRows());
	}

	@Test
	void revealScrollsJustEnoughToShowACard() {
		StoragePages.Layout l = StoragePages.layout(640, 360, 20, 5);
		int row = 15 / l.cols();
		int scroll = l.reveal(15, 0);
		assertTrue(l.visible(15, scroll));
		assertEquals(Math.min(l.maxScroll(), row - l.visibleRows() + 1), scroll);
		assertEquals(scroll, l.reveal(15, scroll));
	}

	@Test
	void agesReadLikeSpeech() {
		assertEquals("just now", StoragePages.age(5_000));
		assertEquals("5m ago", StoragePages.age(300_000));
		assertEquals("2d ago", StoragePages.age(2 * 86_400_000L));
	}
}
