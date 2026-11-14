package dev.lauchy.lazarus.feature.staff;

import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import static dev.lauchy.lazarus.feature.staff.DeskColours.*;

/**
 * The staff desk's punishment dialog (docs/dragsim/desk-design: Ban, Mute, Warn and kick): for a ban or mute the rule
 * (from the punishment ladder), a duration (that rule's step for this player's next offence picked for them, or typed
 * under Other) and the reason; for a warning or kick a reason, typed or picked. The exact command shows before it's
 * sent. A ban is held to confirm; the rest are a click or Enter.
 */
final class DeskDialog {
	enum Kind {BAN, MUTE, WARN, KICK}

	/** What the desk lends the dialog: hit areas and hover. */
	interface Host {
		void hit(float x, float y, float w, float h, Runnable action);

		float hover(Object key, float x, float y, float w, float h);

		/** A tooltip while the mouse is over the box. */
		void tip(float x, float y, float w, float h, String text);
	}

	private static final int W = 520, PAD = 20;
	private static final long HOLD_MS = 900;
	private static final int DURATION = 0, REASON = 1;

	final Kind kind;
	final String player;
	private final String subtitle;
	private final List<Ladder.Rule> rules;
	private final List<String> durations, reasons;
	/** How many of this player's past punishments of this kind were under a rule ("1.0") — their offences so far. */
	private final ToIntFunction<String> offences;
	/** Their warnings so far, for "Their 2nd warning". */
	private final int warnings;
	private final long openedAt = Util.getMillis();

	private final String[] values = {"", ""};
	private int focus;
	private Ladder.Rule rule;
	private boolean ip, otherDuration, holding;
	private long holdAt;
	private float[] confirmBox = new float[4];

	DeskDialog(Kind kind, String player, boolean ip, String subtitle, List<Ladder.Rule> rules, List<String> durations, List<String> reasons,
			ToIntFunction<String> offences, int warnings) {
		this.kind = kind;
		this.player = player;
		this.ip = ip;
		this.subtitle = subtitle;
		this.rules = kind == Kind.BAN || kind == Kind.MUTE ? rules : List.of();
		this.durations = durations;
		this.reasons = reasons;
		this.offences = offences;
		this.warnings = warnings;
		focus = timed() ? DURATION : REASON;
	}

	private boolean timed() {
		return kind == Kind.BAN || kind == Kind.MUTE;
	}

	/** The action this sends, with the IP box. */
	StaffActions action() {
		return switch (kind) {
			case BAN -> ip ? StaffActions.IP_BAN : StaffActions.BAN;
			case MUTE -> ip ? StaffActions.IP_MUTE : StaffActions.MUTE;
			case WARN -> StaffActions.WARN;
			case KICK -> StaffActions.KICK;
		};
	}

	String duration() {
		return values[DURATION].trim();
	}

	String reason() {
		return values[REASON].trim();
	}

	/** Whether everything it needs is filled in. */
	boolean ready() {
		return (!timed() || !duration().isEmpty()) && !reason().isEmpty();
	}

	/** The command as it will be sent, without its slash. */
	String command(String me) {
		return StaffActions.fill(action(), player, me, duration(), reason(), "");
	}

	private String verb() {
		return switch (kind) {
			case BAN -> ip ? "IP ban" : "Ban";
			case MUTE -> ip ? "IP mute" : "Mute";
			case WARN -> "Warn";
			case KICK -> "Kick";
		};
	}

	// ---- layout --------------------------------------------------------------------------------

	private record Tile(String label, String sub, String value, boolean other) {
	}

	private List<Tile> tiles() {
		List<Tile> out = new ArrayList<>();
		if (rule != null && !rule.set()) {
			List<String> steps = rule.steps();
			for (int i = 0; i < steps.size(); i++) out.add(new Tile(steps.get(i), steps.size() == 1 ? "" : Ladder.ordinal(i + 1), steps.get(i), false));
		} else {
			for (String d : durations) out.add(new Tile(d, "", d, false));
		}
		out.add(new Tile(otherDuration && !duration().isEmpty() ? duration() : "Other", "type it", "", true));
		return out;
	}

