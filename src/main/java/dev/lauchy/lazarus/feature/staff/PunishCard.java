package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The punishment dialog shared by the staff desk and the profile drawer (docs/dragsim/desk-design, "Ban dialog"):
 * a rule from the punishment ladder (which fills the reason and offers the duration for a 1st, 2nd, 3rd…
 * offence as tiles), a duration and a reason, each picked or typed, the exact command it will send, and
 * confirm. A ban has to be held. Where the owner allows it, a box makes it an IP ban or IP mute. The screen that
 * owns it keeps the action and sends the command; the dialog keeps what was answered.
 */
public final class PunishCard {
	/** Where the dialog registers what can be clicked, in the owning screen's own hit list. */
	public interface Hits {
		/** Makes the box at {@code x, y, w, h} run {@code action} when clicked. */
		void add(int x, int y, int w, int h, Runnable action);
	}

	/** Which ladder the dialog offers: ban rules, mute rules, or none (a kick, a warning). */
	public enum Kind {BAN, MUTE, OTHER}

	private static final long HOLD_MS = 700;
	private static final int DURATION = 0, REASON = 1;
	private static final int PAD = 12, HEAD_H = 32, FOOT_H = 28, CHIP_H = 13, TILE_H = 22, FIELD_H = 15;

	private final Font font;
	private final Anim.Group<Object> hover;
	private final Hits hits;
	private final List<String> durations, reasons;
	private final List<Ladder.Rule> banRules, muteRules;

	private boolean open;
	private String label = "", subtitle = "";
	private boolean duration, reason, ipOffered, ip;
	private Kind kind = Kind.OTHER;
	private List<Ladder.Rule> rules = List.of();
	private final String[] values = {"", ""};
	private int focus;
	private Ladder.Rule rule;
	private long openedAt, holdAt;
	private boolean holding;
	/** The full rule under the mouse this frame, shown as a tooltip. */
	private String tip;

	public PunishCard(Font font, Anim.Group<Object> hover, Hits hits, List<String> durations, List<String> reasons,
			List<Ladder.Rule> banRules, List<Ladder.Rule> muteRules) {
		this.font = font;
		this.hover = hover;
		this.hits = hits;
		this.durations = durations;
		this.reasons = reasons;
		this.banRules = banRules;
		this.muteRules = muteRules;
	}

	/** Opens the dialog for an action: its label, whether it wants a duration and a reason, and which ladder applies. */
	public void open(String label, boolean duration, boolean reason, Kind kind) {
		open(label, duration, reason, kind, "", false, false);
	}

	/**
	 * As above, with a line under the title ("Network-wide. Their 1 open report closes with it."), and whether the
	 * dialog offers to make it an IP ban or mute ({@code ipOffered}), ticked to start when {@code ip}.
	 */
	public void open(String label, boolean duration, boolean reason, Kind kind, String subtitle, boolean ipOffered, boolean ip) {
		this.open = true;
		this.label = label;
		this.subtitle = subtitle;
		this.duration = duration;
		this.reason = reason;
		this.kind = kind;
		this.ipOffered = ipOffered;
		this.ip = ip;
		this.rules = kind == Kind.BAN ? banRules : kind == Kind.MUTE ? muteRules : List.of();
		values[DURATION] = "";
		values[REASON] = "";
		rule = null;
		focus = duration ? DURATION : REASON;
		openedAt = Util.getMillis();
		holding = false;
	}

	/** Closes the dialog without sending anything. */
	public void close() {
		open = false;
		holding = false;
	}

	public boolean isOpen() {
		return open;
	}

	public String duration() {
		return values[DURATION].trim();
	}

	public String reason() {
		return values[REASON].trim();
	}

	/** Whether the IP box is ticked (only ever when it was offered). */
	public boolean ip() {
		return ipOffered && ip;
	}

	/** Whether every field the action wants has an answer. */
	public boolean ready() {
		return open && (!duration || !values[DURATION].isBlank()) && (!reason || !values[REASON].isBlank());
	}

	/** Whether confirming means holding the button (a ban) rather than a click or Enter. */
	public boolean held() {
		return kind == Kind.BAN;
	}

	// ---- layout --------------------------------------------------------------------------

	/** One chip or tile: what it shows, what picking it puts in the field, a second line (the offence), and a tooltip. */
	private record Chip(String label, String value, String sub, String tip) {
	}

