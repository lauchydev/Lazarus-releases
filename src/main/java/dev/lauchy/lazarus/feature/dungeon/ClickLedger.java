package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * The clicks made in a terminal that the server hasn't answered yet. A click takes a ping to land and its answer
 * another to come back, and in between the window still shows the slot as it was: so the clicks already on their way
 * have to be counted, or a slot that needs two gets a third, and one that needs one gets two. Each slot keeps its
 * clicks in flight; one is taken off when the slot is seen to change (that's the answer), and one that's had no
 * answer in a while is forgotten, so a click the server dropped doesn't lock the slot for good. No Minecraft
 * types, so it's tested on its own.
 */
final class ClickLedger {
	/** A slot's clicks in flight: what the slot held when last looked at, how far round its wheel it was (-1 when it has none), and when each click was made. */
	private static final class Slot {
		int held, step;
		final ArrayDeque<Long> clicks = new ArrayDeque<>();
	}

	private final Map<Integer, Slot> slots = new HashMap<>();
	private final int wheel;

	/** A ledger for a terminal whose panes turn round a wheel of this many colours (the colour terminal), or 0 for one where a click simply finishes a slot. */
	ClickLedger(int wheel) {
		this.wheel = wheel;
	}

	/** How many of a slot's clicks are still in flight, as of now: what it holds, and how far round its wheel it is (-1 when it has none). */
	int flying(int slot, int held, int step, long now, long patience) {
		Slot s = slots.get(slot);
		if (s == null) return 0;
		while (!s.clicks.isEmpty() && now - s.clicks.peekFirst() > patience) s.clicks.pollFirst(); // never answered: dropped
		if (s.held != held) {
			// The slot changed: that many of its clicks have landed. A pane that has no wheel is done with one.
			int landed = wheel > 0 && s.step >= 0 && step >= 0 ? Math.floorMod(step - s.step, wheel) : s.clicks.size();
			for (int i = 0; i < landed && !s.clicks.isEmpty(); i++) s.clicks.pollFirst();
			s.held = held;
			s.step = step;
		}
		return s.clicks.size();
	}

	/**
	 * Takes one of the clicks a slot still needs, by what the window shows: true when it may be made, and it's then
	 * counted as in flight. False when the clicks already on their way are all it needs.
	 */
	boolean take(int slot, int held, int step, int needs, long now, long patience) {
		if (needs - flying(slot, held, step, now, patience) <= 0) return false;
		Slot s = slots.computeIfAbsent(slot, k -> {
			Slot made = new Slot();
			made.held = held;
			made.step = step;
			return made;
		});
		s.clicks.addLast(now);
		return true;
	}

	void clear() {
		slots.clear();
	}
}
