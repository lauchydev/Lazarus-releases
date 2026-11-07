package dev.lauchy.lazarus.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the storage overview move a window's real slots onto its cards, so they keep working where they're drawn. */
@Mixin(Slot.class)
public interface SlotAccessor {
	/** Moves the slot sideways, relative to the window's corner. */
	@Mutable
	@Accessor("x")
	void lazarus$setX(int x);

	/** Moves the slot up or down, relative to the window's corner. */
	@Mutable
	@Accessor("y")
	void lazarus$setY(int y);
}