	private List<Chip> ruleChips() {
		List<Chip> out = new ArrayList<>();
		for (Ladder.Rule r : rules) out.add(new Chip(r.chip(), r.code(), "", Ladder.describe(r)));
		return out;
	}

	/** The duration tiles: the picked rule's offence steps ("7d" over "1st"), else the usual durations. */
	private List<Chip> durationChips() {
		List<Chip> out = new ArrayList<>();
		if (rule != null && !rule.set()) {
			List<String> steps = rule.steps();
			for (int i = 0; i < steps.size(); i++) out.add(new Chip(steps.get(i), steps.get(i), steps.size() == 1 ? "" : Ladder.ordinal(i + 1), null));
			return out;
		}
		for (String d : durations) out.add(new Chip(d, d, "", null));
		return out;
	}

	/** The reason chips; with a ladder the rule chips stand in for them. */
	private List<Chip> reasonChips() {
		List<Chip> out = new ArrayList<>();
		if (!rules.isEmpty()) return out;
		for (String r : reasons) out.add(new Chip(r, r, "", null));
		return out;
	}

	/** How many rows chips of these labels take across {@code w}. */
	private int rows(List<Chip> chips, int w) {
		int rows = 1, cx = 0;
		for (Chip c : chips) {
			int cw = DeskUi.width(font, c.label) + 12;
			if (cx > 0 && cx + cw > w) {
				cx = 0;
				rows++;
			}
			cx += cw + 4;
		}
		return chips.isEmpty() ? 0 : rows;
	}

	private int width(int pw) {
		return Math.min(310, pw - 24);
	}

	private int height(int w) {
		int inner = w - PAD * 2, h = HEAD_H + 8;
		if (!rules.isEmpty()) h += 12 + rows(ruleChips(), inner) * (CHIP_H + 3) + 6;
		if (duration) h += 12 + TILE_H + 8;
		if (reason) h += 12 + FIELD_H + (reasonChips().isEmpty() ? 0 : 3 + rows(reasonChips(), inner) * (CHIP_H + 3)) + 6;
		return h + 18 + 6 + FOOT_H;
	}

	/** The confirm button: x, y, w, h, laid out as {@link #draw} lays it. */
	private int[] confirmBox(int px, int py, int pw, int ph) {
		int w = width(pw), h = height(w), x = px + (pw - w) / 2, y = py + (ph - h) / 2;
		String text = confirmText(false, 0f);
		int bw = Math.max(70, DeskUi.width(font, text) + 20);
		return new int[]{x + w - PAD - bw, y + h - FOOT_H + 7, bw, DeskUi.BTN_H};
	}

	/** Whether a point is on the confirm button. */
	public boolean insideConfirm(int px, int py, int pw, int ph, double mx, double my) {
		if (!open) return false;
		int[] b = confirmBox(px, py, pw, ph);
		return Draw.inside(mx, my, b[0], b[1], b[2], b[3]);
	}

	private String confirmText(boolean ready, float held) {
		String what = (ip() ? "IP " : "") + label.toLowerCase(Locale.ROOT).replace("ip ", "");
		if (held() && held > 0f) return "Keep holding…";
		return held() ? "Hold to " + what : Character.toUpperCase(what.charAt(0)) + what.substring(1);
	}

	// ---- drawing -------------------------------------------------------------------------

