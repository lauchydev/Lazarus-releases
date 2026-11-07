package dev.lauchy.lazarus.feature.slayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts a slayer boss's silent rewards from your inventory. The server puts items such as
 * Null Ovoid or Revenant Viscera straight into your inventory with no chat line, so the only
 * way to count them is to watch how many you carry: a count a second before the kill is the
 * baseline, and whatever more you hold three seconds after it is the reward. Anything the
 * server did announce in that window is taken off, so it isn't counted twice.
 */
final class RewardWindow {
	/** Snapshots are kept this long, so the baseline can be taken from before the kill. */
	static final long KEEP_MS = 3_000;
	/** The baseline is the newest snapshot at least this old: a pickup in the same second as the kill is still a reward. */
	static final long BASELINE_MS = 1_000;
	/** The reward lands within this of the kill. */
	static final long OPEN_MS = 3_000;

	private record Snap(long at, Map<String, Integer> counts) {
	}

	private final Deque<Snap> snaps = new ArrayDeque<>();
	private final Map<String, Integer> gain = new LinkedHashMap<>(), announced = new LinkedHashMap<>();
	private Map<String, Integer> baseline;
	private long until = -1;

	/** What you carry now, by item, for the items watched. */
	void seen(long now, Map<String, Integer> counts) {
		snaps.addLast(new Snap(now, Map.copyOf(counts)));
		while (!snaps.isEmpty() && now - snaps.peekFirst().at > KEEP_MS) snaps.removeFirst();
		if (!watching(now)) return;
		for (var e : counts.entrySet()) {
			int more = e.getValue() - baseline.getOrDefault(e.getKey(), 0);
			if (more > gain.getOrDefault(e.getKey(), 0)) gain.put(e.getKey(), more);
		}
	}

	/**
	 * The boss died: fixes the baseline and starts watching for the reward. With no snapshot from
	 * before the kill there is no baseline, so nothing is watched: what you already carried would
	 * otherwise all count as the reward.
	 */
	void open(long now) {
		Snap base = null;
		for (Snap s : snaps) if (now - s.at >= BASELINE_MS) base = s;
		if (base == null) base = snaps.peekFirst();
		if (base == null) return;
		baseline = base.counts;
		gain.clear();
		announced.clear();
		until = now + OPEN_MS;
	}

	/** The kill happened and the reward is still being waited for. */
	boolean watching(long now) {
		return until >= 0 && now <= until;
	}

	/** The window has passed and its result is waiting to be taken with {@link #close()}. */
	boolean due(long now) {
		return until >= 0 && now > until;
	}

	/** The server announced {@code n} of {@code item} inside the window, so they're already counted. */
	void announced(String item, int n) {
		announced.merge(item, n, Integer::sum);
	}

	/** The silent reward: each watched item you gained, less what was announced; empty when nothing was. */
	Map<String, Integer> close() {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (var e : gain.entrySet()) {
			int n = e.getValue() - announced.getOrDefault(e.getKey(), 0);
			if (n > 0) out.put(e.getKey(), n);
		}
		gain.clear();
		announced.clear();
		until = -1;
		return out;
	}
}
