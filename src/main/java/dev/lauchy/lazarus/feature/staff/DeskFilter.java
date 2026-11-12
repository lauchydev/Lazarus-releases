package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Config;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The staff desk's filter as it was last left: the ranks ("" for Default) and the reasons picked. Kept in
 * config/lazarus/desk-filter.json, so a desk opens filtered as the last one was closed, after a restart too.
 */
final class DeskFilter {
	/** What the filter picks. */
	record Picks(Set<String> ranks, Set<Roster.Source> reasons) {
	}

	private static Picks last;

	private DeskFilter() {
	}

	/** The picks the last desk was left with, nothing picked before any. */
	static Picks load() {
		if (last == null) last = read(Config.readJson(file()));
		return last;
	}

	/** Keeps these picks for the next desk, on disk too. */
	static void save(Set<String> ranks, Set<Roster.Source> reasons) {
		last = new Picks(new LinkedHashSet<>(ranks), reasons.isEmpty() ? EnumSet.noneOf(Roster.Source.class) : EnumSet.copyOf(reasons));
		Config.writeJson(file(), write(last));
	}

	/** Picks from the file's JSON; a rank or reason it doesn't know is skipped, and nothing (or no file) picks nothing. */
	static Picks read(JsonElement json) {
		Set<String> ranks = new LinkedHashSet<>();
		Set<Roster.Source> reasons = EnumSet.noneOf(Roster.Source.class);
		if (json instanceof JsonObject o) {
			if (o.get("ranks") instanceof JsonArray a) for (JsonElement e : a) if (e.isJsonPrimitive()) ranks.add(e.getAsString().trim().toUpperCase(Locale.ROOT));
			if (o.get("reasons") instanceof JsonArray a) {
				for (JsonElement e : a) {
					if (!e.isJsonPrimitive()) continue;
					try {
						reasons.add(Roster.Source.valueOf(e.getAsString()));
					} catch (IllegalArgumentException ignored) {
						// a reason an older or newer version had
					}
				}
			}
		}
		return new Picks(ranks, reasons);
	}

	/** The picks as the file keeps them: {"ranks":["", "VIP"], "reasons":["REPORT"]}. */
	static JsonObject write(Picks p) {
		JsonObject o = new JsonObject();
		JsonArray ranks = new JsonArray(), reasons = new JsonArray();
		p.ranks().forEach(ranks::add);
		p.reasons().forEach(r -> reasons.add(r.name()));
		o.add("ranks", ranks);
		o.add("reasons", reasons);
		return o;
	}

	private static Path file() {
		return Config.dir().resolve("desk-filter.json");
	}
}
