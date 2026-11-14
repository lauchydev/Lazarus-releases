package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Gate;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Ranks;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.feature.profile.Nicks;
import dev.lauchy.lazarus.feature.profile.ProfileViewer;
import dev.lauchy.lazarus.ui.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.lauchy.lazarus.feature.staff.DeskColours.*;

/**
 * The staff desk, drawn one to one from its design (docs/dragsim/desk-design): a 1280×760 page of cards in CSS pixels,
 * shown at 1, 1.5 or 2 screen pixels to the design pixel (whichever is the largest that fits), scaled down only when a
 * window is too small for 1. Along the top, who you are, what's going on and your own actions; on the left everyone
 * online, the flagged first; in the middle the selected player (Overview, Records, Account); on the right the actions.
 * Anything with more to say opens a detail panel from the right; punishments open a dialog.
 */
final class StaffDeskScreen extends Screen {
	private static final int W = 1280, H = 760;
	private static final int SEARCH_MAX = 16;
	private static final long POLL_MS = 10_000, SETTLE_MS = 400, CONFIRM_MS = 3000;
	/** How long a player's alerts, read off their dashboard page, stand before selecting them reads them again. */
	private static final long ALERTS_FRESH_MS = 3 * 60_000;

	/** A player's alerts as last read off their page on the flag dashboard (none when it couldn't be read), when, and whether it could. */
	private record AlertRead(List<FlagAlerts.Alert> alerts, long at, boolean ok) {
	}

	/** Each player's last alert read, by lower-case name; kept from one desk to the next, since a read is good for a few minutes. */
	private static final Map<String, AlertRead> alertReads = new HashMap<>();

	/** How far the desk has got reading a player's alerts: reading now, read, couldn't be read, or not yet asked. */
	private enum AlertState {READING, READ, FAILED, UNREAD}

	/**
	 * What the anticheat has caught a player on over the last hour (their dashboard page's alerts and what staff chat
	 * heard since), their dashboard flags, how far the desk has got reading the page, and when it was read (0 never).
	 */
	private record Detected(List<StaffFlags.Flag> flags, List<FlagAlerts.Check> checks, AlertState read, long readAt) {
	}

	enum Tab {
		OVERVIEW("Overview"), RECORDS("Records"), ACCOUNT("Account");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	/** Which players the list shows. */
	private enum Filter {FLAGGED, EVERYONE, WATCHED}

	/**
	 * A desk with nothing live, for screenshots: its alert reads by lower-case name, and what the filter picks (ranks,
	 * "" Default, and reasons; none for all).
	 */
	record Sample(List<Roster.Online> online, StaffFlags.State state, Gate.Tier tier, String selected, Tab tab, Map<StaffActions, List<String>> replies,
			Map<String, List<FlagAlerts.Alert>> alerts, List<String> ranks, List<Roster.Source> reasons) {
		Sample(List<Roster.Online> online, StaffFlags.State state, Gate.Tier tier, String selected, Tab tab, Map<StaffActions, List<String>> replies) {
			this(online, state, tier, selected, tab, replies, Map.of(), List.of(), List.of());
		}
	}

	private record Hit(float x, float y, float w, float h, Runnable action) {
	}

	/** What an Overview card is about. */
	private enum SignalKind {PRIORITY, SETBACK, DASHBOARD, CAPTCHA, ALT, WATCH, CLIENT}

	/** One Overview card: its dot, title, when, its main value (in mono or not) with a note beside, a line of detail, and what it's about. */
	private record Signal(SignalKind kind, int dot, String title, String meta, String value, boolean mono, int valueColour, String note, int noteColour,
			String detail, boolean detailMono, Object about) {
	}

	/** A detail panel open on the right: a card's details, a record's, or the audit log. */
	private sealed interface Sheet permits SignalSheet, EntrySheet, AuditSheet {
	}

	private record SignalSheet(Signal signal) implements Sheet {
	}

	private record EntrySheet(StaffReplies.Entry entry) implements Sheet {
	}

	private record AuditSheet(String player) implements Sheet {
	}

	/** A menu dropped from a button: servers to send the player to, or to hop to yourself, or the ranks the list shows. */
	private enum Menu {SEND, HOP, RANKS}

	private final long openedAt = Util.getMillis();
	private final List<Hit> hits = new ArrayList<>();
	private final Anim.Group<Object> hover = new Anim.Group<>(14f);
	private final Anim.Smooth listScroll = new Anim.Smooth(18f, 0f), bodyScroll = new Anim.Smooth(18f, 0f), sheetScroll = new Anim.Smooth(18f, 0f);
	private final Map<String, Component> tab = new HashMap<>();
	private final Map<String, Dossier> dossiers = new HashMap<>();
	private final Map<String, String> pinnedRanks = new HashMap<>();
	private final Sample sample;

	private double listScrollTo, bodyScrollTo, sheetScrollTo;
	private float listContentH, listViewH, bodyContentH, bodyViewH, sheetContentH, sheetViewH;
	private boolean asked, follow;
	private String search = "", selected = "", pinned = "";
	private long selectedAt;
	private Tab shown = Tab.OVERVIEW;
	private Filter filter = Filter.EVERYONE;
	/** The ranks the list shows, as {@link #rankKey} gives them ("" for Default); empty shows every rank. */
	private final java.util.Set<String> ranks = new java.util.LinkedHashSet<>();
	/** The reasons the list shows players flagged for (any of them); empty shows everyone. */
	private final java.util.Set<Roster.Source> reasons = java.util.EnumSet.noneOf(Roster.Source.class);
	private Sheet sheet;
	private long sheetAt;
	private DeskDialog dialog;
	private Menu menu;
	private float menuX, menuY;
	/** What's waiting for its second click ("UNBAN#292"), and since when. */
	private String confirming = "";
	private long confirmingAt, copiedAt;
	/** The last tool asked on the Account tab (dupe scan or captcha status), null for none. */
	private StaffActions tool;
	private final Map<StaffActions, String> queued = new LinkedHashMap<>();
	private String auditFilter = "";
	private boolean ipShown;
	/** Whether you're vanished, as the server last said; null before it has. */
	private Boolean vanished;
	private String problem = "", opening = "";
	private String outcomeLabel = "", outcomeText = "";
	private long outcomeAt;
	private boolean outcomeWaiting, outcomeGood;

	// This frame: the painter, the mouse in design pixels, the clip hits must fall inside, and the tooltip.
	private DeskPaint p;
	/** Who's online and their rows, worked out once at the start of a frame and kept until it ends. */
	private List<Roster.Online> frameOnline;
	private List<Roster.Row> frameRows;
	private final Map<String, List<Signal>> frameSignals = new HashMap<>();
	private float mx, my;
	private float[] clip;
	private String tipText;
	private float[] tipAt;

	private StaffDeskScreen(String player, Sample sample) {
		super(Component.literal("Staff desk"));
		this.sample = sample;
		if (sample != null) {
			selected = sample.selected();
			shown = sample.tab();
			ranks.addAll(sample.ranks());
			reasons.addAll(sample.reasons());
			Dossier d = dossier(selected);
			for (var e : sample.replies().entrySet()) d.answered(e.getKey(), ServerReply.of(e.getKey().command, e.getValue(), Util.getMillis()));
			tool = sample.replies().keySet().stream().filter(a -> a == StaffActions.SCAN || a == StaffActions.CAPTCHA_STATUS).findFirst().orElse(null);
		} else {
			// The filter as the last desk was left.
			DeskFilter.Picks picks = DeskFilter.load();
			ranks.addAll(picks.ranks());
			reasons.addAll(picks.reasons());
			if (player != null && !player.isEmpty()) {
				String real = Nicks.realName(player);
				selected = real != null ? real : player;
				shown = Tab.RECORDS;
			}
		}
		String chosen = selected;
		if (!chosen.isEmpty() && online().stream().noneMatch(o -> o.name().equalsIgnoreCase(chosen) || o.nick().equalsIgnoreCase(chosen))) pinned = chosen;
		selectedAt = Util.getMillis() - (selected.isEmpty() ? 0 : SETTLE_MS);
	}

	StaffDeskScreen(Sample sample) {
		this(sample.selected(), sample);
	}

	// ---- samples, for the game test's pictures ---------------------------------------------------------

	/** This sample desk with a punishment dialog open on the selected player. */
	StaffDeskScreen withDialog(DeskDialog.Kind kind, String rule) {
		openDialog(kind, false);
		if (rule != null) dialogRule(rule);
		return this;
	}

	/** This sample desk with the first Overview card's details open. */
	StaffDeskScreen withSignalSheet() {
		Roster.Row row = selectedRow();
		if (row != null) {
			List<Signal> s = signals(row);
			if (!s.isEmpty()) openSheet(new SignalSheet(s.getFirst()));
		}
		return this;
	}

	/** This sample desk with the selected player's flag dashboard card's details open. */
	StaffDeskScreen withFlagSheet() {
		Roster.Row row = selectedRow();
		if (row != null) signals(row).stream().filter(s -> s.kind() == SignalKind.DASHBOARD).findFirst().ifPresent(s -> openSheet(new SignalSheet(s)));
		return this;
	}

	/** This sample desk with the filter's list dropped. */
	StaffDeskScreen withRankMenu() {
		toggleMenu(Menu.RANKS, 293 + 34 - 232, 95 + 38);
		return this;
	}

	private void dialogRule(String code) {
		if (dialog == null) return;
		dialog.pickRuleByCode(code);
	}

	/** Opens the desk with {@code player} selected (pinned when they aren't online), or the first flagged player when null. */
	static void open(String player) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (!OnlineRoster.allowed()) {
			Message.lazarus().error("The staff desk is for Dragsim staff, with Staff Tools on.").send();
			return;
		}
		// A command typed in chat closes the chat box after it runs: schedule waits until it has, so the desk stays.
		mc.schedule(() -> mc.setScreen(new StaffDeskScreen(player, null)));
	}

	@Override
	protected void init() {
		super.init();
		if (asked || sample != null) return;
		asked = true;
		refresh();
	}

	private void refresh() {
		if (sample != null) return;
		problem = "";
		FlagSync.refresh(ok -> {
			if (!ok) problem = "Lazarus couldn't reach its server, so flags and reports may be old.";
		});
		if (DashboardReader.working()) return;
		DashboardReader.read(this, result -> {
			if (result == null) problem = "The flag dashboard couldn't be read.";
			if (AltCheck.due()) AltCheck.run(why -> {
				if (!why.isEmpty() && problem.isEmpty()) problem = why;
			});
		});
	}

	@Override
	public void tick() {
		super.tick();
		if (sample != null) return;
		FlagSync.refreshIfOlder(POLL_MS);
		askQueued();
		askAlerts();
		askNext();
	}

	/**
	 * Reads the selected player's alerts off their page on the flag dashboard, out of sight, once the selection has
	 * settled and nothing else is asking the server; only a player on the dashboard has one, and a read stands for a
	 * few minutes (one that failed too, so a player not there isn't asked about over and over).
	 */
	private void askAlerts() {
		if (selected.isEmpty() || Util.getMillis() - selectedAt < SETTLE_MS) return;
		if (Reply.busy() || DashboardReader.working() || AltCheck.running()) return;
		String player = selected, key = player.toLowerCase(Locale.ROOT);
		if (state().flags().stream().noneMatch(f -> f.name().equalsIgnoreCase(player))) return;
		AlertRead last = alertReads.get(key);
		long now = System.currentTimeMillis();
		if (last != null && now - last.at() < ALERTS_FRESH_MS) return;
		// Marked as asked at once: the read takes a few seconds, and this is asked again every tick.
		alertReads.put(key, last != null ? new AlertRead(last.alerts(), now, last.ok()) : new AlertRead(List.of(), now, false));
		DashboardReader.alerts(player, this, read -> alertReads.put(key, read == null
				? new AlertRead(last != null ? last.alerts() : List.of(), System.currentTimeMillis(), last != null && last.ok())
				: new AlertRead(read, System.currentTimeMillis(), true)));
	}

	/** Reads a player's alerts again as soon as the server is free. */
	private void rereadAlerts(String player) {
		alertReads.remove(player.toLowerCase(Locale.ROOT));
	}

	// ---- asking the server -----------------------------------------------------------------------

	/** The selected player's next record (or account detail, on the Account tab) once the selection has settled and the server is free. */
	private void askNext() {
		if (selected.isEmpty() || Util.getMillis() - selectedAt < SETTLE_MS || !tier().allows(Gate.Tier.MOD)) return;
		if (Reply.busy() || DashboardReader.working() || AltCheck.running()) return;
		Dossier d = dossier(selected);
		if (d.asking()) return;
		StaffActions next = shown == Tab.ACCOUNT ? d.next(Dossier.ACCOUNT) : null;
		if (next == null) next = d.next();
		if (next != null) ask(d, next);
	}

	/** What a command's answer looks like, so only its lines are taken (and kept out of chat). */
	private static Predicate<String> shape(StaffActions a, String player) {
		return switch (a) {
			case HISTORY -> LookHistory::looksLike;
			case WARNS -> t -> StaffReplies.entry(t) != null || (StaffReplies.header(t) != null && StaffReplies.header(t).startsWith("Warnings of"));
			case LOOKUP, ACCOUNT_HISTORY -> StaffReplies::lookupLine;
			// A detection about anyone (the anticheat's own line) can land while the answer is awaited: it isn't the answer.
			case AC_INFO -> t -> StaffReplies.clean(t).startsWith("[DragSim AC]") && !AcLine.isDetection(StaffReplies.clean(t));
			case SCAN -> t -> t.startsWith("Scan");
			// "greenum: no activity tracked" names the player like a chat line: one starting with their name is the answer.
			case CAPTCHA_STATUS -> t -> t.toLowerCase(Locale.ROOT).contains("captcha") || t.toLowerCase(Locale.ROOT).contains("check")
					|| StaffReplies.clean(t).toLowerCase(Locale.ROOT).startsWith(player.toLowerCase(Locale.ROOT) + ":");
			case AUDIT_LOG -> StaffReplies::auditLine;
			default -> StaffReplies::outcomeLine;
		};
	}

	/** How long a command's answer may take to start: /lookup ~4 s, /auditlog ~30 s. */
	private static long waitFor(StaffActions a) {
		return switch (a) {
			case LOOKUP, ACCOUNT_HISTORY -> 9000;
			case AUDIT_LOG -> 40000;
			case CAPTCHA_STATUS, SCAN, AC_INFO -> 6000;
			default -> Reply.WAIT_MS;
		};
	}

	/** Asks the server one of the dossier's commands, quietly; the answer lands in the dossier. */
	private void ask(Dossier d, StaffActions a) {
		d.asked(a);
		String p = d.player;
		askFor(a, p, lines -> d.answered(a, ServerReply.of(StaffActions.fill(a, p, "", "", "", ""), lines, Util.getMillis())));
	}

	/** Asks one of the commands the desk reads about {@code p}, quietly, the way the desk does; {@code done} gets the lines it took. */
	static void askFor(StaffActions a, String p, Consumer<List<String>> done) {
		switch (a) {
			case HISTORY -> LookHistory.lookup(p, done);
			case ALTS -> AltFlag.lookup(p, done);
			default -> Reply.ask(StaffActions.fill(a, p, "", "", "", ""), p, true, shape(a, p), waitFor(a), done);
		}
	}

	/** Asks a tool's command again (dupe scan, captcha status, audit log): now, or as soon as the server has answered what it's busy with. */
	private void askTool(StaffActions a, String p) {
		if (sample != null) return;
		Dossier d = dossier(p);
		if (d.status(a) == Dossier.Status.ASKING) return;
		d.retry(a);
		if (Reply.busy()) queued.put(a, p);
		else ask(d, a);
	}

	/** Tools asked while the server was busy, asked in turn once it's free. */
	private void askQueued() {
		if (queued.isEmpty() || Reply.busy()) return;
		var first = queued.entrySet().iterator().next();
		queued.remove(first.getKey());
		ask(dossier(first.getValue()), first.getKey());
	}

	/** Asks a record again, past any remembered answer (history is kept for a while by History on Look). */
	private static void retry(Dossier d, StaffActions a) {
		if (a == StaffActions.HISTORY) LookHistory.forget(d.player);
		d.retry(a);
	}

	private Dossier dossier(String player) {
		return dossiers.computeIfAbsent(player.toLowerCase(Locale.ROOT), k -> new Dossier(player));
	}

	private void select(String player) {
		if (player.equalsIgnoreCase(selected)) return;
		selected = player;
		selectedAt = Util.getMillis();
		bodyScrollTo = 0;
		confirming = "";
		menu = null;
		ipShown = false;
	}

	private void lookUp(String player) {
		if (online().stream().noneMatch(o -> o.name().equalsIgnoreCase(player))) pinned = player;
		select(player);
	}

	private void openFlags(String player) {
		if (sample != null) return;
		if (DashboardReader.working()) {
			note("Flag details", "Wait a moment while the desk reads the dashboard.", false);
			return;
		}
		opening = player;
		DashboardReader.open(player, this, ok -> {
			opening = "";
			if (!ok) note("Flag details", player + " isn't on the flag dashboard right now.", false);
		});
	}

	private void note(String label, String text, boolean good) {
		outcomeLabel = label;
		outcomeText = text;
		outcomeAt = Util.getMillis();
		outcomeWaiting = false;
		outcomeGood = good;
	}

	@Override
	public void onClose() {
		if (sample == null) DashboardReader.abandon();
		super.onClose();
	}

	// ---- who and what ----------------------------------------------------------------------------

	private StaffFlags.State state() {
		return sample != null ? sample.state() : FlagSync.state();
	}

	private Gate.Tier tier() {
		if (sample != null) return sample.tier();
		String rank = Staff.rank();
		if (rank.isEmpty()) rank = RankColours.known(Players.me());
		return Gate.tier(rank);
	}

	private List<Roster.Online> online() {
		if (sample != null) return sample.online();
		if (frameOnline != null) return frameOnline;
		return liveOnline();
	}

