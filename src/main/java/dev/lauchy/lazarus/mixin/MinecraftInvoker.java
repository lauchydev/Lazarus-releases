package dev.lauchy.lazarus.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own attack and use, as a mouse button starts them, for the Auto Clicker to fire at its own rate. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	/** One left click: an attack, a block broken into, or a swing at nothing; false when the miss timer refused it. */
	@Invoker("startAttack")
	boolean lazarus$startAttack();

	/** One right click: using what's held, or a block or entity. */
	@Invoker("startUseItem")
	void lazarus$startUseItem();
}
