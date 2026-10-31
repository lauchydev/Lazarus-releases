package dev.lauchy.lazarus.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One reader of the server's loot lines for every tracker. Each line is read once, and a staff
 * roll a tick before an announcement says which mob dropped it, so a zealot's eye and a slayer
 * boss's can be told apart. Trackers get every drop and decide for themselves which are theirs.
 */
public final class LootFeed {
	/** A drop credited to you: what it was, the mob the staff roll named (null when no roll was seen), and when. */
	public record Loot(Drops.Drop drop, String mob, long at) {
	}

	/** Hears every drop credited to you, in the order the server told them. */
	@FunctionalInterface
	public interface Listener {
		void loot(Loot loot);
	}

	/** A roll and the announcement of what it rolled land this close together. */
	private static final long ROLL_MS = 2_000;

	private static final List<Listener> LISTENERS = new ArrayList<>();
	/** The last staff roll that landed, waiting for the announcement of what it rolled; used once. */
	private static Drops.Roll lastRoll;
	private static long rollAt;

	private LootFeed() {
	}

	/** Starts reading chat; called once at start-up, before any tracker's drops can arrive. */
	public static void init() {
		Chat.listen(l -> {
			long now = System.currentTimeMillis();
			String legacy = legacy(l.component());
			for (String raw : l.text().split("\\R")) line(raw.trim(), legacy, now);
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> lastRoll = null);
	}

	/** The message as § codes and text, whether the server coloured it with styles or wrote the codes into the text. */
	private static String legacy(Component message) {
		StringBuilder out = new StringBuilder();
		message.visit((style, text) -> {
			ChatFormatting f = style.getColor() == null ? null : ChatFormatting.getByName(style.getColor().serialize());
			if (f != null) out.append('§').append(f.getChar());
			out.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	/** Adds a tracker that wants every drop. */
	public static void listen(Listener listener) {
		LISTENERS.add(listener);
	}

	/**
	 * Reads one chat line: remembers a roll that landed, or tells every listener about a drop.
	 * A roll that missed dropped nothing, so it names no mob; a roll that landed names the mob
	 * of the next announcement of the same item, and only that one. {@code legacy} is the whole
	 * message with its § codes, which says what colour the drop's name is written in.
	 */
	static void line(String line, String legacy, long now) {
		Drops.Roll roll = Drops.roll(line);
		if (roll != null) {
			lastRoll = roll.hit() ? roll : null;
			rollAt = now;
			return;
		}
		Drops.Drop d = Drops.parse(line);
		if (d == null) return;
		DropColours.learn(legacy, d.item());
		String mob = null;
		if (lastRoll != null && now - rollAt <= ROLL_MS && Drops.same(lastRoll.item(), d.item())) {
			mob = lastRoll.mob();
			lastRoll = null;
		}
		Loot loot = new Loot(d, mob, now);
		for (Listener l : LISTENERS) {
			// One tracker's slip never costs the others the drop.
			try {
				l.loot(loot);
			} catch (RuntimeException e) {
				dev.lauchy.lazarus.Lazarus.LOG.error("Loot listener failed on {}", d, e);
			}
		}
	}
}
