package dev.lauchy.lazarus.feature.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Ranks;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.feature.profile.Nicks;
import dev.lauchy.lazarus.feature.profile.ProfileViewer;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The staff panel for one player, opened by clicking their name in chat ({@link ChatPunish}) or by
 * {@code /lazarus staff <player>}: a report on them (rank, nick, watch note, open reports, alts and
 * punishment history, the last two asked of the server quietly) beside the actions (ban, mute, kick
 * with duration and reason chips, teleport, captcha, watch) and a View profile button that opens the
 * usual lookup. The server's reply to an action shows along the bottom.
 */
final class StaffScreen extends Screen {
	private static final int PANEL_W = 400, PANEL_H = 250, HEADER_H = 30, ACTIONS_W = 104, FOOT_H = 24;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	/** An action button. {@code duration} and {@code reason} say what the form asks before sending. */
	private enum Action {
		BAN("Ban", "ban %p %d %r", true, true, true),
		MUTE("Mute", "mute %p %d %r", true, true, true),
		KICK("Kick", "kick %p %r", false, true, true),
		TP_TO("Go to them", "tp %p", false, false, false),
		TP_HERE("Bring here", "tp %p %me", false, false, false),
		CAPTCHA("Captcha", "captcha %p", false, false, false),
		WATCH("Watch", "", false, false, false),
		PROFILE("View profile", "", false, false, false);

		final String label, command;
		final boolean duration, reason, punish;

		Action(String label, String command, boolean duration, boolean reason, boolean punish) {
			this.label = label;
			this.command = command;
			this.duration = duration;
			this.reason = reason;
			this.punish = punish;
		}
	}

	private final String player;
	private final List<String> durations, reasons;
	private final long openedAt = Util.getMillis();
	private final List<Hit> hits = new ArrayList<>();
	private final Anim.Group<Object> hover = new Anim.Group<>(14f);
	private final Anim.Smooth shownScroll = new Anim.Smooth(18f, 0f);
	private double scroll;
	private int contentH, viewH, reportX, reportY, reportW;

	/** The report, filled in as answers come. */
	private String rank = "", rankState = "asking…";
	private List<String> history, alts;
	private boolean rankAsked, historyAsked, altsAsked;

	/** The action whose punishment card is open, and the card itself. */
	private Action form;
	private final PunishCard card;

	/** What the server said to the last action, and when. */
	private String outcomeLabel = "";
	private List<String> outcome = List.of();
	private long outcomeAt;
	private boolean outcomeWaiting;

	StaffScreen(String player, List<String> durations, List<String> reasons, List<Ladder.Rule> banRules, List<Ladder.Rule> muteRules) {
		super(Component.literal("Staff"));
		this.player = player;
		this.durations = durations;
		this.reasons = reasons;
		this.card = new PunishCard(Minecraft.getInstance().font, hover, (x, y, w, h, action) -> hits.add(new Hit(x, y, w, h, action)),
				durations, reasons, banRules, muteRules);
	}

	/** Opens the panel for a player, the real one behind a nick; outside a world nothing happens. */
	static void open(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String real = Nicks.realName(name);
		StaffTools tools = StaffTools.INSTANCE;
		List<String> durations = tools == null ? List.of("1h", "1d", "7d", "perm") : tools.durations();
		List<String> reasons = tools == null ? List.of("Cheating", "Chat abuse") : tools.reasons();
		List<Ladder.Rule> bans = tools == null ? List.of() : tools.banRules(), mutes = tools == null ? List.of() : tools.muteRules();
		mc.setScreen(new StaffScreen(real != null ? real : name, durations, reasons, bans, mutes));
	}

	@Override
	protected void init() {
		super.init();
		if (rankAsked) return; // init runs again on a resize
		rankAsked = true;
		Http.json("/api/player/" + Http.encode(player) + "?mode=main").whenComplete((json, error) -> {
			if (minecraft == null) return;
			minecraft.execute(() -> {
				if (error != null || json == null || !json.isJsonObject()) {
					rankState = "unknown";
					return;
				}
				JsonElement p = json.getAsJsonObject().get("player");
				JsonObject o = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
				rank = o.has("rank") && o.get("rank").isJsonPrimitive() ? Staff.rankKey(o.get("rank").getAsString()) : "";
				rankState = rank.isEmpty() ? "none" : rank;
			});
		});
		askServer();
	}

