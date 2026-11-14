package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * Your ender chest and every storage page as they were the last time you had them open, kept on disk per
 * account so the storage overview and Storage Preview can show them without opening anything. It stays on this
 * computer: nothing here is sent anywhere.
 */
public final class StorageCache {
	/** One page as last seen: its stacks in order, empties kept so holes stay holes, and when. */
	public record Page(List<ItemStack> items, long seenAt) {
		/** Rows of nine this page fills. */
		public int rows() {
			return Math.max(1, (items.size() + StoragePages.COLS - 1) / StoragePages.COLS);
		}

		/** The stack at {@code i}, or empty past the end. */
		public ItemStack at(int i) {
			return i >= 0 && i < items.size() ? items.get(i) : ItemStack.EMPTY;
		}
	}

	private static final Map<Integer, Page> pages = new TreeMap<>();
	private static final Set<Integer> locked = new TreeSet<>();
	private static final List<BooleanSupplier> users = new ArrayList<>();
	private static int count;
	private static String account;
	private static boolean dirty;
	/** The container window open now and when it opened, to tell an empty page from one whose contents haven't arrived. */
	private static AbstractContainerScreen<?> openScreen;
	private static long openedAt;
	/** How many of the open window's storage slots held something last tick, and whether the server has since wiped them all. */
	private static int lastFilled;
	private static boolean wiped;
	/** How long after a window opens its contents are taken as having arrived, even when nothing says so. */
	private static final long SETTLE_MS = 1500;

	private StorageCache() {
	}