	/** Chips laid out in rows across {@code w}: each chip's x offset and row. */
	private static List<float[]> flow(DeskPaint p, List<String> labels, DeskType type, float pad, float w, float gap) {
		List<float[]> out = new ArrayList<>();
		float x = 0;
		int row = 0;
		for (String l : labels) {
			float cw = p.width(l, type) + pad * 2 + 2;
			if (x > 0 && x + cw > w) {
				x = 0;
				row++;
			}
			out.add(new float[]{x, row, cw});
			x += cw + gap;
		}
		return out;
	}

	private int offenceNumber() {
		return rule == null ? 0 : offences.applyAsInt(rule.code()) + 1;
	}

	/** Picking a rule fills the reason and picks the duration for their next offence under it; picking it again clears both. */
	private void pickRule(Ladder.Rule r) {
		if (rule != null) {
			if (values[REASON].equals(rule.reason())) values[REASON] = "";
			if (!rule.set() && rule.steps().contains(values[DURATION])) values[DURATION] = "";
		}
		rule = r;
		otherDuration = false;
		if (r == null) return;
		values[REASON] = r.reason();
		if (!r.set()) {
			int step = Math.min(offenceNumber(), r.steps().size()) - 1;
			values[DURATION] = r.steps().get(Math.max(0, step));
		}
		focus = timed() && values[DURATION].isEmpty() ? DURATION : REASON;
	}

	/** Picks the rule with this code, as a click on its chip would. */
	void pickRuleByCode(String code) {
		Ladder.Rule r = Ladder.find(rules, code);
		if (r != null) pickRule(r);
	}

	// ---- drawing -------------------------------------------------------------------------------

