package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

/** Reading Dragsim item names and lore as plain text. */
public final class Items {
	private Items() {
	}

	public static String name(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "" : Text.plain(stack.getHoverName());
	}

	public static List<String> lore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return List.of();
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Text::plain).toList();
	}

	/** The dragsim.co catalog id: the server's own tag when it carries one, else looked up by name and lore. */
	public static String id(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			for (String key : new String[]{"entry", "id", "item_id"}) {
				String v = tag.getStringOr(key, "").toLowerCase(Locale.ROOT);
				if (!v.isEmpty() && Catalog.item(v) != null) return v;
			}
		}
		return Catalog.id(name(stack), lore(stack));
	}

	/** The NPC price the server stamped on the item ({@code item_data.npc_sell_price}), or -1 when it carries none. */
	public static long npcSell(ItemStack stack) {
		return itemData(stack).getLongOr("npc_sell_price", -1);
	}

	/** The item's own name as the server stamped it ({@code item_data.item_name}, no reforge or stars), or "". */
	public static String itemName(ItemStack stack) {
		return itemData(stack).getStringOr("item_name", "");
	}

	private static final java.util.regex.Pattern RARITY = java.util.regex.Pattern.compile("\\b(VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b");

	/**
	 * The item's rarity ("LEGENDARY"): as the server stamped it, else from the rarity line at
	 * the foot of its lore (a menu's preview items carry only that); "" when it shows none.
	 */
	public static String rarity(ItemStack stack) {
		String stamped = itemData(stack).getStringOr("rarity", "");
		if (!stamped.isEmpty()) return stamped.toUpperCase(Locale.ROOT);
		List<String> lore = lore(stack);
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).trim();
			if (line.isEmpty() || !line.equals(line.toUpperCase(Locale.ROOT))) continue;
			java.util.regex.Matcher m = RARITY.matcher(line);
			if (m.find()) return m.group(1).replace(' ', '_');
		}
		return "";
	}

	/**
	 * Everything the server stamped on the item ({@code entry}, {@code item_data},
	 * {@code item_modifiers}, {@code extra}…) in the shape dragsim.co's site uses; empty when it
	 * carries nothing.
	 */
	public static JsonObject data(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return new JsonObject();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return new JsonObject();
		JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data.copyTag());
		return json instanceof JsonObject o ? o : new JsonObject();
	}

	private static CompoundTag itemData(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return new CompoundTag();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("item_data");
	}

	private static final java.util.regex.Pattern UUID_TEXT = java.util.regex.Pattern.compile(
			"[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");

	/**
	 * What sets this one item apart from look-alikes: a UUID the server stamped on it, found
	 * under a key naming one (uuid, uid…) or as any UUID-shaped value in its custom data.
	 * Null when the server gives it none, so identical items can't be told apart.
	 */
	public static String unique(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : findUuid(data.copyTag(), 0);
	}

	private static String findUuid(net.minecraft.nbt.Tag tag, int depth) {
		if (depth > 4 || tag == null) return null;
		if (tag instanceof CompoundTag c) {
			for (String key : c.keySet()) {
				String k = key.toLowerCase(Locale.ROOT);
				if ((k.contains("uuid") || k.equals("uid")) && c.get(key) instanceof net.minecraft.nbt.StringTag st) {
					java.util.regex.Matcher m = UUID_TEXT.matcher(st.value());
					if (m.find()) return m.group().replace("-", "").toLowerCase(Locale.ROOT);
				}
			}
			for (String key : c.keySet()) {
				String found = findUuid(c.get(key), depth + 1);
				if (found != null) return found;
			}
		} else if (tag instanceof net.minecraft.nbt.StringTag st) {
			java.util.regex.Matcher m = UUID_TEXT.matcher(st.value());
			if (m.matches()) return m.group().replace("-", "").toLowerCase(Locale.ROOT);
		}
		return null;
	}
}
