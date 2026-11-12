package dev.lauchy.lazarus.mixin;

import dev.lauchy.lazarus.feature.dungeon.ChestProfit;
import dev.lauchy.lazarus.feature.dungeon.ChestRoll;
import dev.lauchy.lazarus.feature.dungeon.Terminals;
import dev.lauchy.lazarus.feature.inventory.HotSlots;
import dev.lauchy.lazarus.feature.inventory.MissingAccessories;
import dev.lauchy.lazarus.feature.inventory.RarityLines;
import dev.lauchy.lazarus.feature.inventory.SlotLock;
import dev.lauchy.lazarus.feature.inventory.StorageOverview;
import dev.lauchy.lazarus.feature.staff.DashboardReader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
	/** The Flag Dashboard is read behind your back: while it is, the screen you came from draws in its place. */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenRead(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.cover(g, mouseX, mouseY, delta, false);
		ci.cancel();
	}

	/** A click on a menu read behind your back goes to the screen drawn in its place, never to a head or button in it. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.click(event, doubleClick);
		cir.setReturnValue(true);
	}

	/** So does letting a button go. */
	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.release(event);
		cir.setReturnValue(true);
	}

	/** Drags never reach the hidden menu, so none can move an item in it. */
	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (DashboardReader.hiding()) cir.setReturnValue(true);
	}

	/** The wheel scrolls the screen drawn in the hidden menu's place. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenScroll(double mx, double my, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.scroll(mx, my, dx, dy);
		cir.setReturnValue(true);
	}

	/** Keys go to the screen drawn in the hidden menu's place, so Esc or the inventory key never close the menu mid-read. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void lazarus$hiddenKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!DashboardReader.hiding()) return;
		DashboardReader.key(event);
		cir.setReturnValue(true);
	}

	/** Every way a window moves an item goes through slotClicked; cancelling here sends nothing. */
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void lazarus$slotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (SlotLock.INSTANCE != null && SlotLock.INSTANCE.intercept(screen, slot, button, input)) ci.cancel();
		else if (HotSlots.INSTANCE != null && HotSlots.INSTANCE.intercept(screen, slot, input)) ci.cancel();
		else if (Terminals.INSTANCE != null && Terminals.INSTANCE.intercept(screen, slot, button)) ci.cancel();
	}

	/**
	 * Where the storage overview draws when the chest panel can't be hidden, and Missing Accessories and Chest Profit their panels:
	 * over the window's panel, under its labels, slots and tooltips, so a tooltip asked for here shows.
	 */
	@Inject(method = "extractContents", at = @At("HEAD"), require = 0)
	private void lazarus$storageOverview(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (StorageOverview.INSTANCE != null) StorageOverview.INSTANCE.backdrop((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY, true);
		if (MissingAccessories.INSTANCE != null) MissingAccessories.INSTANCE.backdrop((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY);
		if (ChestProfit.INSTANCE != null) ChestProfit.INSTANCE.backdrop((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY);
	}

	/** No item tooltips through the chest roll: it would give the chest away before it lands. */
	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
	private void lazarus$hideTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if (ChestRoll.covering((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
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
