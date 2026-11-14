package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Everything Dragsim staff share about players, as the Lazarus server answers every staff call: the watch
 * list, the last day's captcha results, open reports, and the latest Flag Dashboard read and alt check. No
 * Minecraft types, so reading an answer is tested on its own.
 */
public final class StaffFlags {
	/** A watch list entry: who is watched, why, who added them and when. */
	public record Watch(String name, String note, String by, long at) {
	}

	/** A captcha result: who took it, whether they passed, who sent it, how long they took (-1 unsaid) and when. */
	public record Captcha(String player, boolean passed, String by, int seconds, long at) {
	}

	/** An open report: its id, who was reported, by whom, why ("" when unsaid) and when. */
	public record Report(int id, String target, String reporter, String reason, long at) {
	}

	/** A flag on the dashboard: the player, its type ("MACRO"), detector, the player's score, and when it first showed. */
	public record Flag(String name, String type, String detector, double score, long since) {
	}

	/** A player linked to a banned account or IP: why, the rest of the group, and when it first showed. */
	public record Alt(String name, String why, String others, long since) {
	}

	/** The anticheat's latest priority score for a player ("[FLAG REVIEW] … has priority score 67.5"), when it was heard, and the last few heard, newest first. */
	public record Priority(String player, double score, long at, List<Reading> readings) {
		/** A score with no earlier ones known. */
		public Priority(String player, double score, long at) {
			this(player, score, at, List.of(new Reading(score, at)));
		}

		/** The reading before this one, or null when it's the first heard. */
		public Reading previous() {
			return readings.size() < 2 ? null : readings.get(1);
		}
	}

	/** One priority score as heard, and when. */
	public record Reading(double score, long at) {
	}

	/** A run of position setbacks: the running count the anticheat gives, where the last was, and when the first and last were heard. */
	public record Setback(String player, int count, String place, long since, long at) {
	}

	/** The client a player last joined with, as the anticheat names it ("Lunarclient:v2.23.14-2645,fabric"), and when. */
	public record Brand(String player, String client, long at) {
	}

	/** One check the anticheat caught a player on, as staff chat heard it ("Click rate", "CPS=14.5"), and when. */
	public record Detection(String player, String check, String detail, long at) {
	}

	/** How close together the same detection, sent by two staff members' clients, counts once. */
	static final long SAME_DETECTION_MS = 10_000;

	/**
	 * One answer: every list, when the dashboard and the alt check were last read (0 when never), and what the
	 * anticheat said in chat (detections newest first, the last hour's).
	 */
	public record State(List<Watch> watch, List<Captcha> captchas, List<Report> reports, List<Flag> flags, long flagsAt,
			List<Alt> alts, long altsAt, List<Priority> priorities, List<Setback> setbacks, List<Brand> brands, List<Detection> detections) {
		/** An answer with nothing from the anticheat. */
		public State(List<Watch> watch, List<Captcha> captchas, List<Report> reports, List<Flag> flags, long flagsAt, List<Alt> alts, long altsAt) {
			this(watch, captchas, reports, flags, flagsAt, alts, altsAt, List.of(), List.of(), List.of(), List.of());
		}

		/** An answer with no detections. */
		public State(List<Watch> watch, List<Captcha> captchas, List<Report> reports, List<Flag> flags, long flagsAt, List<Alt> alts, long altsAt,
				List<Priority> priorities, List<Setback> setbacks, List<Brand> brands) {
			this(watch, captchas, reports, flags, flagsAt, alts, altsAt, priorities, setbacks, brands, List.of());
		}

		/** This answer with what the anticheat just said put in first, so the roster shows it before the server echoes it. */
		State with(Priority p, Setback s, Brand b) {
			return new State(watch, captchas, reports, flags, flagsAt, alts, altsAt,
					p == null ? priorities : replace(priorities, withEarlier(p), Priority::player),
					s == null ? setbacks : replace(setbacks, s, Setback::player),
					b == null ? brands : replace(brands, b, Brand::player), detections);
		}

		/** This answer with a detection just heard put first, unless the same one is already there from a moment ago. */
		State with(Detection d) {
			for (Detection old : detections) {
				if (old.player().equalsIgnoreCase(d.player()) && old.check().equals(d.check()) && old.detail().equals(d.detail())
						&& Math.abs(d.at() - old.at()) < SAME_DETECTION_MS) return this;
			}
			List<Detection> out = new ArrayList<>(List.of(d));
			out.addAll(detections);
			return new State(watch, captchas, reports, flags, flagsAt, alts, altsAt, priorities, setbacks, brands, List.copyOf(out));
		}

		/** A score just heard, carrying on the readings already known for that player (the same line twice within a minute counts once). */
		private Priority withEarlier(Priority p) {
			List<Reading> out = new ArrayList<>(List.of(new Reading(p.score(), p.at())));
			for (Priority old : priorities) {
				if (!old.player().equalsIgnoreCase(p.player())) continue;
				for (Reading r : old.readings()) if (out.size() < 5 && !(r.score() == p.score() && p.at() - r.at() < 60_000)) out.add(r);
			}
			return new Priority(p.player(), p.score(), p.at(), List.copyOf(out));
		}

		private static <T> List<T> replace(List<T> list, T item, Function<T, String> player) {
			List<T> out = new ArrayList<>();
			out.add(item);
			for (T t : list) if (!player.apply(t).equalsIgnoreCase(player.apply(item))) out.add(t);
			return List.copyOf(out);
		}
	}

