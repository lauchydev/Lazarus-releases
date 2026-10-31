package dev.lauchy.lazarus.feature.inventory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which accessories you're missing, by name: every one you don't have, less those on the way up
 * to one you do (with a Rose Artifact, the Rose Talisman and Rose Ring aren't missing, only the
 * Rose Relic is). Pure, so it is tested without the game.
 */
final class AccessoryPaths {
	/** The tiers a family's names climb through, lowest first. */
	private static final List<String> TIERS = List.of("Talisman", "Ring", "Artifact", "Relic");
	private static final Pattern TIERED = Pattern.compile("^(.+) (Talisman|Ring|Artifact|Relic)$");

	private AccessoryPaths() {
	}

	/**
	 * For each accessory, the ones it is made from: any accessory in its recipe (a Defender Talisman
	 * in a Warden's Blade), and the tier below it in its family's names (a Rose Ring under a Rose
	 * Artifact), which also covers families whose recipes haven't been learned.
	 */
	static Map<String, Set<String>> below(Collection<String> accessories, Function<String, Collection<String>> recipeParts) {
		Set<String> all = new HashSet<>(accessories);
		Map<String, Set<String>> out = new HashMap<>();
		for (String a : accessories) {
			Set<String> under = new LinkedHashSet<>();
			for (String p : recipeParts.apply(a)) if (all.contains(p) && !p.equals(a)) under.add(p);
			Matcher m = TIERED.matcher(a);
			if (m.matches()) {
				for (int t = TIERS.indexOf(m.group(2)) - 1; t >= 0; t--) {
					String lower = m.group(1) + " " + TIERS.get(t);
					if (all.contains(lower)) {
						under.add(lower);
						break;
					}
				}
			}
			out.put(a, under);
		}
		return out;
	}

	/** The accessories neither owned nor below one that is, in the order given. */
	static List<String> missing(List<String> accessories, Set<String> owned, Map<String, Set<String>> below) {
		Set<String> covered = new HashSet<>();
		Deque<String> todo = new ArrayDeque<>(owned);
		while (!todo.isEmpty()) {
			String a = todo.pop();
			if (covered.add(a)) todo.addAll(below.getOrDefault(a, Set.of()));
		}
		return accessories.stream().filter(a -> !covered.contains(a)).toList();
	}

	/** Whether everything a recipe needs is on hand; never for a recipe that isn't known. */
	static boolean craftable(Map<String, Integer> needs, Map<String, Integer> have) {
		if (needs.isEmpty()) return false;
		for (var e : needs.entrySet()) if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
		return true;
	}
}
