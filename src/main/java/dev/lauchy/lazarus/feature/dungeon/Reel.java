package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * The strip of cards a case-opening roll spins past its marker, and where it is at a moment: fast at
 * first, easing out to a stop on the winning card. Pure, so it is tested without the game.
 */
final class Reel {
	/** Cards on the strip, and where the winner sits: far enough along for a long spin, with a few after it. */
	static final int CARDS = 50, WINNER = 44;
	/** The marker stops this far into the winning card, at the least and the most: never on its edge. */
	static final double LAND_MIN = 0.15, LAND_MAX = 0.85;

	private Reel() {
	}

	/**
	 * The strip: {@code winner} at {@link #WINNER}, and every other card drawn from {@code pool} at
	 * random, each as likely as its weight (a cheap item turns up often, a dear one rarely).
	 */
	static <T> List<T> fill(List<T> pool, ToDoubleFunction<T> weight, T winner, Random rnd) {
		List<T> out = new ArrayList<>(CARDS);
		double total = 0;
		for (T t : pool) total += Math.max(0, weight.applyAsDouble(t));
		for (int i = 0; i < CARDS; i++) {
			if (i == WINNER || pool.isEmpty() || total <= 0) {
				out.add(winner);
				continue;
			}
			double r = rnd.nextDouble() * total;
			T pick = pool.get(pool.size() - 1);
			for (T t : pool) {
				r -= Math.max(0, weight.applyAsDouble(t));
				if (r < 0) {
					pick = t;
					break;
				}
			}
			out.add(pick);
		}
		return out;
	}

	/** Where the strip comes to rest, in pixels: the marker {@code land} (0 to 1) of the way into the winning card. */
	static double stop(int cardWidth, int gap, double land) {
		return WINNER * (cardWidth + gap) + cardWidth * Math.clamp(land, LAND_MIN, LAND_MAX);
	}

	/** Cards already left of the marker when the roll starts, so the strip is full from the first frame. */
	static final int START = 5;

	/** Where the strip starts, in pixels: {@link #START} cards along. */
	static double start(int cardWidth, int gap) {
		return START * (cardWidth + gap);
	}

	/**
	 * How far the strip has moved after {@code t} of {@code total} milliseconds, from {@code from} to
	 * {@code stop}: quick at first, then a long slow finish.
	 */
	static double offset(double from, double stop, long t, long total) {
		if (total <= 0 || t >= total) return stop;
		if (t <= 0) return from;
		double x = (double) t / total;
		return from + (stop - from) * (1 - Math.pow(1 - x, 4));
	}

	/** The card under the marker at this offset (it may sit in the gap after one; that one still counts). */
	static int under(double offset, int cardWidth, int gap) {
		return (int) Math.floor(offset / (cardWidth + gap));
	}
}