	/** Nothing shared yet, before the server first answers. */
	public static final State EMPTY = new State(List.of(), List.of(), List.of(), List.of(), 0, List.of(), 0);

	private StaffFlags() {
	}

	/** The server's answer, or null when it isn't one. */
	public static State parse(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) return null;
			JsonObject o = root.getAsJsonObject();
			if (!o.has("watch") || !o.has("dashboard") || !o.has("alts")) return null;
			JsonObject dashboard = o.getAsJsonObject("dashboard"), alts = o.getAsJsonObject("alts");
			return new State(
					list(o, "watch", e -> new Watch(text(e, "name"), text(e, "note"), text(e, "by"), number(e, "at"))),
					list(o, "captchas", e -> new Captcha(text(e, "player"), e.has("passed") && e.get("passed").getAsBoolean(), text(e, "by"),
							e.has("seconds") ? (int) number(e, "seconds") : -1, number(e, "at"))),
					list(o, "reports", e -> new Report((int) number(e, "id"), text(e, "target"), text(e, "reporter"), text(e, "reason"), number(e, "at"))),
					list(dashboard, "flags", e -> new Flag(text(e, "name"), text(e, "type"), text(e, "detector"),
							e.has("score") ? e.get("score").getAsDouble() : 0, number(e, "since"))),
					number(dashboard, "at"),
					list(alts, "links", e -> new Alt(text(e, "name"), text(e, "why"), text(e, "others"), number(e, "since"))),
					number(alts, "at"),
					list(o, "priorities", e -> {
						double score = e.has("score") ? e.get("score").getAsDouble() : 0;
						List<Reading> readings = list(e, "readings", r -> new Reading(r.has("score") ? r.get("score").getAsDouble() : 0, number(r, "at")));
						return new Priority(text(e, "player"), score, number(e, "at"), readings.isEmpty() ? List.of(new Reading(score, number(e, "at"))) : readings);
					}),
					list(o, "setbacks", e -> new Setback(text(e, "player"), (int) number(e, "count"), text(e, "place"), number(e, "since"), number(e, "at"))),
					list(o, "brands", e -> new Brand(text(e, "player"), text(e, "client"), number(e, "at"))),
					list(o, "detections", e -> new Detection(text(e, "player"), text(e, "check"), text(e, "detail"), number(e, "at"))));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Each object in an array field, read by {@code read}; an empty list when the field is missing. */
	private static <T> List<T> list(JsonObject o, String key, Function<JsonObject, T> read) {
		List<T> out = new ArrayList<>();
		if (!o.has(key) || !o.get(key).isJsonArray()) return out;
		JsonArray arr = o.getAsJsonArray(key);
		for (JsonElement e : arr) if (e.isJsonObject()) out.add(read.apply(e.getAsJsonObject()));
		return List.copyOf(out);
	}

	/** A text field, "" when it's missing. */
	private static String text(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	/** A number field, 0 when it's missing. */
	private static long number(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
	}
}
