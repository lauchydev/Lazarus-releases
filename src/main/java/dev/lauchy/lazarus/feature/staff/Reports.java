package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An inbox for player reports: each report line the server sends becomes a card with the reporter, the
 * target and the reason, and buttons to open the target's profile, punish them, teleport to them or mark the
 * report done. Reports are shared by every Dragsim staff member through the Lazarus server ({@link FlagSync}):
 * done for one is done for all, and an open one drops off after three days. Open reports are counted on the
 * HUD and listed by {@code /lazarus reports}. The report line is matched by a pattern you can edit, since
 * every server words it differently.
 */
public final class Reports extends Feature {
	/** One report: its shared id (0 before the server has numbered it), who reported whom, why, and when. */
	public record Report(int id, String reporter, String target, String reason, long at) {
	}

	/** The report line as the pattern setting ships: "[Report] Steve reported Alex for flying", "Steve has reported Alex: macro". */
	static final String DEFAULT_PATTERN = "(?i)^(?:\\[report\\]\\s*)?(?<reporter>\\w{1,16}) (?:has )?reported (?<target>\\w{1,16})(?:\\s*(?:for|:|-)\\s*(?<reason>.+?))?\\.?$";
	/** A number, which names a report by its id rather than a player. */
	private static final Pattern ID = Pattern.compile("\\d{1,9}");

	private final TextSetting pattern = add(new TextSetting("pattern", "Report line",
			"A regex for the server's report line, with groups named reporter, target and reason.",
			DEFAULT_PATTERN, 300));
	private final BoolSetting hide = add(new BoolSetting("hide", "Replace the server's line", "Show only the card, not the line it came from.", true));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "A toast when a report comes in.", true));
	private final BoolSetting counter = add(new BoolSetting("counter", "HUD counter", "How many reports are open, on the HUD while there are any.", true));

	public static Reports INSTANCE;

	public Reports() {
		super("reports", "Report Inbox", "Each player report becomes a card with profile, punish, teleport and done buttons.", Category.STAFF, true);
		INSTANCE = this;
	}

	/** Every open report, newest first, as staff share them. */
	public List<Report> open() {
		return FlagSync.state().reports().stream().map(r -> new Report(r.id(), r.reporter(), r.target(), r.reason(), r.at())).toList();
	}

	/** The open reports against a player, newest first. */
	public List<Report> openFor(String target) {
		return open().stream().filter(r -> r.target().equalsIgnoreCase(target)).toList();
	}

	@Override
	public void init() {
		forgetLocal();
		Chat.hide(line -> {
			if (!enabled()) return false;
			Report r = parse(line.text(), pattern.get(), 0, System.currentTimeMillis());
			if (r == null) return false;
			FlagSync.report(r.reporter(), r.target(), r.reason());
			card(r).send();
			if (toast.on()) Toast.show("§7Report: §f" + r.target(), Theme.WARN);
			return hide.on();
		});
		Hud.register(new HudElement(this, "reports", "Open reports", 0.01f, 0.30f) {
			@Override
			protected boolean visible() {
				return counter.on() && !FlagSync.state().reports().isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				int n = sample ? 3 : FlagSync.state().reports().size();
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
		}).then(ClientCommands.literal("done").then(ClientCommands.argument("report", StringArgumentType.word()).executes(c -> {
			done(StringArgumentType.getString(c, "report"));
			return 1;
		}))).then(ClientCommands.literal("show").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
			String id = StringArgumentType.getString(c, "id");
			Report r = byId(id);
			if (r == null) Message.lazarus().error("No open report #" + id + ".").send();
			else card(r).send();
			return 1;
		}))).then(ClientCommands.literal("clear").executes(c -> {
			clear();
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

	/** One report as a chat card with its buttons; Done closes it by id once it has one, else every open report on the target. */
	static Message card(Report r) {
		Message m = Message.lazarus().error(r.id() > 0 ? "Report #" + r.id() : "Report").info("  ").player(r.target()).muted(" by " + r.reporter());
		if (!r.reason().isEmpty()) m.line().info(r.reason());
		String done = "/lazarus reports done " + (r.id() > 0 ? r.id() : r.target());
		m.line().danger("Staff", "Click for staff options on " + r.target(), "/lazarus staff " + r.target()).info("  ")
				.command("Teleport", "Click to teleport to " + r.target(), "/tp " + r.target()).info("  ")
				.command("Done", "Click to close this report for all staff", done);
		return m;
	}

	/** The open report with this id, or null. */
	private Report byId(String id) {
		if (!ID.matcher(id).matches()) return null;
		int n = Integer.parseInt(id);
		return open().stream().filter(x -> x.id() == n).findFirst().orElse(null);
	}

	private void list() {
		List<Report> open = open();
		if (open.isEmpty()) {
			Message.lazarus().info(FlagSync.readAt() == 0 ? "No reports have been shared yet." : "No open reports.").send();
			return;
		}
		Message m = Message.lazarus().heading(open.size() + " open report" + (open.size() == 1 ? "" : "s"));
		for (Report r : open) {
			m.line().muted("#" + r.id() + "  ").player(r.target()).muted(" by " + r.reporter() + (r.reason().isEmpty() ? "" : ": ") + r.reason()).info("  ")
					.command("Open", "Click to see this report's card", "/lazarus reports show " + r.id());
		}
		m.line().command("Close all", "Click to close every open report for all staff", "/lazarus reports clear");
		m.send();
	}

	/** Closes a report by its id, or every open report on a player, for all staff. */
	private void done(String which) {
		if (!FlagSync.allowed()) {
			Message.lazarus().error("Only Dragsim staff can close reports.").send();
			return;
		}
		if (ID.matcher(which).matches()) {
			Report r = byId(which);
			if (r == null) {
				Message.lazarus().error("No open report #" + which + ".").send();
				return;
			}
			FlagSync.reportDone(r.id(), answered("Report #" + r.id() + " closed.", r.target()));
			return;
		}
		FlagSync.reportsDone(which, answered("Reports on " + which + " closed.", ""));
	}

	/** Closes every open report, for all staff. */
	private void clear() {
		Set<String> targets = new LinkedHashSet<>();
		for (Report r : open()) targets.add(r.target());
		if (targets.isEmpty()) {
			Message.lazarus().info("No open reports.").send();
			return;
		}
		for (String t : targets) FlagSync.reportsDone(t, ok -> {
		});
		Message.lazarus().success("Reports closed.").send();
	}

	/** Says {@code success} when the server answered, else that it couldn't be reached. */
	private static Consumer<Boolean> answered(String success, String muted) {
		return ok -> {
			if (ok) Message.lazarus().success(success).muted(muted.isEmpty() ? "" : "  " + muted).send();
			else Message.lazarus().error("Lazarus couldn't reach its server. Try again in a moment.").send();
		};
	}

	/** Reports used to be kept on this PC; they're shared now, so the old file goes. */
	private static void forgetLocal() {
		try {
			Files.deleteIfExists(Config.dir().resolve("reports.json"));
		} catch (IOException e) {
			// a file that can't be removed is never read again
		}
	}
}