	/** Draws the dialog centred on the desk; {@code confirm} runs when it's confirmed (a finished hold counts). */
	void draw(DeskPaint p, float mx, float my, Host host, String me, Runnable confirm, Runnable close) {
		float in = Math.min(1f, (Util.getMillis() - openedAt) / 160f);
		float iw = W - 2 - PAD * 2;
		List<String> ruleLabels = rules.stream().map(Ladder.Rule::chip).toList();
		List<float[]> ruleFlow = flow(p, ruleLabels, DeskType.I500_12, 10, iw, 6);
		List<String> reasonLabels = timed() && !rules.isEmpty() ? List.of() : reasons;
		List<float[]> reasonFlow = flow(p, reasonLabels, DeskType.I500_12, 10, iw, 6);
		int ruleRows = ruleFlow.isEmpty() ? 0 : (int) ruleFlow.getLast()[1] + 1, reasonRows = reasonFlow.isEmpty() ? 0 : (int) reasonFlow.getLast()[1] + 1;

		float bodyH = 0;
		if (ruleRows > 0) bodyH += 15 + 8 + ruleRows * 30 + (ruleRows - 1) * 6 + 16;
		if (timed()) bodyH += 15 + 8 + 48 + 16;
		bodyH += 15 + 8 + 34 + 16;
		if (reasonRows > 0) bodyH += reasonRows * 28 + (reasonRows - 1) * 6 + 16;
		bodyH += 38;
		float h = 72 + 32 + bodyH + 65;
		float x = 380, y = Math.round((760 - h) / 2 + (1f - in) * 6);

		host.hit(0, 0, 1280, 760, () -> {
		}); // a miss lands on the backdrop, not the desk under it
		p.rect(0, 0, 1280, 760, 0xB3050507);
		float before = p.alpha;
		p.alpha *= in;
		boolean danger = kind == Kind.BAN;
		p.shadow(x, y, W, h, 14, 24, 64, 0.6f);
		p.box(x, y, W, h, 14, CARD, danger ? 0xFF3F1D1D : BORDER);

		// Header: an icon, what it does, and close.
		float hx = x + 1 + PAD, hy = y + 1 + 18;
		switch (kind) {
			case BAN -> {
				p.round(hx, hy, 36, 36, 9, RED_CHIP);
				p.icon(DeskIcons.BAN, hx + 9, hy + 9, 18, RED_TEXT);
			}
			case MUTE -> {
				p.box(hx, hy, 36, 36, 9, RED_BG, RED_EDGE);
				p.icon(DeskIcons.MIC_OFF, hx + 9, hy + 9, 18, RED_TEXT);
			}
			case WARN -> {
				p.round(hx, hy, 36, 36, 9, YELLOW_BG);
				p.icon(DeskIcons.WARNING, hx + 9, hy + 9, 18, YELLOW);
			}
			case KICK -> {
				p.box(hx, hy, 36, 36, 9, FIELD, BORDER);
				p.icon(DeskIcons.KICK, hx + 9, hy + 9, 18, SUB);
			}
		}
		float tx = hx + 36 + 12;
		p.text(verb() + " " + player, tx, hy, DeskType.I600_16, TEXT);
		p.text(p.fit(subtitle, DeskType.I400_12, W - 2 - PAD * 2 - 48 - 40), tx, hy + 23, DeskType.I400_12, MUTED);
		float cx = x + W - 1 - PAD - 28;
		float hc = host.hover("dlg-close", cx, hy, 28, 28);
		p.box(cx, hy, 28, 28, 7, mix(0x00000000, FIELD, hc), BORDER);
		p.icon(DeskIcons.CLOSE, cx + 7, hy + 7, 14, MUTED);
		host.hit(cx, hy, 28, 28, close);
		p.rect(x + 1, y + 71, W - 2, 1, BORDER);

		// Body.
		float bx = x + 1 + PAD, by = y + 72 + 16;
		if (ruleRows > 0) {
			p.text("Rule", bx, by, DeskType.I600_12, SUB2);
			by += 23;
			for (int i = 0; i < rules.size(); i++) {
				Ladder.Rule r = rules.get(i);
				float[] f = ruleFlow.get(i);
				float rx = bx + f[0], ry = by + f[1] * 36;
				boolean on = rule != null && rule.code().equals(r.code());
				float hv = host.hover("rule" + r.code(), rx, ry, f[2], 30);
				chip(p, ruleLabels.get(i), rx, ry, f[2], 30, on, hv);
				host.hit(rx, ry, f[2], 30, () -> pickRule(on ? null : r));
				host.tip(rx, ry, f[2], 30, Ladder.describe(r));
			}
			by += ruleRows * 30 + (ruleRows - 1) * 6 + 16;
		}
		if (timed()) {
			p.text("Duration", bx, by, DeskType.I600_12, SUB2);
			String note = rule == null ? (focus == DURATION ? "type one, or pick" : "")
					: rule.set() ? "set the time under " + rule.code() : "Their " + Ladder.ordinal(offenceNumber()) + " offence under " + rule.code();
			boolean repeat = rule != null && !rule.set() && offenceNumber() > 1;
			p.text(note, bx + iw - p.width(note, DeskType.I400_11), by + 1, DeskType.I400_11, repeat ? RED_TEXT : MUTED);
			by += 23;
			List<Tile> tiles = tiles();
			// A typed duration that's one of the tiles picks that tile.
			boolean typedTile = otherDuration && tiles.stream().anyMatch(t -> !t.other && t.value.equalsIgnoreCase(duration()));
			int n = tiles.size();
			float gap = 6, tw = (iw - gap * (n - 1)) / n;
			for (int i = 0; i < n; i++) {
				Tile t = tiles.get(i);
				float tx0 = bx + i * (tw + gap);
				boolean on = t.other ? otherDuration && !typedTile : (!otherDuration || typedTile) && t.value.equalsIgnoreCase(duration());
				float hv = host.hover("tile" + i + t.label, tx0, by, tw, 48);
				if (t.other && !on) {
					if (hv > 0) p.round(tx0, by, tw, 48, 8, mix(0x00000000, 0x0AFFFFFF, hv));
					p.dashed(tx0, by, tw, 48, 8, DIM);
				} else {
					p.box(tx0, by, tw, 48, 8, on ? RED_PICK : mix(FIELD, 0xFF232328, hv), on ? RED : BORDER);
				}
				int label = on ? RED_SOFT : t.other ? MUTED : SUB;
				String shown = t.other && typedTile ? "Other" : t.other && on && focus == DURATION ? (duration().isEmpty() ? "" : duration()) + (Util.getMillis() / 500 % 2 == 0 ? "|" : "") : t.label;
				float lw = p.width(shown, DeskType.I600_14), sw = p.width(t.sub, DeskType.I400_10);
				if (t.sub.isEmpty() || (t.other && on && !typedTile)) p.text(shown, tx0 + (tw - lw) / 2, by + (48 - 17) / 2f, DeskType.I600_14, label);
				else {
					float top = by + (48 - 17 - 2 - 12) / 2f;
					p.text(shown, tx0 + (tw - lw) / 2, top, DeskType.I600_14, label);
					p.text(t.sub, tx0 + (tw - sw) / 2, top + 19, DeskType.I400_10, on ? RED_TEXT : FAINT);
				}
				host.hit(tx0, by, tw, 48, () -> {
					if (t.other) {
						otherDuration = true;
						values[DURATION] = "";
						focus = DURATION;
					} else {
						otherDuration = false;
						values[DURATION] = t.value;
						focus = REASON;
					}
				});
			}
			by += 48 + 16;
		}
		p.text("Reason", bx, by, DeskType.I600_12, SUB2);
		by += 23;
		boolean focused = focus == REASON;
		p.box(bx, by, iw, 34, 8, INSET, focused ? VIOLET_EDGE : BORDER);
		boolean caret = focused && Util.getMillis() / 500 % 2 == 0;
		if (values[REASON].isEmpty()) {
			p.text(focused ? (caret ? "|" : "") : "Type a reason, or pick " + (rules.isEmpty() || !timed() ? "one" : "a rule"), bx + 11, by + 9, DeskType.I400_13, focused ? TEXT : FAINT);
		} else {
			String v = values[REASON] + (caret ? "|" : "");
			while (p.width(v, DeskType.I400_13) > iw - 22 && v.length() > 1) v = v.substring(1);
			p.text(v, bx + 11, by + 9, DeskType.I400_13, TEXT);
		}
		host.hit(bx, by, iw, 34, () -> focus = REASON);
		by += 34 + 16;
		if (reasonRows > 0) {
			for (int i = 0; i < reasonLabels.size(); i++) {
				float[] f = reasonFlow.get(i);
				float rx = bx + f[0], ry = by + f[1] * 34;
				String r = reasonLabels.get(i);
				boolean on = r.equalsIgnoreCase(reason());
				float hv = host.hover("reason" + r, rx, ry, f[2], 28);
				chip(p, r, rx, ry, f[2], 28, on, hv);
				host.hit(rx, ry, f[2], 28, () -> values[REASON] = r);
			}
			by += reasonRows * 28 + (reasonRows - 1) * 6 + 16;
		}
		p.box(bx, by, iw, 38, 8, INSET, SOFT);
		String preview = "/" + command(me) + (ready() ? "" : " …");
		p.text(p.fit(preview, DeskType.M500_12, iw - 26), bx + 13, by + 11, DeskType.M500_12, ready() ? MUTED : FAINT);

		// Footer: the IP box (or a note), Cancel, and the confirm.
		float fy = y + h - 65;
		p.rect(x + 1, fy, W - 2, 1, BORDER);
		float rowY = fy + 1 + 14;
		if (timed()) {
			String what = kind == Kind.BAN ? "Ban their IP too" : "Mute their IP too";
			float cbY = rowY + 11.5f;
			p.box(bx, cbY, 13, 13, 3, ip ? RED : INSET, ip ? RED : 0xFF52525B);
			if (ip) p.icon(DeskIcons.CHECK, bx + 1.5f, cbY + 1.5f, 10, 0xFFFFFFFF);
			p.text(what, bx + 21, rowY + 10.5f, DeskType.I400_12, MUTED);
			host.hit(bx, rowY + 6, 21 + p.width(what, DeskType.I400_12), 24, () -> ip = !ip);
		} else {
			String note = kind == Kind.WARN ? "Their " + Ladder.ordinal(warnings + 1) + " warning" : ready() ? "Disconnects them now" : "A reason is needed";
			p.text(note, bx, rowY + 10.5f, DeskType.I400_12, MUTED);
		}
		String go = kind == Kind.BAN ? (holding && ready() ? "Keep holding…" : "Hold to " + verb().toLowerCase(Locale.ROOT))
				: kind == Kind.MUTE && !duration().isEmpty() ? verb() + " for " + duration() : verb();
		float gw = p.width(go, DeskType.I600_13) + 32, gx = x + W - 1 - PAD - gw;
		float cw = p.width("Cancel", DeskType.I500_13) + 30, cancelX = gx - 8 - cw;
		float hcl = host.hover("dlg-cancel", cancelX, rowY, cw, 36);
		p.box(cancelX, rowY, cw, 36, 8, mix(0x00000000, FIELD, hcl), BORDER);
		p.text("Cancel", cancelX + 15, rowY + 10, DeskType.I500_13, TEXT);
		host.hit(cancelX, rowY, cw, 36, close);

		boolean ready = ready();
		float held = holding && ready ? Math.min(1f, (Util.getMillis() - holdAt) / (float) HOLD_MS) : 0f;
		int fill = !ready ? BORDER : kind == Kind.WARN ? WARN_BTN : RED;
		float hg = ready ? host.hover("dlg-go", gx, rowY, gw, 36) : 0;
		p.round(gx, rowY, gw, 36, 8, ready && kind != Kind.WARN ? mix(fill, RED_HOT, hg * 0.6f) : fill);
		if (held > 0) p.round(gx, rowY, gw * held, 36, 8, 0x8CEF4444);
		p.text(go, gx + 16, rowY + 10, DeskType.I600_13, ready ? 0xFFFFFFFF : FAINT);
		confirmBox = new float[]{gx, rowY, gw, 36};
		if (kind != Kind.BAN) host.hit(gx, rowY, gw, 36, () -> {
			if (ready()) confirm.run();
		});
		p.alpha = before;
		if (held >= 1f) {
			holding = false;
			confirm.run();
		}
	}

