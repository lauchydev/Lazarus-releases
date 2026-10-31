package dev.lauchy.lazarus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where a window writes its title and "Inventory", and how big its box is, so the storage overview can move the
 * labels out of sight and stretch the box over the screen, then put both back.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerLabelsAccessor {
	/** The title's left edge. */
	@Accessor("titleLabelX")
	int lazarus$titleX();

	/** The title's top edge. */
	@Accessor("titleLabelY")
	int lazarus$titleY();

	/** The "Inventory" label's left edge. */
	@Accessor("inventoryLabelX")
	int lazarus$inventoryX();

	/** The "Inventory" label's top edge. */
	@Accessor("inventoryLabelY")
	int lazarus$inventoryY();

	/** Moves the title. */
	@Mutable
	@Accessor("titleLabelX")
	void lazarus$setTitleX(int x);

	/** Moves the title. */
	@Mutable
	@Accessor("titleLabelY")
	void lazarus$setTitleY(int y);

	/** Moves the "Inventory" label. */
	@Mutable
	@Accessor("inventoryLabelX")
	void lazarus$setInventoryX(int x);

	/** Moves the "Inventory" label. */
	@Mutable
	@Accessor("inventoryLabelY")
	void lazarus$setInventoryY(int y);

	/** Resizes the window's box sideways; clicks outside it count as outside the window. */
	@Mutable
	@Accessor("imageWidth")
	void lazarus$setImageWidth(int width);

	/** Resizes the window's box up and down. */
	@Mutable
	@Accessor("imageHeight")
	void lazarus$setImageHeight(int height);
}