	/** Starts remembering pages while {@code wanted} says a feature needs them. Each feature calls this once. */
	public static void use(BooleanSupplier wanted) {
		users.add(wanted);
		if (users.size() > 1) return;
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			openScreen = cs;
			openedAt = Util.getMillis();
			lastFilled = 0;
			wiped = false;
			ScreenEvents.afterTick(screen).register(s -> capture(cs));
			ScreenEvents.remove(screen).register(s -> {
				capture(cs);
				save();
			});
		});
	}

	private static boolean wanted() {
		for (BooleanSupplier u : users) if (u.getAsBoolean()) return true;
		return false;
	}

	/** Every remembered page, ender chest first, for the current account. */
	public static Map<Integer, Page> pages() {
		load();
		return pages;
	}

	/** The highest page number the server has shown, from page titles and the Storage menu. */
	public static int count() {
		load();
		return count;
	}

	/** Whether the Storage menu last showed this page as locked. */
	public static boolean locked(int page) {
		load();
		return locked.contains(page);
	}

	/**
	 * Whether a window's contents have arrived from the server. The game marks that with a state id, but Dragsim's
	 * storage pages arrive with none, so a page with anything in it counts, and so does one that has been open a moment:
	 * a page that is still empty by then is empty.
	 */
	public static boolean settled(AbstractContainerScreen<?> cs) {
		if (cs.getMenu().getStateId() != 0) return true;
		for (Slot s : cs.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			if (!s.getItem().isEmpty()) return true;
		}
		return cs != openScreen || Util.getMillis() - openedAt >= SETTLE_MS;
	}

	/** The container slots of a storage window that hold storage, in order; the server's button row is left out. */
	public static List<Slot> content(AbstractContainerScreen<?> screen, int page) {
		List<Slot> all = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			all.add(s);
		}
		if (all.size() <= StoragePages.COLS) return all;
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		for (Slot s : all.subList(0, StoragePages.COLS)) {
			ids.add(s.getItem().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString());
			names.add(Items.name(s.getItem()));
		}
		return StoragePages.chromeRow(page, ids, names) ? all.subList(StoragePages.COLS, all.size()) : all;
	}

	private static void capture(AbstractContainerScreen<?> screen) {
		if (!wanted() || Minecraft.getInstance().level == null) return;
		load();
		String title = Text.plain(screen.getTitle());
		if (StoragePages.isRoot(title)) {
			readRoot(screen);
			return;
		}
		int page = StoragePages.pageOfWindow(title);
		if (page < 0) return;
		if (StoragePages.countOfWindow(title) > count) {
			count = StoragePages.countOfWindow(title);
			dirty = true;
		}
		List<ItemStack> items = new ArrayList<>();
		for (Slot s : content(screen, page)) items.add(s.getItem().copy());
		if (!settled(screen)) return; // contents not sent yet: an empty page now would wipe what was remembered
		// Leaving a page, the server empties its slots a moment before the window switches. Nobody can clear
		// several slots in one tick by hand, so that is the server, and the page is remembered as it was.
		int filled = (int) items.stream().filter(s -> !s.isEmpty()).count();
		if (screen == openScreen) {
			if (filled == 0 && lastFilled >= 2) wiped = true;
			lastFilled = filled;
			if (wiped) return;
		}
		Page old = pages.get(page);
		if (old != null && same(old.items(), items)) return;
		pages.put(page, new Page(items, System.currentTimeMillis()));
		dirty = true;
	}

	/** The Storage menu lists every page you have, and which are locked. */
	private static void readRoot(AbstractContainerScreen<?> screen) {
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			int page = StoragePages.pageOfButton(Items.name(s.getItem()));
			if (page < 0) continue;
			if (page > count) {
				count = page;
				dirty = true;
			}
			boolean isLocked = StoragePages.locked(BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString(), Items.lore(s.getItem()));
			if (isLocked ? locked.add(page) : locked.remove(page)) dirty = true;
		}
	}

	private static boolean same(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
		return true;
	}

	private static Path file(String uuid) {
		return Config.dir().resolve("storage").resolve(uuid + ".nbt");
	}

	/** Reads the current account's pages the first time they're needed, and again after switching accounts. */
	private static void load() {
		Minecraft mc = Minecraft.getInstance();
		String uuid = String.valueOf(mc.getUser().getProfileId());
		if (uuid.equals(account) || mc.level == null) return;
		account = uuid;
		pages.clear();
		locked.clear();
		count = 0;
		dirty = false;
		Path file = file(uuid);
		Path legacy = Config.dir().resolve("storage.nbt"); // before 1.17.0 one file held whoever opened storage last
		if (!Files.exists(file) && Files.exists(legacy)) file = legacy;
		if (!Files.exists(file)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
			for (CompoundTag p : root.getListOrEmpty("pages").compoundStream().toList()) {
				List<ItemStack> items = new ArrayList<>();
				for (Tag t : p.getListOrEmpty("items")) items.add(ItemStack.OPTIONAL_CODEC.parse(ops, t).result().orElse(ItemStack.EMPTY));
				int page = p.getIntOr("page", -1);
				if (page >= 0 && page <= StoragePages.MAX_PAGES) pages.put(page, new Page(items, p.getLongOr("seen", 0L)));
			}
			count = Math.min(StoragePages.MAX_PAGES, root.getIntOr("count", 0));
			for (int n : root.getIntArray("locked").orElse(new int[0])) locked.add(n);
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not read storage snapshots", e);
		}
	}

	/** Writes the current account's pages if anything changed since the last write. */
	public static void save() {
		Minecraft mc = Minecraft.getInstance();
		if (!dirty || account == null || mc.level == null) return;
		dirty = false;
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
			ListTag list = new ListTag();
			for (var e : pages.entrySet()) {
				CompoundTag p = new CompoundTag();
				p.putInt("page", e.getKey());
				p.putLong("seen", e.getValue().seenAt());
				ListTag items = new ListTag();
				for (ItemStack s : e.getValue().items()) ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).result().ifPresent(items::add);
				p.put("items", items);
				list.add(p);
			}
			CompoundTag root = new CompoundTag();
			root.put("pages", list);
			root.putInt("count", count);
			root.put("locked", new IntArrayTag(locked.stream().mapToInt(Integer::intValue).toArray()));
			Path file = file(account);
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(root, file);
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not save storage snapshots", e);
		}
	}
}
