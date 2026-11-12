package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;

/**
 * A dragon's hitboxes, and nothing else's: the game's own hitbox view (F3+B) boxes every mob, arrow and item on
 * screen, which in a dragon fight buries the one thing you want to see. A dragon is hit on its parts (head, neck,
 * body, tail, wings), not the big box round the whole of it, so each part is outlined, the head in its own colour.
 */
public final class DragonHitbox extends Feature {
	private final ColorSetting colour = add(new ColorSetting("colour", "Colour", "The colour of the body, tail and wing boxes.", 0xFFFFFFFF));
	private final ColorSetting head = add(new ColorSetting("head", "Head colour", "The colour of the head and neck boxes.", 0xFFFF5555));
	private final BoolSetting whole = add(new BoolSetting("whole", "Whole box", "Also outline the one big box round the whole dragon.", false));
	private final BoolSetting through = add(new BoolSetting("throughWalls", "See through walls", "Show the boxes through blocks.", false));

	public DragonHitbox() {
		super("dragonHitbox", "Dragon Hitbox", "Outlines a dragon's hitboxes, and no other mob's.", Category.END, false);
	}

	@Override
	public void init() {
		World.paint(() -> {
			if (!enabled()) return;
			for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
				if (!(e instanceof EnderDragon dragon)) continue;
				if (whole.on()) World.box(dragon.getBoundingBox(), colour.argb(), true, false, through.on());
				for (EnderDragonPart part : dragon.getSubEntities()) {
					boolean front = part == dragon.head || part.name.equals("neck");
					World.box(part.getBoundingBox(), (front ? head : colour).argb(), true, false, through.on());
				}
			}
		});
	}
}
