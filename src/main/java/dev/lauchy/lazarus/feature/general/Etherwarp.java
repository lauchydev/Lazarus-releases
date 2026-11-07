package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * While sneaking with an Etherwarp item, boxes the block you would land on. Etherwarp
 * stops at the first solid block on your look line within range, and only works if the
 * two blocks above are free; that walk is repeated here each frame. Optionally a red box
 * shows a target you can't stand on.
 */
public final class Etherwarp extends Feature {
	private static final Pattern RANGE = Pattern.compile("up to (\\d+) blocks");

	private final ColorSetting color = add(new ColorSetting("color", "Colour", "Colour of a valid target.", 0xFF55FFFF));
	private final BoolSetting fill = add(new BoolSetting("fill", "Filled", "Fill the box as well as outlining it.", true));
	private final BoolSetting showBlocked = add(new BoolSetting("showBlocked", "Show blocked targets", "Box in red where there is no room to land.", false));

	private ItemStack cachedStack;
	private int cachedRange;

	public Etherwarp() {
		super("etherwarp", "Etherwarp Outline", "Shows where Etherwarp will put you before you click.", Category.GENERAL, true);
	}

	@Override
	public void init() {
		World.paint(this::paint);
	}

	private void paint() {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || mc.screen != null || !mc.player.isShiftKeyDown()) return;
		int range = range(mc.player.getMainHandItem());
		if (range <= 0) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(pt), dir = mc.player.getViewVector(pt);
		BlockPos hit = firstSolid(mc.level, eye, dir, range);
		if (hit == null) return;
		boolean room = passable(mc.level, hit.above()) && passable(mc.level, hit.above(2));
		if (!room && !showBlocked.on()) return;
		VoxelShape shape = mc.level.getBlockState(hit).getShape(mc.level, hit);
		AABB box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(hit);
		World.box(box, room ? color.argb() : 0xFFFF5555, true, fill.on(), true);
	}

	/** The range on the item's Etherwarp ability, or 0. Cached per stack instance. */
	private int range(ItemStack stack) {
		if (stack == cachedStack) return cachedRange;
		cachedStack = stack;
		cachedRange = 0;
		boolean inAbility = false;
		for (String line : Items.lore(stack)) {
			if (line.contains("Etherwarp")) inAbility = true;
			if (!inAbility) continue;
			Matcher m = RANGE.matcher(line);
			if (m.find()) {
				cachedRange = Integer.parseInt(m.group(1));
				break;
			}
		}
		return cachedRange;
	}

	/** Voxel walk (Amanatides-Woo) along the look line to the first block with collision. */
	static BlockPos firstSolid(Level level, Vec3 from, Vec3 dir, int range) {
		int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
		int stepX = (int) Math.signum(dir.x), stepY = (int) Math.signum(dir.y), stepZ = (int) Math.signum(dir.z);
		double dX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.x);
		double dY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.y);
		double dZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.z);
		double tX = boundary(from.x, stepX) * dX, tY = boundary(from.y, stepY) * dY, tZ = boundary(from.z, stepZ) * dZ;
		double t = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (t <= range) {
			pos.set(x, y, z);
			if (!passable(level, pos)) return pos.immutable();
			if (tX <= tY && tX <= tZ) {
				t = tX;
				tX += dX;
				x += stepX;
			} else if (tY <= tZ) {
				t = tY;
				tY += dY;
				y += stepY;
			} else {
				t = tZ;
				tZ += dZ;
				z += stepZ;
			}
		}
		return null;
	}

	private static double boundary(double p, int step) {
		if (step == 0) return Double.MAX_VALUE;
		double f = p - Math.floor(p);
		return step > 0 ? 1 - f : f;
	}

	private static boolean passable(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