	/** History first, then alts: the server answers one ask at a time. */
	private void askServer() {
		if (historyAsked) return;
		historyAsked = true;
		LookHistory.lookup(player, lines -> {
			history = lines;
			if (altsAsked) return;
			altsAsked = true;
			AltFlag.lookup(player, answer -> alts = answer);
		});
	}

	// ---- drawing -------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		hits.clear();
		if (form != null && !card.isOpen()) form = null; // the card closed itself (Esc or Cancel)
		float open = Anim.since(openedAt, 240);
		g.fill(0, 0, width, height, Draw.alpha(0x0B0B10, 0.45f * open));
		Draw.opacity = open;
		try {
			draw(g, mouseX, mouseY, Math.round((1f - open) * 8));
		} finally {
			Draw.opacity = 1f;
		}
	}

	private void draw(GuiGraphicsExtractor g, int mx, int my, int rise) {
		int pw = Math.min(PANEL_W, width - 16), ph = Math.min(PANEL_H, height - 16);
		int px = (width - pw) / 2, py = (height - ph) / 2 + rise;
		boolean modal = form != null;
		int fmx = modal ? -1 : mx, fmy = modal ? -1 : my;

		Draw.round(g, px, py, pw, ph, Theme.PANEL);
		header(g, px, py, pw, fmx, fmy);
		Draw.rect(g, px + 8, py + HEADER_H, pw - 16, 1, Theme.LINE);

		int top = py + HEADER_H + 6, bottom = py + ph - FOOT_H;
		reportX = px + 10;
		reportY = top;
		reportW = pw - 20 - ACTIONS_W - 8;
		viewH = bottom - top - 4;
		report(g, fmx, fmy);
		actions(g, px + pw - 10 - ACTIONS_W, top, fmx, fmy);
		footer(g, px, bottom, pw);
		if (modal) {
			hits.clear();
			card.draw(g, px, py, pw, ph, mx, my, player, command(form), this::confirm);
		}
	}

	private void header(GuiGraphicsExtractor g, int px, int py, int pw, int mx, int my) {
		String nick = Nicks.of(player);
		String title = "§l" + player + (nick == null ? "" : " §r§7nicked as " + nick);
		Draw.text(g, font, title, px + 10, py + 8, Theme.TEXT);
		int after = px + 10 + font.width(title) + 6;
		String tag = Ranks.tag(rankState);
		if (tag != null) after = chip(g, tag.substring(2), after, py + 6, 0x40000000 | rankTint(tag.charAt(1)), 0xFF000000 | rankTint(tag.charAt(1))) + 4;
		else if (!rankState.equals("asking…")) after = chip(g, rankState.equals("none") ? "no rank" : rankState, after, py + 6, Theme.FIELD, Theme.MUTED) + 4;
		boolean online = Players.online().stream().anyMatch(n -> n.equalsIgnoreCase(player));
		chip(g, online ? "online" : "offline", after, py + 6, online ? 0x335BE38A : Theme.FIELD, online ? Theme.GOOD : Theme.FAINT);
		Draw.text(g, font, "Esc", px + pw - 10 - font.width("Esc"), py + 8, Theme.FAINT);
	}

	/** The main colour of a § colour code, for a rank chip. */
	private static int rankTint(char code) {
		return switch (code) {
			case '9', '1' -> 0x5A8CFF;
			case 'c' -> 0xFF6B6B;
			case '4' -> 0xC43C3C;
			case '2', 'a' -> 0x5BE38A;
			case 'b', '3' -> 0x6CD9E8;
			default -> 0xB46CFF;
		};
	}

	private int chip(GuiGraphicsExtractor g, String text, int x, int y, int bg, int fg) {
		int w = font.width(text) + 8;
		Draw.round(g, x, y, w, 13, bg);
		Draw.text(g, font, text, x + 4, y + 3, fg);
		return x + w;
	}

	/** The report column: rows of text, scrollable, each wrapped to the column. */
	private void report(GuiGraphicsExtractor g, int mx, int my) {
		List<String> rows = new ArrayList<>();
		List<Runnable> rowActions = new ArrayList<>();
		WatchList watch = WatchList.INSTANCE;
		Reports reports = Reports.INSTANCE;
		String note = watch == null ? "" : watch.noteOf(player);
		boolean watched = watch != null && watch.watched(player);
		row(rows, rowActions, "§7Watch  §f" + (!watched ? "§8not on the list" : note.isEmpty() ? "§con the list" : "§con the list §7" + note), null);
		List<Reports.Report> open = reports == null ? List.of() : reports.openFor(player);
		row(rows, rowActions, "§7Reports  " + (open.isEmpty() ? "§8none open" : "§e" + open.size() + " open"), null);
		for (Reports.Report r : open) row(rows, rowActions, "  §8#" + r.id() + " §7by " + r.reporter() + (r.reason().isEmpty() ? "" : " §f" + r.reason()), null);

		row(rows, rowActions, "", null);
		if (alts == null) row(rows, rowActions, "§7Alts  §8" + (altsAsked ? "asking the server…" : "after the history"), null);
		else {
			List<String> names = AltFlag.names(alts, player);
			if (names.isEmpty()) row(rows, rowActions, "§7Alts  §8" + (alts.isEmpty() ? "no answer" : "none"), null);
			else {
				row(rows, rowActions, "§7Alts  §f" + names.size(), null);
				for (String a : names) row(rows, rowActions, "  §n" + a, () -> open(a));
			}
		}

		row(rows, rowActions, "", null);
		if (history == null) row(rows, rowActions, "§7History  §8asking the server…", null);
		else if (history.isEmpty()) row(rows, rowActions, "§7History  §8no answer", null);
		else {
			List<LookHistory.Entry> entries = LookHistory.parse(history);
			if (entries.isEmpty()) {
				row(rows, rowActions, "§7History  " + (LookHistory.isHistory(history) ? "§aclean record" : "§8see below"), null);
				if (!LookHistory.isHistory(history)) for (String l : history) row(rows, rowActions, "  §f" + l, null);
			} else {
				row(rows, rowActions, "§7History  §f" + LookHistory.summary(entries), null);
				for (LookHistory.Entry e : entries) row(rows, rowActions, "  " + e.row(), null);
			}
		}

		// Lay the rows out, wrapped, inside a scrolling window.
		int x = reportX, y = reportY, w = reportW;
		scroll = Math.clamp(scroll, 0, Math.max(0, contentH - viewH));
		int off = Math.round(shownScroll.to((float) scroll));
		g.enableScissor(x, y, x + w, y + viewH);
		int ry = y - off;
		int firstHit = hits.size();
		for (int i = 0; i < rows.size(); i++) {
			String row = rows.get(i);
			if (row.isEmpty()) {
				ry += 5;
				continue;
			}
			Runnable action = rowActions.get(i);
			List<String> lines = Draw.wrap(font::width, row, w);
			int h = lines.size() * 10;
			if (action != null) {
				float hv = hover.to("row" + i, Draw.inside(mx, my, x, ry - 1, w, h + 1));
				Draw.round(g, x - 2, ry - 2, w + 4, h + 3, Draw.alpha(Theme.ACCENT, 0.18f * hv));
				hits.add(new Hit(x, ry - 1, w, h + 1, action));
			}
			for (String l : lines) {
				Draw.text(g, font, l, x, ry, Theme.TEXT);
				ry += 10;
			}
		}
		contentH = ry + off - y;
		g.disableScissor();
		for (int i = hits.size() - 1; i >= firstHit; i--) {
			Hit hit = hits.get(i);
			if (hit.y() < y || hit.y() + hit.h() > y + viewH) hits.remove(i);
		}
		if (contentH > viewH) {
			int barH = Math.max(12, viewH * viewH / contentH);
			int barY = y + Math.round((viewH - barH) * (off / (float) (contentH - viewH)));
			Draw.rect(g, x + w + 2, barY, 2, barH, Theme.LINE);
		}
	}

	private static void row(List<String> rows, List<Runnable> actions, String text, Runnable action) {
		rows.add(text);
		actions.add(action);
	}

	/** The action column: punishments in red, the rest plain, View profile last. */
	private void actions(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
		Draw.rect(g, x - 5, y - 2, 1, viewH + 4, Theme.LINE);
		int by = y;
		boolean watched = WatchList.INSTANCE != null && WatchList.INSTANCE.watched(player);
		for (Action a : Action.values()) {
			if (a == Action.TP_TO || a == Action.CAPTCHA || a == Action.PROFILE) by += 4;
			String label = a == Action.WATCH ? (watched ? "Unwatch" : "Watch") : a.label;
			float h = hover.to(a, Draw.inside(mx, my, x, by, ACTIONS_W, 15));
			int base = a.punish ? 0x2AFF6B6B : a == Action.PROFILE ? Theme.ACCENT_SOFT : Theme.FIELD;
			int hot = a.punish ? 0xCCE05555 : a == Action.PROFILE ? 0xCCB46CFF : 0x30FFFFFF;
			Draw.round(g, x, by, ACTIONS_W, 15, Anim.lerpColor(base, hot, h));
			Draw.textCentered(g, font, label, x + ACTIONS_W / 2, by + 4, a.punish ? Anim.lerpColor(0xFFFF9C9C, Theme.TEXT, h) : Theme.TEXT);
			hits.add(new Hit(x, by, ACTIONS_W, 15, () -> pick(a)));
			by += 17;
		}
	}

	/** The last action's outcome along the bottom: waiting, then the server's reply, fading after a while. */
	private void footer(GuiGraphicsExtractor g, int px, int y, int pw) {
		Draw.rect(g, px + 8, y, pw - 16, 1, Theme.LINE);
		if (outcomeLabel.isEmpty()) {
			Draw.text(g, font, "§8Click a name in chat, or /lazarus staff <player>", px + 10, y + 8, Theme.FAINT);
			return;
		}
		long now = Util.getMillis();
		boolean waiting = outcomeWaiting && outcome.isEmpty() && now - outcomeAt < Reply.WAIT_MS;
		int colour = waiting ? Theme.ACCENT : outcome.isEmpty() && outcomeWaiting ? Theme.BAD : Theme.GOOD;
		float pulse = waiting ? 0.55f + 0.45f * (float) Math.sin(now / 140.0) : 1f;
		Draw.round(g, px + 10, y + 10, 4, 4, Draw.alpha(colour, pulse));
		String text = "§l" + outcomeLabel + " §r§7" + (waiting ? "waiting for the server…" : outcome.isEmpty() ? (outcomeWaiting ? "no reply from the server" : "") : String.join(" · ", outcome));
		while (font.width(text) > pw - 30 && text.length() > 4) text = text.substring(0, text.length() - 2);
		Draw.text(g, font, text, px + 18, y + 8, Theme.TEXT);
	}

	// ---- actions -------------------------------------------------------------------------

	private void pick(Action a) {
		switch (a) {
			case PROFILE -> {
				onClose();
				ProfileViewer.show(player);
			}
			case WATCH -> {
				if (WatchList.INSTANCE != null) WatchList.INSTANCE.toggle(player);
			}
			default -> {
				if (!a.duration && !a.reason) {
					run(a);
					return;
				}
				form = a;
				card.open(a.label, a.duration, a.reason, a == Action.BAN ? PunishCard.Kind.BAN : a == Action.MUTE ? PunishCard.Kind.MUTE : PunishCard.Kind.OTHER);
			}
		}
	}

	private String command(Action a) {
		String me = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
		return a.command.replace("%p", player).replace("%me", me).replace("%d", card.duration()).replace("%r", card.reason()).replaceAll(" +", " ").trim();
	}

	/** Sends the action's command and catches the server's reply for the footer. */
	private void run(Action a) {
		if (minecraft == null || minecraft.player == null) return;
		String cmd = command(a);
		outcomeLabel = a.label;
		outcome = List.of();
		outcomeAt = Util.getMillis();
		outcomeWaiting = true;
		Reply.ask(cmd, player, false, text -> true, lines -> {
			outcome = lines;
			outcomeAt = Util.getMillis();
		});
	}

	private boolean ready() {
		return form != null && card.ready();
	}

	private void confirm() {
		if (!ready()) return;
		run(form);
		form = null;
		card.close();
	}

	// ---- input ---------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int pw = Math.min(PANEL_W, width - 16), ph = Math.min(PANEL_H, height - 16);
		if (card.mousePressed((width - pw) / 2, (height - ph) / 2, pw, ph, event.x(), event.y())) return true;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Draw.inside(event.x(), event.y(), h.x, h.y, h.w, h.h)) {
				h.action.run();
				return true;
			}
		}
		// A click outside the panel closes it.
		if (!card.isOpen() && !Draw.inside(event.x(), event.y(), (width - pw) / 2, (height - ph) / 2, pw, ph)) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		card.mouseReleased();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (card.isOpen()) return true;
		scroll -= dy * 20;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!card.isOpen()) return super.keyPressed(event);
		card.keyPressed(event.key(), this::confirm);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!card.isOpen()) return super.charTyped(event);
		card.charTyped(event.codepointAsString());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
