package dev.lauchy.lazarus.feature.dungeon;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The run of a Simon Says device as it's shown and pressed. Each round the device flashes the whole run again from
 * its first tile, one longer than the last, the flashes about half a second apart; then you press them in order.
 * A flash after a quiet spell, or after a button has been pressed, is the start of a new showing, so the run is taken afresh from there; a press of the
 * button that's next moves on a step. (Only
 * {@code BlockPos}, a plain value, is from the game; it's tested on its own.)
 */
final class SimonSteps {
	/** A flash this long after the one before starts a new showing; flashes of one showing come closer than this. */
	static final long NEW_SHOWING_MS = 1200;

	private final List<BlockPos> run = new ArrayList<>();
	private int done;
	private long lastFlashAt;
	/** Whether a button has gone down since the last flash: the next flash is then a new showing. */
	private boolean pressedSince;

	/** A tile lit that wasn't just pressed: the next of the showing, or the first of a new one. */
	void flashed(BlockPos button, long now) {
		long since = now - lastFlashAt;
		if (lastFlashAt == 0 || since > NEW_SHOWING_MS || pressedSince) {
			run.clear();
			done = 0;
			pressedSince = false;
		}
		// The same tile seen lit twice within a moment is one flash.
		if (run.isEmpty() || !run.getLast().equals(button) || since > 250) run.add(button);
		lastFlashAt = now;
	}

	/** A button went down: a step on when it's the one that was next. */
	void pressed(BlockPos button) {
		pressedSince = true;
		if (done < run.size() && run.get(done).equals(button)) done++;
	}

	/** The button to press now, or null when the run is all pressed (or none has been shown). */
	BlockPos next() {
		return done < run.size() ? run.get(done) : null;
	}

	/** How long the run is. */
	int length() {
		return run.size();
	}

	/** Forgets the run: the device has started over. */
	void clear() {
		run.clear();
		done = 0;
		lastFlashAt = 0;
		pressedSince = false;
	}
}
