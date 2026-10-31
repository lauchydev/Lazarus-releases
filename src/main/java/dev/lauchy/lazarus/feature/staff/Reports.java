package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An inbox for player reports: each report line the server sends becomes a card with the reporter,
 * the target and the reason, and buttons to open the target's profile, punish them, teleport to
 * them or mark the report done. Open reports are counted on the HUD and listed by
 * {@code /lazarus reports}, and kept between sessions. The report line is matched by a pattern you
 * can edit, since every server words it differently.
 */
public final class Reports extends Feature {
	/** One report: who reported whom, why, and when. */
	public record Report(int id, String reporter, String target, String reason, long at) {
	}

	private static final int KEEP = 100;
	/** The report line as the pattern setting ships: "[Report] Steve reported Alex for flying", "Steve has reported Alex: macro". */
	static final String DEFAULT_PATTERN = "(?i)^(?:\\[report\\]\\s*)?(?<reporter>\\w{1,16}) (?:has )?reported (?<target>\\w{1,16})(?:\\s*(?:for|:|-)\\s*(?<reason>.+?))?\\.?$";

	private final TextSetting pattern = add(new TextSetting("pattern", "Report line",
			"A regex for the server's report line, with groups named reporter, target and reason.",
			DEFAULT_PATTERN, 300));
	private final BoolSetting hide = add(new BoolSetting("hide", "Replace the server's line", "Show only the card, not the line it came from.", true));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "A toast when a report comes in.", true));
	private final BoolSetting counter = add(new BoolSetting("counter", "HUD counter", "How many reports are open, on the HUD while there are any.", true));

	private final List<Report> open = new ArrayList<>();
	private int nextId = 1;
	private boolean dirty;

	public static Reports INSTANCE;

	/** The open reports against a player, newest last. */
	public List<Report> openFor(String target) {
		return open.stream().filter(r -> r.target().equalsIgnoreCase(target)).toList();
	}

	public Reports() {
		super("reports", "Report Inbox", "Each player report becomes a card with profile, punish, teleport and done buttons.", Category.STAFF, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		Config.saveOn(this::save);
		Chat.hide(line -> {
			if (!enabled()) return false;
			Report r = parse(line.text(), pattern.get(), nextId, Util.getMillis());
			if (r == null) return false;
			nextId++;
			open.add(r);
			while (open.size() > KEEP) open.removeFirst();
			dirty = true;
			card(r).send();
			if (toast.on()) Toast.show("§7Report: §f" + r.target(), Theme.WARN);
			return hide.on();
		});
		Hud.register(new HudElement(this, "reports", "Open reports", 0.01f, 0.30f) {
			@Override
			protected boolean visible() {
				return counter.on() && !open.isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				int n = sample ? 3 : open.size();
				String text = "§e" + n + " §7open report" + (n == 1 ? "" : "s");
				int w = font.width(text) + 10, h = 13;
				if (g != null) {
					Draw.rect(g, 0, 0, w, h, Theme.HUD_BG);
					Draw.text(g, font, text, 5, 3, Theme.TEXT);
				}
				return new int[]{w, h};
			}
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("reports").executes(c -> {
			list();
			return 1;
		}).then(ClientCommands.literal("done").then(ClientCommands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
			done(IntegerArgumentType.getInteger(c, "id"));
			return 1;
		}))).then(ClientCommands.literal("show").then(ClientCommands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
			int id = IntegerArgumentType.getInteger(c, "id");
			Report r = open.stream().filter(x -> x.id() == id).findFirst().orElse(null);
			if (r == null) Message.lazarus().error("No open report #" + id + ".").send();
			else card(r).send();
			return 1;
		}))).then(ClientCommands.literal("clear").executes(c -> {
			open.clear();
			dirty = true;
			Message.lazarus().success("Reports cleared.").send();
			return 1;
		}))));
	}

	/** The report a line is, by {@code regex}, or null when it isn't one or the regex doesn't compile. */
	public static Report parse(String line, String regex, int id, long now) {
		try {
			Matcher m = Pattern.compile(regex).matcher(line.trim());
			if (!m.matches()) return null;
			String reporter = group(m, "reporter"), target = group(m, "target"), reason = group(m, "reason");
			if (target.isEmpty()) return null;
			return new Report(id, reporter.isEmpty() ? "someone" : reporter, target, reason, now);
		} catch (IllegalArgumentException e) { // a PatternSyntaxException is one too: a bad pattern matches nothing
			return null;
		}
	}

	private static String group(Matcher m, String name) {
		try {
			String g = m.group(name);
			return g == null ? "" : g.trim();
		} catch (IllegalArgumentException noSuchGroup) {
			return "";
		}
	}

	/** One report as a chat card with its buttons. */
	static Message card(Report r) {
		Message m = Message.lazarus().error("Report #" + r.id()).info("  ").player(r.target()).muted(" by " + r.reporter());
		if (!r.reason().isEmpty()) m.line().info(r.reason());
		m.line().danger("Staff", "Click for staff options on " + r.target(), "/lazarus staff " + r.target()).info("  ")
				.command("Teleport", "Click to teleport to " + r.target(), "/tp " + r.target()).info("  ")
				.command("Done", "Click to close this report", "/lazarus reports done " + r.id());
		return m;
	}

	private void list() {
		if (open.isEmpty()) {
			Message.lazarus().info("No open reports.").send();
			return;
		}
		Message m = Message.lazarus().heading(open.size() + " open report" + (open.size() == 1 ? "" : "s"));
		for (Report r : open) {
			m.line().muted("#" + r.id() + "  ").player(r.target()).muted(" by " + r.reporter() + (r.reason().isEmpty() ? "" : ": ") + r.reason()).info("  ")
					.command("Open", "Click to see this report's card", "/lazarus reports show " + r.id());
		}
		m.line().command("Clear all", "Click to close every report", "/lazarus reports clear");
		m.send();
	}

	private void done(int id) {
		Report r = open.stream().filter(x -> x.id() == id).findFirst().orElse(null);
		if (r == null) {
			Message.lazarus().error("No open report #" + id + ".").send();
			return;
		}
		open.remove(r);
		dirty = true;
		Message.lazarus().success("Report #" + id + " closed.").muted("  " + r.target()).send();
	}

	/** Where open reports are kept between sessions; looked up late so the pure parts need no game. */
	private static Path file() {
		return Config.dir().resolve("reports.json");
	}

	private void load() {
		JsonElement json = Config.readJson(file());
		if (json == null || !json.isJsonObject()) return;
		JsonObject root = json.getAsJsonObject();
		nextId = root.has("next") ? root.get("next").getAsInt() : 1;
		if (!root.has("open")) return;
		for (JsonElement e : root.getAsJsonArray("open")) {
			JsonObject o = e.getAsJsonObject();
			open.add(new Report(o.get("id").getAsInt(), o.get("reporter").getAsString(), o.get("target").getAsString(), o.get("reason").getAsString(), o.get("at").getAsLong()));
		}
	}

	private void save() {
		if (!dirty) return;
		dirty = false;
		JsonObject root = new JsonObject();
		root.addProperty("next", nextId);
		JsonArray arr = new JsonArray();
		for (Report r : open) {
			JsonObject o = new JsonObject();
			o.addProperty("id", r.id());
			o.addProperty("reporter", r.reporter());
			o.addProperty("target", r.target());
			o.addProperty("reason", r.reason());
			o.addProperty("at", r.at());
			arr.add(o);
		}
		root.add("open", arr);
		Config.writeJson(file(), root);
	}
}
