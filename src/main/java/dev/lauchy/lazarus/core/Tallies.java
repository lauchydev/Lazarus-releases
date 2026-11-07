package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A tracker's counts, each kept twice: this session and all time, under any number of keys
 * (one tracker counts each boss apart; most use the one key ""). All-time counts live in one
 * JSON file under config/lazarus and are written only when something changed.
 */
public final class Tallies {
	/** The JSON file the all-time counts live in. */
	private final Path file;
	/** The counts by key, this session's and all time's. */
	private final Map<String, Tally> session = new LinkedHashMap<>(), total = new LinkedHashMap<>();
	/** True while the all-time counts have changed since they were last written. */
	private boolean dirty;

	/** Counts saved in {@code file}; nothing is read until {@link #load()}. */
	public Tallies(Path file) {
		this.file = file;
	}

	/** Reads the saved all-time counts; the session starts empty. */
	public void load() {
		read(Config.readJson(file));
	}

	/** Writes the all-time counts if any changed; call it on autosave, quit and disconnect. */
	public void save() {
		if (!dirty) return;
		Config.writeJson(file, write());
		dirty = false;
	}

	/**
	 * Takes the all-time counts from saved JSON: {key: tally}. A file from before tallies were
	 * shared holds one tracker's numbers at the top level and is read as the one key "".
	 */
	void read(JsonElement json) {
		if (json == null || !json.isJsonObject()) return;
		JsonObject root = json.getAsJsonObject();
		boolean keyed = root.size() > 0 && root.entrySet().stream().allMatch(e -> e.getValue().isJsonObject());
		if (keyed) for (var e : root.entrySet()) total.put(e.getKey(), Tally.load(e.getValue()));
		else total.put("", Tally.load(root));
	}

	/** The all-time counts as JSON, {key: tally}. */
	JsonObject write() {
		JsonObject root = new JsonObject();
		total.forEach((key, t) -> root.add(key, t.save()));
		return root;
	}

	/** This session's counts under {@code key}, started empty when first asked for. */
	public Tally session(String key) {
		return session.computeIfAbsent(key, k -> new Tally());
	}

	/** The all-time counts under {@code key}, started empty when first asked for. */
	public Tally total(String key) {
		return total.computeIfAbsent(key, k -> new Tally());
	}

	/** The one the readout shows for its period. */
	public Tally shown(String key, Period period) {
		return period == Period.SESSION ? session(key) : total(key);
	}

	/** The one the readout shows for its period, for a tracker with the one key "". */
	public Tally shown(Period period) {
		return shown("", period);
	}

	/** Applies one change to both periods' counts under {@code key}, and marks the file to be saved. */
	public void both(String key, Consumer<Tally> change) {
		change.accept(session(key));
		change.accept(total(key));
		dirty = true;
	}

	/** Applies one change to both periods' counts, for a tracker with the one key "". */
	public void both(Consumer<Tally> change) {
		both("", change);
	}

	/** Every key with any count in {@code period}, in the order first seen. */
	public List<String> keys(Period period) {
		Map<String, Tally> from = period == Period.SESSION ? session : total;
		return from.entrySet().stream().filter(e -> !e.getValue().empty()).map(Map.Entry::getKey).toList();
	}

	/** Forgets this session's counts; all-time totals are kept. */
	public void resetSession() {
		session.clear();
	}

	/** Forgets this session's counts under {@code key}; all-time totals are kept. */
	public void resetSession(String key) {
		session.remove(key);
	}
}