	/**
	 * Draws the dialog over the panel at {@code px, py, pw, ph}. {@code preview} is the command as it would be
	 * sent; {@code confirm} runs when it's confirmed (a held ban finishing counts).
	 */
	public void draw(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my, String player, String preview, Runnable confirm) {
		if (!open) return;
		float in = Anim.since(openedAt, 180);
		// While the dialog is up, only its own controls can be clicked; a miss lands on the backdrop.
		hits.add(px, py, pw, ph, () -> {
		});
		Draw.round(g, px, py, pw, ph, Draw.alpha(0xFF060608, 0.7f * in));
		float before = Draw.opacity;
		Draw.opacity *= in;
		int w = width(pw), h = height(w), x = px + (pw - w) / 2;
		int y = py + (ph - h) / 2 + Math.round((1f - in) * 6);
		boolean danger = kind != Kind.OTHER;
		DeskUi.card(g, x, y, w, h, 0xFF111114, danger ? 0xFF3F1D1D : DeskUi.BORDER);
		tip = null;

		// Header: an icon, the title and what it does, and a close button.
		Draw.round(g, x + PAD, y + 8, 18, 18, danger ? 0xFF3A1414 : DeskUi.FIELD);
		DeskUi.textCentered(g, font, "§l!", x + PAD + 9, y + 13, danger ? DeskUi.RED_TEXT : DeskUi.MUTED);
		DeskUi.text(g, font, "§l" + (ip() ? "IP " + label.toLowerCase(Locale.ROOT).replace("ip ", "") : label) + " §r§f" + player, x + PAD + 25, y + 8, DeskUi.TEXT);
		DeskUi.text(g, font, DeskUi.fit(font, subtitle.isEmpty() ? "Esc to cancel" : subtitle, w - PAD * 2 - 45), x + PAD + 25, y + 19, DeskUi.MUTED);
		int cx = x + w - PAD - 13;
		float hx = hover.to("dlg-close", Draw.inside(mx, my, cx, y + 8, 13, 13));
		Draw.round(g, cx, y + 8, 13, 13, Anim.lerpColor(DeskUi.CARD, DeskUi.FIELD, hx));
		Draw.outline(g, cx, y + 8, 13, 13, DeskUi.BORDER);
		DeskUi.textCentered(g, font, "×", cx + 7, y + 10, DeskUi.MUTED);
		hits.add(cx, y + 8, 13, 13, this::close);
		Draw.rect(g, x + 1, y + HEAD_H, w - 2, 1, DeskUi.BORDER);

		int bx = x + PAD, bw = w - PAD * 2, by = y + HEAD_H + 8;
		if (!rules.isEmpty()) by = ruleChipsRow(g, bx, by, bw, mx, my);
		if (duration) by = tiles(g, bx, by, bw, mx, my);
		if (reason) by = reasonField(g, bx, by, bw, mx, my);

		// The exact command, in an inset box.
		Draw.round(g, bx, by, bw, 16, DeskUi.INSET);
		Draw.outline(g, bx, by, bw, 16, DeskUi.BORDER_SOFT);
		DeskUi.text(g, font, DeskUi.fit(font, "/" + preview, bw - 10), bx + 5, by + 4, DeskUi.MUTED);

		// Footer: the IP box, Cancel, and the confirm (held for a ban).
		int fy = y + h - FOOT_H;
		Draw.rect(g, x + 1, fy, w - 2, 1, DeskUi.BORDER);
		if (ipOffered) {
			String what = kind == Kind.BAN ? "Ban their IP too" : "Mute their IP too";
			Draw.round(g, bx, fy + 9, 9, 9, ip ? DeskUi.RED : DeskUi.INSET);
			Draw.outline(g, bx, fy + 9, 9, 9, ip ? DeskUi.RED_HOT : DeskUi.BORDER);
			if (ip) DeskUi.textCentered(g, font, "✔", bx + 5, fy + 10, 0xFFFFFFFF);
			DeskUi.text(g, font, what, bx + 13, fy + 10, DeskUi.MUTED);
			hits.add(bx, fy + 7, DeskUi.width(font, what) + 14, 13, () -> ip = !ip);
		}
		boolean ready = ready(), hold = held();
		float held = holding && ready ? Math.min(1f, (Util.getMillis() - holdAt) / (float) HOLD_MS) : 0f;
		if (held >= 1f) {
			holding = false;
			Draw.opacity = before;
			confirm.run();
			return;
		}
		int[] c = confirmBox(px, py, pw, ph);
		c[1] = fy + 7;
		float hc = hover.to("dlg-confirm", ready && Draw.inside(mx, my, c[0], c[1], c[2], c[3]));
		DeskUi.button(g, font, c[0], c[1], c[2], confirmText(ready, held), "", danger ? DeskUi.Kind.DANGER : DeskUi.Kind.ACCENT, hc, ready);
		if (held > 0f) {
			Draw.round(g, c[0], c[1], Math.max(3, Math.round(c[2] * held)), c[3], Draw.alpha(0xFFEF4444, 0.55f));
			DeskUi.textCentered(g, font, confirmText(ready, held), c[0] + c[2] / 2, c[1] + 3, 0xFFFFFFFF);
		}
		if (!hold) hits.add(c[0], c[1], c[2], c[3], () -> {
			if (ready()) confirm.run();
		});
		int cw = DeskUi.width(font, "Cancel") + 16, cancelX = c[0] - 6 - cw;
		float hn = hover.to("dlg-cancel", Draw.inside(mx, my, cancelX, c[1], cw, c[3]));
		DeskUi.button(g, font, cancelX, c[1], cw, "Cancel", "", DeskUi.Kind.PLAIN, hn, true);
		hits.add(cancelX, c[1], cw, c[3], this::close);
		Draw.opacity = before;
		if (tip != null && Draw.opacity >= 1f) {
			Vector2f at = g.pose().transformPosition(mx, my, new Vector2f()); // the desk may draw this scaled down
			g.setTooltipForNextFrame(font, DeskFont.forTooltip(tip), Math.round(at.x), Math.round(at.y));
		}
	}

