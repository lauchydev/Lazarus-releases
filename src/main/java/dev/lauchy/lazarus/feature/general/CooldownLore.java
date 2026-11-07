package dev.lauchy.lazarus.feature.general;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads an ability's cooldown out of an item's lore, with no game types so it can be tested. */
final class CooldownLore {
	private static final Pattern COOLDOWN = Pattern.compile("^Cooldown: ([\\d.]+)s");

	private CooldownLore() {
	}

	/** One ability in an item's lore: its header line, whether it needs sneaking, and its cooldown (0 for none). */
	record Section(String header, boolean sneak, double cooldown) {
	}

	/**
	 * Splits lore into ability sections. A section starts at an "Ability:" line or one
	 * naming a click, and owns the first "Cooldown:" line before the next section.
	 */
	static List<Section> sections(List<String> lore) {
		List<Section> out = new ArrayList<>();
		String header = null;
		double cooldown = 0;
		for (String line : lore) {
			// Headers shout the click in capitals; descriptions saying "right click" in passing don't count.
			if (line.startsWith("Ability:") || line.contains("RIGHT CLICK") || line.contains("LEFT CLICK")) {
				if (header != null) out.add(new Section(header, header.contains("SNEAK"), cooldown));
				header = line;
				cooldown = 0;
				continue;
			}
			Matcher m = COOLDOWN.matcher(line);
			if (header != null && cooldown == 0 && m.find()) cooldown = Double.parseDouble(m.group(1));
		}
		if (header != null) out.add(new Section(header, header.contains("SNEAK"), cooldown));
		return out;
	}

	/**
	 * The lore's cooldown in seconds for the ability that fired, or null when the lore can't say,
	 * so the catalog and the item's fallback get their turn. The section naming the ability
	 * wins; failing that, the one whose sneak requirement matches how it was used, so a plain
	 * right click never starts a sneak ability's timer. Lore with no ability headers at all
	 * falls back to its only "Cooldown:" line.
	 */
	static Double cooldown(List<String> lore, String ability, boolean sneaking) {
		List<Section> sections = sections(lore);
		if (sections.isEmpty()) {
			for (String line : lore) {
				Matcher m = COOLDOWN.matcher(line);
				if (m.find()) return Double.parseDouble(m.group(1));
			}
			return null;
		}
		String a = ability.toLowerCase(Locale.ROOT);
		for (Section s : sections) if (!a.isEmpty() && s.header.toLowerCase(Locale.ROOT).contains(a)) return known(s.cooldown);
		for (Section s : sections) if (s.sneak == sneaking) return known(s.cooldown);
		return null;
	}

	/** A section with no Cooldown line says nothing, rather than "zero seconds". */
	private static Double known(double seconds) {
		return seconds > 0 ? seconds : null;
	}

	/** Whether an item is on the player's comma-separated list of items never to time. */
	static boolean ignored(String itemName, String ignoredList) {
		String n = itemName.toLowerCase(Locale.ROOT);
		for (String part : ignoredList.split(",")) {
			String p = part.trim().toLowerCase(Locale.ROOT);
			if (!p.isEmpty() && n.contains(p)) return true;
		}
		return false;
	}
}
