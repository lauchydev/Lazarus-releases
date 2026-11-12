package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.core.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Terminal aura, a setting of the bannable Auto Solve ({@link AutoSolve}): a terminal still to do that comes within
 * your reach is right-clicked for you, and its window is never put on screen. A terminal is its block, a plain
 * command block; one that's done turns into a purple (repeating) command block, and is left alone. Its label isn't
 * gone by: the labels read differently from terminal to terminal. The game still holds the window (the server
 * thinks it's open and sends its panes), so {@link Terminals} solves it as it would one you can see, while you keep
 * walking and looking about. So that you know it's happening, chat says which terminal it opened and the terminal
 * is boxed and labelled until it's done. The server closes it when it's done; one that sits unanswered is closed
 * after a while. A terminal you open yourself is shown as ever.
 */
public final class TerminalAura {
	/** How long a click of its own is waited on for a window, how long before the same terminal is tried again, and how long between any two tries. */
	private static final long OPEN_MS = 2500, AGAIN_MS = 900, BETWEEN_MS = 400;
	/** How long a hidden terminal may sit with nothing clicked before it's closed; the timed one waits on its moving pane, so longer. */
	private static final long IDLE_MS = 12_000, IDLE_TIMED_MS = 30_000;
	private static final int COLOUR = 0xFF55FFFF;

	/** The terminal held out of sight, and its title. */
	private static AbstractContainerMenu hidden;
	private static String title = "";
	/** When it last clicked a terminal open, when the hidden one was last opened or clicked in, and when it may next try. */
	private static long clickedAt, activeAt, nextTryAt;
	/** Where the terminal it last clicked is, for the box and the label while it's being solved. */
	private static Vec3 target;
	/** When each terminal was last tried. */
	private static final Map<BlockPos, Long> tried = new HashMap<>();
	private static int ticks;

	private TerminalAura() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.level == null || mc.gameMode == null) {
				hidden = null;
				tried.clear();
				return;
			}
			long now = Util.getMillis();
			if (hidden != null) {
				if (mc.player.containerMenu != hidden) hidden = null; // the server closed it: done
				else if (!AutoSolve.aura() || now - activeAt > (title.equals("Click the button on time!") ? IDLE_TIMED_MS : IDLE_MS)) {
					mc.player.closeContainer();
					hidden = null;
				}
				return;
			}
			if (!AutoSolve.aura() || !Area.inDungeon() || mc.screen != null || mc.player.containerMenu != mc.player.inventoryMenu) return;
			if (++ticks % 2 != 0 || now < nextTryAt) return;
			Vec3 eye = mc.player.getEyePosition();
			double reach = AutoSolve.auraReach();
			int span = (int) Math.ceil(reach) + 1;
			BlockPos around = BlockPos.containing(eye), nearest = null;
			double nearestD = reach * reach;
			// The nearest plain command block within your reach that wasn't tried a moment ago.
			for (BlockPos p : BlockPos.betweenClosed(around.offset(-span, -span, -span), around.offset(span, span, span))) {
				if (!mc.level.getBlockState(p).is(Blocks.COMMAND_BLOCK)) continue;
				double far = new AABB(p).distanceToSqr(eye);
				Long before = tried.get(p);
				if (far > nearestD || before != null && now - before < AGAIN_MS) continue;
				nearestD = far;
				nearest = p.immutable();
			}
			if (nearest == null) return;
			// The face of the block that's towards you, as a click of your own would land on.
			Vec3 middle = Vec3.atCenterOf(nearest), to = eye.subtract(middle);
			Direction face = Direction.getApproximateNearest(to.x, to.y, to.z);
			Vec3 on = middle.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(on, face, nearest, false));
			mc.player.swing(InteractionHand.MAIN_HAND);
			Lazarus.LOG.info("[Aura] Right-clicked the terminal at {} on its {} face, {} blocks off", nearest, face, String.format(Locale.ROOT, "%.1f", Math.sqrt(nearestD)));
			target = middle;
			tried.put(nearest, now);
			clickedAt = now;
			nextTryAt = now + BETWEEN_MS;
		});
		World.paint(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (hidden == null || target == null || menu(mc) == null) return;
			World.box(AABB.ofSize(target, 1.1, 1.1, 1.1), COLOUR, true, false, true);
			World.text(target.add(0, 1.1, 0), "Solving: " + title, COLOUR);
		});
	}

	/** A label's first line, which says what it's the label of; a second line under it ("CLICK HERE") says nothing more. */
	static String line(String label) {
		for (String part : label.split("\\R")) if (!part.isBlank()) return part.trim();
		return "";
	}

	/**
	 * Whether a window about to be put on screen is to be kept off it: a terminal's, that came of a click of the
	 * aura's own, or that replaces the one it's already holding (some servers open the terminal afresh after each
	 * click). The game already holds the window by then; only the screen is left out.
	 */
	public static boolean hide(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> cs) || !AutoSolve.aura()) return false;
		String named = Text.plain(cs.getTitle());
		if (!Terminals.titled(named)) return false;
		long now = Util.getMillis();
		boolean again = hidden != null && now - activeAt < IDLE_TIMED_MS;
		boolean opened = now - clickedAt < OPEN_MS && Minecraft.getInstance().screen == null;
		if (!again && !opened) return false;
		if (!again) {
			// So you know it's at work: which terminal it has opened, and the clock for how long it takes.
			Message.lazarus().info("Terminal aura opened ").highlight(named).info(".").send();
			TerminalStatus.opened();
			Lazarus.LOG.info("[Aura] Opened '{}' and kept it off screen", named);
		}
		hidden = cs.getMenu();
		title = named;
		activeAt = now;
		return true;
	}

	/** The terminal held out of sight, while the game still holds it; else null. */
	static AbstractContainerMenu menu(Minecraft mc) {
		return hidden != null && mc.player != null && mc.player.containerMenu == hidden ? hidden : null;
	}

	/** The title of the terminal held out of sight. */
	static String title() {
		return title;
	}

	/** A click was made in a terminal: the hidden one isn't sitting idle. */
	static void touched() {
		activeAt = Util.getMillis();
	}
}