	/** A rule or reason chip, ringed red when picked. */
	private static void chip(DeskPaint p, String label, float x, float y, float w, float h, boolean on, float hover) {
		p.box(x, y, w, h, 7, on ? RED_PICK : mix(FIELD, 0xFF232328, hover), on ? RED : BORDER);
		p.text(label, x + 11, y + (h - 15) / 2f, DeskType.I500_12, on ? RED_SOFT : SUB);
	}

	// ---- input ---------------------------------------------------------------------------------

	/** A press on a ban's confirm starts the hold; true when it did. */
	boolean mousePressed(float mx, float my) {
		if (kind != Kind.BAN || !ready()) return false;
		float[] b = confirmBox;
		if (mx < b[0] || my < b[1] || mx >= b[0] + b[2] || my >= b[1] + b[3]) return false;
		holding = true;
		holdAt = Util.getMillis();
		return true;
	}

	void mouseReleased() {
		holding = false;
	}

	/** Esc closes, Enter confirms (never a ban), Tab moves between duration and reason, Backspace edits. True when handled. */
	void keyPressed(int key, Runnable confirm, Runnable close) {
		switch (key) {
			case 256 -> close.run();
			case 257, 335 -> {
				if (kind != Kind.BAN && ready()) confirm.run();
			}
			case 258 -> {
				if (timed()) {
					focus = 1 - focus;
					if (focus == DURATION) otherDuration = true;
				}
			}
			case 259 -> {
				if (!values[focus].isEmpty()) values[focus] = values[focus].substring(0, values[focus].length() - 1);
			}
			default -> {
			}
		}
	}

	/** Types into the focused field; a space ends a duration, since durations are one word. */
	void charTyped(String typed) {
		if (focus == DURATION) {
			if (typed.equals(" ")) {
				focus = REASON;
				return;
			}
			otherDuration = true;
		}
		if (values[focus].length() < 120 && !typed.equals("§")) values[focus] += typed;
	}
}
