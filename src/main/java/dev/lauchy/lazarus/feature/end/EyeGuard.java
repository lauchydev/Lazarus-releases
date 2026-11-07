package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.IntSetting;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/**
 * In the End, a second quick right-click on the altar frame you just used is swallowed, so a
 * double click can't place an eye and immediately pull it back out. Other frames stay
 * clickable, so running down the altar isn't slowed.
 */
public final class EyeGuard extends Feature {
	private final IntSetting window = add(new IntSetting("window", "Guard time", "How long repeat clicks on the same frame are ignored.", 900, 200, 3000, "ms"));

	private BlockPos last;
	private long lastMs;

	public EyeGuard() {
		super("eyeGuard", "Protect Placed Eyes", "Ignores a repeat click that would pull out the eye you just placed.", Category.END, true);
	}

	@Override
	public void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!enabled() || !level.isClientSide()) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			// Only altar frames: guarding every block swallowed item abilities (Hyperion) used
			// while looking at the ground, since a failed block use skips the item use too.
			if (!Area.inEnd() || !level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) return InteractionResult.PASS;
			long now = Util.getMillis();
			if (pos.equals(last) && now - lastMs < window.get()) {
				lastMs = now; // holding the button keeps the frame safe
				return InteractionResult.FAIL;
			}
			last = pos;
			lastMs = now;
			return InteractionResult.PASS;
		});
		if (DragonTimer.INSTANCE != null) DragonTimer.INSTANCE.onMyEye(() -> lastMs = Util.getMillis());
	}
}
