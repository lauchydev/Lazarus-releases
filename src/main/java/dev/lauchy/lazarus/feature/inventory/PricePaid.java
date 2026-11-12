package dev.lauchy.lazarus.feature.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.Purchases;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.ZoneId;

/**
 * What you paid for an item you bought at auction, as a line in its tooltip while Shift is held, beside the
 * recipe and the price breakdown. The price comes from dragsim.co's record of sales ({@link Purchases}), which
 * the Price Database reads, so purchases are learned while it is on; they're kept in config/lazarus/purchases.json.
 */
public final class PricePaid extends Feature {
	/** The one instance, which the Price Database hands every feed it reads. */
	public static PricePaid INSTANCE;
	private static final Path FILE = Config.dir().resolve("purchases.json");

	private final BoolSetting shift = add(new BoolSetting("shift", "Only with Shift", "Show the price paid only while Shift is held.", true));

	private final Purchases purchases = new Purchases();

	public PricePaid() {
		super("pricePaid", "Price Paid", "Items you bought at auction show what you paid, while Shift is held. Needs the Price Database on.", Category.INVENTORY, false);
		INSTANCE = this;
	}

	@Override
	public void init() {
		if (Config.readJson(FILE) instanceof JsonObject saved) purchases.load(saved);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!enabled() || shift.on() && !Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT) && !Keys.down(GLFW.GLFW_KEY_RIGHT_SHIFT)) return;
			JsonObject item = Items.data(stack);
			if (!item.has("entry")) {
				String id = Items.id(stack);
				if (id == null) return;
				item.addProperty("entry", id);
			}
			String uuid = Items.unique(stack);
			if (purchases.tie(item, uuid)) Config.writeJson(FILE, purchases.save());
			Purchases.Paid paid = purchases.paid(item, uuid);
			if (paid != null) lines.add(Component.literal("§7Paid: §6" + Text.compact(paid.unit()) + " §8· " + paid.day(ZoneId.systemDefault())));
		});
	}

	/**
	 * One of dragsim.co's feeds, as the Price Database read it: your purchases in it are remembered whether or not
	 * this is switched on, so they're there the day it is.
	 */
	public void feed(JsonElement json) {
		Minecraft mc = Minecraft.getInstance();
		if (!(json instanceof JsonObject feed) || mc.player == null) return;
		if (purchases.read(feed, mc.player.getName().getString())) Config.writeJson(FILE, purchases.save());
	}
}
