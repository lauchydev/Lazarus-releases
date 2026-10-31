package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.inventory.HotSlots;
import dev.lauchy.lazarus.feature.inventory.MissingAccessories;
import dev.lauchy.lazarus.feature.inventory.RarityLines;
import dev.lauchy.lazarus.feature.inventory.SlotLock;
import dev.lauchy.lazarus.feature.inventory.StorageOverview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
	/** Every way a window moves an item goes through slotClicked; cancelling here sends nothing. */
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void lazarus$slotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (SlotLock.INSTANCE != null && SlotLock.INSTANCE.intercept(screen, slot, button, input)) ci.cancel();
		else if (HotSlots.INSTANCE != null && HotSlots.INSTANCE.intercept(screen, slot, input)) ci.cancel();
	}

	/**
	 * Where the storage overview draws when the chest panel can't be hidden, and Missing Accessories its panel:
	 * over the window's panel, under its labels, slots and tooltips, so a tooltip asked for here shows.
	 */
	@Inject(method = "extractContents", at = @At("HEAD"), require = 0)
	private void lazarus$storageOverview(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (StorageOverview.INSTANCE != null) StorageOverview.INSTANCE.backdrop((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY, true);
		if (MissingAccessories.INSTANCE != null) MissingAccessories.INSTANCE.backdrop((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY);
	}

	/** The storage overview draws its live slots bigger, scaled around each slot's corner. */
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void lazarus$slotScale(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (StorageOverview.INSTANCE != null) StorageOverview.INSTANCE.scaleSlot((AbstractContainerScreen<?>) (Object) this, g, slot, true);
	}

	/** Rarity marks, slot locks and storage search dimming drawn with each slot (already offset to the window), so tooltips cover them. */
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void lazarus$slotMark(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (RarityLines.INSTANCE != null) RarityLines.INSTANCE.slotMark(g, slot.getItem(), slot.x, slot.y);
		if (SlotLock.INSTANCE != null) SlotLock.INSTANCE.slotMark((AbstractContainerScreen<?>) (Object) this, g, slot);
		if (StorageOverview.INSTANCE != null) {
			StorageOverview.INSTANCE.slotMark((AbstractContainerScreen<?>) (Object) this, g, slot);
			StorageOverview.INSTANCE.scaleSlot((AbstractContainerScreen<?>) (Object) this, g, slot, false);
		}
	}
}