	/** The rule chips: picking one fills the reason and offers its durations; the picked one is ringed red. */
	private int ruleChipsRow(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		DeskUi.text(g, font, "§lRule", x, y, DeskUi.SUB);
		if (rule != null) {
			String full = DeskUi.fit(font, rule.reason(), w - 40);
			DeskUi.text(g, font, full, x + w - DeskUi.width(font, full), y, DeskUi.MUTED);
		}
		int cx = x, cy = y + 12;
		for (Chip c : ruleChips()) {
			int cw = DeskUi.width(font, c.label) + 12;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				cy += CHIP_H + 3;
			}
			boolean on = rule != null && rule.code().equals(c.value), over = Draw.inside(mx, my, cx, cy, cw, CHIP_H);
			if (over) tip = c.tip;
			float h = hover.to("rule" + c.value, over);
			chip(g, c.label, cx, cy, cw, on, h);
			hits.add(cx, cy, cw, CHIP_H, () -> pickRule(on ? null : Ladder.find(rules, c.value)));
			cx += cw + 4;
		}
		return cy + CHIP_H + 9;
	}

	/** A chip: ringed red (or purple for a warning or kick) when picked. */
	private void chip(GuiGraphicsExtractor g, String text, int x, int y, int w, boolean on, float h) {
		boolean danger = kind != Kind.OTHER;
		int fill = on ? (danger ? 0xFF2A1012 : DeskUi.VIOLET_BG) : Anim.lerpColor(DeskUi.FIELD, 0xFF232328, h);
		int edge = on ? (danger ? DeskUi.RED : DeskUi.VIOLET_EDGE) : DeskUi.BORDER;
		Draw.round(g, x, y, w, CHIP_H, fill);
		Draw.outline(g, x, y, w, CHIP_H, edge);
		DeskUi.text(g, font, text, x + 6, y + 3, on ? (danger ? DeskUi.RED_SOFT : DeskUi.VIOLET_TEXT) : DeskUi.SUB);
	}

	/** Picking a rule fills the reason and, for a single fixed duration, the duration; clearing it empties what it filled. */
	private void pickRule(Ladder.Rule r) {
		if (rule != null) {
			if (values[REASON].equals(rule.reason())) values[REASON] = "";
			if (!rule.set() && rule.steps().contains(values[DURATION])) values[DURATION] = "";
		}
		rule = r;
		if (r == null) return;
		values[REASON] = r.reason();
		if (!r.set() && r.steps().size() == 1) values[DURATION] = r.steps().get(0);
		focus = duration && values[DURATION].isEmpty() ? DURATION : REASON;
	}

	/** The duration: tiles across the width (the offence under each when a rule is picked), typing goes in too. */
	private int tiles(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		DeskUi.text(g, font, "§lDuration", x, y, DeskUi.SUB);
		String typed = focus == DURATION ? (values[DURATION].isEmpty() ? "§8type one, or pick" : "§f" + values[DURATION] + (Util.getMillis() / 500 % 2 == 0 ? "_" : "")) : "";
		if (!typed.isEmpty()) DeskUi.text(g, font, typed, x + w - DeskUi.width(font, typed), y, DeskUi.MUTED);
		else if (rule != null && !rule.set() && rule.steps().size() > 1) {
			String note = "offence under " + rule.code();
			DeskUi.text(g, font, note, x + w - DeskUi.width(font, note), y, DeskUi.FAINT);
		}
		hits.add(x, y - 1, w, 10, () -> focus = DURATION);
		List<Chip> tiles = durationChips();
		int n = Math.max(1, tiles.size()), gap = 4, tw = (w - gap * (n - 1)) / n, ty = y + 12;
		for (int i = 0; i < tiles.size(); i++) {
			Chip c = tiles.get(i);
			int tx = x + i * (tw + gap);
			boolean on = c.value.equalsIgnoreCase(values[DURATION].trim());
			float h = hover.to("tile" + c.value + i, Draw.inside(mx, my, tx, ty, tw, TILE_H));
			boolean danger = kind != Kind.OTHER;
			Draw.round(g, tx, ty, tw, TILE_H, on ? (danger ? 0xFF2A1012 : DeskUi.VIOLET_BG) : Anim.lerpColor(DeskUi.FIELD, 0xFF232328, h));
			Draw.outline(g, tx, ty, tw, TILE_H, on ? (danger ? DeskUi.RED : DeskUi.VIOLET_EDGE) : DeskUi.BORDER);
			int text = on ? (danger ? DeskUi.RED_SOFT : DeskUi.VIOLET_TEXT) : DeskUi.TEXT;
			if (c.sub.isEmpty()) DeskUi.textCentered(g, font, "§l" + c.label, tx + tw / 2, ty + 7, text);
			else {
				DeskUi.textCentered(g, font, "§l" + c.label, tx + tw / 2, ty + 3, text);
				DeskUi.textCentered(g, font, c.sub, tx + tw / 2, ty + 12, on ? DeskUi.RED_TEXT : DeskUi.FAINT);
			}
			hits.add(tx, ty, tw, TILE_H, () -> {
				values[DURATION] = c.value;
				if (reason) focus = REASON;
			});
		}
		return ty + TILE_H + 8;
	}

	/** The reason: a text field (focused with a purple edge), with chips under it when there's no ladder. */
	private int reasonField(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		DeskUi.text(g, font, "§lReason", x, y, DeskUi.SUB);
		int fy = y + 12;
		boolean focused = focus == REASON;
		Draw.round(g, x, fy, w, FIELD_H, DeskUi.INSET);
		Draw.outline(g, x, fy, w, FIELD_H, focused ? DeskUi.VIOLET_EDGE : DeskUi.BORDER);
		boolean caret = focused && Util.getMillis() / 500 % 2 == 0;
		String v = values[REASON] + (caret ? "_" : "");
		if (values[REASON].isEmpty() && !focused) v = "§8type a reason, or pick a rule";
		while (DeskUi.width(font, v) > w - 10 && v.length() > 1) v = v.substring(1);
		DeskUi.text(g, font, v, x + 5, fy + 4, DeskUi.TEXT);
		hits.add(x, fy, w, FIELD_H, () -> focus = REASON);
		int cy = fy + FIELD_H + 3, cx = x;
		List<Chip> chips = reasonChips();
		for (Chip c : chips) {
			int cw = DeskUi.width(font, c.label) + 12;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				cy += CHIP_H + 3;
			}
			boolean on = c.value.equalsIgnoreCase(values[REASON].trim());
			float h = hover.to("reason" + c.label, Draw.inside(mx, my, cx, cy, cw, CHIP_H));
			chip(g, c.label, cx, cy, cw, on, h);
			hits.add(cx, cy, cw, CHIP_H, () -> values[REASON] = c.value);
			cx += cw + 4;
		}
		return (chips.isEmpty() ? fy + FIELD_H : cy + CHIP_H) + 8;
	}

	// ---- input ---------------------------------------------------------------------------

	/** A press on the confirm button of a held action starts the hold; true when it did. */
	public boolean mousePressed(int px, int py, int pw, int ph, double mx, double my) {
		if (!open || !held() || !ready() || !insideConfirm(px, py, pw, ph, mx, my)) return false;
		holding = true;
		holdAt = Util.getMillis();
		return true;
	}

	/** Letting go of the mouse stops a ban's hold. */
	public void mouseReleased() {
		holding = false;
	}

	/** Handles a key while the dialog is open: Esc closes, Enter confirms (never a ban), Tab moves on, Backspace edits. */
	public void keyPressed(int key, Runnable confirm) {
		switch (key) {
			case 256 -> close();
			case 257, 335 -> {
				if (!held() && ready()) confirm.run();
			}
			case 258 -> {
				if (duration && reason) focus = 1 - focus;
			}
			case 259 -> {
				if (!values[focus].isEmpty()) values[focus] = values[focus].substring(0, values[focus].length() - 1);
			}
			default -> {
			}
		}
	}

	/** Types into the focused field; a space in the duration moves on, since durations are one word. */
	public void charTyped(String typed) {
		if (focus == DURATION && typed.equals(" ")) focus = reason ? REASON : DURATION;
		else if (values[focus].length() < 120 && !typed.equals("§")) values[focus] += typed;
	}
}