	/** Everyone on the tab list, with their nick or real name and rank. */
	private List<Roster.Online> liveOnline() {
		List<Roster.Online> out = new ArrayList<>();
		tab.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return out;
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (!Players.NAME.matcher(name).matches()) continue;
			Component shownName = info.getTabListDisplayName();
			String real = Nicks.realName(name);
			if (real != null) {
				out.add(new Roster.Online(real, name, RankColours.known(real)));
				continue;
			}
			String nick = Nicks.of(name);
			if (nick != null) {
				out.add(new Roster.Online(name, nick, RankColours.known(name)));
				continue;
			}
			// With many online the server pages the tab list with rows of its own, so a player's own entry may carry no
			// rank: then it's the one Rank Colours has learnt.
			String rank = shownName == null ? "" : Roster.rankOf(visible(shownName));
			out.add(new Roster.Online(name, "", rank.isEmpty() ? RankColours.known(name) : rank));
			if (shownName != null) tab.put(name.toLowerCase(Locale.ROOT), shownName);
		}
		return out;
	}

	private Roster.Row pinnedRow() {
		if (pinned.isEmpty()) return null;
		String key = pinned.toLowerCase(Locale.ROOT);
		if (!pinnedRanks.containsKey(key) && sample == null) {
			pinnedRanks.put(key, null);
			Http.json("/api/player/" + Http.encode(pinned) + "?mode=main").whenComplete((json, error) -> Minecraft.getInstance().execute(() -> {
				String rank = "";
				if (error == null && json != null && json.isJsonObject()) {
					JsonElement p = json.getAsJsonObject().get("player");
					JsonObject o = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
					rank = o.has("rank") && o.get("rank").isJsonPrimitive() ? Staff.rankKey(o.get("rank").getAsString()) : "";
				}
				pinnedRanks.put(key, rank);
			}));
		}
		String rank = pinnedRanks.get(key);
		return Roster.row(new Roster.Online(pinned, "", rank == null ? "" : rank), state(), System.currentTimeMillis());
	}

	private static String visible(Component c) {
		return c.getString().replaceAll("§.", "");
	}

	private boolean watched(String name) {
		if (sample != null) return sample.state().watch().stream().anyMatch(w -> w.name().equalsIgnoreCase(name));
		return WatchList.INSTANCE != null && WatchList.INSTANCE.watched(name);
	}

	private boolean isOnline(String name) {
		return online().stream().anyMatch(o -> o.name().equalsIgnoreCase(name));
	}

	private Roster.Row selectedRow() {
		Roster.Row pin = pinnedRow();
		if (pin != null && pin.name().equalsIgnoreCase(selected)) return pin;
		return rows().stream().filter(r -> r.name().equalsIgnoreCase(selected)).findFirst().orElse(null);
	}

	/** Everyone online as rows, this frame's when one is being drawn. */
	private List<Roster.Row> rows() {
		return frameRows != null ? frameRows : build(online(), state(), System.currentTimeMillis());
	}

	/** The last rows built, and what they were built from: the same online list and shared state within the same half minute give the same rows. */
	private List<Roster.Row> builtRows;
	private List<Roster.Online> builtFrom;
	private StaffFlags.State builtState;
	private long builtBucket;

	private List<Roster.Row> build(List<Roster.Online> online, StaffFlags.State state, long now) {
		long bucket = now / 30_000;
		if (builtRows != null && state == builtState && bucket == builtBucket && online.equals(builtFrom)) return builtRows;
		builtRows = Roster.build(online, state, now);
		builtFrom = List.copyOf(online);
		builtState = state;
		builtBucket = bucket;
		return builtRows;
	}

	/** The colour a player's rank shows in: the tab list's own colour for the tag when there is one, else the rank's. */
	private int rankColour(Roster.Row r) {
		Component shownName = tab.get(r.name().toLowerCase(Locale.ROOT));
		if (shownName != null) {
			int[] found = {0};
			shownName.visit((style, text) -> {
				if (found[0] == 0 && !text.isBlank() && style.getColor() != null) found[0] = 0xFF000000 | style.getColor().getValue();
				return found[0] != 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
			}, Style.EMPTY);
			if (found[0] != 0) return found[0];
		}
		String rank = r.rank().toUpperCase(Locale.ROOT);
		if (rank.startsWith("DRAGON") || rank.equals("WHALE") || rank.equals("PATRICK")) return DRAGON;
		if (rank.startsWith("MVP")) return 0xFF67E8F9;
		if (rank.startsWith("VIP")) return GREEN;
		char code = Ranks.code(r.rank());
		return switch (code) {
			case 'c', '4' -> RED_DOT;
			case 'a', '2' -> GREEN;
			case 'b', '3' -> 0xFF67E8F9;
			case '9', '1' -> 0xFF60A5FA;
			case '6', 'e' -> 0xFFFBBF24;
			case 'd', '5' -> DRAGON;
			default -> MUTED;
		};
	}

	/** The client the anticheat saw a player join with ("Lunarclient:v2.23.14-2645,fabric"), as people say it: "Lunar 2.23.14 · Fabric". */
	static String clientName(String brand) {
		if (brand.isBlank()) return "";
		String[] parts = brand.split(",");
		String head = parts[0].trim(), name = head, version = "";
		int colon = head.indexOf(':');
		if (colon >= 0) {
			name = head.substring(0, colon);
			version = head.substring(colon + 1).replaceFirst("^[vV]", "").replaceFirst("-\\d+$", "");
		}
		name = name.replaceFirst("(?i)client$", "");
		StringBuilder out = new StringBuilder(cap(name.isEmpty() ? head : name));
		if (!version.isEmpty()) out.append(' ').append(version);
		for (int i = 1; i < parts.length; i++) if (!parts[i].isBlank()) out.append(" · ").append(cap(parts[i].trim()));
		return out.toString();
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The last {@link #detected} worked out for each player, and what from: worked out again only when one of those changes. */
	private record DetectedFrom(StaffFlags.State state, AlertRead read, AlertState status, long minute, Detected detected) {
	}

	private final Map<String, DetectedFrom> detectedCache = new HashMap<>();

	/** What the anticheat has caught a player on over the last hour, and how far reading their dashboard page has got. */
	private Detected detected(Roster.Row row) {
		String key = row.name().toLowerCase(Locale.ROOT);
		StaffFlags.State s = state();
		AlertRead read = sample != null ? sampleRead(key) : alertReads.get(key);
		boolean reading = sample == null && row.name().equalsIgnoreCase(DashboardReader.readingAlertsOf());
		AlertState status = reading ? AlertState.READING : read == null ? AlertState.UNREAD : read.ok() ? AlertState.READ : AlertState.FAILED;
		long now = System.currentTimeMillis(), minute = now / 60_000;
		DetectedFrom last = detectedCache.get(key);
		if (last != null && last.state() == s && last.read() == read && last.status() == status && last.minute() == minute) return last.detected();
		List<StaffFlags.Flag> flags = s.flags().stream().filter(f -> f.name().equalsIgnoreCase(row.name())).toList();
		boolean ok = read != null && read.ok();
		List<FlagAlerts.Check> checks = FlagAlerts.summarise(ok ? read.alerts() : List.of(), ok ? read.at() : 0, s.detections(), row.name(), row.nick(), now);
		Detected d = new Detected(flags, checks, status, ok ? read.at() : 0);
		detectedCache.put(key, new DetectedFrom(s, read, status, minute, d));
		return d;
	}

	/** A sample desk's alert read for a player, as if just read. */
	private final Map<String, AlertRead> sampleReads = new HashMap<>();

	private AlertRead sampleRead(String key) {
		List<FlagAlerts.Alert> alerts = sample.alerts().get(key);
		if (alerts == null) return null;
		return sampleReads.computeIfAbsent(key, k -> new AlertRead(alerts, System.currentTimeMillis() - 30_000, true));
	}

	private StaffFlags.Brand brandOf(String name) {
		return state().brands().stream().filter(b -> b.player().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	// ---- this frame's hits, hover and tips -------------------------------------------------------

	private void hit(float x, float y, float w, float h, Runnable action) {
		if (clip != null) {
			float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
			x = Math.max(x, clip[0]);
			y = Math.max(y, clip[1]);
			w = x1 - x;
			h = y1 - y;
			if (w <= 0 || h <= 0) return;
		}
		hits.add(new Hit(x, y, w, h, action));
	}

	private boolean inside(float x, float y, float w, float h) {
		if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[0] + clip[2] || my >= clip[1] + clip[3])) return false;
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private float hover(Object key, float x, float y, float w, float h) {
		return hover.to(key, inside(x, y, w, h));
	}

	private void tip(float x, float y, float w, float h, String text) {
		if (text != null && !text.isEmpty() && inside(x, y, w, h)) {
			tipText = text;
			tipAt = new float[]{x, y, w, h};
		}
	}

	private void clipTo(float x, float y, float w, float h) {
		clip = new float[]{x, y, w, h};
		p.clip(x, y, w, h);
	}

	private void unclip() {
		clip = null;
		p.unclip();
	}

	private final DeskDialog.Host host = new DeskDialog.Host() {
		@Override
		public void hit(float x, float y, float w, float h, Runnable action) {
			StaffDeskScreen.this.hit(x, y, w, h, action);
		}

		@Override
		public float hover(Object key, float x, float y, float w, float h) {
			return StaffDeskScreen.this.hover(key, x, y, w, h);
		}

		@Override
		public void tip(float x, float y, float w, float h, String text) {
			StaffDeskScreen.this.tip(x, y, w, h, text);
		}
	};

	// ---- buttons ---------------------------------------------------------------------------------

	/** How a button looks (docs/dragsim/desk-design). */
	private enum Look {PLAIN, DANGER, DANGER_OUTLINE, ACCENT, ACCENT_OUTLINE, DASHED, GHOST, SURE}

	/**
	 * A button: its box, its label (centred, or left with {@code hint} on the right), faded with {@code why} as its
	 * tooltip when it can't be used. Returns nothing; registers its hit.
	 */
	private void button(Object key, float x, float y, float w, float h, float r, String label, DeskType type, String hint, Look look,
			String why, Runnable action) {
		boolean can = why == null || why.isEmpty();
		float hv = can ? hover(key, x, y, w, h) : 0f;
		float before = p.alpha;
		if (!can) p.alpha *= 0.4f;
		int text = TEXT;
		switch (look) {
			case DANGER, SURE -> {
				p.round(x, y, w, h, r, mix(RED, RED_HOT, hv));
				text = 0xFFFFFFFF;
			}
			case DANGER_OUTLINE -> {
				p.box(x, y, w, h, r, mix(RED_BG, 0xFF2A1214, hv), RED_EDGE);
				text = RED_SOFT;
			}
			case ACCENT -> {
				p.round(x, y, w, h, r, mix(VIOLET_BG, 0xFF3A2860, hv));
				text = VIOLET_TEXT;
			}
			case ACCENT_OUTLINE -> {
				p.box(x, y, w, h, r, mix(0x00000000, 0x802A1D42, hv), VIOLET_EDGE);
				text = VIOLET_TEXT;
			}
			case DASHED -> {
				if (hv > 0) p.round(x, y, w, h, r, mix(0x00000000, 0x0AFFFFFF, hv));
				p.dashed(x, y, w, h, r, DIM);
				text = mix(MUTED, TEXT, hv);
			}
			case GHOST -> p.box(x, y, w, h, r, mix(0x00000000, FIELD, hv), BORDER);
			default -> p.box(x, y, w, h, r, mix(FIELD, BUTTON_HOVER, hv), BORDER);
		}
		float th = type.lineHeight();
		if (hint == null || hint.isEmpty()) {
			p.text(label, x + (w - p.width(label, type)) / 2, y + (h - th) / 2, type, text);
		} else {
			p.text(label, x + 13, y + (h - th) / 2, type, text);
			if (hint.equals(">")) p.icon(DeskIcons.CHEVRON_RIGHT, x + w - 9 - 14, y + (h - 14) / 2, 14, FAINT);
			else p.text(hint, x + w - 13 - p.width(hint, DeskType.I400_11), y + (h - 14) / 2, DeskType.I400_11, FAINT);
		}
		p.alpha = before;
		if (can) hit(x, y, w, h, action);
		else tip(x, y, w, h, why);
	}

	// ---- drawing ---------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		hits.clear();
		tipText = null;
		clip = null;
		if (!confirming.isEmpty() && Util.getMillis() - confirmingAt > CONFIRM_MS) confirming = "";
		float open = sample != null ? 1f : Math.min(1f, (Util.getMillis() - openedAt) / 240f);
		g.fill(0, 0, width, height, Math.round(0.6f * open * 255) << 24 | 0x050507);

		float[] fit = fit();
		int gui = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
		float f = fit[0], shrink = fit[1];
		float ox = fit[2], oy = fit[3] + (1f - open) * 8 * f;
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(shrink / gui, shrink / gui);
		p = new DeskPaint(g, font, f, ox, oy, shrink / gui);
		p.alpha = open;
		float rawX = (mouseX * gui / shrink - ox) / f, rawY = (mouseY * gui / shrink - oy) / f;
		boolean overlay = dialog != null || sheet != null;
		mx = overlay ? -9999 : rawX;
		my = overlay ? -9999 : rawY;
		try {
			frameOnline = online();
			frameRows = build(frameOnline, state(), System.currentTimeMillis());
			desk();
			if (menu != null && !overlay) {
				mx = rawX;
				my = rawY;
				menu();
			}
			if (sheet != null) {
				hits.clear();
				mx = rawX;
				my = rawY;
				sheet();
			}
			if (dialog != null) {
				hits.clear();
				mx = rawX;
				my = rawY;
				dialog.draw(p, mx, my, host, Players.me(), this::confirmDialog, () -> dialog = null);
			}
			tooltip();
		} finally {
			pose.popMatrix();
			frameOnline = null;
			frameRows = null;
			frameSignals.clear();
		}
	}

	private void desk() {
		p.box(0, 0, W, H, 16, BG, SOFT);
		List<Roster.Row> all = rows();
		// Someone who leaves (kicked, banned, logged off) while selected stays, pinned, rather than the desk jumping to someone else.
		if (!selected.isEmpty() && pinned.isEmpty() && sample == null && all.stream().noneMatch(r -> r.name().equalsIgnoreCase(selected))) pinned = selected;
		Roster.Row pin = pinnedRow();
		if (pin != null && all.stream().anyMatch(r -> r.name().equalsIgnoreCase(pin.name()))) pinned = "";
		Roster.Row pinRow = pinned.isEmpty() ? null : pin;
		List<Roster.Row> rows = visibleRows(all);
		boolean selectedShown = rows.stream().anyMatch(r -> r.name().equalsIgnoreCase(selected)) || pinRow != null && pinRow.name().equalsIgnoreCase(selected);
		if (!selectedShown && !(sample != null && !selected.isEmpty())) select(rows.isEmpty() ? (pinRow != null ? pinRow.name() : "") : rows.getFirst().name());

		header(all);
		list(all, rows, pinRow);
		Roster.Row row = pinRow != null && pinRow.name().equalsIgnoreCase(selected) ? pinRow
				: all.stream().filter(r -> r.name().equalsIgnoreCase(selected)).findFirst().orElse(null);
		boolean on = isOnline(selected);
		main(row, on);
		rail(row, on);
	}

	/** A rank as the rank filter keeps it: "VIP", "MVP+", "" for Default. */
	private static String rankKey(String rank) {
		return rank.trim().toUpperCase(Locale.ROOT);
	}

	/** A rank as the rank filter shows it. */
	private static String rankLabel(String key) {
		return key.isEmpty() ? "Default" : key;
	}

	/** The rows the filter keeps: of a rank picked (any, when none are) and flagged for a reason picked (any, when none are). */
	private List<Roster.Row> picked(List<Roster.Row> all) {
		if (ranks.isEmpty() && reasons.isEmpty()) return all;
		return all.stream().filter(r -> (ranks.isEmpty() || ranks.contains(rankKey(r.rank())))
				&& (reasons.isEmpty() || r.reasons().stream().anyMatch(x -> reasons.contains(x.source())))).toList();
	}

	/** What the filter picks, as words: "REPORT or ALT · Default or VIP". */
	private String pickedWords() {
		List<String> parts = new ArrayList<>();
		if (!reasons.isEmpty()) parts.add(String.join(" or ", reasons.stream().map(StaffDeskScreen::reasonLabel).toList()));
		if (!ranks.isEmpty()) parts.add(String.join(" or ", ranks.stream().map(StaffDeskScreen::rankLabel).toList()));
		return String.join(" · ", parts);
	}

	/** The rows the list shows: filtered by what the filter picks, the segment and the search. */
	private List<Roster.Row> visibleRows(List<Roster.Row> all) {
		all = picked(all);
		List<Roster.Row> rows = switch (filter) {
			case FLAGGED -> all.stream().filter(Roster.Row::flagged).toList();
			case WATCHED -> all.stream().filter(r -> watched(r.name())).toList();
			case EVERYONE -> all;
		};
		return Roster.filter(rows, search);
	}

	// ---- header ----------------------------------------------------------------------------------

	private void header(List<Roster.Row> all) {
		p.round(20, 27, 32, 32, 8, VIOLET_BG);
		p.icon(DeskIcons.SHIELD, 27, 34, 18, VIOLET);
		Gate.Tier tier = tier();
		String who = "Dragsim staff · " + (tier == Gate.Tier.NONE ? "helper" : tier.tag());
		p.text("Staff Desk", 62, 26, DeskType.I600_15, TEXT);
		p.text(who, 62, 45, DeskType.I400_12, MUTED);
		float sx = 62 + Math.max(p.width("Staff Desk", DeskType.I600_15), p.width(who, DeskType.I400_12)) + 28;

		// Your own actions, from the right.
		float right = 1260;
		if (DashboardReader.working() || AltCheck.running()) {
			String reading = DashboardReader.readingAlertsOf() != null ? "Reading alerts…" : DashboardReader.working() ? "Reading the dashboard…" : "Checking alts…";
			right -= p.width(reading, DeskType.I400_12);
			p.text(reading, right, 35, DeskType.I400_12, MUTED);
			right -= 8;
		} else {
			float hv = hover("refresh", right - 32, 27, 32, 32);
			p.box(right - 32, 27, 32, 32, 8, mix(FIELD, BUTTON_HOVER, hv), BORDER);
			p.icon(DeskIcons.REFRESH, right - 32 + 8.5f, 35.5f, 15, SUB);
			hit(right - 32, 27, 32, 32, this::refresh);
			tip(right - 32, 27, 32, 32, "Read the flag dashboard and Lazarus's shared lists again (F5)");
			right -= 40;
		}
		if (tier.allows(Gate.Tier.ADMIN)) {
			float w = p.width("Hop", DeskType.I500_13) + 12 + 6 + 14 + 10 + 2;
			float x = right - w;
			float hv = hover("hop", x, 27, w, 32);
			p.box(x, 27, w, 32, 8, mix(FIELD, BUTTON_HOVER, hv), menu == Menu.HOP ? VIOLET_EDGE : BORDER);
			p.text("Hop", x + 13, 35, DeskType.I500_13, TEXT);
			p.icon(menu == Menu.HOP ? DeskIcons.CHEVRON_UP : DeskIcons.CHEVRON_DOWN, x + 13 + p.width("Hop", DeskType.I500_13) + 6, 36, 14, MUTED);
			hit(x, 27, w, 32, () -> toggleMenu(Menu.HOP, Math.min(x, 1260 - 232), 63));
			right = x - 8;
		}
		for (StaffActions a : List.of(StaffActions.STAFF_CHAT, StaffActions.LOCK_CHAT)) {
			String label = a == StaffActions.STAFF_CHAT ? "Staff chat" : a.label;
			float w = p.width(label, DeskType.I500_13) + 26;
			float x = right - w;
			String why = StaffActions.blocked(a, tier, true);
			button("hb" + a, x, 27, w, 32, 8, label, DeskType.I500_13, "", Look.PLAIN, why.isEmpty() ? "" : why + " only", () -> pick(a, null));
			right = x - 8;
		}
		float vw = p.width("Vanish", DeskType.I500_13) + 26 + 26 + 8;
		float vx = right - vw;
		String vwhy = StaffActions.blocked(StaffActions.VANISH, tier, true);
		float before = p.alpha;
		if (!vwhy.isEmpty()) p.alpha *= 0.4f;
		float hv = vwhy.isEmpty() ? hover("vanish", vx, 27, vw, 32) : 0;
		p.box(vx, 27, vw, 32, 8, mix(FIELD, BUTTON_HOVER, hv), BORDER);
		boolean on = Boolean.TRUE.equals(vanished);
		p.round(vx + 13, 35.5f, 26, 15, 99, on ? 0xFF7C3AED : DIM);
		p.round(vx + 13 + (on ? 13 : 2), 37.5f, 11, 11, 99, on ? 0xFFFFFFFF : MUTED);
		p.text("Vanish", vx + 13 + 26 + 8, 35, DeskType.I500_13, TEXT);
		p.alpha = before;
		if (vwhy.isEmpty()) hit(vx, 27, vw, 32, () -> pick(StaffActions.VANISH, null));
		else tip(vx, 27, vw, 32, vwhy + " only");
		right = vx - 8;

		// The stats, as many as fit between.
		long flagged = all.stream().filter(Roster.Row::flagged).count();
		StaffFlags.State s = state();
		String read = s.flagsAt() > 0 ? Roster.ago(System.currentTimeMillis() - s.flagsAt()) : "not yet";
		String[][] stats = {{"Online", String.valueOf(all.size())}, {"Flagged", String.valueOf(flagged)}, {"Open reports", String.valueOf(s.reports().size())},
				{"Dashboard read", read}};
		for (int i = 0; i < stats.length; i++) {
			float min = i == 3 ? 110 : 84;
			float w = Math.max(min, Math.max(p.width(stats[i][0], DeskType.I400_11), p.width(stats[i][1], DeskType.I600_16)) + 26);
			if (sx + w > right - 8) break;
			boolean danger = i == 1 && flagged > 0;
			boolean live = i == 1;
			float hv2 = live ? hover("stat" + i, sx, 20, w, 46) : 0;
			p.box(sx, 20, w, 46, 10, danger ? mix(RED_BG, 0xFF2A1214, hv2) : mix(0x00000000, CARD, hv2), danger ? RED_CARD_EDGE : BORDER);
			p.text(stats[i][0], sx + 13, 27, DeskType.I400_11, danger ? RED_TEXT : MUTED);
			p.text(stats[i][1], sx + 13, 41, DeskType.I600_16, danger ? RED_SOFT : TEXT);
			if (live) hit(sx, 20, w, 46, () -> {
				filter = Filter.FLAGGED;
				listScrollTo = 0;
			});
			sx += w + 8;
		}
	}

	private void toggleMenu(Menu m, float x, float y) {
		if (menu == m) {
			menu = null;
			return;
		}
		menu = m;
		menuX = x;
		menuY = y;
	}

	// ---- the list --------------------------------------------------------------------------------

	private void list(List<Roster.Row> all, List<Roster.Row> rows, Roster.Row pin) {
		p.box(20, 82, 320, 658, 12, CARD, BORDER);
		// Search, and beside it the rank filter.
		boolean typing = !search.isEmpty();
		p.box(33, 95, 254, 34, 8, INSET, typing ? VIOLET_EDGE : BORDER);
		p.icon(DeskIcons.SEARCH, 44, 105, 14, FAINT);
		boolean caret = dialog == null && sheet == null && Util.getMillis() / 500 % 2 == 0;
		if (search.isEmpty()) p.text("Search name or nick…", 66, 104, DeskType.I400_13, FAINT);
		else p.text(p.fit(search + (caret ? "|" : ""), DeskType.I400_13, 210), 66, 104, DeskType.I400_13, TEXT);
		filterButton(293, 95);
		// Segments, counting only what the filter picks.
		List<Roster.Row> ofRanks = picked(all);
		long flaggedN = ofRanks.stream().filter(Roster.Row::flagged).count(), watchedN = ofRanks.stream().filter(r -> watched(r.name())).count();
		p.round(33, 139, 294, 32, 8, FIELD);
		String[] labels = {"Flagged · " + flaggedN, "All · " + ofRanks.size(), "Watched · " + watchedN};
		Filter[] filters = {Filter.FLAGGED, Filter.EVERYONE, Filter.WATCHED};
		float segW = (288 - 8) / 3f;
		for (int i = 0; i < 3; i++) {
			float x = 36 + i * (segW + 4);
			boolean on = filter == filters[i];
			float hv = hover("seg" + i, x, 142, segW, 26);
			if (on) p.round(x, 142, segW, 26, 6, BORDER);
			else if (hv > 0) p.round(x, 142, segW, 26, 6, mix(0x00000000, HOVER, hv));
			p.text(labels[i], x + (segW - p.width(labels[i], DeskType.I500_12)) / 2, 142 + 5.5f, DeskType.I500_12, on ? TEXT : mix(MUTED, TEXT, hv));
			Filter fl = filters[i];
			hit(x, 142, segW, 26, () -> {
				filter = fl;
				listScrollTo = 0;
			});
		}
		p.rect(21, 183, 318, 1, BORDER);

		// The rows, scrolled.
		float top = 184, viewH = 739 - top;
		listViewH = viewH;
		float off = listScroll.to((float) listScrollTo);
		clipTo(21, top, 318, viewH);
		float y = top + 8 - off, selTop = -1;
		if (pin != null) {
			label("LOOKED UP", y);
			y += 22;
			if (pin.name().equalsIgnoreCase(selected)) selTop = y + off;
			y = rowCard(pin, y, true) + 4 + 6;
		}
		List<Roster.Row> flaggedRows = filter == Filter.WATCHED ? rows : rows.stream().filter(Roster.Row::flagged).toList();
		List<Roster.Row> rest = filter == Filter.EVERYONE ? rows.stream().filter(r -> !r.flagged()).toList() : List.of();
		if (!flaggedRows.isEmpty()) {
			label((filter == Filter.WATCHED ? "WATCHED · " : "FLAGGED · ") + flaggedRows.size(), y);
			y += 22 + 4;
			for (Roster.Row r : flaggedRows) {
				if (r.name().equalsIgnoreCase(selected)) selTop = y + off;
				y = (r.flagged() ? rowCard(r, y, false) : rowLine(r, y)) + 4;
			}
		}
		if (!rest.isEmpty()) {
			y += 8;
			label("EVERYONE ELSE · " + rest.size(), y);
			y += 22 + 4;
			for (Roster.Row r : rest) {
				if (r.name().equalsIgnoreCase(selected)) selTop = y + off;
				y = rowLine(r, y) + 4;
			}
		}
		if (rows.isEmpty() && pin == null) emptyList(y);
		listContentH = y + off - top + 4;
		unclip();
		fades(21, top, 318, viewH, off, listContentH, CARD);
		if (follow && selTop >= 0) {
			float rel = selTop - top;
			if (rel < listScrollTo) listScrollTo = rel - 8;
			else if (rel + 76 > listScrollTo + viewH) listScrollTo = rel + 84 - viewH;
		}
		follow = false;
		listScrollTo = Math.clamp(listScrollTo, 0, Math.max(0, listContentH - viewH));
		scrollbar(332, top + 4, viewH - 8, listContentH, (float) listScrollTo);
	}

	private void label(String text, float y) {
		p.text(text, 39, y + 4, DeskType.I600_11, FAINT, 0.06f);
	}

	/** The filter's button: a funnel, lit with how many reasons and ranks are picked once any are; it drops the filter's list. */
	private void filterButton(float x, float y) {
		boolean on = !ranks.isEmpty() || !reasons.isEmpty(), open = menu == Menu.RANKS;
		float hv = hover("ranks", x, y, 34, 34);
		p.box(x, y, 34, 34, 8, on ? mix(VIOLET_BG, 0xFF3A2860, hv) : mix(INSET, BUTTON_HOVER, hv), on || open ? VIOLET_EDGE : BORDER);
		p.icon(DeskIcons.FILTER, x + 10, y + 10, 14, on ? VIOLET_TEXT : mix(FAINT, SUB, hv));
		if (on) {
			String n = String.valueOf(ranks.size() + reasons.size());
			float bw = Math.max(14, p.width(n, DeskType.I600_10) + 8);
			p.round(x + 34 - bw + 4, y - 4, bw, 14, 99, 0xFF7C3AED);
			p.text(n, x + 34 - bw + 4 + (bw - p.width(n, DeskType.I600_10)) / 2, y - 3, DeskType.I600_10, 0xFFFFFFFF);
		}
		hit(x, y, 34, 34, () -> toggleMenu(Menu.RANKS, x + 34 - 232, y + 38));
		tip(x, y, 34, 34, on ? "Showing " + pickedWords() : "Filter by what they're flagged for, or by rank");
	}

	private void emptyList(float y) {
		String title, sub;
		DeskIcons icon;
		int colour;
		if (!search.isEmpty()) {
			icon = DeskIcons.SEARCH;
			colour = FAINT;
			title = "Nobody online matches “" + search + "”";
			sub = Players.NAME.matcher(search).matches() ? "Press Enter to look up " + search + " anyway." : "Try part of a name or nick.";
		} else if (!ranks.isEmpty() || !reasons.isEmpty()) {
			icon = DeskIcons.FILTER;
			colour = FAINT;
			title = "Nobody " + (filter == Filter.WATCHED ? "watched " : "") + "online matches the filter";
			sub = pickedWords() + ". Pick others from the filter, or show everyone.";
		} else if (filter == Filter.FLAGGED) {
			icon = DeskIcons.CHECK;
			colour = GREEN;
			title = "Nobody is flagged";
			long at = state().flagsAt();
			sub = at > 0 ? "The dashboard was read " + Roster.ago(System.currentTimeMillis() - at) + "." : "The dashboard hasn't been read yet.";
		} else if (filter == Filter.WATCHED) {
			icon = DeskIcons.SEARCH;
			colour = FAINT;
			title = "Nobody watched is online";
			sub = "Watch a player from the actions on the right.";
		} else {
			icon = DeskIcons.SEARCH;
			colour = FAINT;
			title = "Nobody is online";
			sub = "";
		}
		float h = 24 + 20 + 8 + 16 + (sub.isEmpty() ? 0 : 8 + 15) + 24;
		p.box(29, y, 296, h, 12, CARD, BORDER);
		p.icon(icon, 29 + 148 - 10, y + 24, 20, colour);
		float tw = p.width(title, DeskType.I600_13);
		p.text(p.fit(title, DeskType.I600_13, 264), 29 + Math.max(16, 148 - tw / 2), y + 52, DeskType.I600_13, TEXT);
		if (!sub.isEmpty()) {
			List<String> lines = p.wrap(sub, DeskType.I400_12, 264);
			p.text(lines.getFirst(), 29 + 148 - p.width(lines.getFirst(), DeskType.I400_12) / 2, y + 76, DeskType.I400_12, MUTED);
		}
	}

	/** A flagged (or looked-up) player as a card: face, rank and name, a line under, their score, and chips. Returns its bottom. */
	private float rowCard(Roster.Row r, float y, boolean lookedUp) {
		List<String> chips = new ArrayList<>();
		List<int[]> colours = new ArrayList<>();
		if (lookedUp) {
			chips.add("");
		}
		for (Roster.Chip c : r.chips()) {
			chips.add(c.label());
			colours.add(chipColours(c));
		}
		float x = 29, w = 296;
		// Chips wrap under the name, from 38 pixels in.
		List<float[]> flow = new ArrayList<>();
		float cx = 0;
		int row = 0;
		float avail = w - 2 - 20 - 38;
		for (Roster.Chip c : r.chips()) {
			float cw = p.width(c.label(), DeskType.I600_10, 0.04f) + 12;
			if (cx > 0 && cx + cw > avail) {
				cx = 0;
				row++;
			}
			flow.add(new float[]{cx, row, cw});
			cx += cw + 4;
		}
		int chipRows = r.chips().isEmpty() || lookedUp ? 0 : row + 1;
		float h = 2 + 20 + 30 + (chipRows > 0 ? 8 + chipRows * 16 + (chipRows - 1) * 4 : 0);
		if (hidden(y, h)) return y + h;
		boolean sel = r.name().equalsIgnoreCase(selected);
		float hv = hover("row" + r.name(), x, y, w, h);
		int bg = sel ? SELECTED : mix(0x00000000, HOVER, hv);
		p.box(x, y, w, h, 10, bg, sel ? VIOLET_EDGE : 0x00000000);
		float ix = x + 11, iy = y + 11;
		p.face(r.name(), ix, iy + 1, 28, 6, sel ? SELECTED : hv > 0.5f ? HOVER : CARD);
		float nx = ix + 38;
		String score = r.score() > 0 ? Roster.scoreText(r.score()) : "";
		float scoreW = score.isEmpty() ? 0 : p.width(score, DeskType.M600_13) + 10;
		if (lookedUp) {
			float pw = p.width("offline", DeskType.I500_11) + 16;
			scoreW = pw + 10;
			p.box(x + w - 11 - pw, iy + 6, pw, 18, 99, 0x00000000, BORDER);
			p.text("offline", x + w - 11 - pw + 8, iy + 8, DeskType.I500_11, MUTED);
		}
		nameLine(r, nx, iy, w - 22 - 38 - scoreW, DeskType.I600_13, DeskType.I600_13);
		String sub = lookedUp ? "not online" : rowSub(r);
		p.text(p.fit(sub, DeskType.I400_11, w - 22 - 38 - scoreW), nx, iy + 16, DeskType.I400_11, MUTED);
		if (!score.isEmpty() && !lookedUp) p.text(score, x + w - 11 - p.width(score, DeskType.M600_13), iy + 7, DeskType.M600_13, ORANGE_TEXT);
		for (int i = 0; i < flow.size() && chipRows > 0; i++) {
			float[] f = flow.get(i);
			float chx = nx + f[0], chy = iy + 30 + 8 + f[1] * 20;
			int[] c = colours.get(i);
			p.round(chx, chy, f[2], 16, 5, c[0]);
			p.text(r.chips().get(i).label(), chx + 6, chy + 2, DeskType.I600_10, c[1], 0.04f);
		}
		hit(x, y, w, h, () -> select(r.name()));
		return y + h;
	}

	/** Anyone else as a line: a small face, rank, name, their nick, and "you". Returns its bottom. */
	private float rowLine(Roster.Row r, float y) {
		float x = 29, w = 296, h = 32;
		if (hidden(y, h)) return y + h;
		boolean sel = r.name().equalsIgnoreCase(selected), me = r.name().equalsIgnoreCase(Players.me());
		float hv = hover("row" + r.name(), x, y, w, h);
		int bg = sel ? SELECTED : mix(0x00000000, HOVER, hv);
		p.box(x, y, w, h, 8, bg, sel ? VIOLET_EDGE : 0x00000000);
		p.face(r.name(), x + 11, y + 6, 20, 5, sel ? SELECTED : hv > 0.5f ? HOVER : CARD);
		float youW = me ? p.width("you", DeskType.I400_11) + 10 : 0;
		float tx = nameLine(r, x + 41, y + 8, w - 52 - youW, DeskType.I600_13, DeskType.I500_13);
		if (!r.nick().isEmpty() && tx < x + w - 11 - youW - 30) {
			String nick = p.fit("as " + r.nick(), DeskType.I400_12, x + w - 11 - youW - tx - 10);
			p.text(nick, tx + 10, y + 8.5f, DeskType.I400_12, FAINT);
		}
		if (me) p.text("you", x + w - 11 - p.width("you", DeskType.I400_11), y + 9, DeskType.I400_11, FAINT);
		hit(x, y, w, h, () -> select(r.name()));
		return y + h;
	}

	/** Whether a row from {@code y}, {@code h} tall, lies wholly outside the clip, so drawing it would show nothing. */
	private boolean hidden(float y, float h) {
		return clip != null && (y + h <= clip[1] || y >= clip[1] + clip[3]);
	}

	/** A player's rank (in its colour) and name in {@code max} pixels: the rank goes first when both don't fit. Returns where it ends. */
	private float nameLine(Roster.Row r, float x, float y, float max, DeskType rankType, DeskType nameType) {
		String rank = r.rank();
		float nameW = p.width(r.name(), nameType);
		float gap = rankType == nameType ? p.width(" ", nameType) : 10;
		float rankW = rank.isEmpty() ? 0 : p.width(rank, rankType) + gap;
		if (rankW + nameW > max) rankW = 0;
		if (rankW > 0) p.text(rank, x, y, rankType, rankColour(r));
		String name = p.fit(r.name(), nameType, max - rankW);
		p.text(name, x + rankW, y, nameType, TEXT);
		return x + rankW + p.width(name, nameType);
	}

	/** The line under a flagged player's name: their rank and when they joined, or what flags them. */
	private String rowSub(Roster.Row r) {
		StaffFlags.Brand b = brandOf(r.name());
		String rank = r.rank().isEmpty() ? "Default" : r.rank();
		// What the anticheat keeps catching them on says more than when they joined.
		List<FlagAlerts.Check> checks = detected(r).checks();
		if (!checks.isEmpty()) return rank + " · " + FlagAlerts.line(checks);
		if (b != null) return rank + " · joined " + Roster.ago(System.currentTimeMillis() - b.at());
		if (!r.reasons().isEmpty()) {
			String t = r.reasons().getFirst().text();
			return t.isEmpty() ? rank : Character.toLowerCase(t.charAt(0)) + t.substring(1);
		}
		return rank;
	}

	/** A chip's fill and text colours. */
	private static int[] chipColours(Roster.Chip c) {
		return switch (c.source()) {
			case PRIORITY -> new int[]{ORANGE_BG, ORANGE_TEXT};
			case SETBACK -> new int[]{PINK_BG, PINK_TEXT};
			case REPORT -> new int[]{YELLOW_BG, YELLOW};
			case CAPTCHA -> c.bad() ? new int[]{RED_CHIP, RED_TEXT} : new int[]{AMBER_BG, ORANGE_TEXT};
			case ALT -> new int[]{PURPLE_BG, PURPLE};
			case WATCH -> new int[]{BLUE_BG, BLUE};
			case DASHBOARD, DETECTION -> new int[]{RED_CHIP, RED_TEXT};
		};
	}

	/** Where content runs on past a scrolling area's top or bottom edge, it fades into {@code bg} rather than being cut. */
	private void fades(float x, float y, float w, float h, float off, float contentH, int bg) {
		int rgb = bg & 0xFFFFFF;
		float depth = 18;
		p.group();
		for (int i = 0; i < depth; i++) {
			int a = Math.round(255 * (1f - i / depth));
			if (off > 1) p.rect(x, y + i, w, 1, a << 24 | rgb);
			if (contentH - off > h + 1) p.rect(x, y + h - 1 - i, w, 1, a << 24 | rgb);
		}
		p.endGroup();
	}

	private void scrollbar(float x, float y, float viewH, float contentH, float off) {
		if (contentH <= viewH + 1) return;
		float h = Math.max(24, viewH * viewH / contentH), t = (viewH - h) * off / (contentH - viewH);
		p.round(x, y + t, 4, h, 99, BORDER);
	}

	// ---- the player ------------------------------------------------------------------------------

	private void main(Roster.Row row, boolean on) {
		if (row == null) {
			p.box(356, 82, 628, 90, 12, CARD, BORDER);
			p.text("Pick a player, or type a name and press Enter.", 373, 118, DeskType.I400_13, FAINT);
			return;
		}
		playerCard(row, on);
		// Tabs.
		float tx = 356;
		for (Tab t : Tab.values()) {
			String count = t == Tab.RECORDS ? recordsCount(row) : "";
			float lw = p.width(t.label, DeskType.I600_13), cw = count.isEmpty() ? 0 : p.width(" " + count, DeskType.I500_11);
			float w = lw + cw + 28;
			boolean sel = t == shown;
			float hv = hover(t, tx, 184, w, 34);
			p.text(t.label, tx + 14, 192, DeskType.I600_13, sel ? TEXT : mix(MUTED, TEXT, hv));
			if (!count.isEmpty()) p.text(" " + count, tx + 14 + lw, 193, DeskType.I500_11, FAINT);
			if (sel) p.rect(tx, 216, w, 2, VIOLET);
			Tab target = t;
			hit(tx, 184, w, 34, () -> {
				shown = target;
				bodyScrollTo = 0;
			});
			tx += w + 4;
		}
		p.rect(356, 218, 628, 1, BORDER);

		// The tab's body, scrolled.
		float top = 231, viewH = 740 - top;
		bodyViewH = viewH;
		float off = bodyScroll.to((float) bodyScrollTo);
		boolean scrolls = bodyContentH > viewH + 1;
		float w = scrolls ? 618 : 628;
		// The clip reaches past the content (clipping rounds to whole GUI units, which would otherwise shave a card's border).
		float clipTop = 221, clipBottom = 746;
		clipTo(352, clipTop, 636, clipBottom - clipTop);
		float y = top - off;
		float end = switch (shown) {
			case OVERVIEW -> overview(row, y, w);
			case RECORDS -> records(row, y, w);
			case ACCOUNT -> account(row, on, y, w);
		};
		bodyContentH = end + off - top;
		unclip();
		fades(352, clipTop, 636, clipBottom - clipTop, off, bodyContentH + (top - clipTop), BG);
		bodyScrollTo = Math.clamp(bodyScrollTo, 0, Math.max(0, bodyContentH - viewH));
		scrollbar(980, top, viewH, bodyContentH, (float) bodyScrollTo);
	}

	private String recordsCount(Roster.Row row) {
		ServerReply h = dossier(row.name()).reply(StaffActions.HISTORY);
		if (h == null) return "";
		int n = StaffReplies.entries(h.lines()).size();
		return n == 0 ? "" : String.valueOf(n);
	}

	/** The header card: face, name, rank and online pills, client, when they joined, nick, and the Flag score. */
	private void playerCard(Roster.Row row, boolean on) {
		p.box(356, 82, 628, 90, 12, CARD, BORDER);
		p.face(row.name(), 373, 99, 56, 10, CARD);
		boolean me = row.name().equalsIgnoreCase(Players.me());
		float nx = 445, ny = 104.5f;
		p.text(row.name(), nx, ny, DeskType.I700_20, TEXT, -0.02f);
		float px = nx + p.width(row.name(), DeskType.I700_20, -0.02f) + 8;
		String rank = row.rank().isEmpty() ? "Default" : row.rank();
		float rw = p.width(rank, DeskType.I500_11) + 18;
		p.box(px, ny + 2, rw, 20, 99, 0x00000000, BORDER);
		p.text(rank, px + 9, ny + 5, DeskType.I500_11, MUTED);
		px += rw + 8;
		if (me) pill(px, ny + 3, "You", VIOLET_BG, VIOLET_TEXT, 0);
		else if (on) pill(px, ny + 3, "Online", GREEN_BG, GREEN_TEXT, GREEN);
		else pill(px, ny + 3, "Offline", FIELD, MUTED, FAINT);

		boolean scored = row.score() > 0;
		float metaMax = (scored ? 967 - 140 : 967) - nx;
		StaffFlags.Brand b = brandOf(row.name());
		String client = clientName(row.brand());
		String[][] meta = {{"Client", client.isEmpty() ? "unknown" : client}, {"Joined", b == null ? (on ? "unknown" : "not online") : Roster.ago(System.currentTimeMillis() - b.at())},
				{"Nick", row.nick().isEmpty() ? "none" : row.nick()}};
		float mxp = nx, my0 = ny + 30;
		for (int i = 0; i < meta.length; i++) {
			if (i == 1 && b == null) continue;
			String lab = meta[i][0] + " ", val = meta[i][1];
			float lw = p.width(lab, DeskType.I400_12), room = nx + metaMax - mxp - lw;
			if (room < 30) break;
			val = p.fit(val, DeskType.I400_12, room);
			p.text(lab, mxp, my0, DeskType.I400_12, MUTED);
			boolean faint = val.equals("unknown") || val.equals("none") || val.equals("not online");
			p.text(val, mxp + lw, my0, DeskType.I400_12, faint ? FAINT : SUB);
			mxp += lw + p.width(val, DeskType.I400_12) + 16;
		}
		if (scored) {
			String score = Roster.scoreText(row.score());
			float rx = 967, top = 100;
			float hv = hover("score", rx - 120, top, 120, 54);
			float lw = p.width("Flag score", DeskType.I400_11);
			p.text("Flag score", rx - 14 - lw, top, DeskType.I400_11, mix(MUTED, TEXT, hv));
			p.icon(DeskIcons.CHEVRON_RIGHT, rx - 13, top + 1, 12, FAINT);
			// The score at line-height 1: a 26-pixel box with the font's 35-pixel line centred in it.
			p.text(score, rx - p.width(score, DeskType.M700_26), top + 18 + (26 - 35) / 2f, DeskType.M700_26, ORANGE_TEXT);
			p.round(rx - 120, top + 48, 120, 6, 99, BORDER);
			p.round(rx - 120, top + 48, Math.max(6, 120 * (float) Math.min(1, row.score() / 100.0)), 6, 99, ORANGE);
			Signal s = signals(row).stream().filter(sg -> sg.kind() == SignalKind.PRIORITY || sg.kind() == SignalKind.DASHBOARD).findFirst().orElse(null);
			if (s != null) hit(rx - 120, top, 120, 54, () -> openSheet(new SignalSheet(s)));
		}
	}

	/** A small rounded pill with an optional dot; returns its right edge. */
	private float pill(float x, float y, String text, int bg, int fg, int dot) {
		float w = p.width(text, DeskType.I500_11) + 16 + (dot != 0 ? 11 : 0);
		p.round(x, y, w, 18, 99, bg);
		float tx = x + 8;
		if (dot != 0) {
			p.round(tx, y + 6, 6, 6, 99, dot);
			tx += 11;
		}
		p.text(text, tx, y + 2, DeskType.I500_11, fg);
		return x + w;
	}

	// ---- Overview --------------------------------------------------------------------------------

	private List<Signal> signals(Roster.Row row) {
		if (frameRows == null) return buildSignals(row);
		return frameSignals.computeIfAbsent(row.name().toLowerCase(Locale.ROOT), k -> buildSignals(row));
	}

	private List<Signal> buildSignals(Roster.Row row) {
		StaffFlags.State s = state();
		long now = System.currentTimeMillis();
		List<Signal> out = new ArrayList<>();
		Predicate<String> him = n -> n.equalsIgnoreCase(row.name()) || !row.nick().isEmpty() && n.equalsIgnoreCase(row.nick());
		for (StaffFlags.Priority pr : s.priorities()) {
			if (!him.test(pr.player())) continue;
			StaffFlags.Reading prev = pr.previous();
			String note = prev == null ? "first reading" : (pr.score() >= prev.score() ? "up from " : "down from ") + Roster.scoreText(prev.score());
			int noteColour = prev == null ? FAINT : pr.score() >= prev.score() ? RED_TEXT : GREEN_TEXT;
			out.add(new Signal(SignalKind.PRIORITY, ORANGE, "Priority score", Roster.ago(now - pr.at()), Roster.scoreText(pr.score()), true, TEXT,
					note, noteColour, "The anticheat asks staff to review the evidence.", false, pr));
		}
		for (StaffFlags.Setback b : s.setbacks()) {
			if (!him.test(b.player())) continue;
			out.add(new Signal(SignalKind.SETBACK, PINK, "Position setbacks", Roster.ago(now - b.at()), "×" + b.count(), true, TEXT,
					b.since() > 0 && b.at() > b.since() ? "in " + span(b.at() - b.since()) : "", MUTED, place(b.place()), true, b));
		}
		Detected det = detected(row);
		if (!det.flags().isEmpty() || !det.checks().isEmpty()) out.add(dashboardSignal(det, now));
		for (StaffFlags.Captcha c : s.captchas()) {
			if (!him.test(c.player())) continue;
			out.add(new Signal(SignalKind.CAPTCHA, c.passed() ? GREEN : RED_DOT, "Captcha", Roster.ago(now - c.at()), c.passed() ? "Passed" : "Failed", false,
					c.passed() ? GREEN_TEXT : RED_TEXT, c.seconds() >= 0 ? "answered in " + c.seconds() + " s" : c.passed() ? "" : "didn't answer", MUTED,
					c.by().isEmpty() ? "" : "Sent by " + c.by(), false, c));
		}
		for (StaffFlags.Alt a : s.alts()) {
			if (!him.test(a.name())) continue;
			List<String> banned = AltPages.banned(a.others()), rest = new ArrayList<>(AltPages.names(a.others()));
			rest.removeAll(banned);
			String also = rest.isEmpty() ? "" : "Also linked: " + String.join(", ", rest);
			out.add(banned.isEmpty()
					? new Signal(SignalKind.ALT, DRAGON, "Linked to a ban", Roster.ago(now - a.since()), Roster.words(a.why()), false, PURPLE, "", MUTED,
							a.others(), false, a)
					: new Signal(SignalKind.ALT, DRAGON, "Linked to a ban", Roster.ago(now - a.since()), String.join(", ", banned), false, PURPLE,
							banned.size() == 1 ? "is banned" : "are banned", RED_TEXT, also.isEmpty() ? Roster.words(a.why()) : also, false, a));
		}
		for (StaffFlags.Watch wt : s.watch()) {
			if (!him.test(wt.name())) continue;
			out.add(new Signal(SignalKind.WATCH, BLUE, "Watched", Roster.ago(now - wt.at()), wt.note().isEmpty() ? "On the watch list" : wt.note(), false, TEXT,
					"", MUTED, "Added by " + wt.by(), false, wt));
		}
		StaffFlags.Brand b = brandOf(row.name());
		if (b != null) {
			String client = clientName(b.client());
			int dot = client.indexOf(" · ");
			out.add(new Signal(SignalKind.CLIENT, BLUE, "Client", Roster.ago(now - b.at()), dot < 0 ? client : client.substring(0, dot), false, TEXT,
					dot < 0 ? "" : "with " + client.substring(dot + 3).replace(" · ", ", "), MUTED, "As the anticheat saw them join.", false, b));
		}
		return out;
	}

	/**
	 * The card for what the anticheat caught a player on: the check caught most in the last hour and how far
	 * ("Click rate ×12", "CPS up to 20"), the rest after it; before their alerts are read, the dashboard's review
	 * score. "Anticheat detections" for a player only staff chat has heard about.
	 */
	private Signal dashboardSignal(Detected d, long now) {
		StaffFlags.Flag fl = d.flags().isEmpty() ? null : d.flags().getFirst();
		String title = fl != null ? "Flag dashboard" : "Anticheat detections";
		String meta = Roster.ago(now - (fl != null ? fl.since() : d.checks().getFirst().newest()));
		String status = switch (d.read()) {
			case READING -> "Reading the last hour’s alerts…";
			case FAILED -> fl != null ? "Its alerts couldn’t be read; open the flag details." : "";
			default -> "";
		};
		if (!d.checks().isEmpty()) {
			FlagAlerts.Check top = d.checks().getFirst();
			List<FlagAlerts.Check> rest = d.checks().subList(1, d.checks().size());
			String detail = (rest.isEmpty() ? "" : "Also " + FlagAlerts.line(rest) + ". ")
					+ (status.isEmpty() ? fl != null ? "Review score " + Roster.scoreText(fl.score()) + "." : "Heard in staff chat." : status);
			return new Signal(SignalKind.DASHBOARD, RED_DOT, title, meta, top.counted(), false, RED_SOFT, top.peak(), MUTED, detail, false, d);
		}
		String[] said = dashboardSaid(fl);
		String detail = !status.isEmpty() ? status : d.read() == AlertState.READ ? "No alerts in the last hour."
				: said[1].isEmpty() ? "Review score on the dashboard." : "Newest flag from " + said[1] + ".";
		return new Signal(SignalKind.DASHBOARD, RED_DOT, title, meta, Roster.scoreText(fl.score()), true, RED_SOFT,
				said[0].isEmpty() ? Roster.words(fl.type()) : said[0], MUTED, detail, false, d);
	}

	/** What a dashboard flag's detector says, as {unreviewed, newest source}: "427 unreviewed · DragSim AC"; an old one's detector is its source. */
	private static String[] dashboardSaid(StaffFlags.Flag fl) {
		String d = fl.detector();
		int dot = d.indexOf(" · ");
		if (d.endsWith(" unreviewed")) return new String[]{d, ""};
		if (dot < 0) return d.contains("unreviewed") ? new String[]{d, ""} : new String[]{"", d};
		return new String[]{d.substring(0, dot), d.substring(dot + 3)};
	}

	/** "minestom-3ae7ecbe, -669, 10, -274" → "minestom-3ae7ecbe · −669, 10, −274". */
	private static String place(String place) {
		if (place.isEmpty()) return "ghost block";
		int c = place.indexOf(',');
		String out = c < 0 ? place : place.substring(0, c).trim() + " · " + place.substring(c + 1).trim();
		return out.replace("-", "−").replaceFirst("^([^ ]*)−", "$1-");
	}

	/** A length of time as "15 min", "2 h", "3 d". */
	private static String span(long ms) {
		long m = ms / 60_000;
		if (m < 1) return (ms / 1000) + " s";
		if (m < 60) return m + " min";
		if (m < 48 * 60) return m / 60 + " h";
		return m / 1440 + " d";
	}

	private float overview(Roster.Row row, float y, float w) {
		List<Signal> signals = signals(row);
		List<StaffFlags.Report> reports = state().reports().stream().filter(r -> r.target().equalsIgnoreCase(row.name())).toList();
		if (signals.isEmpty() && reports.isEmpty()) {
			p.box(356, y, w, 74, 12, CARD, BORDER);
			p.round(356 + 15, y + 19, 8, 8, 99, GREEN);
			p.text("Nothing flags " + row.name(), 356 + 31, y + 15, DeskType.I600_13, TEXT);
			p.text("No dashboard flags, anticheat scores, setbacks, captchas, reports or watch entries.", 356 + 31, y + 41, DeskType.I400_12, MUTED);
			return y + 74;
		}
		float cw = (w - 12) / 2;
		for (int i = 0; i < signals.size(); i += 2) {
			float h = Math.max(signalHeight(signals.get(i), cw), i + 1 < signals.size() ? signalHeight(signals.get(i + 1), cw) : 0);
			signalCard(signals.get(i), 356, y, cw, h, i);
			if (i + 1 < signals.size()) signalCard(signals.get(i + 1), 356 + cw + 12, y, cw, h, i + 1);
			y += h + 12;
		}
		if (!reports.isEmpty()) y = reportsCard(reports, y, w) + 12;
		return y - 12;
	}

	private float signalHeight(Signal s, float w) {
		DeskType vt = s.mono() ? DeskType.M700_22 : DeskType.I600_15;
		int lines = s.detail().isEmpty() ? 0 : Math.min(2, p.wrap(s.detail(), s.detailMono() ? DeskType.M500_11 : DeskType.I400_12, w - 30).size());
		float dh = lines * (s.detailMono() ? 14 : 15);
		return 2 + 28 + 16 + 10 + vt.lineHeight() + (lines > 0 ? 10 + dh : 0);
	}

	private void signalCard(Signal s, float x, float y, float w, float h, int i) {
		float hv = hover("sig" + i, x, y, w, h);
		p.box(x, y, w, h, 12, mix(CARD, 0xFF16161A, hv), mix(BORDER, 0xFF3F3F46, hv));
		float ix = x + 15, iy = y + 15;
		p.round(ix, iy + 4, 8, 8, 99, s.dot());
		p.text(s.title(), ix + 16, iy, DeskType.I600_13, TEXT);
		float chev = 14 + 8, mw = p.width(s.meta(), DeskType.I400_11);
		p.icon(DeskIcons.CHEVRON_RIGHT, x + w - 15 - 14, iy + 1, 14, FAINT);
		p.text(s.meta(), x + w - 15 - chev - mw, iy + 1, DeskType.I400_11, FAINT);
		float vy = iy + 16 + 10;
		DeskType vt = s.mono() ? DeskType.M700_22 : DeskType.I600_15;
		float vw = p.width(s.value(), vt);
		String value = p.fit(s.value(), vt, w - 30);
		p.text(value, ix, vy, vt, s.valueColour());
		if (!s.note().isEmpty() && vw + 8 < w - 30) {
			// Baseline-aligned with the value.
			float base = vy + vt.ascent(1f);
			p.text(p.fit(s.note(), DeskType.I400_12, w - 30 - vw - 8), ix + vw + 8, base - DeskType.I400_12.ascent(1f), DeskType.I400_12, s.noteColour());
		}
		if (!s.detail().isEmpty()) {
			DeskType dt = s.detailMono() ? DeskType.M500_11 : DeskType.I400_12;
			List<String> lines = p.wrap(s.detail(), dt, w - 30);
			float dy = vy + vt.lineHeight() + 10;
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				String line = l == 1 && lines.size() > 2 ? p.fit(lines.get(1) + " …", dt, w - 30) : lines.get(l);
				p.text(line, ix, dy + l * (s.detailMono() ? 14 : 15), dt, MUTED);
			}
		}
		hit(x, y, w, h, () -> openSheet(new SignalSheet(s)));
	}

	private float reportsCard(List<StaffFlags.Report> reports, float y, float w) {
		float h = 2 + 28 + 16 + reports.size() * (10 + 50) ;
		p.box(356, y, w, h, 12, CARD, BORDER);
		float ix = 371, iy = y + 15;
		p.round(ix, iy + 4, 8, 8, 99, YELLOW);
		p.text("Reports", ix + 16, iy, DeskType.I600_13, TEXT);
		pill(ix + 16 + p.width("Reports", DeskType.I600_13) + 8, iy - 1, reports.size() + " open", YELLOW_BG, YELLOW, 0);
		float ry = iy + 16 + 10;
		long now = System.currentTimeMillis();
		for (StaffFlags.Report r : reports) {
			float rw = w - 30;
			p.box(ix, ry, rw, 50, 8, INSET, SOFT);
			String id = "#" + r.id();
			p.text(id, ix + 11, ry + 18, DeskType.M500_11, FAINT);
			float tx = ix + 11 + p.width(id, DeskType.M500_11) + 12;
			float bw = p.width("Mark done", DeskType.I500_12) + 22, bx = ix + rw - 11 - bw;
			String reason = r.reason().isEmpty() ? "no reason given" : r.reason();
			String by = "by " + r.reporter() + " · " + Roster.ago(now - r.at());
			float room = bx - 12 - tx;
			float byW = p.width(by, DeskType.I400_12);
			String shownReason = p.fit(reason, DeskType.I400_13, Math.max(60, room - byW - 12));
			p.text(shownReason, tx, ry + 17, DeskType.I400_13, TEXT);
			float after = tx + p.width(shownReason, DeskType.I400_13) + 12;
			if (after + byW <= bx - 12) p.text(by, after, ry + 17.5f, DeskType.I400_12, MUTED);
			int rid = r.id();
			button("done" + rid, bx, ry + 11, bw, 28, 7, "Mark done", DeskType.I500_12, "", Look.PLAIN, "", () -> reportDone(rid));
			tip(tx, ry, room, 50, reason.length() > 40 ? reason : null);
			ry += 60;
		}
		return y + h;
	}

	// ---- Records ---------------------------------------------------------------------------------

	private float records(Roster.Row row, float y, float w) {
		if (!tier().allows(Gate.Tier.MOD)) return locked(y, w, "Records need MOD+",
				"History, warnings and alts come from staff commands your rank can’t run. The Overview still shows why this player is flagged.");
		Dossier d = dossier(row.name());
		ServerReply history = d.reply(StaffActions.HISTORY), warns = d.reply(StaffActions.WARNS), alts = d.reply(StaffActions.ALTS);
		List<StaffReplies.Entry> entries = history == null ? List.of() : StaffReplies.entries(history.lines());
		List<StaffReplies.Entry> warnings = warns == null ? List.of() : StaffReplies.entries(warns.lines());
		List<String> altNames = alts == null ? List.of() : StaffReplies.alts(alts.lines());
		if (altNames.isEmpty() && alts != null) altNames = AltFlag.names(alts.lines(), row.name());

		StaffReplies.Entry active = entries.stream().filter(e -> e.active() && !e.kind().equals("WARN")).findFirst().orElse(null);
		long bans = entries.stream().filter(e -> e.kind().contains("BAN")).count(), mutes = entries.stream().filter(e -> e.kind().contains("MUTE")).count(),
				kicks = entries.stream().filter(e -> e.kind().equals("KICK")).count();
		long activeWarns = warnings.stream().filter(StaffReplies.Entry::active).count();
		List<String> names = altNames;
		long altsOn = names.stream().filter(this::isOnline).count();
		List<String> kinds = new ArrayList<>();
		if (bans > 0) kinds.add("ban");
		if (mutes > 0) kinds.add("mute");
		if (kicks > 0) kinds.add("kick");
		String[][] cards = {
				{"Active now", history == null ? "" : active == null ? "Nothing" : kindWord(active.kind()),
						active == null ? "" : active.left().equalsIgnoreCase("Permanent") ? "permanent" : active.left() + " left"},
				{"Punishments", history == null ? "" : entries.stream().filter(e -> !e.kind().equals("WARN")).count() + " on file", String.join(", ", kinds)},
				{"Warnings", warns == null ? "" : warnings.isEmpty() ? "None" : activeWarns + " active", warnings.isEmpty() ? "" : warnings.size() + " on file"},
				{"Alts", alts == null ? "" : names.isEmpty() ? "None" : names.size() + " linked", altsOn > 0 ? altsOn + " online now" : ""}};
		float cw = (w - 24) / 4;
		for (int i = 0; i < 4; i++) {
			float cx = 356 + i * (cw + 8);
			boolean danger = i == 0 && active != null;
			p.box(cx, y, cw, 70, 12, danger ? RED_BG : CARD, danger ? RED_CARD_EDGE : BORDER);
			p.text(cards[i][0], cx + 13, y + 11, DeskType.I400_11, danger ? RED_TEXT : MUTED);
			if (cards[i][1].isEmpty()) p.round(cx + 13, y + 31, 60, 12, 4, SOFT);
			else p.text(p.fit(cards[i][1], DeskType.I600_15, cw - 26), cx + 13, y + 27, DeskType.I600_15, danger ? RED_SOFT : TEXT);
			int sub = danger ? RED_TEXT : i == 3 ? GREEN_TEXT : FAINT;
			if (!cards[i][2].isEmpty()) p.text(p.fit(cards[i][2], DeskType.I400_11, cw - 26), cx + 13, y + 48, DeskType.I400_11, sub);
		}
		y += 70 + 12;
		y = entriesCard("History", history, entries, d, StaffActions.HISTORY, "Clean record: nothing on file.", y, w) + 12;
		y = entriesCard("Warnings", warns, warnings, d, StaffActions.WARNS, "No warnings.", y, w) + 12;
		y = altsCard(alts, names, d, y, w);
		return y;
	}

	private static String kindWord(String kind) {
		return switch (kind) {
			case "BAN" -> "Banned";
			case "IPBAN" -> "IP banned";
			case "MUTE" -> "Muted";
			case "IPMUTE" -> "IP muted";
			default -> Roster.words(kind);
		};
	}

	private static StaffActions undoFor(StaffReplies.Entry e) {
		if (!e.active()) return null;
		return switch (e.kind()) {
			case "BAN", "IPBAN" -> StaffActions.UNBAN;
			case "MUTE", "IPMUTE" -> StaffActions.UNMUTE;
			case "WARN" -> StaffActions.UNWARN;
			default -> null;
		};
	}

	/**
	 * A card's header, laid out so nothing overlaps: the title and a count pill on the left; on the right Ask again (hidden
	 * while asking), then either the pulsing "asking…" or when it was read; the command hint ("/dragsimac info") in the
	 * room left between, shortened or left out when there isn't enough. Returns the body's top.
	 */
	private float cardHeader(String title, String count, String hint, String meta, boolean asking, float y, float w, Runnable again) {
		return cardHeader(356, title, count, hint, meta, asking, y, w, again, null);
	}

	/** As above for a card at {@code cx}, with {@code badge} (a pill: text, fill, colour) on the right when there's nothing to say about asking. */
	private float cardHeader(float cx, String title, String count, String hint, String meta, boolean asking, float y, float w, Runnable again, Object[] badge) {
		p.text(title, cx + 16, y + 15.5f, DeskType.I600_14, TEXT);
		float x = cx + 16 + p.width(title, DeskType.I600_14) + 8;
		if (!count.isEmpty()) {
			float pw = p.width(count, DeskType.I500_11) + 16;
			p.box(x, y + 15, pw, 18, 99, 0x00000000, BORDER);
			p.text(count, x + 8, y + 17, DeskType.I500_11, MUTED);
			x += pw + 8;
		}
		float right = cx + w - 13;
		if (again != null && !asking) {
			float hv = hover("again" + title, right - 28, y + 10, 28, 28);
			p.box(right - 28, y + 10, 28, 28, 7, mix(FIELD, BUTTON_HOVER, hv), BORDER);
			p.icon(DeskIcons.REFRESH, right - 28 + 7.5f, y + 17.5f, 13, SUB);
			hit(right - 28, y + 10, 28, 28, again);
			tip(right - 28, y + 10, 28, 28, "Ask the server again");
			right -= 36;
		}
		if (asking) right = asking(right, y + 17) - 12;
		else if (badge != null) {
			String text = (String) badge[0];
			float bw = p.width(text, DeskType.I500_11) + 16;
			pill(right - bw, y + 15, text, (Integer) badge[1], (Integer) badge[2], 0);
			right -= bw + 12;
		} else if (!meta.isEmpty()) {
			p.text(meta, right - p.width(meta, DeskType.I400_11), y + 17, DeskType.I400_11, FAINT);
			right -= p.width(meta, DeskType.I400_11) + 12;
		}
		if (!hint.isEmpty() && right - x >= 48) p.text(p.fit(hint, DeskType.I400_11, right - x), x, y + 17, DeskType.I400_11, FAINT);
		p.rect(cx + 1, y + 48, w - 2, 1, BORDER);
		return y + 49;
	}

	/** Where a command's answer stands, at the right of a card's header: asking, waiting its turn, or no reply. */
	private String readMeta(Dossier d, StaffActions a, ServerReply r) {
		return switch (d.status(a)) {
			case ASKING -> "";
			case WAITING -> r == null ? "waiting its turn" : "";
			case NO_ANSWER -> "";
			case ANSWERED -> r == null ? "" : "read " + Roster.ago(Math.max(0, Util.getMillis() - r.at()));
		};
	}

	private float entriesCard(String title, ServerReply reply, List<StaffReplies.Entry> entries, Dossier d, StaffActions which, String empty, float y, float w) {
		Dossier.Status st = d.status(which);
		if (st == Dossier.Status.NO_ANSWER) return failedCard(title, "/" + which.command.replace(" %p", "") + " didn’t answer", y, w, () -> retry(d, which));
		boolean listed = reply != null && reply.answered() && !entries.isEmpty();
		List<float[]> sizes = new ArrayList<>();
		float bodyH;
		if (listed) {
			bodyH = 0;
			for (StaffReplies.Entry e : entries) {
				float rh = entryHeight(e, w);
				sizes.add(new float[]{rh});
				bodyH += rh;
			}
			bodyH += entries.size() - 1;
		} else bodyH = reply == null ? (st == Dossier.Status.ASKING ? 90 : 0) : 44;
		float h = 49 + bodyH + 1;
		if (reply == null && st != Dossier.Status.ASKING) h = 48;
		p.box(356, y, w, h, 12, CARD, BORDER);
		if (reply == null && st != Dossier.Status.ASKING) {
			p.text(title, 372, y + 15.5f, DeskType.I600_14, TEXT);
			String note = which == StaffActions.HISTORY ? "waiting its turn" : "next, after " + (which == StaffActions.WARNS ? "history" : "warnings");
			p.text(note, 356 + w - 13 - p.width(note, DeskType.I400_11), y + 17, DeskType.I400_11, FAINT);
			return y + h;
		}
		float by = cardHeader(title, listed ? String.valueOf(entries.size()) : "", "", readMeta(d, which, reply), st == Dossier.Status.ASKING, y, w,
				reply == null ? null : () -> retry(d, which));
		if (st == Dossier.Status.ASKING) {
			skeletonRows(by + 12, w, 2);
			return y + h;
		}
		if (!listed) {
			p.text(empty, 372, by + 14, DeskType.I400_13, FAINT);
			return y + h;
		}
		int year = Year.now().getValue();
		float ry = by;
		for (int i = 0; i < entries.size(); i++) {
			StaffReplies.Entry e = entries.get(i);
			float rh = sizes.get(i)[0];
			if (i > 0) {
				p.rect(357, ry, w - 2, 1, SOFT);
				ry += 1;
			}
			entryRow(e, ry, w, rh, year);
			ry += rh;
		}
		return y + h;
	}

	private static final float ID_W = 40, KIND_W = 50, ACT_W = 64;

	private float entryHeight(StaffReplies.Entry e, float w) {
		float main = entryMainWidth(e, w);
		int lines = Math.min(2, p.wrap(reasonOf(e), DeskType.I600_13, main).size());
		return 20 + Math.max(28, lines * 16 + 2 + 15);
	}

	private float stateWidth(StaffReplies.Entry e) {
		String st = shortState(e);
		return e.active() ? p.width(st, DeskType.I500_11) + 16 : p.width(st, DeskType.I400_11);
	}

	private float entryMainWidth(StaffReplies.Entry e, float w) {
		return w - 2 - 32 - ID_W - KIND_W - stateWidth(e) - ACT_W - 4 * 12;
	}

	private static String reasonOf(StaffReplies.Entry e) {
		return e.reason().isEmpty() ? "No reason given" : e.reason();
	}

	/** An entry's state as the row shows it: "6d 23h left", "Permanent", "Undone by Porier", "Ended". */
	private static String shortState(StaffReplies.Entry e) {
		return switch (e.state()) {
			case ACTIVE -> e.left().isEmpty() ? "Active" : e.left().equalsIgnoreCase("Permanent") ? "Permanent" : e.left() + " left";
			case UNDONE -> e.undoneBy().isEmpty() ? "Undone" : e.undoneBy().toLowerCase(Locale.ROOT).startsWith("superseded by ")
					? "Replaced by " + e.undoneBy().substring("superseded by ".length()) : "Undone by " + e.undoneBy();
			case ENDED -> "Ended";
		};
	}

	private void entryRow(StaffReplies.Entry e, float y, float w, float h, int year) {
		boolean faded = !e.active();
		// The row opens its details; its undo button, registered after, is on top of it.
		hit(357, y, w - 2, h, () -> openSheet(new EntrySheet(e)));
		float hv = hover("entry" + e.id(), 357, y, w - 2, h);
		if (hv > 0) p.rect(357, y, w - 2, h, mix(0x00000000, 0x08FFFFFF, hv));
		float x = 372, mid = y + h / 2;
		p.text("#" + e.id(), x, mid - 7, DeskType.M500_11, FAINT);
		x += ID_W + 12;
		int[] kc = kindColours(e);
		p.round(x, mid - 9, KIND_W, 18, 5, kc[0]);
		String kind = e.kind();
		float kw = p.width(kind, DeskType.I600_10, 0.05f);
		p.text(kind, x + (KIND_W - kw) / 2, mid - 6, DeskType.I600_10, kc[1], 0.05f);
		x += KIND_W + 12;
		float main = entryMainWidth(e, w);
		List<String> lines = p.wrap(reasonOf(e), DeskType.I600_13, main);
		int n = Math.min(2, lines.size());
		float blockH = n * 16 + 2 + 15, ty = mid - blockH / 2;
		for (int l = 0; l < n; l++) {
			String line = l == 1 && lines.size() > 2 ? p.fit(lines.get(1) + " …", DeskType.I600_13, main) : lines.get(l);
			p.text(line, x, ty + l * 16, DeskType.I600_13, faded ? SUB2 : TEXT);
		}
		p.text(p.fit("by " + e.by() + " · " + StaffReplies.shortDate(e.date(), year), DeskType.I400_12, main), x, ty + n * 16 + 2, DeskType.I400_12, faded ? FAINT : MUTED);
		x += main + 12;
		String st = shortState(e);
		if (e.active()) {
			boolean warn = e.kind().equals("WARN");
			pill(x, mid - 9, st, warn ? BLUE_BG : RED_CHIP, warn ? BLUE : RED_TEXT, 0);
		} else p.text(st, x, mid - 7, DeskType.I400_11, FAINT);
		StaffActions undo = undoFor(e);
		float ax = 356 + w - 17 - ACT_W;
		if (undo != null) undoButton(undo, e, ax, mid - 14, ACT_W);
	}

	private static int[] kindColours(StaffReplies.Entry e) {
		if (!e.active()) return new int[]{BORDER, e.kind().equals("KICK") ? SUB2 : MUTED};
		return switch (e.kind()) {
			case "BAN", "IPBAN", "MUTE", "IPMUTE" -> new int[]{RED_CHIP, RED_TEXT};
			case "WARN" -> new int[]{BLUE_BG, BLUE};
			default -> new int[]{BORDER, SUB2};
		};
	}

	/** Unban, unmute or unwarn: the second click within three seconds sends it. */
	private void undoButton(StaffActions undo, StaffReplies.Entry e, float x, float y, float w) {
		String key = undo.name() + "#" + e.id();
		boolean sure = confirming.equals(key);
		String why = StaffActions.blocked(undo, tier(), true);
		Look look = sure ? Look.SURE : undo == StaffActions.UNWARN ? Look.PLAIN : Look.DANGER_OUTLINE;
		if (look == Look.DANGER_OUTLINE) {
			// The record's undo is a red outline on the card, not the filled red of the rail.
			boolean can = why.isEmpty();
			float hv = can ? hover(key, x, y, w, 28) : 0;
			float before = p.alpha;
			if (!can) p.alpha *= 0.4f;
			p.box(x, y, w, 28, 7, mix(0x00000000, RED_BG, hv), RED_EDGE);
			p.text(undo.label, x + (w - p.width(undo.label, DeskType.I500_12)) / 2, y + 6.5f, DeskType.I500_12, RED_TEXT);
			p.alpha = before;
			if (can) hit(x, y, w, 28, () -> undo(undo, e, key));
			else tip(x, y, w, 28, why + " only");
			return;
		}
		button(key, x, y, w, 28, 7, sure ? "Sure?" : undo.label, sure ? DeskType.I600_12 : DeskType.I500_12, "", look, why.isEmpty() ? "" : why + " only",
				() -> undo(undo, e, key));
	}

	private void undo(StaffActions undo, StaffReplies.Entry e, String key) {
		if (confirming.equals(key)) {
			confirming = "";
			run(undo, e.player(), "");
		} else {
			confirming = key;
			confirmingAt = Util.getMillis();
		}
	}

	private float altsCard(ServerReply alts, List<String> names, Dossier d, float y, float w) {
		Dossier.Status st = d.status(StaffActions.ALTS);
		if (st == Dossier.Status.NO_ANSWER) return failedCard("Alts", "/alts didn’t answer", y, w, () -> d.retry(StaffActions.ALTS));
		if (alts == null && st != Dossier.Status.ASKING) {
			p.box(356, y, w, 48, 12, CARD, BORDER);
			p.text("Alts", 372, y + 15.5f, DeskType.I600_14, TEXT);
			String note = "after warnings";
			p.text(note, 356 + w - 13 - p.width(note, DeskType.I400_11), y + 17, DeskType.I400_11, FAINT);
			return y + 48;
		}
		float bodyH = alts == null ? 52 : names.isEmpty() ? 44 : 12 + names.size() * 40 + (names.size() - 1) * 2;
		float h = 49 + bodyH + 1;
		p.box(356, y, w, h, 12, CARD, BORDER);
		float by = cardHeader("Alts", names.isEmpty() ? "" : String.valueOf(names.size()), "", readMeta(d, StaffActions.ALTS, alts), st == Dossier.Status.ASKING, y, w,
				alts == null ? null : () -> d.retry(StaffActions.ALTS));
		if (alts == null) {
			skeletonRows(by + 12, w, 1);
			return y + h;
		}
		if (names.isEmpty()) {
			p.text("No linked accounts.", 372, by + 14, DeskType.I400_13, FAINT);
			return y + h;
		}
		float ry = by + 6;
		for (String a : names) {
			float hv = hover("alt" + a, 364, ry, w - 16, 40);
			if (hv > 0) p.round(364, ry, w - 16, 40, 8, mix(0x00000000, HOVER, hv));
			p.face(a, 372, ry + 8, 24, 5, hv > 0.5f ? HOVER : CARD);
			p.text(a, 406, ry + 12, DeskType.I500_13, TEXT);
			if (isOnline(a)) pill(356 + w - 18 - p.width("online", DeskType.I500_11) - 16, ry + 11, "online", GREEN_BG, GREEN_TEXT, 0);
			else {
				float pw = p.width("offline", DeskType.I500_11) + 16, px = 356 + w - 18 - pw;
				p.box(px, ry + 11, pw, 18, 99, 0x00000000, BORDER);
				p.text("offline", px + 8, ry + 13, DeskType.I500_11, MUTED);
			}
			hit(364, ry, w - 16, 40, () -> lookUp(a));
			ry += 42;
		}
		return y + h;
	}

	/** A pulsing "asking…" at the right of a card's header, its right edge at {@code right}; returns its left edge. */
	private float asking(float right, float y) {
		String t = "asking…";
		float tw = p.width(t, DeskType.I400_11);
		float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 140.0);
		p.round(right - tw - 12, y + 4, 6, 6, 99, mix(0x00000000, VIOLET, pulse));
		p.text(t, right - tw, y, DeskType.I400_11, VIOLET);
		return right - tw - 12;
	}

	/** Grey placeholder rows while an answer is on its way. */
	private void skeletonRows(float y, float w, int n) {
		for (int i = 0; i < n; i++) {
			float ry = y + i * 38;
			p.round(372, ry + 6, 36, 10, 4, SOFT);
			p.round(420, ry + 2, 46, 18, 5, SOFT);
			p.round(478, ry, (w - 140) * (i == 0 ? 0.6f : 0.48f), 10, 4, SOFT);
			p.round(478, ry + 16, (w - 140) * (i == 0 ? 0.38f : 0.32f), 8, 4, FIELD);
		}
	}

	/** A red card for a command that didn't answer, with Ask again. */
	private float failedCard(String title, String why, float y, float w, Runnable again) {
		p.box(356, y, w, 48, 12, RED_BG, RED_CARD_EDGE);
		p.icon(DeskIcons.ALERT, 372, y + 16.5f, 15, RED_TEXT);
		p.text(title, 395, y + 15.5f, DeskType.I600_14, RED_SOFT);
		p.text(why, 395 + p.width(title, DeskType.I600_14) + 8, y + 16.5f, DeskType.I400_12, RED_TEXT);
		float bw = p.width("Ask again", DeskType.I500_12) + 22, bx = 356 + w - 13 - bw;
		float hv = hover("fail" + title, bx, y + 10, bw, 28);
		p.box(bx, y + 10, bw, 28, 7, mix(0x00000000, 0xFF2A1214, hv), RED_EDGE);
		p.text("Ask again", bx + 11, y + 16.5f, DeskType.I500_12, RED_TEXT);
		hit(bx, y + 10, bw, 28, again);
		return y + 48;
	}

	/** A card saying the viewer's rank can't see this. */
	private float locked(float y, float w, String title, String text) {
		List<String> lines = p.wrap(text, DeskType.I400_13, 360);
		float h = 40 + 44 + 12 + 19 + 12 + lines.size() * 20 + 12 + 32 + 40;
		p.box(356, y, w, h, 12, CARD, BORDER);
		float cx = 356 + w / 2;
		p.box(cx - 22, y + 40, 44, 44, 12, FIELD, BORDER);
		p.icon(DeskIcons.LOCK, cx - 10, y + 52, 20, MUTED);
		p.text(title, cx - p.width(title, DeskType.I600_15) / 2, y + 96, DeskType.I600_15, TEXT);
		float ly = y + 127;
		for (String l : lines) {
			p.text(l, cx - p.width(l, DeskType.I400_13) / 2, ly, DeskType.I400_13, MUTED);
			ly += 20;
		}
		float bw = p.width("Back to Overview", DeskType.I500_13) + 26;
		button("back", cx - bw / 2, ly + 12, bw, 32, 8, "Back to Overview", DeskType.I500_13, "", Look.PLAIN, "", () -> shown = Tab.OVERVIEW);
		return y + h;
	}

	// ---- Account ---------------------------------------------------------------------------------

	private float account(Roster.Row row, boolean on, float y, float w) {
		if (!tier().allows(Gate.Tier.MOD)) return locked(y, w, "Account details need MOD+",
				"Lookup, account history and the anticheat’s view come from staff commands your rank can’t run.");
		Dossier d = dossier(row.name());
		ServerReply lookup = d.reply(StaffActions.LOOKUP), history = d.reply(StaffActions.ACCOUNT_HISTORY), ac = d.reply(StaffActions.AC_INFO);
		Map<String, String> f = new LinkedHashMap<>();
		if (lookup != null) f.putAll(StaffReplies.fields(lookup.lines()));
		if (history != null) f.putAll(StaffReplies.fields(history.lines()));

		// Account: two columns of label and value.
		float h = 49 + 12 + 4 * 15 + 3 * 8 + 12 + 2;
		p.box(356, y, w, h, 12, CARD, BORDER);
		boolean asking = d.status(StaffActions.LOOKUP) == Dossier.Status.ASKING || d.status(StaffActions.ACCOUNT_HISTORY) == Dossier.Status.ASKING;
		String meta = lookup == null ? "" : "read " + Roster.ago(Math.max(0, Util.getMillis() - lookup.at()));
		float by = cardHeader("Account", "", "/lookup and /accounthistory", meta, asking, y, w, lookup == null ? null : () -> {
			d.retry(StaffActions.LOOKUP);
			d.retry(StaffActions.ACCOUNT_HISTORY);
		});
		float colW = (w - 2 - 32 - 76 * 2 - 12 * 3) / 2;
		float lx = 372, vx = lx + 76 + 12, rx = vx + colW + 12, rvx = rx + 76 + 12;
		String[] left = {"Rank", "Balance", "Health", "Mana"}, rightKeys = {"IP", "Addresses", "Names", "UUID"};
		for (int i = 0; i < 4; i++) {
			float ry = by + 12 + i * 23;
			p.text(left[i], lx, ry, DeskType.I400_12, MUTED);
			field(left[i], f, vx, ry, colW, asking);
			p.text(rightKeys[i], rx, ry, DeskType.I400_12, MUTED);
			field(rightKeys[i], f, rvx, ry, colW, asking);
		}
		y += h + 12;

		// The anticheat and the tools, side by side.
		float cw = (w - 12) / 2;
		Map<String, String> info = ac == null ? Map.of() : StaffReplies.acInfo(ac.lines());
		float acH = 49 + 14 + 3 * 39 + 2 * 14 + 14 + 1;
		float tools = toolsHeight(d, cw);
		float rowH = Math.max(acH, tools);
		p.box(356, y, cw, rowH, 12, CARD, BORDER);
		boolean alerts = "true".equalsIgnoreCase(info.get("alerts"));
		Object[] badge = info.isEmpty() ? null : new Object[]{alerts ? "alerts on" : "no alerts", alerts ? RED_CHIP : GREEN_BG, alerts ? RED_TEXT : GREEN_TEXT};
		cardHeader(356, "Anticheat", "", "/dragsimac info", "", d.status(StaffActions.AC_INFO) == Dossier.Status.ASKING, y, cw, null, badge);
		if (d.status(StaffActions.AC_INFO) == Dossier.Status.NO_ANSWER) {
			p.text("The anticheat didn’t answer.", 372, y + 66, DeskType.I400_12, RED_TEXT);
			float bw = p.width("Ask again", DeskType.I500_12) + 22;
			button("acagain", 372, y + 90, bw, 28, 7, "Ask again", DeskType.I500_12, "", Look.PLAIN, "", () -> d.retry(StaffActions.AC_INFO));
		} else {
			String ping = info.getOrDefault("transaction", "");
			String[][] stats = {{"Violation level", info.getOrDefault("vl", "")}, {"Checks run", info.getOrDefault("checks", "")},
					{"Ping", ping.replaceFirst("(?i)ms$", "")}, {"Alerts", info.isEmpty() ? "" : "true".equalsIgnoreCase(info.get("alerts")) ? "On" : "Off"},
					{"Packets in", thousands(info.getOrDefault("received", ""))}, {"Packets out", thousands(info.getOrDefault("sent", ""))}};
			float sw = (cw - 2 - 32 - 12) / 2;
			for (int i = 0; i < stats.length; i++) {
				float sx = 372 + (i % 2) * (sw + 12), sy = y + 49 + 14 + (i / 2) * (39 + 14);
				p.text(stats[i][0], sx, sy, DeskType.I400_11, MUTED);
				if (stats[i][1].isEmpty()) p.round(sx, sy + 20, 48, 14, 4, SOFT);
				else if (i == 3) p.text(stats[i][1], sx, sy + 16 + 1.5f, DeskType.I600_15, TEXT);
				else {
					String v = p.fit(stats[i][1], DeskType.M600_18, sw);
					p.text(v, sx, sy + 16, DeskType.M600_18, TEXT);
					if (i == 2) p.text(" ms", sx + p.width(v, DeskType.M600_18), sy + 16 + (DeskType.M600_18.ascent(1f) - DeskType.I400_12.ascent(1f)), DeskType.I400_12, MUTED);
				}
			}
		}
		toolsCard(row, on, d, 356 + cw + 12, y, cw, rowH);
		return y + rowH;
	}

	/** "392282" → "392,282". */
	private static String thousands(String n) {
		if (!n.matches("\\d{4,}")) return n;
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < n.length(); i++) {
			if (i > 0 && (n.length() - i) % 3 == 0) out.append(',');
			out.append(n.charAt(i));
		}
		return out.toString();
	}

	/** One value on the Account card: the IP behind Reveal, the UUID shortened with Copy, the rest as said. */
	private void field(String key, Map<String, String> f, float x, float y, float w, boolean asking) {
		String v = f.get(key);
		if (v == null || v.isEmpty()) {
			if (asking) p.round(x, y + 3, Math.min(w, 70), 10, 4, SOFT);
			else p.text("—", x, y, DeskType.I400_12, FAINT);
			return;
		}
		switch (key) {
			case "IP" -> {
				// Only ADMIN+ may see a player's address: a helper or moderator gets it masked, with Reveal greyed.
				boolean may = tier().allows(Gate.Tier.ADMIN);
				if (!may) ipShown = false;
				String ip = v.replaceFirst("^/", "").replaceFirst(":\\d+$", "");
				String shownIp = ipShown ? ip : "•••.•••.•••.•••";
				p.text(shownIp, x, y, DeskType.M500_12, ipShown ? SUB : FAINT);
				float bx = x + p.width(shownIp, DeskType.M500_12) + 8;
				String label = ipShown ? "Hide" : "Reveal";
				float bw = p.width(label, DeskType.I500_11) + 18;
				button("ip", bx, y - 3.5f, bw, 22, 6, label, DeskType.I500_11, "", Look.PLAIN, may ? "" : "Revealing an IP needs ADMIN+.",
						() -> ipShown = !ipShown);
			}
			case "UUID" -> {
				String shortId = v.length() > 16 ? v.substring(0, 8) + "…" + v.substring(v.length() - 6) : v;
				p.text(shortId, x, y + 1, DeskType.M500_11, SUB2);
				float bx = x + p.width(shortId, DeskType.M500_11) + 6;
				boolean copied = Util.getMillis() - copiedAt < 1500;
				float hv = hover("uuid", bx, y - 3.5f, 22, 22);
				p.box(bx, y - 3.5f, 22, 22, 6, mix(FIELD, BUTTON_HOVER, hv), copied ? GREEN : BORDER);
				p.icon(copied ? DeskIcons.CHECK : DeskIcons.COPY, bx + 5.5f, y + 2, 11, copied ? GREEN : MUTED);
				hit(bx, y - 3.5f, 22, 22, () -> {
					Minecraft.getInstance().keyboardHandler.setClipboard(v);
					copiedAt = Util.getMillis();
				});
				tip(bx, y - 3.5f, 22, 22, copied ? "Copied" : "Copy " + v);
			}
			case "Balance" -> p.text(p.fit(v.replace(" coins", ""), DeskType.M500_12, w), x, y, DeskType.M500_12, GOLD);
			case "Health", "Mana" -> p.text(thousands(v), x, y, DeskType.M500_12, TEXT);
			case "Addresses" -> {
				String n = v.replaceFirst("\\s*\\(.*\\)$", "");
				p.text(n + " seen", x, y, DeskType.I400_12, TEXT);
				p.text(" · use Alts", x + p.width(n + " seen", DeskType.I400_12), y, DeskType.I400_12, FAINT);
			}
			default -> p.text(p.fit(v, DeskType.I400_12, w), x, y, DeskType.I400_12, TEXT);
		}
	}

	private float toolsHeight(Dossier d, float w) {
		float h = 49 + 12 + 4 * 32 + 3 * 6 + 12;
		if (tool != null) h += replyPanelHeight(d, w) + 12;
		return h + 1;
	}

	private float replyPanelHeight(Dossier d, float w) {
		ServerReply r = d.reply(tool);
		int lines = r == null || !r.answered() ? 1 : Math.max(1, replyLines(r, w).size());
		return 20 + 14 + 4 + lines * 17;
	}

	private List<String> replyLines(ServerReply r, float w) {
		List<String> out = new ArrayList<>();
		for (String l : r.lines()) out.addAll(p.wrap(StaffReplies.captchaStatus(l), DeskType.I400_12, w - 2 - 24 - 24));
		return out.size() > 8 ? out.subList(0, 8) : out;
	}

	private void toolsCard(Roster.Row row, boolean on, Dossier d, float x, float y, float w, float h) {
		p.box(x, y, w, h, 12, CARD, BORDER);
		p.text("Tools", x + 16, y + 15.5f, DeskType.I600_14, TEXT);
		p.text("asked when you press", x + 16 + p.width("Tools", DeskType.I600_14) + 8, y + 17, DeskType.I400_11, FAINT);
		p.rect(x + 1, y + 48, w - 2, 1, BORDER);
		Gate.Tier tier = tier();
		float by = y + 49 + 12, bw = w - 2 - 24;
		Object[][] tools = {{StaffActions.SCAN, "Dupe scan", "answers in MOD+ chat"}, {StaffActions.CAPTCHA_STATUS, "Captcha status", "/captcha status"},
				{StaffActions.AUDIT_LOG, "Audit log", ">"}, {StaffActions.PROFILE_ADMIN, "Profile admin", "leaves the desk"}};
		for (Object[] t : tools) {
			StaffActions a = (StaffActions) t[0];
			String why = StaffActions.blocked(a, tier, on);
			String hint = (String) t[2];
			if (a.tier == Gate.Tier.ADMIN && why.isEmpty() && !hint.equals(">")) hint = "ADMIN+ · " + hint;
			String tipWhy = why.isEmpty() ? "" : why.equals("offline") ? row.name() + " isn’t online, so a dupe scan can’t run." : why + " only";
			button("tool" + a, x + 13, by, bw, 32, 8, (String) t[1], DeskType.I500_13, a == StaffActions.AUDIT_LOG && why.isEmpty() ? ">" : hint,
					a == tool ? Look.ACCENT_OUTLINE : Look.PLAIN, tipWhy, () -> pick(a, row));
			by += 38;
		}
		if (tool == null) return;
		float py = by - 6 + 12, ph = replyPanelHeight(d, w);
		p.box(x + 13, py, bw, ph, 8, INSET, SOFT);
		ServerReply r = d.reply(tool);
		String head = (tool == StaffActions.SCAN ? "Dupe scan" : tool.label) + (r == null ? "" : " · " + Roster.ago(Math.max(0, Util.getMillis() - r.at())));
		p.text(head, x + 25, py + 10, DeskType.I400_11, FAINT);
		float ly = py + 10 + 14 + 4;
		if (r == null) {
			if (d.status(tool) == Dossier.Status.ASKING) p.text("Asking the server…", x + 25, ly, DeskType.I400_12, VIOLET);
			else p.text("Not asked yet.", x + 25, ly, DeskType.I400_12, FAINT);
		} else if (!r.answered()) p.text(tool == StaffActions.CAPTCHA_STATUS ? "Nothing came back: the server stays quiet when there’s no captcha on record."
				: "The server didn’t answer.", x + 25, ly, DeskType.I400_12, tool == StaffActions.CAPTCHA_STATUS ? MUTED : RED_TEXT);
		else for (String l : replyLines(r, w)) {
			p.text(l, x + 25, ly, DeskType.I400_12, SUB2);
			ly += 17;
		}
	}

	// ---- the actions -----------------------------------------------------------------------------

	private void rail(Roster.Row row, boolean on) {
		p.box(1000, 82, 260, 658, 12, CARD, BORDER);
		String player = row == null ? "" : row.name();
		boolean me = player.equalsIgnoreCase(Players.me());
		Gate.Tier tier = tier();
		float x = 1015, w = 230, half = (w - 6) / 2;
		boolean none = row == null || me;

		// Punish.
		String punishNote = !tier.allows(Gate.Tier.MOD) ? "MOD+" : "";
		railLabel("PUNISH", punishNote, 97);
		railButton(StaffActions.BAN, "Ban", x, 119, half, 34, Look.DANGER, DeskType.I600_13, "", row, on, none);
		railButton(StaffActions.MUTE, "Mute", x + half + 6, 119, half, 34, Look.DANGER_OUTLINE, DeskType.I600_13, "", row, on, none);
		railButton(StaffActions.KICK, "Kick", x, 159, half, 34, Look.PLAIN, DeskType.I500_13, "", row, on, none);
		railButton(StaffActions.WARN, "Warn", x + half + 6, 159, half, 34, Look.PLAIN, DeskType.I500_13, "", row, on, none);
		railButton(StaffActions.IP_BAN, "IP ban", x, 201, half, 28, Look.DASHED, DeskType.I500_12, "", row, on, none);
		railButton(StaffActions.IP_MUTE, "IP mute", x + half + 6, 201, half, 28, Look.DASHED, DeskType.I500_12, "", row, on, none);
		p.rect(x, 243, w, 1, BORDER);

		// Investigate.
		railLabel("INVESTIGATE", "", 258);
		String captchaHint = !tier.allows(Gate.Tier.MOD) ? "MOD+" : !on && row != null ? "offline" : "C";
		railButton(StaffActions.CAPTCHA, "Send captcha", x, 280, w, 32, Look.PLAIN, DeskType.I500_13, captchaHint, row, on, none);
		boolean isWatched = row != null && watched(player);
		railButton(StaffActions.WATCH, isWatched ? "Unwatch" : "Watch", x, 320, w, 32, Look.PLAIN, DeskType.I500_13, "shared list", row, on, row == null);
		boolean dashboard = row != null && row.reasons().stream().anyMatch(r -> r.source() == Roster.Source.DASHBOARD);
		if (dashboard || row == null) railButton(StaffActions.FLAG_DETAILS, "Flag details", x, 360, w, 32, Look.PLAIN, DeskType.I500_13, "dashboard", row, on, row == null);
		else {
			float before = p.alpha;
			p.alpha *= 0.4f;
			p.box(x, 360, w, 32, 8, FIELD, BORDER);
			p.text("Flag details", x + 13, 368, DeskType.I500_13, TEXT);
			p.text("not on dashboard", x + w - 13 - p.width("not on dashboard", DeskType.I400_11), 369, DeskType.I400_11, FAINT);
			p.alpha = before;
			tip(x, 360, w, 32, player + " isn’t on the flag dashboard, so it has no details for them.");
		}
		railButton(StaffActions.PROFILE, "View profile", x, 400, w, 32, Look.PLAIN, DeskType.I500_13, ">", row, on, row == null);
		p.rect(x, 446, w, 1, BORDER);

		// Travel.
		String travelNote = !tier.allows(Gate.Tier.MOD) ? "MOD+" : row != null && !on ? "needs them online" : "";
		railLabel("TRAVEL", travelNote, 461);
		railButton(StaffActions.GO_TO, "Go to", x, 483, half, 32, Look.ACCENT, DeskType.I600_13, "", row, on, none);
		railButton(StaffActions.BRING, "Bring here", x + half + 6, 483, half, 32, Look.ACCENT_OUTLINE, DeskType.I500_13, "", row, on, none);
		// Send to server, with its menu chevron.
		String why = none ? "" : StaffActions.blocked(StaffActions.SEND, tier, on);
		boolean can = !none && why.isEmpty();
		float hv = can ? hover("send", x, 523, w, 32) : 0;
		float before = p.alpha;
		if (!can) p.alpha *= 0.4f;
		p.box(x, 523, w, 32, 8, mix(FIELD, BUTTON_HOVER, hv), menu == Menu.SEND ? VIOLET_EDGE : BORDER);
		float lw = p.width("Send to server", DeskType.I500_13), sx = x + (w - lw - 6 - 14) / 2;
		p.text("Send to server", sx, 531, DeskType.I500_13, TEXT);
		p.icon(menu == Menu.SEND ? DeskIcons.CHEVRON_UP : DeskIcons.CHEVRON_DOWN, sx + lw + 6, 532, 14, MUTED);
		p.alpha = before;
		if (can) hit(x, 523, w, 32, () -> toggleMenu(Menu.SEND, x, 559));
		else if (!none) tip(x, 523, w, 32, whyText(StaffActions.SEND, why, player));

		status();
	}

	private void railLabel(String text, String note, float y) {
		p.text(text, 1015, y, DeskType.I600_11, FAINT, 0.06f);
		if (!note.isEmpty()) p.text(note, 1245 - p.width(note, DeskType.I400_11), y, DeskType.I400_11, FAINT);
	}

	private void railButton(StaffActions a, String label, float x, float y, float w, float h, Look look, DeskType type, String hint, Roster.Row row, boolean on,
			boolean none) {
		String player = row == null ? "" : row.name();
		String why = none ? (row == null ? "Pick a player first" : "That’s you") : StaffActions.blocked(a, tier(), on);
		if (a == StaffActions.WATCH || a == StaffActions.PROFILE || a == StaffActions.FLAG_DETAILS) why = row == null ? "Pick a player first" : "";
		button("rail" + a, x, y, w, h, look == Look.DASHED || h < 34 && look == Look.PLAIN && w < 120 ? 7 : 8, label, type, hint, look,
				why.isEmpty() ? "" : whyText(a, why, player), () -> pick(a, row));
	}

	/** Why an action can't be used, as a sentence for its tooltip. */
	private static String whyText(StaffActions a, String why, String player) {
		if (why.equals("offline")) return switch (a) {
			case KICK -> player + " isn’t online, so they can’t be kicked. Ban or mute work offline.";
			case CAPTCHA -> player + " isn’t online, so a captcha can’t reach them.";
			case GO_TO, BRING, SEND -> player + " isn’t online, so there’s nowhere to go.";
			default -> player + " isn’t online.";
		};
		if (why.endsWith("+")) return a.label + " needs " + why + ".";
		return why;
	}

	/** The server's last answer to an action, at the foot of the rail. */
	private void status() {
		String text, link = "";
		int dot;
		boolean pulse = false;
		long now = Util.getMillis();
		if (!opening.isEmpty()) {
			text = "Opening " + opening + "’s flag details…";
			dot = VIOLET;
			pulse = true;
		} else if (outcomeLabel.isEmpty()) {
			text = problem.isEmpty() ? "What the server answers shows here." : problem;
			dot = problem.isEmpty() ? DIM : YELLOW;
			if (!problem.isEmpty()) link = "Retry";
		} else {
			boolean waiting = outcomeWaiting && outcomeText.isEmpty() && now - outcomeAt < Reply.WAIT_MS;
			pulse = waiting;
			text = waiting ? outcomeLabel + " · waiting for the server…" : outcomeText.isEmpty() ? outcomeLabel + " · the server didn’t answer." : outcomeText;
			dot = waiting ? VIOLET : outcomeText.isEmpty() ? RED_DOT : outcomeGood ? GREEN : MUTED;
		}
		List<String> lines = p.wrap(text + (link.isEmpty() ? "" : " " + link), DeskType.I400_12, 193);
		if (lines.size() > 4) lines = lines.subList(0, 4);
		float h = 2 + 20 + lines.size() * 17;
		float y = 725 - h;
		p.box(1015, y, 230, h, 8, INSET, SOFT);
		float a = pulse ? 0.55f + 0.45f * (float) Math.sin(now / 140.0) : 1f;
		p.round(1026, y + 1 + 10 + 5, 7, 7, 99, mix(0x00000000, dot, a));
		float ly = y + 11;
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i);
			boolean last = i == lines.size() - 1;
			if (last && !link.isEmpty() && l.endsWith(link)) {
				String head = l.substring(0, l.length() - link.length());
				p.text(head, 1041, ly + 1, DeskType.I400_12, MUTED);
				float lx = 1041 + p.width(head, DeskType.I400_12);
				p.text(link, lx, ly + 1, DeskType.I400_12, VIOLET);
				hit(lx, ly, p.width(link, DeskType.I400_12), 17, this::refresh);
			} else p.text(l, 1041, ly + 1, DeskType.I400_12, MUTED);
			ly += 17;
		}
	}

	// ---- menus -----------------------------------------------------------------------------------

	private void menu() {
		if (menu == Menu.RANKS) {
			filterMenu();
			return;
		}
		List<String> servers = ServerHop.list();
		String title = menu == Menu.SEND ? "Send " + selected + " to…" : "Hop to…";
		float w = 232, h = 4 + 25 + servers.size() * 30 + 4;
		float x = Math.min(menuX, 1260 - w), y = menuY;
		if (y + h > 745) y = Math.max(20, (menu == Menu.SEND ? 519 : 23) - h);
		hit(-2000, -2000, 5000, 5000, () -> menu = null); // a click anywhere else closes it
		p.shadow(x, y, w, h, 10, 12, 32, 0.5f);
		p.box(x, y, w, h, 10, POPUP, BORDER);
		p.text(title, x + 13, y + 4 + 7, DeskType.I400_11, FAINT);
		float iy = y + 4 + 25;
		for (String s : servers) {
			float hv = hover("server" + s, x + 5, iy, w - 10, 30);
			if (hv > 0) p.round(x + 5, iy, w - 10, 30, 6, mix(0x00000000, VIOLET_BG, hv));
			p.text(s, x + 13, iy + 7, DeskType.I500_13, mix(TEXT, VIOLET_TEXT, hv));
			Menu m = menu;
			hit(x + 5, iy, w - 10, 30, () -> {
				menu = null;
				if (m == Menu.SEND) run(StaffActions.SEND, selected, s);
				else run(StaffActions.HOP, "", s);
			});
			iy += 30;
		}
	}

	/** The reasons the filter offers, in the order it lists them, each with the chip label the list shows. */
	private static final List<Roster.Source> REASONS = List.of(Roster.Source.REPORT, Roster.Source.ALT, Roster.Source.CAPTCHA, Roster.Source.DASHBOARD,
			Roster.Source.DETECTION, Roster.Source.PRIORITY, Roster.Source.SETBACK, Roster.Source.WATCH);

	/** A reason as the filter names it, in the words of the list's chips: "REPORT", "ALT", "CAPTCHA". */
	private static String reasonLabel(Roster.Source s) {
		return switch (s) {
			case DASHBOARD -> "DASHBOARD";
			case PRIORITY -> "SCORE";
			case SETBACK -> "SETBACK";
			case DETECTION -> "DETECTION";
			case CAPTCHA -> "CAPTCHA";
			case REPORT -> "REPORT";
			case ALT -> "ALT";
			case WATCH -> "WATCH";
		};
	}

	/**
	 * The filter's list: what a player is flagged for (each reason, with how many online have it), then every rank
	 * someone online has, highest first, with how many have it. Each click picks or drops one, and the list stays open
	 * for the next; "Show everyone" clears the picks.
	 */
	private void filterMenu() {
		Map<Roster.Source, Integer> reasonCounts = new java.util.EnumMap<>(Roster.Source.class);
		Map<String, Integer> rankCounts = new LinkedHashMap<>();
		for (Roster.Row r : rows()) {
			rankCounts.merge(rankKey(r.rank()), 1, Integer::sum);
			r.reasons().stream().map(Roster.Reason::source).distinct().forEach(s -> reasonCounts.merge(s, 1, Integer::sum));
		}
		for (String picked : ranks) rankCounts.putIfAbsent(picked, 0); // a rank picked stays to be dropped, though nobody has it now
		List<String> order = new ArrayList<>(rankCounts.keySet());
		order.sort(java.util.Comparator.comparingInt(Roster::rankOrder).thenComparing(k -> k));
		boolean any = !ranks.isEmpty() || !reasons.isEmpty();
		float w = 232, rowH = 26, head = 24;
		float h = 4 + (any ? rowH + 4 : 0) + head + REASONS.size() * rowH + 8 + head + order.size() * rowH + 6;
		float x = Math.max(20, Math.min(menuX, 1260 - w)), y = menuY;
		if (y + h > 745) h = 745 - y;
		hit(-2000, -2000, 5000, 5000, () -> menu = null); // a click anywhere else closes it
		p.shadow(x, y, w, h, 10, 12, 32, 0.5f);
		p.box(x, y, w, h, 10, POPUP, BORDER);
		hit(x, y, w, h, () -> {
		});
		float iy = y + 4;
		if (any) {
			float hv = hover("filter-all", x + 5, iy, w - 10, rowH);
			if (hv > 0) p.round(x + 5, iy, w - 10, rowH, 6, mix(0x00000000, VIOLET_BG, hv));
			p.text("Show everyone", x + 13, iy + 5, DeskType.I500_13, mix(VIOLET_TEXT, TEXT, hv));
			hit(x + 5, iy, w - 10, rowH, () -> {
				ranks.clear();
				reasons.clear();
				filterChanged();
			});
			iy += rowH + 4;
		}
		p.text("Flagged for", x + 13, iy + 6, DeskType.I400_11, FAINT);
		iy += head;
		for (Roster.Source s : REASONS) {
			if (iy + rowH > y + h) return;
			String label = reasonLabel(s);
			int[] c = chipColours(new Roster.Chip(label, s, false));
			float ry = iy;
			filterRow("reason" + s, x, iy, w, rowH, reasons.contains(s), reasonCounts.getOrDefault(s, 0), () -> {
				float cw = p.width(label, DeskType.I600_10, 0.04f) + 12;
				p.round(x + 37, ry + 5, cw, 16, 5, c[0]);
				p.text(label, x + 37 + 6, ry + 7, DeskType.I600_10, c[1], 0.04f);
			}, () -> {
				if (!reasons.remove(s)) reasons.add(s);
				filterChanged();
			});
			iy += rowH;
		}
		iy += 4;
		p.rect(x + 1, iy, w - 2, 1, SOFT);
		iy += 4;
		p.text("Ranks", x + 13, iy + 6, DeskType.I400_11, FAINT);
		iy += head;
		for (String k : order) {
			if (iy + rowH > y + h) return;
			boolean on = ranks.contains(k);
			float ry = iy;
			filterRow("rank" + k, x, iy, w, rowH, on, rankCounts.get(k), () -> p.text(rankLabel(k), x + 37, ry + 5, DeskType.I500_13, on ? TEXT : SUB), () -> {
				if (!ranks.remove(k)) ranks.add(k);
				filterChanged();
			});
			iy += rowH;
		}
	}

	/** After a pick changes: the list goes back to its top, and the next desk opens filtered the same (a sample's picks are its own). */
	private void filterChanged() {
		listScrollTo = 0;
		if (sample == null) DeskFilter.save(ranks, reasons);
	}

	/** One row of the filter's list: a tick box, what {@code label} draws, and how many online it fits. */
	private void filterRow(String key, float x, float y, float w, float h, boolean on, int count, Runnable label, Runnable toggle) {
		float hv = hover(key, x + 5, y, w - 10, h);
		if (hv > 0) p.round(x + 5, y, w - 10, h, 6, mix(0x00000000, HOVER, hv));
		p.box(x + 13, y + 6, 14, 14, 4, on ? 0xFF7C3AED : INSET, on ? 0xFF7C3AED : BORDER);
		if (on) p.icon(DeskIcons.CHECK, x + 14, y + 7, 12, 0xFFFFFFFF);
		label.run();
		String n = String.valueOf(count);
		p.text(n, x + w - 13 - p.width(n, DeskType.M500_12), y + 6, DeskType.M500_12, count == 0 ? DIM : FAINT);
		hit(x + 5, y, w - 10, h, toggle);
	}

	// ---- detail panels ---------------------------------------------------------------------------

	private void openSheet(Sheet s) {
		sheet = s;
		sheetAt = Util.getMillis();
		sheetScrollTo = 0;
		menu = null;
		if (s instanceof AuditSheet a && dossier(a.player()).reply(StaffActions.AUDIT_LOG) == null) askTool(StaffActions.AUDIT_LOG, a.player());
	}

	private void closeSheet() {
		sheet = null;
		confirming = "";
	}

	/** The panel from the right: a header (icon, title, what it's about, close), a scrolling body, and a footer of actions. */
	private void sheet() {
		float in = sample != null ? 1f : Math.min(1f, (Util.getMillis() - sheetAt) / 180f);
		hit(0, 0, W, H, this::closeSheet);
		p.rect(0, 0, W, H, Math.round(0.6f * in * 255) << 24 | 0x050507);
		float x = W - 440 + (1f - in) * 40;
		hit(x, 0, 440, H, () -> {
		});
		p.shadow(x, 0, 440, H, 0, 0, 64, 0.5f);
		p.rect(x, 0, 440, H, CARD);
		p.rect(x, 0, 1, H, BORDER);
		switch (sheet) {
			case SignalSheet s -> signalSheet(s.signal(), x);
			case EntrySheet e -> entrySheet(e.entry(), x);
			case AuditSheet a -> auditSheet(a.player(), x);
		}
	}

	/** The panel's header; returns the body's top. */
	private float sheetHeader(float x, DeskIcons icon, int iconBg, int iconEdge, int iconColour, String title, String sub) {
		float hx = x + 21, hy = 20;
		p.box(hx, hy, 36, 36, 9, iconBg, iconEdge);
		p.icon(icon, hx + 9, hy + 9, 18, iconColour);
		p.text(title, hx + 48, hy, DeskType.I600_16, TEXT);
		p.text(p.fit(sub, DeskType.I400_12, 440 - 21 - 48 - 60), hx + 48, hy + 23, DeskType.I400_12, MUTED);
		float cx = x + 440 - 20 - 28;
		float hv = hover("sheet-close", cx, hy, 28, 28);
		p.box(cx, hy, 28, 28, 7, mix(0x00000000, FIELD, hv), BORDER);
		p.icon(DeskIcons.CLOSE, cx + 7, hy + 7, 14, MUTED);
		hit(cx, hy, 28, 28, this::closeSheet);
		p.rect(x + 1, 20 + 38 + 20, 439, 1, BORDER);
		return 79;
	}

	/** Starts the panel's scrolling body; returns where content starts. */
	private float sheetBody(float x, float top, float footer) {
		sheetViewH = H - footer - top;
		float off = sheetScroll.to((float) sheetScrollTo);
		clipTo(x + 1, top, 439, sheetViewH);
		return top + 20 - off;
	}

	private void sheetBodyEnd(float x, float top, float end) {
		float off = sheetScroll.to((float) sheetScrollTo);
		sheetContentH = end + off - top + 20;
		unclip();
		fades(x + 1, top, 439, sheetViewH, off, sheetContentH, CARD);
		sheetScrollTo = Math.clamp(sheetScrollTo, 0, Math.max(0, sheetContentH - sheetViewH));
		scrollbar(x + 440 - 6, top + 4, sheetViewH - 8, sheetContentH, (float) sheetScrollTo);
	}

	/** The panel's footer line and its top. */
	private float sheetFooter(float x) {
		p.rect(x + 1, H - 69, 439, 1, BORDER);
		return H - 69 + 1 + 16;
	}

	private void sectionLabel(String text, float x, float y) {
		p.text(text, x, y, DeskType.I600_11, FAINT, 0.06f);
	}

	/** Label and value rows, the label column {@code labelW} wide; returns the bottom. */
	private float facts(float x, float y, float labelW, float w, String[][] rows) {
		for (String[] r : rows) {
			if (r[1] == null || r[1].isEmpty()) continue;
			p.text(r[0], x, y, DeskType.I400_13, MUTED);
			List<String> lines = p.wrap(r[1], DeskType.I400_13, w - labelW);
			for (String l : lines) {
				p.text(l, x + labelW, y, DeskType.I400_13, TEXT);
				y += 18;
			}
			y += 6;
		}
		return y - 6;
	}

	private float paragraph(String text, float x, float y, float w, DeskType type, int colour, float lineH) {
		for (String l : p.wrap(text, type, w)) {
			p.text(l, x, y + (lineH - type.lineHeight()) / 2, type, colour);
			y += lineH;
		}
		return y;
	}

	private void signalSheet(Signal s, float x) {
		long now = System.currentTimeMillis();
		Roster.Row row = selectedRow();
		String player = row == null ? selected : row.name();
		DeskIcons icon = switch (s.kind()) {
			case PRIORITY -> DeskIcons.TREND;
			case SETBACK -> DeskIcons.ARROW_RIGHT;
			case DASHBOARD -> DeskIcons.ALERT;
			case CAPTCHA -> s.valueColour() == GREEN_TEXT ? DeskIcons.CHECK : DeskIcons.WARNING;
			case ALT -> DeskIcons.SEARCH;
			case WATCH -> DeskIcons.SEARCH;
			case CLIENT -> DeskIcons.SHIELD;
		};
		int[] tint = switch (s.kind()) {
			case PRIORITY -> new int[]{ORANGE_BG, ORANGE_TEXT};
			case SETBACK -> new int[]{PINK_BG, PINK_TEXT};
			case DASHBOARD -> new int[]{RED_CHIP, RED_TEXT};
			case CAPTCHA -> s.valueColour() == GREEN_TEXT ? new int[]{GREEN_BG, GREEN_TEXT} : new int[]{RED_CHIP, RED_TEXT};
			case ALT -> new int[]{PURPLE_BG, PURPLE};
			case WATCH, CLIENT -> new int[]{BLUE_BG, BLUE};
		};
		String sub = switch (s.kind()) {
			case PRIORITY -> player + " · from the anticheat’s FLAG REVIEW lines";
			case SETBACK -> player + " · from the anticheat’s setback lines";
			case DASHBOARD -> player + (s.about() instanceof Detected d && d.flags().isEmpty() ? " · from the anticheat’s lines in staff chat" : " · from the flag dashboard");
			case CAPTCHA -> player + " · the last captcha sent";
			case ALT -> player + " · from the alt check";
			case WATCH -> player + " · on Lazarus’s shared watch list";
			case CLIENT -> player + " · as the anticheat saw them join";
		};
		float top = sheetHeader(x, icon, tint[0], 0x00000000, tint[1], s.title(), sub);
		float bx = x + 21, bw = 398;
		boolean footer = s.kind() != SignalKind.CLIENT && s.kind() != SignalKind.ALT;
		float y = sheetBody(x, top, footer ? 69 : 0);
		switch (s.about()) {
			case StaffFlags.Priority pr -> {
				p.text(Roster.scoreText(pr.score()), bx, y + (26 - 35) / 2f, DeskType.M700_26, ORANGE_TEXT);
				float vx = bx + p.width(Roster.scoreText(pr.score()), DeskType.M700_26) + 12;
				StaffFlags.Reading prev = pr.previous();
				if (prev != null) {
					double delta = pr.score() - prev.score();
					String d = (delta >= 0 ? "+" : "−") + Roster.scoreText(Math.abs(delta)) + " in " + span(pr.at() - prev.at());
					pill(vx, y + 6, d, delta >= 0 ? RED_CHIP : GREEN_BG, delta >= 0 ? RED_TEXT : GREEN_TEXT, 0);
				}
				String ago = Roster.ago(now - pr.at());
				p.text(ago, bx + bw - p.width(ago, DeskType.I400_12), y + 8, DeskType.I400_12, MUTED);
				y += 26 + 20;
				sectionLabel("LAST READINGS", bx, y);
				y += 22;
				float listH = pr.readings().size() * 40 + 1;
				p.box(bx, y, bw, listH + 1, 8, INSET, SOFT);
				float ry = y + 1;
				for (int i = 0; i < pr.readings().size(); i++) {
					StaffFlags.Reading r = pr.readings().get(i);
					if (i > 0) p.rect(bx + 1, ry - 1, bw - 2, 1, SOFT);
					p.text(Roster.ago(now - r.at()), bx + 13, ry + 12, DeskType.I400_12, MUTED);
					float barX = bx + 13 + 90 + 12, barW = bw - 26 - 90 - 48 - 24;
					p.round(barX, ry + 16, barW, 6, 99, BORDER);
					p.round(barX, ry + 16, Math.max(6, barW * (float) Math.min(1, r.score() / 100.0)), 6, 99, ORANGE);
					String v = Roster.scoreText(r.score());
					p.text(v, bx + bw - 13 - p.width(v, DeskType.M600_12), ry + 12, DeskType.M600_12, TEXT);
					ry += 40;
				}
				y += listH + 1 + 20;
				sectionLabel("WHAT IT MEANS", bx, y);
				y = paragraph("The anticheat’s own estimate of how likely this player is cheating. Each new FLAG REVIEW line replaces the last; "
						+ "the newest of this and the flag dashboard sets the Flag score.", bx, y + 22, bw, DeskType.I400_13, SUB2, 20) + 20;
				y = facts(bx, y, 110, bw, new String[][]{{"Kept for", "24 hours after the last line"}, {"Shared", "with every staff member using Lazarus"}});
			}
			case StaffFlags.Setback b -> {
				p.text("×" + b.count(), bx, y + (26 - 35) / 2f, DeskType.M700_26, PINK_TEXT);
				String ago = "last " + Roster.ago(now - b.at());
				p.text(ago, bx + bw - p.width(ago, DeskType.I400_12), y + 8, DeskType.I400_12, MUTED);
				y += 26 + 20;
				sectionLabel("WHERE", bx, y);
				y += 22;
				p.box(bx, y, bw, 38, 8, INSET, SOFT);
				p.text(p.fit(place(b.place()), DeskType.M500_12, bw - 26), bx + 13, y + 11, DeskType.M500_12, SUB2);
				y += 38 + 20;
				y = facts(bx, y, 110, bw, new String[][]{{"First heard", b.since() > 0 ? Roster.ago(now - b.since()) : ""}, {"Last heard", Roster.ago(now - b.at())},
						{"Count", b.count() + " in a row, as the anticheat counts them"}}) + 20;
				sectionLabel("WHAT IT MEANS", bx, y);
				y = paragraph("The anticheat moved this player back to where it thinks they should be: usually movement it couldn’t explain, "
						+ "sometimes a ghost block or lag.", bx, y + 22, bw, DeskType.I400_13, SUB2, 20);
			}
			case Detected was -> {
				// Worked out again each frame, so a read that finishes while the panel is open shows in it.
				Detected d = row != null ? detected(row) : was;
				StaffFlags.Flag fl = d.flags().isEmpty() ? null : d.flags().getFirst();
				int total = FlagAlerts.total(d.checks());
				String big = fl != null ? Roster.scoreText(fl.score()) : "×" + total;
				p.text(big, bx, y + (26 - 35) / 2f, DeskType.M700_26, RED_SOFT);
				p.text(fl != null ? "review score" : total == 1 ? "detection in the last hour" : "detections in the last hour",
						bx + p.width(big, DeskType.M700_26) + 10, y + 8, DeskType.I400_12, MUTED);
				y += 26 + 20;
				sectionLabel("LAST HOUR", bx, y);
				String when = d.read() == AlertState.READING ? "reading…" : d.readAt() > 0 ? "alerts read " + Roster.ago(now - d.readAt()) : "";
				p.text(when, bx + bw - p.width(when, DeskType.I400_11), y + 1, DeskType.I400_11, FAINT);
				y += 22;
				if (d.checks().isEmpty()) {
					String why = switch (d.read()) {
						case READING -> "Reading the alerts on their dashboard page…";
						case READ -> "No alerts in the last hour.";
						case FAILED -> "Their dashboard page couldn’t be read. Open the flag details to look yourself.";
						case UNREAD -> "Their dashboard page is read once they’re selected.";
					};
					y = paragraph(why, bx, y, bw, DeskType.I400_13, SUB2, 20) + 20;
				} else {
					for (FlagAlerts.Check c : d.checks()) y = checkBox(c, bx, y, bw, now) + 8;
					y += 12;
				}
				if (fl != null) {
					String[] said = dashboardSaid(fl);
					String types = String.join(", ", d.flags().stream().map(f -> Roster.words(f.type())).distinct().toList());
					y = facts(bx, y, 130, bw, new String[][]{{"Flags", types}, {"Unreviewed", said[0].replace(" unreviewed", "")},
							{"Newest from", said[1]}, {"On it since", Roster.ago(now - fl.since())}}) + 20;
				}
				sectionLabel("WHAT IT MEANS", bx, y);
				y = paragraph("Every alert on the player’s dashboard page from the last hour, and what staff chat has heard since, summed up by check: "
						+ "how often the anticheat caught them and the most it measured. A review-only check such as click rate isn’t proof on its own; "
						+ "the flag details hold the evidence.", bx, y + 22, bw, DeskType.I400_13, SUB2, 20);
			}
			case StaffFlags.Captcha c -> {
				p.text(c.passed() ? "Passed" : "Failed", bx, y, DeskType.I600_16, c.passed() ? GREEN_TEXT : RED_TEXT);
				y += 20 + 20;
				y = facts(bx, y, 110, bw, new String[][]{{"Sent by", c.by()}, {"Answered in", c.seconds() >= 0 ? c.seconds() + " s" : c.passed() ? "" : "didn’t answer"},
						{"When", Roster.ago(now - c.at())}});
			}
			case StaffFlags.Alt a -> {
				p.text(Roster.words(a.why()), bx, y, DeskType.I600_16, PURPLE);
				y += 20 + 20;
				List<String> linked = AltPages.names(a.others()), banned = AltPages.banned(a.others());
				if (!linked.isEmpty()) {
					sectionLabel("LINKED ACCOUNTS", bx, y);
					y += 22;
					float listH = linked.size() * 44 + 1;
					p.box(bx, y, bw, listH + 1, 8, INSET, SOFT);
					float ry = y + 1;
					for (int i = 0; i < linked.size(); i++) {
						String n = linked.get(i);
						boolean isBanned = banned.contains(n);
						if (i > 0) p.rect(bx + 1, ry - 1, bw - 2, 1, SOFT);
						float hv = hover("sheet-alt" + n, bx + 1, ry, bw - 2, 43);
						if (hv > 0) p.rect(bx + 1, ry, bw - 2, 43, mix(0x00000000, HOVER, hv));
						p.face(n, bx + 13, ry + 10, 24, 5, hv > 0.5f ? HOVER : INSET);
						p.text(n, bx + 47, ry + 14, DeskType.I500_13, TEXT);
						String tag = isBanned ? "Banned" : isOnline(n) ? "Online" : "Offline";
						float pw = p.width(tag, DeskType.I500_11) + 16 + 11, px = bx + bw - 13 - 14 - 8 - pw;
						if (isBanned) pill(px, ry + 13, tag, RED_CHIP, RED_TEXT, RED_DOT);
						else if (isOnline(n)) pill(px, ry + 13, tag, GREEN_BG, GREEN_TEXT, GREEN);
						else pill(px, ry + 13, tag, FIELD, MUTED, FAINT);
						p.icon(DeskIcons.CHEVRON_RIGHT, bx + bw - 13 - 14, ry + 15, 14, FAINT);
						hit(bx + 1, ry, bw - 2, 43, () -> {
							closeSheet();
							lookUp(n);
						});
						ry += 44;
					}
					y += listH + 1 + 20;
				}
				y = facts(bx, y, 110, bw, new String[][]{{"Banned", banned.isEmpty() ? (linked.isEmpty() ? "" : "the check didn’t say which") : String.join(", ", banned)},
						{"Since", Roster.ago(now - a.since())}}) + 20;
				sectionLabel("WHAT IT MEANS", bx, y);
				y = paragraph("The server’s alt check links accounts that played from the same IP. A banned account in the group, or a banned IP, "
						+ "flags everyone in it. Open an account to see its records.", bx, y + 22, bw, DeskType.I400_13, SUB2, 20);
			}
			case StaffFlags.Watch wt -> {
				p.text(wt.note().isEmpty() ? "On the watch list" : wt.note(), bx, y, DeskType.I600_16, TEXT);
				y += 20 + 20;
				y = facts(bx, y, 110, bw, new String[][]{{"Added by", wt.by()}, {"When", Roster.ago(now - wt.at())}});
			}
			case StaffFlags.Brand b -> {
				p.text(clientName(b.client()), bx, y, DeskType.I600_16, TEXT);
				y += 20 + 20;
				sectionLabel("AS SENT", bx, y);
				y += 22;
				p.box(bx, y, bw, 38, 8, INSET, SOFT);
				p.text(p.fit(b.client(), DeskType.M500_12, bw - 26), bx + 13, y + 11, DeskType.M500_12, SUB2);
				y += 38 + 20;
				y = facts(bx, y, 110, bw, new String[][]{{"Joined", Roster.ago(now - b.at())}});
			}
			default -> {
			}
		}
		sheetBodyEnd(x, top, y);
		if (!footer) return;
		float fy = sheetFooter(x);
		boolean on = row != null && isOnline(row.name());
		switch (s.kind()) {
			case PRIORITY -> {
				float bw2 = p.width("Send captcha", DeskType.I500_13) + 30;
				button("sh-captcha", bx + bw - bw2, fy, bw2, 36, 8, "Send captcha", DeskType.I500_13, "", Look.PLAIN,
						on ? StaffActions.blocked(StaffActions.CAPTCHA, tier(), true) : player + " isn’t online.", () -> pick(StaffActions.CAPTCHA, row));
				button("sh-staff", bx, fy, bw - bw2 - 8, 36, 8, "Review evidence in /staff", DeskType.I600_13, "", Look.ACCENT, "", () -> {
					Minecraft mc = Minecraft.getInstance();
					onClose();
					if (mc.player != null) mc.player.connection.sendCommand("staff");
				});
			}
			case SETBACK -> {
				button("sh-goto", bx, fy, (bw - 8) / 2, 36, 8, "Go to " + player, DeskType.I600_13, "", Look.ACCENT,
						on ? StaffActions.blocked(StaffActions.GO_TO, tier(), true) : player + " isn’t online.", () -> pick(StaffActions.GO_TO, row));
				button("sh-captcha", bx + (bw - 8) / 2 + 8, fy, (bw - 8) / 2, 36, 8, "Send captcha", DeskType.I500_13, "", Look.PLAIN,
						on ? StaffActions.blocked(StaffActions.CAPTCHA, tier(), true) : player + " isn’t online.", () -> pick(StaffActions.CAPTCHA, row));
			}
			case DASHBOARD -> {
				boolean onBoard = state().flags().stream().anyMatch(f -> f.name().equalsIgnoreCase(player));
				float rw = p.width("Read again", DeskType.I500_13) + 30;
				button("sh-reread", bx, fy, rw, 36, 8, "Read again", DeskType.I500_13, "", Look.PLAIN, onBoard ? "" : player + " isn’t on the flag dashboard.",
						() -> rereadAlerts(player));
				button("sh-flags", bx + rw + 8, fy, bw - rw - 8, 36, 8, "Open flag details", DeskType.I600_13, "", Look.ACCENT, "", () -> {
					closeSheet();
					openFlags(player);
				});
			}
			case CAPTCHA -> button("sh-again", bx, fy, bw, 36, 8, "Send another captcha", DeskType.I500_13, "", Look.PLAIN,
					on ? StaffActions.blocked(StaffActions.CAPTCHA, tier(), true) : player + " isn’t online.", () -> pick(StaffActions.CAPTCHA, row));
			case WATCH -> button("sh-unwatch", bx, fy, bw, 36, 8, "Remove from the watch list", DeskType.I500_13, "", Look.PLAIN, "", () -> {
				pick(StaffActions.WATCH, row);
				closeSheet();
			});
			default -> {
			}
		}
	}

	/**
	 * One check in the panel: its name and count, the range of each measure (the telling one first), the servers, when
	 * it was last caught and what it added to the score, and the newest alert's evidence. Returns its bottom.
	 */
	private float checkBox(FlagAlerts.Check c, float x, float y, float w, long now) {
		List<String> measures = c.ranges().isEmpty() ? List.of() : p.wrap(String.join(" · ", c.ranges().stream().map(FlagAlerts.Range::text).toList()), DeskType.M500_12, w - 26);
		List<String> evidence = c.latest().isEmpty() ? List.of() : p.wrap(c.latest(), DeskType.M500_11, w - 26);
		int ms = Math.min(2, measures.size()), ev = Math.min(3, evidence.size());
		float h = 12 + 18 + (ms > 0 ? 6 + ms * 16 : 0) + 6 + 15 + (ev > 0 ? 8 + ev * 14 : 0) + 12;
		p.box(x, y, w, h, 8, INSET, SOFT);
		float ix = x + 13, iy = y + 12;
		String n = "×" + c.count();
		float nw = p.width(n, DeskType.M600_13);
		p.text(p.fit(c.name(), DeskType.I600_13, w - 26 - nw - 8), ix, iy, DeskType.I600_13, TEXT);
		p.text(n, x + w - 13 - nw, iy, DeskType.M600_13, RED_SOFT);
		iy += 18;
		for (int i = 0; i < ms; i++) {
			String line = i == 1 && measures.size() > 2 ? p.fit(measures.get(1) + " …", DeskType.M500_12, w - 26) : measures.get(i);
			p.text(line, ix, iy + 6 + i * 16, DeskType.M500_12, SUB2);
		}
		iy += ms > 0 ? 6 + ms * 16 : 0;
		String facts = (c.servers().isEmpty() ? "" : String.join(", ", c.servers()) + " · ") + "last " + Roster.ago(now - c.newest())
				+ (c.score() > 0 ? " · adds " + Roster.scoreText(c.score()) + " to the score" : "");
		p.text(p.fit(facts, DeskType.I400_11, w - 26), ix, iy + 6, DeskType.I400_11, MUTED);
		iy += 6 + 15;
		for (int i = 0; i < ev; i++) {
			String line = i == 2 && evidence.size() > 3 ? p.fit(evidence.get(2) + " …", DeskType.M500_11, w - 26) : evidence.get(i);
			p.text(line, ix, iy + 8 + i * 14, DeskType.M500_11, FAINT);
		}
		return y + h;
	}

	private void entrySheet(StaffReplies.Entry e, float x) {
		boolean ban = e.kind().contains("BAN"), mute = e.kind().contains("MUTE"), warn = e.kind().equals("WARN");
		DeskIcons icon = ban ? DeskIcons.BAN : mute ? DeskIcons.MIC_OFF : warn ? DeskIcons.WARNING : DeskIcons.KICK;
		int[] tint = !e.active() ? new int[]{FIELD, MUTED} : ban || mute ? new int[]{RED_CHIP, RED_TEXT} : warn ? new int[]{BLUE_BG, BLUE} : new int[]{FIELD, SUB};
		String title = Roster.words(e.kind());
		float top = sheetHeader(x, icon, tint[0], tint[0] == FIELD ? BORDER : 0x00000000, tint[1], title + " ", e.player() + " · from /" + (warn ? "warns" : "history"));
		p.text("#" + e.id(), x + 21 + 48 + p.width(title + " ", DeskType.I600_16), 21.5f, DeskType.M500_13, FAINT);
		float bx = x + 21, bw = 398;
		StaffActions undo = undoFor(e);
		float y = sheetBody(x, top, 69);
		if (e.active()) {
			boolean perm = e.left().isEmpty() || e.left().equalsIgnoreCase("Permanent");
			int bg = warn ? BLUE_BG : RED_BG, edge = warn ? 0xFF164E63 : RED_CARD_EDGE, fg = warn ? BLUE : RED_SOFT, fg2 = warn ? BLUE : RED_TEXT;
			float h = perm ? 44 : 64;
			p.box(bx, y, bw, h, 10, bg, edge);
			p.text("Active · " + (e.left().isEmpty() ? "until removed" : perm ? "permanent" : e.left() + " left"), bx + 15, y + 12, DeskType.I600_13, fg);
			if (!perm) {
				long ends = remaining(e.left());
				if (ends > 0) {
					String when = java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)
							.format(java.time.LocalDateTime.now().plusSeconds(ends / 1000));
					p.text("Ends about " + when, bx + 15, y + 32, DeskType.I400_12, fg2);
				}
			}
			y += h + 20;
		} else {
			p.box(bx, y, bw, 44, 10, FIELD, BORDER);
			p.text(shortState(e), bx + 15, y + 12, DeskType.I600_13, MUTED);
			y += 44 + 20;
		}
		sectionLabel("REASON", bx, y);
		y = paragraph(reasonOf(e), bx, y + 22, bw, DeskType.I500_14, TEXT, 21) + 20;
		String rule = reasonOf(e).matches("^\\d+\\.\\d+\\s*-.*") ? reasonOf(e).replaceFirst("\\s*-\\s*", " ").replaceFirst("^(\\S+ \\S+).*", "$1") : "";
		y = facts(bx, y, 110, bw, new String[][]{{"Rule", rule}, {"By", e.by()}, {"On", e.date()}, {"Undone by", e.undoneBy()}});
		sheetBodyEnd(x, top, y);
		float fy = sheetFooter(x);
		float cw = p.width("Copy ID", DeskType.I500_13) + 30 + 22;
		boolean copied = Util.getMillis() - copiedAt < 1500;
		float hv = hover("copyid", bx, fy, cw, 36);
		p.box(bx, fy, cw, 36, 8, mix(FIELD, BUTTON_HOVER, hv), BORDER);
		p.icon(copied ? DeskIcons.CHECK : DeskIcons.COPY, bx + 15, fy + 11, 14, copied ? GREEN : MUTED);
		p.text(copied ? "Copied" : "Copy ID", bx + 37, fy + 10, DeskType.I500_13, TEXT);
		hit(bx, fy, cw, 36, () -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(e.id()));
			copiedAt = Util.getMillis();
		});
		if (undo != null) {
			String key = undo.name() + "#" + e.id();
			boolean sure = confirming.equals(key);
			String label = sure ? "Sure? Click again" : undo.label + " " + e.player();
			float uw = p.width(label, DeskType.I600_13) + 32;
			String why = StaffActions.blocked(undo, tier(), true);
			button("sh-" + key, bx + bw - uw, fy, uw, 36, 8, label, DeskType.I600_13, "", sure ? Look.SURE : warn ? Look.PLAIN : Look.DANGER_OUTLINE,
					why.isEmpty() ? "" : why + " only", () -> undo(undo, e, key));
		}
	}

	/** "6d 23h 26m" → milliseconds; 0 when it can't be read. */
	private static long remaining(String left) {
		long ms = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([ydhms])").matcher(left.toLowerCase(Locale.ROOT));
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			ms += switch (m.group(2)) {
				case "y" -> n * 365 * 86_400_000L;
				case "d" -> n * 86_400_000L;
				case "h" -> n * 3_600_000L;
				case "m" -> n * 60_000L;
				default -> n * 1000L;
			};
		}
		return ms;
	}

	private void auditSheet(String player, float x) {
		Dossier d = dossier(player);
		ServerReply r = d.reply(StaffActions.AUDIT_LOG);
		Dossier.Status st = d.status(StaffActions.AUDIT_LOG);
		float top = sheetHeader(x, DeskIcons.FILE, FIELD, BORDER, SUB, "Audit log", player + "’s transactions · /auditlog");
		float bx = x + 21, bw = 398;
		float y = sheetBody(x, top, 69);
		if (st == Dossier.Status.ASKING || r == null) {
			boolean waitingTurn = st != Dossier.Status.ASKING;
			long since = waitingTurn ? 0 : Util.getMillis() - d.askedAt(StaffActions.AUDIT_LOG);
			float t = Math.min(1f, since / 30_000f);
			p.box(bx, y, bw, 92, 10, SELECTED, VIOLET_EDGE);
			p.round(bx + 15, y + 17, 7, 7, 99, VIOLET);
			p.text(waitingTurn ? "Asked next" : "Waiting for the server", bx + 30, y + 12, DeskType.I600_13, VIOLET_TEXT);
			String secs = waitingTurn ? "" : (since / 1000) + " s";
			p.text(secs, bx + bw - 15 - p.width(secs, DeskType.M500_11), y + 13, DeskType.M500_11, VIOLET);
			p.round(bx + 15, y + 40, bw - 30, 4, 99, VIOLET_BG);
			if (!waitingTurn) p.round(bx + 15, y + 40, Math.max(4, (bw - 30) * t), 4, 99, VIOLET);
			paragraph(waitingTurn ? "The server is still answering another command; the audit log is asked as soon as it's done."
					: "The audit log usually answers in about 30 seconds. You can keep using the desk.", bx + 15, y + 52, bw - 30, DeskType.I400_12, VIOLET_SOFT, 17);
			y += 92 + 20;
			sectionLabel("TRANSACTIONS", bx, y);
			y += 22;
			p.box(bx, y, bw, 3 * 35 + 2, 8, INSET, SOFT);
			for (int i = 0; i < 3; i++) {
				float ry = y + 1 + i * 35;
				if (i > 0) p.rect(bx + 1, ry - 1, bw - 2, 1, SOFT);
				p.round(bx + 13, ry + 12, 64, 10, 4, SOFT);
				p.round(bx + 13 + 64 + 12, ry + 12, bw - 26 - 64 - 48 - 24, 10, 4, FIELD);
				p.round(bx + bw - 13 - 48, ry + 12, 48, 10, 4, SOFT);
			}
			y += 3 * 35 + 2;
		} else if (!r.answered()) {
			p.box(bx, y, bw, 64, 10, RED_BG, RED_CARD_EDGE);
			p.text("The server didn’t answer", bx + 15, y + 12, DeskType.I600_13, RED_SOFT);
			p.text("Nothing came back within 40 seconds.", bx + 15, y + 34, DeskType.I400_12, RED_TEXT);
			y += 64;
		} else if (StaffReplies.auditEmpty(r.lines()) || StaffReplies.audits(r.lines()).isEmpty()) {
			p.box(bx, y, bw, 64, 10, FIELD, BORDER);
			p.text("No transactions for " + player, bx + 15, y + 12, DeskType.I600_13, TEXT);
			p.text("The server found nothing in its audit log.", bx + 15, y + 34, DeskType.I400_12, MUTED);
			y += 64;
		} else {
			List<StaffReplies.Audit> all = StaffReplies.audits(r.lines());
			String span = StaffReplies.auditSpan(r.lines());
			int said = StaffReplies.auditCount(r.lines());
			p.text((said > 0 ? said : all.size()) + " transactions" + (span.isEmpty() ? "" : " · " + span), bx, y, DeskType.I600_13, TEXT);
			y += 16 + 12;
			// Each action as a filter chip with its count.
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (StaffReplies.Audit a : all) counts.merge(a.action(), 1, Integer::sum);
			float cx = bx;
			for (Map.Entry<String, Integer> c : counts.entrySet()) {
				String label = Roster.words(c.getKey()) + " " + c.getValue();
				float cw = p.width(label, DeskType.I500_12) + 22;
				if (cx + cw > bx + bw) {
					cx = bx;
					y += 34;
				}
				boolean on = auditFilter.equals(c.getKey());
				int[] ac = auditColours(c.getKey());
				float hv = hover("af" + c.getKey(), cx, y, cw, 28);
				p.box(cx, y, cw, 28, 7, on ? ac[0] : mix(FIELD, BUTTON_HOVER, hv), on ? ac[1] : BORDER);
				p.text(label, cx + 11, y + 6.5f, DeskType.I500_12, on ? ac[1] : SUB);
				String key = c.getKey();
				hit(cx, y, cw, 28, () -> auditFilter = auditFilter.equals(key) ? "" : key);
				cx += cw + 6;
			}
			y += 28 + 16;
			List<StaffReplies.Audit> rows = auditFilter.isEmpty() ? all : all.stream().filter(a -> a.action().equals(auditFilter)).toList();
			float listH = rows.size() * 52 + 1;
			p.box(bx, y, bw, listH + 1, 8, INSET, SOFT);
			float ry = y + 1;
			for (int i = 0; i < rows.size(); i++) {
				StaffReplies.Audit a = rows.get(i);
				if (i > 0) p.rect(bx + 1, ry - 1, bw - 2, 1, SOFT);
				int[] ac = auditColours(a.action());
				float kw = p.width(a.action(), DeskType.I600_10, 0.05f) + 12;
				p.round(bx + 13, ry + 10, kw, 18, 5, ac[0]);
				p.text(a.action(), bx + 19, ry + 13, DeskType.I600_10, ac[1], 0.05f);
				String time = a.time();
				p.text(time, bx + bw - 13 - p.width(time, DeskType.M500_11), ry + 12, DeskType.M500_11, FAINT);
				float ix = bx + 13 + kw + 10, room = bx + bw - 13 - p.width(time, DeskType.M500_11) - 10 - ix;
				String item = p.fit(a.itemName(), DeskType.I500_13, room - (a.count() > 1 ? 40 : 0));
				p.text(item, ix, ry + 10, DeskType.I500_13, TEXT);
				if (a.count() > 1) p.text("×" + a.count(), ix + p.width(item, DeskType.I500_13) + 6, ry + 11, DeskType.M500_12, MUTED);
				String why = Roster.words(a.source()).toLowerCase(Locale.ROOT) + (a.with().isEmpty() ? "" : " · with " + a.with());
				p.text(p.fit(why, DeskType.I400_11, room), ix, ry + 29, DeskType.I400_11, a.with().isEmpty() ? FAINT : VIOLET);
				if (!a.with().isEmpty()) {
					String other = a.with();
					hit(ix, ry + 27, p.width(why, DeskType.I400_11), 16, () -> {
						closeSheet();
						lookUp(other);
					});
				}
				ry += 52;
			}
			y += listH + 1;
		}
		sheetBodyEnd(x, top, y);
		float fy = sheetFooter(x);
		boolean asking = st == Dossier.Status.ASKING;
		float aw = p.width("Ask again", DeskType.I500_13) + 30;
		button("audit-again", bx, fy, aw, 36, 8, "Ask again", DeskType.I500_13, "", Look.PLAIN, asking ? "Still waiting for the last answer" : "",
				() -> askTool(StaffActions.AUDIT_LOG, player));
		float cw = p.width("Close", DeskType.I500_13) + 30;
		button("audit-close", bx + bw - cw, fy, cw, 36, 8, "Close", DeskType.I500_13, "", Look.GHOST, "", this::closeSheet);
	}

	private static int[] auditColours(String action) {
		return switch (action) {
			case "GIVE" -> new int[]{GREEN_BG, GREEN_TEXT};
			case "CREATE" -> new int[]{BLUE_BG, BLUE};
			case "DESTROY" -> new int[]{RED_CHIP, RED_TEXT};
			case "TRANSFER" -> new int[]{VIOLET_BG, VIOLET_TEXT};
			default -> new int[]{BORDER, SUB2};
		};
	}

	// ---- tooltip ---------------------------------------------------------------------------------

	private void tooltip() {
		if (tipText == null || tipAt == null) return;
		float maxW = 290;
		List<String> lines = p.wrap(tipText, DeskType.I400_12, maxW - 20);
		float w = 0;
		for (String l : lines) w = Math.max(w, p.width(l, DeskType.I400_12));
		w += 22;
		float h = lines.size() * 17 + 16 + 2;
		float x = Math.clamp(tipAt[0], 8, W - 8 - w), y = tipAt[1] - h - 6;
		if (y < 8) y = tipAt[1] + tipAt[3] + 6;
		p.shadow(x, y, w, h, 8, 8, 24, 0.5f);
		p.box(x, y, w, h, 8, POPUP, BORDER);
		float ly = y + 9;
		for (String l : lines) {
			p.text(l, x + 11, ly, DeskType.I400_12, SUB);
			ly += 17;
		}
	}

	// ---- doing things ----------------------------------------------------------------------------

	private void openDialog(DeskDialog.Kind kind, boolean ip) {
		StaffTools tools = StaffTools.INSTANCE;
		List<String> durations = tools == null ? List.of("1h", "1d", "7d", "14d", "30d", "perm") : tools.durations();
		List<String> reasons = tools == null ? List.of("Cheating", "Macroing", "Chat abuse", "Alt abuse") : tools.reasons();
		List<Ladder.Rule> rules = tools == null ? List.of() : kind == DeskDialog.Kind.BAN ? tools.banRules() : kind == DeskDialog.Kind.MUTE ? tools.muteRules() : List.of();
		if (tools == null && sample != null) {
			rules = kind == DeskDialog.Kind.BAN ? Ladder.parse("1.0 Cheating Through Unfair Advantages: 7d/30d/90d/1y; 1.1 Dishonest Behaviour: 1d/7d/30d; 1.2 Mass Rule Breaking: set; "
					+ "1.3 Punishment Evasion: 30d/perm; 1.4 Alt Abuse: 7d/30d; 1.5 Bug Abuse: 7d/30d/90d; 1.6 Duping: perm; 2.3 Advertising: 7d/30d/perm")
					: kind == DeskDialog.Kind.MUTE ? Ladder.parse("2.0 Chat Abuse: 30m/12h/7d; 2.1 Begging: 30m/12h/7d; 2.2 Provocation: 6h/24h/30d; 2.4 Harassing: set; "
					+ "2.5 Extreme Behaviour: 7d/30d/90d") : List.of();
		}
		String p0 = selected;
		Dossier d = dossier(p0);
		ServerReply h = d.reply(StaffActions.HISTORY), wr = d.reply(StaffActions.WARNS);
		List<StaffReplies.Entry> past = h == null ? List.of() : StaffReplies.entries(h.lines());
		int warnings = wr == null ? 0 : StaffReplies.entries(wr.lines()).size();
		int reports = Reports.INSTANCE == null ? (int) state().reports().stream().filter(r -> r.target().equalsIgnoreCase(p0)).count() : Reports.INSTANCE.openFor(p0).size();
		String closes = reports > 0 ? " Their " + reports + " open report" + (reports == 1 ? "" : "s") + " close" + (reports == 1 ? "s" : "") + " with it." : "";
		String sub = switch (kind) {
			case BAN -> "Network-wide." + closes;
			case MUTE -> "They can still play; chat and private messages stop." + closes;
			case WARN -> "Stays on their record until a staff member removes it.";
			case KICK -> "They can rejoin straight away." + closes;
		};
		String kindKey = kind == DeskDialog.Kind.BAN ? "BAN" : "MUTE";
		dialog = new DeskDialog(kind, p0, ip, sub, rules, durations, reasons,
				code -> (int) past.stream().filter(e -> e.kind().contains(kindKey) && (e.reason().startsWith(code + " ") || e.reason().equals(code))).count(), warnings);
	}

	private void confirmDialog() {
		if (dialog == null || !dialog.ready()) return;
		DeskDialog dl = dialog;
		dialog = null;
		if (sample != null) return;
		runCommand(dl.action(), dl.player, dl.command(Players.me()));
	}

	private void pick(StaffActions a, Roster.Row row) {
		String pl = row == null ? selected : row.name();
		// A sample desk opens its dialogs, panels and menus (so the game test can click through them) but never leaves or sends.
		if (sample != null && (a == StaffActions.PROFILE || a == StaffActions.WATCH || a == StaffActions.FLAG_DETAILS || a == StaffActions.STAFF_CHAT
				|| a == StaffActions.PROFILE_ADMIN)) return;
		switch (a) {
			case PROFILE -> {
				onClose();
				ProfileViewer.show(pl);
				return;
			}
			case WATCH -> {
				if (WatchList.INSTANCE != null) WatchList.INSTANCE.toggle(pl);
				return;
			}
			case FLAG_DETAILS -> {
				openFlags(pl);
				return;
			}
			case STAFF_CHAT -> {
				Minecraft mc = Minecraft.getInstance();
				onClose();
				mc.setScreen(new ChatScreen("/sc ", false));
				return;
			}
			case PROFILE_ADMIN -> {
				Minecraft mc = Minecraft.getInstance();
				onClose();
				if (mc.player != null) mc.player.connection.sendCommand(StaffActions.fill(a, pl, "", "", "", ""));
				return;
			}
			case AUDIT_LOG -> {
				openSheet(new AuditSheet(pl));
				return;
			}
			case SCAN, CAPTCHA_STATUS -> {
				tool = a;
				askTool(a, pl);
				return;
			}
			default -> {
			}
		}
		switch (a.card) {
			case BAN -> openDialog(DeskDialog.Kind.BAN, a == StaffActions.IP_BAN);
			case MUTE -> openDialog(DeskDialog.Kind.MUTE, a == StaffActions.IP_MUTE);
			case REASON -> openDialog(a == StaffActions.KICK ? DeskDialog.Kind.KICK : DeskDialog.Kind.WARN, false);
			case CONFIRM -> run(a, pl, "");
			case PICK_SERVER -> toggleMenu(a == StaffActions.SEND ? Menu.SEND : Menu.HOP, a == StaffActions.SEND ? 1015 : 1028, a == StaffActions.SEND ? 559 : 63);
			case NONE -> run(a, a.kind == StaffActions.Kind.SELF ? "" : pl, "");
		}
	}

	/** Sends an action's command, filled in, and shows its answer as one tidy line. */
	private void run(StaffActions a, String player, String server) {
		runCommand(a, player, StaffActions.fill(a, player, Players.me(), "", "", server));
	}

	private void runCommand(StaffActions a, String player, String cmd) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || sample != null) return;
		if (AltCheck.running() && Reply.busy()) {
			note(a.label, "Wait a moment while the desk checks alts.", false);
			return;
		}
		outcomeLabel = a.label + (player.isEmpty() ? "" : " " + player);
		outcomeAt = Util.getMillis();
		outcomeText = "";
		outcomeWaiting = true;
		String target = player.isEmpty() ? Players.me() : player;
		Reply.ask(cmd, target, false, StaffReplies::outcomeLine, lines -> {
			String said = StaffReplies.outcome(lines);
			// /goto, /send and /server move people between servers without a word back: silence there isn't a failure.
			if (said.isEmpty() && (a == StaffActions.GO_TO || a == StaffActions.SEND || a == StaffActions.HOP)) said = "Sent /" + cmd + " · the server doesn’t reply to it";
			outcomeText = said;
			outcomeGood = lines.stream().anyMatch(StaffReplies::outcomeLine) || !said.isEmpty() && lines.isEmpty();
			outcomeAt = Util.getMillis();
			if (said.equals("You're vanished")) vanished = true;
			if (said.equals("You're visible again")) vanished = false;
			// What the server now says about them has changed: their records are asked again.
			if (!player.isEmpty() && a.kind != StaffActions.Kind.TRAVEL) {
				Dossier d = dossier(player);
				retry(d, StaffActions.HISTORY);
				retry(d, StaffActions.WARNS);
			}
		});
		if (a.handlesReports && Reports.INSTANCE != null && !Reports.INSTANCE.openFor(player).isEmpty()) FlagSync.reportsDone(player, ok -> {
		});
	}

	private void reportDone(int id) {
		if (sample != null) return;
		note("Report #" + id, "Closing…", false);
		FlagSync.reportDone(id, ok -> note("Report #" + id, ok ? "Report #" + id + " closed" : "Lazarus couldn't reach its server.", ok));
	}

	// ---- input -----------------------------------------------------------------------------------

	/**
	 * The desk's scale and place: the largest of 0.75, 1, 1.5 and 2 screen pixels per design pixel that fits the window
	 * with a margin (each has its own fonts, so text stays sharp), and below 0.75 that shrunk to fit. Returns the scale,
	 * the shrink, and the design origin in the shrunk screen's pixels.
	 */
	private float[] fit() {
		int gui = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
		float fw = width * gui, fh = height * gui;
		float f = DeskType.SCALES[0], shrink = 1f;
		for (float s : DeskType.SCALES) if (W * s + 16 <= fw && H * s + 16 <= fh) f = s;
		if (W * f + 16 > fw || H * f + 16 > fh) shrink = Math.min((fw - 16) / (W * f), (fh - 16) / (H * f));
		return new float[]{f, shrink, (fw / shrink - W * f) / 2, (fh / shrink - H * f) / 2};
	}

	/** A point in GUI units, in design pixels. */
	private float[] design(double gx, double gy) {
		int gui = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
		float[] fit = fit();
		return new float[]{(float) ((gx * gui / fit[1] - fit[2]) / fit[0]), (float) ((gy * gui / fit[1] - fit[3]) / fit[0])};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float[] d = design(event.x(), event.y());
		if (dialog != null && dialog.mousePressed(d[0], d[1])) return true;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (d[0] >= h.x && d[1] >= h.y && d[0] < h.x + h.w && d[1] < h.y + h.h) {
				h.action.run();
				return true;
			}
		}
		if (dialog == null && sheet == null && (d[0] < 0 || d[1] < 0 || d[0] >= W || d[1] >= H)) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dialog != null) dialog.mouseReleased();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double gx, double gy, double dx, double dy) {
		if (dialog != null) return true;
		if (sheet != null) {
			sheetScrollTo -= dy * 40;
			return true;
		}
		float[] d = design(gx, gy);
		if (d[0] < 340) listScrollTo -= dy * 40;
		else if (d[0] < 990) bodyScrollTo -= dy * 40;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (dialog != null) {
			dialog.keyPressed(event.key(), this::confirmDialog, () -> dialog = null);
			return true;
		}
		if (sheet != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeSheet();
			return true;
		}
		switch (event.key()) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				if (menu != null) menu = null;
				else if (!confirming.isEmpty()) confirming = "";
				else if (!search.isEmpty()) {
					search = "";
					listScrollTo = 0;
				} else return super.keyPressed(event);
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				boolean word = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
				search = word || search.isEmpty() ? "" : search.substring(0, search.length() - 1);
				listScrollTo = 0;
			}
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP -> {
				List<Roster.Row> rows = visibleRows(Roster.build(online(), state(), System.currentTimeMillis()));
				select(Roster.move(rows, selected, event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1));
				follow = true;
			}
			case GLFW.GLFW_KEY_TAB -> {
				boolean back = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
				shown = Tab.values()[(shown.ordinal() + (back ? Tab.values().length - 1 : 1)) % Tab.values().length];
				bodyScrollTo = 0;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (!search.isEmpty() && Players.NAME.matcher(search).matches()
						&& visibleRows(Roster.build(online(), state(), System.currentTimeMillis())).isEmpty()) {
					lookUp(search);
					search = "";
				}
				shown = Tab.RECORDS;
			}
			case GLFW.GLFW_KEY_F5 -> {
				if (!DashboardReader.working()) refresh();
			}
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (dialog != null) {
			dialog.charTyped(event.codepointAsString());
			return true;
		}
		if (sheet != null) return true;
		String c = event.codepointAsString();
		if (search.length() < SEARCH_MAX && c.matches("\\w")) {
			search += c;
			listScrollTo = 0;
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** No blur behind the desk: it dims the world itself, and the blur is the costliest part of a menu frame. */
	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor g) {
	}
}
