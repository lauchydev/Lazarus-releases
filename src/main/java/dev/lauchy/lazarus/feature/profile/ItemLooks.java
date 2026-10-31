package dev.lauchy.lazarus.feature.profile;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How items really look in game, learned by looking: every pet and catalog item seen in a
 * menu is remembered as the server drew it (model, head skin, name and lore). {@code /l}
 * then shows a pet or a piece of equipment exactly as the game does when it has seen the
 * same one, and borrows the look (icon) of a near match when it hasn't.
 */
final class ItemLooks {
	private static final Path FILE = Config.dir().resolve("looks.nbt");
	private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d+)] (.+)$");
	private static final Pattern RARITY = Pattern.compile("\\b(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL)\\b");
	/** Menu hints that belong to the menu the item was seen in, not to the item. */
	private static final Pattern HINT = Pattern.compile("^(Click|Left-click|Right-click|Shift-click|Left-Click|Right-Click|Shift-Click)\\b.*");

	/** Learned stacks: "pet:<type>:<rarity>:<level>", "pet:<type>:<rarity>", "item:<id>" and "uuid:<item uuid>". */
	private static final Map<String, ItemStack> looks = new HashMap<>();
	private static boolean loaded, dirty;
	private static int ticks;

	private ItemLooks() {
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			ScreenEvents.afterTick(screen).register(s -> {
				if (++ticks % 10 == 0) learn(cs);
			});
			ScreenEvents.remove(screen).register(s -> {
				if (dirty) save();
			});
		});
	}

	private static void learn(AbstractContainerScreen<?> screen) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		ensureLoaded(mc);
		for (Slot slot : screen.getMenu().slots) {
			ItemStack s = slot.getItem();
			if (s.isEmpty()) continue;
			String name = Items.name(s);
			Matcher pet = PET.matcher(name);
			if (pet.matches()) {
				String rarity = rarity(s);
				String base = "pet:" + norm(pet.group(2)) + ":" + rarity;
				remember(base + ":" + pet.group(1), s);
				remember(base, s);
				continue;
			}
			String id = Items.id(s);
			if (id != null) remember("item:" + id, s);
			String uuid = Items.unique(s);
			if (uuid != null && id != null) remember("uuid:" + uuid, s);
		}
	}

	private static void remember(String key, ItemStack seen) {
		ItemStack clean = seen.copyWithCount(1);
		List<Component> lore = new ArrayList<>(clean.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lore.removeIf(l -> HINT.matcher(Text.plain(l).trim()).matches());
		while (!lore.isEmpty() && Text.plain(lore.getLast()).isBlank()) lore.removeLast();
		clean.set(DataComponents.LORE, new ItemLore(lore));
		ItemStack old = looks.get(key);
		if (old != null && ItemStack.isSameItemSameComponents(old, clean)) return;
		looks.put(key, clean);
		dirty = true;
	}

	/**
	 * A pet built from profile data, as the game shows it: the very same pet when one of
	 * this type, rarity and level has been seen, else its look with the built name and lore.
	 */
	static ItemStack pet(ItemStack built, String type, String rarity, String level) {
		String base = "pet:" + norm(type) + ":" + norm(rarity);
		ItemStack exact = get(base + ":" + level);
		if (exact != null) return keepGlint(exact.copy(), built);
		ItemStack near = get(base);
		return near == null ? built : withText(near, built);
	}

	/**
	 * An item built from profile data: the very item when it has been seen (same item UUID),
	 * else the look of one like it with the built name and lore, which carry its own stats.
	 */
	static ItemStack item(ItemStack built) {
		String uuid = Items.unique(built);
		ItemStack same = uuid == null ? null : get("uuid:" + uuid);
		if (same != null) return keepGlint(same.copyWithCount(built.getCount()), built);
		String id = Items.id(built);
		ItemStack seen = id == null ? null : get("item:" + id);
		return seen == null ? built : withText(seen, built);
	}

	/** The learned look (model, skin, colour) carrying the built name and lore. */
	private static ItemStack withText(ItemStack seen, ItemStack built) {
		ItemStack s = seen.copyWithCount(built.getCount());
		s.set(DataComponents.CUSTOM_NAME, built.get(DataComponents.CUSTOM_NAME));
		s.set(DataComponents.LORE, built.getOrDefault(DataComponents.LORE, ItemLore.EMPTY));
		return keepGlint(s, built);
	}

	private static ItemStack keepGlint(ItemStack s, ItemStack built) {
		Boolean glint = built.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		if (glint != null) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
		return s;
	}

	private static ItemStack get(String key) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) ensureLoaded(mc);
		return looks.get(key);
	}

	/** The rarity a pet's lore ends with ("LEGENDARY PET"), or "" when it shows none. */
	private static String rarity(ItemStack s) {
		List<Component> lore = s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
		for (int i = lore.size() - 1; i >= 0; i--) {
			Matcher m = RARITY.matcher(Text.plain(lore.get(i)));
			if (m.find()) return norm(m.group(1));
		}
		return "";
	}

	/** "Ender Dragon", "ender_dragon" and "ENDER DRAGON" all become "enderdragon". */
	static String norm(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static void ensureLoaded(Minecraft mc) {
		if (loaded) return;
		loaded = true;
		if (!Files.exists(FILE)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(FILE, NbtAccounter.create(16L * 1024 * 1024));
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
			for (String key : root.keySet()) {
				Tag t = root.get(key);
				ItemStack.OPTIONAL_CODEC.parse(ops, t).result().filter(s -> !s.isEmpty()).ifPresent(s -> looks.putIfAbsent(key, s));
			}
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not read learned item looks", e);
		}
	}

	private static void save() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		dirty = false;
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
			CompoundTag root = new CompoundTag();
			looks.forEach((key, s) -> ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).result().ifPresent(t -> root.put(key, t)));
			Files.createDirectories(FILE.getParent());
			NbtIo.writeCompressed(root, FILE);
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.warn("Could not save learned item looks", e);
		}
	}
}
