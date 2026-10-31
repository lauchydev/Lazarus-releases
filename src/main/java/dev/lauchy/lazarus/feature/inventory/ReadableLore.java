package dev.lauchy.lazarus.feature.inventory;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Recolour;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/**
 * Tooltip text too dark to read on the tooltip's dark background, such as "Dyed: Dye of the
 * Pure Black" written in the dye's own black, lifted to a readable shade of its colour.
 */
public final class ReadableLore extends Feature {
	public ReadableLore() {
		super("readableLore", "Readable Lore", "Lifts tooltip text too dark to read, like a black dye's name, to a readable shade.", Category.INVENTORY, true);
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (enabled()) lines.replaceAll(Recolour::readable);
		});
	}
}
