package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading a dungeon reward chest as plain text: which window is one, what opening it costs, and how
 * many of an item a line names. Pure, so it is tested without the game.
 */
final class ChestLoot {
	/** A reward chest's window or button: "Bedrock Chest", "Wood Chest", "Golden Chest", or the material alone ("Obsidian"), as Dragsim titles the window. */
	static final Pattern CHEST = Pattern.compile("^(Wood(?:en)?|Gold(?:en)?|Diamond|Emerald|Obsidian|Bedrock)(?: Chest\\b|$)", Pattern.CASE_INSENSITIVE);
	/** "Cost", "Cost:" or "Cost: 2,000,000 Coins". */
	private static final Pattern COST = Pattern.compile("^Cost:?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COINS = Pattern.compile("^([\\d,]+(?:\\.\\d+)?)\\s*([kmb])?\\s*Coins?$", Pattern.CASE_INSENSITIVE);
	/** "Wither Essence x55", "Undead Essence ×34". */
	private static final Pattern TRAILING_COUNT = Pattern.compile("^(.+?)\\s*[x×]\\s?([\\d,]+)$");
	/** "55x Wither Essence", "2 x Hot Potato Book". */
	private static final Pattern LEADING_COUNT = Pattern.compile("^([\\d,]+)\\s?[x×]\\s+(.+)$");

	/** "Amount: 220" in a reward's lore. */
	private static final Pattern AMOUNT = Pattern.compile("^Amount:\\s*([\\d,]+)$", Pattern.CASE_INSENSITIVE);

	/** What opening a chest takes: coins plus any items, and whether every item in it had a price. */
	record Cost(long coins, boolean known) {
		static final Cost NONE = new Cost(0, false);
	}

	/** An item a line names, and how many. */
	record Counted(String name, int count) {
	}

	private ChestLoot() {
	}

	/** The material of a reward chest ("Bedrock") from its window title or name, or null when it isn't one. */
	static String material(String title) {
		Matcher m = CHEST.matcher(title.trim());
		if (!m.find()) return null;
		String s = m.group(1).toLowerCase(Locale.ROOT);
		if (s.startsWith("wood")) return "Wood";
		if (s.startsWith("gold")) return "Gold";
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Where the "Cost" section starts in a lore, or -1 when it has none. */
	static int costLine(List<String> lore) {
		for (int i = 0; i < lore.size(); i++) if (COST.matcher(lore.get(i).trim()).matches()) return i;
		return -1;
	}

	/**
	 * What the cost section of a lore asks: coins ("2,000,000 Coins", "2M Coins"), nothing
	 * ("FREE"), or items, priced by {@code price} (-1 when it has none). It runs from the "Cost" line
	 * to the next blank line. {@link Cost#NONE} when the lore names no cost.
	 */
	static Cost cost(List<String> lore, ToLongFunction<String> price) {
		int at = costLine(lore);
		if (at < 0) return Cost.NONE;
		List<String> lines = new ArrayList<>();
		Matcher same = COST.matcher(lore.get(at).trim());
		if (same.matches() && !same.group(1).isBlank()) lines.add(same.group(1).trim());
		else for (int i = at + 1; i < lore.size() && !lore.get(i).isBlank(); i++) lines.add(lore.get(i).trim());
		if (lines.isEmpty()) return Cost.NONE;
		long coins = 0;
		boolean known = true;
		for (String line : lines) {
			if (line.equalsIgnoreCase("free")) continue;
			long c = coins(line);
			if (c >= 0) {
				coins += c;
				continue;
			}
			Counted item = counted(line);
			long each = price.applyAsLong(item.name);
			if (each < 0) known = false;
			else coins += each * item.count;
		}
		return new Cost(coins, known);
	}

	/** How many of a reward there are: what its lore says ("Amount: 220"), else the size of its stack. */
	static int amount(List<String> lore, int stack) {
		for (String line : lore) {
			Matcher m = AMOUNT.matcher(line.trim());
			if (m.matches()) return Integer.parseInt(m.group(1).replace(",", ""));
		}
		return stack;
	}

	/** Coins a line names ("2,000,000 Coins", "2.5M Coins"), or -1 when it names none. */
	static long coins(String line) {
		Matcher m = COINS.matcher(line.trim());
		if (!m.matches()) return -1;
		double n = Double.parseDouble(m.group(1).replace(",", ""));
		String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
		return Math.round(n * switch (unit) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			default -> 1;
		});
	}

	/** The item a line names and how many: "Wither Essence x55" and "55x Wither Essence" are 55; a bare name is one. */
	static Counted counted(String line) {
		String s = line.trim();
		Matcher m = TRAILING_COUNT.matcher(s);
		if (m.matches()) return new Counted(m.group(1).trim(), Integer.parseInt(m.group(2).replace(",", "")));
		m = LEADING_COUNT.matcher(s);
		if (m.matches()) return new Counted(m.group(2).trim(), Integer.parseInt(m.group(1).replace(",", "")));
		return new Counted(s, 1);
	}

	/** Whether a name is essence: in every chest, and never the prize. */
	static boolean essence(String name) {
		return name.toLowerCase(Locale.ROOT).contains("essence");
	}

	/**
	 * Which reward a roll lands on: the dearest that isn't essence. An index into the lists, -1 when the chest
	 * holds essence alone (or nothing): there's no prize to roll for.
	 */
	static int winner(List<String> names, List<Long> values) {
		int best = -1;
		for (int i = 0; i < names.size(); i++) {
			if (essence(names.get(i))) continue;
			if (best < 0 || values.get(i) > values.get(best)) best = i;
		}
		return best;
	}

	/** Whether a slot's lore says how many of it the chest gives ("Amount: 24"): a reward says so, a button doesn't. */
	static boolean listed(List<String> lore) {
		for (String line : lore) if (AMOUNT.matcher(line.trim()).matches()) return true;
		return false;
	}

	/** The material of the chest that pays most, of those whose cost is known; null when none is. */
	static String best(List<String> materials, List<Long> profits) {
		String best = null;
		long most = Long.MIN_VALUE;
		for (int i = 0; i < materials.size(); i++) {
			if (profits.get(i) == null || profits.get(i) <= most) continue;
			most = profits.get(i);
			best = materials.get(i);
		}
		return best;
	}
}
