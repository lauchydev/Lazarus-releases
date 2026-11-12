package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import net.minecraft.util.Util;

/**
 * Auto Solve: the terminals and the gates' devices clicked for you, one click at a time with a delay, by the
 * answers their own solvers work out ({@link Terminals}, {@link SimonSays}, {@link ArrowAlign}, {@link Lights}).
 * Each click waits for the server to answer the one before, since a click sent before that is one the server may
 * not take. With Terminal aura on, a terminal within reach is opened for you and solved without its window ever
 * showing, so you keep moving ({@link TerminalAura}).
 * This plays the game for you, which Dragsim can ban for: it's a Lazarus+ feature, off unless you turn it on, and
 * marked BANNABLE in the menu. Each kind can be left to you with its own switch.
 */
public final class AutoSolve extends Feature {
	/** What it can click for you. */
	public enum Kind { TERMINALS, SIMON_SAYS, ARROW_ALIGN, LIGHTS }

	private static AutoSolve instance;

	private final IntSetting delay = add(new IntSetting("delay", "Delay", "About how long between its clicks in a terminal; each varies a little.", 250, 120, 800, "ms"));
	private final IntSetting firstClick = add(new IntSetting("firstClick", "First click delay", "How long a terminal just opened is left before its first click. Too soon and the server doesn't take it.", 400, 0, 1500, "ms"));
	private final IntSetting simonDelay = add(new IntSetting("simonDelay", "Simon Says delay", "About how long between its presses of the Simon Says buttons. Too fast and the server drops a press, and the device starts over.", 450, 100, 1500, "ms"));
	private final IntSetting arrowDelay = add(new IntSetting("arrowDelay", "Arrow delay", "How long between its rounds of turns on the arrows. At 0 it turns them twenty times a second.", 50, 0, 1000, "ms"));
	private final IntSetting deviceDelay = add(new IntSetting("deviceDelay", "Lights delay", "About how long between its flicks of the lights device's levers.", 450, 100, 1500, "ms"));
	private final IntSetting arrowClicks = add(new IntSetting("arrowClicks", "Arrows at once", "How many arrows it turns in the same round, one turn each. Higher is faster.", 4, 1, 10, ""));
	private final IntSetting auraReach = add(new IntSetting("auraReach", "Aura reach", "How near a terminal has to be for Terminal aura to open it.", 4, 2, 6, " blocks"));
	private final BoolSetting aura = add(new BoolSetting("aura", "Terminal aura", "Open a terminal within your reach for you and solve it without showing its window, so you can keep moving. Needs Terminals on.", false));
	private final BoolSetting terminals = add(new BoolSetting("terminals", "Terminals", "Click the terminals' panes.", true));
	private final BoolSetting simon = add(new BoolSetting("simonSays", "Simon Says", "Press the Simon Says buttons in order, once the tiles have stopped flashing.", true));
	private final BoolSetting arrows = add(new BoolSetting("arrowAlign", "Arrow Align", "Turn the arrows of the arrow device that are within your reach, several at once.", true));
	private final BoolSetting lights = add(new BoolSetting("lights", "Lights", "Flick the six levers of the lights device that count, when they're within your reach.", true));

	/** When it may next click each kind of device: each keeps its own pace. */
	private final long[] nextAt = new long[Kind.values().length];

	public AutoSolve() {
		super("autoSolve", "Auto Solve", "Clicks terminals and the gates' devices for you, one click at a time with a delay. It plays for you: servers ban for it.", Category.DUNGEONS, false);
		instance = this;
	}

	@Override
	public boolean plus() {
		return true;
	}

	/** It plays the game for you, which Dragsim's rules can ban for. */
	@Override
	public boolean bannable() {
		return true;
	}

	@Override
	public void init() {
		TerminalAura.init();
	}

	/** Whether terminals within reach are to be opened for you and solved out of sight. */
	static boolean aura() {
		AutoSolve a = instance;
		return a != null && a.enabled() && a.terminals.on() && a.aura.on();
	}

	/** Whether this kind of thing is to be clicked for you: the feature is on, yours to use, and that kind is switched on. */
	static boolean on(Kind kind) {
		AutoSolve a = instance;
		if (a == null || !a.enabled()) return false;
		return switch (kind) {
			case TERMINALS -> a.terminals.on();
			case SIMON_SAYS -> a.simon.on();
			case ARROW_ALIGN -> a.arrows.on();
			case LIGHTS -> a.lights.on();
		};
	}

	/** How many arrows may be turned in one tick. */
	static int arrowClicks() {
		AutoSolve a = instance;
		return a == null ? 1 : a.arrowClicks.get();
	}

	/** How near, in blocks, a terminal has to be for the aura to open it. */
	static double auraReach() {
		AutoSolve a = instance;
		return a == null ? 4 : a.auraReach.get();
	}

	/** Whether a device of this kind may be clicked now, at the pace set for it; saying yes starts the wait before its next click. */
	static boolean paced(Kind kind) {
		AutoSolve a = instance;
		if (a == null || !a.enabled() || Util.getMillis() < a.nextAt[kind.ordinal()]) return false;
		int delay = switch (kind) {
			case SIMON_SAYS -> a.simonDelay.get();
			case ARROW_ALIGN -> a.arrowDelay.get();
			default -> a.deviceDelay.get();
		};
		a.nextAt[kind.ordinal()] = Util.getMillis() + Math.round(delay * (0.85 + Math.random() * 0.3));
		return true;
	}

	/** The wait before the first click in a terminal just opened. */
	static long firstPause() {
		AutoSolve a = instance;
		return Math.round((a == null ? 400 : a.firstClick.get()) * (0.9 + Math.random() * 0.2));
	}

	/** The wait before the next click: the delay set, a third more or less. */
	static long pause() {
		AutoSolve a = instance;
		return Math.round((a == null ? 250 : a.delay.get()) * (0.7 + Math.random() * 0.6));
	}
}
