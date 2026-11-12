package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.Staff;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * What staff share through the Lazarus server ({@link StaffFlags}), kept up to date for the roster, the
 * Report Inbox and the watch list. Every captcha result heard is shared; reports, dashboard reads, alt checks
 * and watch-list changes are sent by the features that see them. Each call is signed, runs off the game thread,
 * and its answer (everything shared) replaces what's known, unless a newer answer came first. While in a world
 * as staff it's read again every {@link #POLL_MS}; the open roster reads it more often.
 */
final class FlagSync {
	/** How often what staff share is read again while you're in a world. */
	private static final long POLL_MS = 60_000;

	/** What staff share, as of the newest answer. */
	private static StaffFlags.State state = StaffFlags.EMPTY;
	/** When the server last answered, 0 before it has. */
	private static long readAt;
	/** When a read was last asked for, so the poll and the roster don't stack reads. */
	private static long askedAt;
	/** Each call's number, and the newest one whose answer was taken, so a slow answer never undoes a newer one. */
	private static int sent, taken;
	/** Who hears when what's known changes. */
	private static final List<Runnable> listeners = new ArrayList<>();

	private FlagSync() {
	}

	/** Shares every captcha result staff hear, and reads what's shared every so often; called once by {@link OnlineRoster}. */
	static void init() {
		Chat.listen(line -> {
			CaptchaLine.Result r = CaptchaLine.parse(line.text());
			if (r != null) captcha(r);
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != null && mc.player != null) refreshIfOlder(POLL_MS);
		});
	}

	/** What staff share, as of the newest answer (empty before the first). */
	static StaffFlags.State state() {
		return state;
	}

	/** When the server last answered, 0 before it has. */
	static long readAt() {
		return readAt;
	}

	/** Runs {@code r} on the game thread whenever what's known changes. */
	static void onChange(Runnable r) {
		listeners.add(r);
	}

	/** Whether this account may share: Dragsim staff, with the Lazarus server set up. */
	static boolean allowed() {
		return Staff.is() && LazarusServer.configured();
	}

	/** Reads what's shared, unless a read was asked for less than {@code ms} ago. */
	static void refreshIfOlder(long ms) {
		if (allowed() && Util.getMillis() - askedAt >= ms) refresh(ok -> {
		});
	}

	/** Reads what's shared now; {@code done} hears whether the server answered. */
	static void refresh(Consumer<Boolean> done) {
		askedAt = Util.getMillis();
		call("/staff/flags", new JsonObject(), done);
	}

	/** Adds a player to the watch list, or changes the note of one already on it. */
	static void watch(String player, String note) {
		JsonObject body = new JsonObject();
		body.addProperty("name", player);
		body.addProperty("note", note);
		call("/staff/watch", body, ok -> {
		});
	}

	/** Takes a player off the watch list. */
	static void unwatch(String player) {
		JsonObject body = new JsonObject();
		body.addProperty("name", player);
		call("/staff/watch/remove", body, ok -> {
		});
	}

	/** Uploads a watch list kept on this PC ({name, note} pairs); names already shared are left as they are. */
	static void importWatch(List<String[]> entries, Consumer<Boolean> done) {
		JsonArray arr = new JsonArray();
		for (String[] e : entries) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e[0]);
			o.addProperty("note", e[1]);
			arr.add(o);
		}
		JsonObject body = new JsonObject();
		body.add("entries", arr);
		call("/staff/watch/import", body, done);
	}

	/** Shares a captcha result heard in chat. */
	static void captcha(CaptchaLine.Result r) {
		JsonObject body = new JsonObject();
		body.addProperty("player", r.player());
		body.addProperty("passed", r.passed());
		body.addProperty("by", r.by());
		body.addProperty("seconds", r.seconds());
		call("/staff/captcha", body, ok -> {
		});
	}

	/** Shares a report heard in chat. */
	static void report(String reporter, String target, String reason) {
		JsonObject body = new JsonObject();
		body.addProperty("reporter", reporter);
		body.addProperty("target", target);
		body.addProperty("reason", reason);
		call("/staff/report", body, ok -> {
		});
	}

	/** Marks one report handled, for every staff member. */
	static void reportDone(int id, Consumer<Boolean> done) {
		JsonObject body = new JsonObject();
		body.addProperty("id", id);
		call("/staff/report/done", body, done);
	}

	/** Marks every open report on a player handled, for every staff member. */
	static void reportsDone(String target, Consumer<Boolean> done) {
		JsonObject body = new JsonObject();
		body.addProperty("target", target);
		call("/staff/report/done", body, done);
	}

	/** Shares a whole Flag Dashboard read, replacing the last one. */
	static void dashboard(List<DashboardLore.Entry> entries) {
		JsonArray flags = new JsonArray();
		for (DashboardLore.Entry e : entries) {
			for (DashboardLore.Flag f : e.flags()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", e.name());
				o.addProperty("type", f.type());
				o.addProperty("detector", f.detector());
				o.addProperty("score", e.score());
				flags.add(o);
			}
		}
		JsonObject body = new JsonObject();
		body.add("flags", flags);
		call("/staff/dashboard", body, ok -> {
		});
	}

	/**
	 * Shares what one of the anticheat's lines said, putting it in what's known first so the roster changes at
	 * once rather than on the server's answer.
	 */
	static void anticheat(AcLine.Line line) {
		long now = System.currentTimeMillis();
		JsonObject body = new JsonObject();
		body.addProperty("player", line.player());
		String path;
		switch (line) {
			case AcLine.Priority p -> {
				body.addProperty("score", p.score());
				path = "/staff/priority";
				state = state.with(new StaffFlags.Priority(p.player(), p.score(), now), null, null);
			}
			case AcLine.Setback s -> {
				body.addProperty("count", s.count());
				body.addProperty("place", s.place());
				path = "/staff/setback";
				long since = state.setbacks().stream().filter(x -> x.player().equalsIgnoreCase(s.player())).mapToLong(StaffFlags.Setback::since).findFirst().orElse(now);
				state = state.with(null, new StaffFlags.Setback(s.player(), s.count(), s.place(), since, now), null);
			}
			case AcLine.Brand b -> {
				body.addProperty("client", b.client());
				path = "/staff/brand";
				state = state.with(null, null, new StaffFlags.Brand(b.player(), b.client(), now));
			}
			case AcLine.Detection d -> {
				body.addProperty("check", d.check());
				body.addProperty("detail", d.detail());
				path = "/staff/detection";
				state = state.with(new StaffFlags.Detection(d.player(), d.check(), d.detail(), now));
			}
		}
		for (Runnable r : listeners) r.run();
		call(path, body, ok -> {
		});
	}

	/** Shares a whole {@code /checkalts} result, replacing the last one. */
	static void alts(List<AltPages.Link> links, Consumer<Boolean> done) {
		JsonArray arr = new JsonArray();
		for (AltPages.Link l : links) {
			JsonObject o = new JsonObject();
			o.addProperty("name", l.name());
			o.addProperty("why", l.why());
			o.addProperty("others", l.others());
			arr.add(o);
		}
		JsonObject body = new JsonObject();
		body.add("links", arr);
		call("/staff/alts", body, done);
	}

	/** The calls the Lazarus server answered "not here" to this session: it's older than this mod, so they aren't made again. */
	private static final java.util.Set<String> missing = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Sends a signed call off the game thread, takes its answer as what's known (unless a newer call's answer
	 * came first) and tells {@code done} on the game thread whether the server answered.
	 */
	private static void call(String path, JsonObject body, Consumer<Boolean> done) {
		if (!allowed()) {
			done.accept(false);
			return;
		}
		if (missing.contains(path)) {
			done.accept(false);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int number = ++sent;
		// Who is asking is never sent: the server asks Mojang for the signed account's name.
		CompletableFuture.runAsync(() -> {
			StaffFlags.State answer = null;
			try {
				HttpResponse<String> r = LazarusServer.post(path, body);
				if (r.statusCode() == 200) answer = StaffFlags.parse(r.body());
				if (r.statusCode() == 404) {
					// A Lazarus server older than this mod doesn't have the call yet: it isn't asked again this session, and that's said once.
					if (missing.add(path)) Lazarus.LOG.warn("Staff call {} isn't on the Lazarus server yet; it won't be made again until the game restarts", path);
				} else if (answer == null) {
					// What the server said goes in the log, so a refusal can be told from an outage.
					Lazarus.LOG.warn("Staff call {} answered {}: {}", path, r.statusCode(), r.body().length() > 200 ? r.body().substring(0, 200) : r.body());
				}
			} catch (Exception e) {
				// the server couldn't be reached: what's known stays as it was
				Lazarus.LOG.warn("Staff call {} couldn't reach the Lazarus server: {}", path, e.toString());
			}
			StaffFlags.State got = answer;
			mc.execute(() -> {
				if (got != null && number > taken) {
					taken = number;
					state = got;
					readAt = Util.getMillis();
					for (Runnable r : listeners) r.run();
				}
				done.accept(got != null);
			});
		});
	}
}
