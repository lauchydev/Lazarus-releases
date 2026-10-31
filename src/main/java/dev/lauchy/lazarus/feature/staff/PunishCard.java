package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The punishment card shared by the staff panel and the profile drawer: a rule from the punishment
 * ladder (which fills the reason and offers the duration for a 1st, 2nd, 3rd… offence), a duration and
 * a reason, each as chips or typed, the exact command it will send, then confirm. A ban has to be held.
 * The screen that owns it keeps the action and sends the command; the card keeps what was answered.
 */
public final class PunishCard {
	/** Where the card registers what can be clicked, in the owning screen's own hit list. */
	public interface Hits {
		/** Makes the box at {@code x, y, w, h} run {@code action} when clicked. */
		void add(int x, int y, int w, int h, Runnable action);
	}

	/** Which ladder the card offers: ban rules, mute rules, or none (a kick). */
	public enum Kind {BAN, MUTE, OTHER}

	private static final long HOLD_MS = 700;
	private static final int DURATION = 0, REASON = 1;

	private final Font font;
	private final Anim.Group<Object> hover;
	private final Hits hits;
	private final List<String> durations, reasons;
	private final List<Ladder.Rule> banRules, muteRules;

	private boolean open;
	private String label = "";
	private boolean duration, reason;
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

	/** Opens the card for an action: its label, whether it wants a duration and a reason, and which ladder applies. */
	public void open(String label, boolean duration, boolean reason, Kind kind) {
		this.open = true;
		this.label = label;
		this.duration = duration;
		this.reason = reason;
		this.kind = kind;
		this.rules = kind == Kind.BAN ? banRules : kind == Kind.MUTE ? muteRules : List.of();
		values[DURATION] = "";
		values[REASON] = "";
		rule = null;
		focus = duration ? DURATION : REASON;
		openedAt = Util.getMillis();
		holding = false;
	}

	/** Closes the card without sending anything. */
	public void close() {
		open = false;
		holding = false;
	}

	/** Whether the card is showing. */
	public boolean isOpen() {
		return open;
	}

	/** The duration answered, trimmed. */
	public String duration() {
		return values[DURATION].trim();
	}

	/** The reason answered, trimmed. */
	public String reason() {
		return values[REASON].trim();
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

	/** One chip: what it shows and what picking it puts in the field. */
	private record Chip(String label, String value, String tip) {
	}

	private List<Chip> ruleChips() {
		List<Chip> out = new ArrayList<>();
		for (Ladder.Rule r : rules) out.add(new Chip(r.chip(), r.code(), Ladder.describe(r)));
		return out;
	}

	/** The duration chips: the rule's offence steps when one is picked and has them, else the usual chips. */
	private List<Chip> durationChips() {
		List<Chip> out = new ArrayList<>();
		if (rule != null && !rule.set()) {
			List<String> steps = rule.steps();
			if (steps.size() == 1) out.add(new Chip(steps.get(0), steps.get(0), null));
			else for (int i = 0; i < steps.size(); i++) out.add(new Chip(Ladder.ordinal(i + 1) + " · " + steps.get(i), steps.get(i), null));
			return out;
		}
		for (String d : durations) out.add(new Chip(d, d, null));
		return out;
	}

	/** The reason chips; with a ladder the rule chips stand in for them, so the card stays short. */
	private List<Chip> reasonChips() {
		List<Chip> out = new ArrayList<>();
		if (!rules.isEmpty()) return out;
		for (String r : reasons) out.add(new Chip(r, r, null));
		return out;
	}

	/** How tall a field is: its line, then its chips in as many rows as the width needs. */
	private int fieldHeight(List<Chip> chips, int w) {
		int rows = 1, cx = 0; // an empty field still keeps one chip line's room, as field() lays it out
		for (Chip c : chips) {
			int cw = font.width(c.label) + 8;
			if (cx > 0 && cx + cw > w) {
				cx = 0;
				rows++;
			}
			cx += cw + 3;
		}
		return 14 + rows * 13 + 5;
	}

	private int cardWidth(int pw) {
		return Math.min(280, pw - 30);
	}

	private int cardHeight(int w) {
		int inner = w - 20, h = 26 + 14 + 30;
		if (!rules.isEmpty()) h += fieldHeight(ruleChips(), inner);
		if (duration) h += fieldHeight(durationChips(), inner);
		if (reason) h += fieldHeight(reasonChips(), inner);
		return h;
	}

	/** Whether a point is on the confirm button, laid out the same way {@link #draw} does. */
	public boolean insideConfirm(int px, int py, int pw, int ph, double mx, double my) {
		if (!open) return false;
		int w = cardWidth(pw), x = px + (pw - w) / 2;
		int h = cardHeight(w), y = py + (ph - h) / 2;
		return Draw.inside(mx, my, x + 10, y + h - 24, (w - 26) / 2, 16);
	}

	// ---- drawing -------------------------------------------------------------------------

	/**
	 * Draws the card over the panel at {@code px, py, pw, ph}. {@code preview} is the command as it
	 * would be sent; {@code confirm} runs when the card is confirmed (a held ban finishing counts).
	 */
	public void draw(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my, String player, String preview, Runnable confirm) {
		if (!open) return;
		float in = Anim.since(openedAt, 180);
		// While the card is up, only its own controls can be clicked; a miss lands on the backdrop.
		hits.add(px, py, pw, ph, () -> {
		});
		Draw.round(g, px, py, pw, ph, Draw.alpha(0x0B0B10, 0.55f * in));
		float before = Draw.opacity;
		Draw.opacity *= in;
		int w = cardWidth(pw), x = px + (pw - w) / 2;
		int h = cardHeight(w);
		int y = py + (ph - h) / 2 + Math.round((1f - in) * 6);
		Draw.round(g, x, y, w, h, 0xF81B1B23);
		Draw.outline(g, x, y, w, h, 0x33FF6B6B);
		Draw.text(g, font, "§l" + label + " §r§f" + player, x + 10, y + 9, Theme.TEXT);
		Draw.text(g, font, "Esc to cancel", x + w - 10 - font.width("Esc to cancel"), y + 9, Theme.FAINT);
		int cy = y + 26;
		tip = null;
		if (!rules.isEmpty()) cy = ruleField(g, x + 10, cy, w - 20, mx, my);
		if (duration) cy = field(g, DURATION, "Duration", durationChips(), x + 10, cy, w - 20, mx, my);
		if (reason) cy = field(g, REASON, "Reason", reasonChips(), x + 10, cy, w - 20, mx, my);
		String shown = "/" + preview;
		while (font.width(shown) > w - 20 && shown.length() > 4) shown = shown.substring(0, shown.length() - 2);
		Draw.text(g, font, shown, x + 10, cy + 2, Theme.MUTED);

		int by = y + h - 24, bw = (w - 26) / 2;
		boolean ready = ready(), hold = held();
		float held = holding && ready ? Math.min(1f, (Util.getMillis() - holdAt) / (float) HOLD_MS) : 0f;
		if (held >= 1f) {
			holding = false;
			Draw.opacity = before;
			confirm.run();
			return;
		}
		float hc = hover.to("confirm", ready && Draw.inside(mx, my, x + 10, by, bw, 16));
		Draw.round(g, x + 10, by, bw, 16, ready ? Anim.lerpColor(0x66E05555, 0xAAE05555, hc) : Theme.FIELD);
		if (held > 0f) Draw.round(g, x + 10, by, Math.max(3, Math.round(bw * held)), 16, 0xFFE05555);
		String text = !ready ? label : hold ? (held > 0f ? "Keep holding…" : "Hold to " + label.toLowerCase(Locale.ROOT)) : label;
		Draw.textCentered(g, font, text, x + 10 + bw / 2, by + 4, ready ? Theme.TEXT : Theme.FAINT);
		if (!hold) hits.add(x + 10, by, bw, 16, () -> {
			if (ready()) confirm.run();
		});
		float hx = hover.to("cancel", Draw.inside(mx, my, x + 16 + bw, by, bw, 16));
		Draw.round(g, x + 16 + bw, by, bw, 16, Anim.lerpColor(Theme.FIELD, 0x30FFFFFF, hx));
		Draw.textCentered(g, font, "Cancel", x + 16 + bw + bw / 2, by + 4, Theme.TEXT);
		hits.add(x + 16 + bw, by, bw, 16, this::close);
		Draw.opacity = before;
		if (tip != null && Draw.opacity >= 1f) g.setTooltipForNextFrame(font, net.minecraft.network.chat.Component.literal(tip), mx, my);
	}

	/** The rule row: the picked rule's full name, with one chip per rule of the ladder under it. */
	private int ruleField(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		Draw.text(g, font, "Rule", x, y, Theme.MUTED);
		Draw.round(g, x + 50, y - 2, w - 50, 12, Theme.FIELD);
		String v = rule == null ? "§8pick one, or fill in below" : rule.reason();
		while (font.width(v) > w - 58 && v.length() > 1) v = v.substring(0, v.length() - 1);
		Draw.text(g, font, v, x + 54, y, Theme.TEXT);
		int cx = x, cy = y + 14;
		for (Chip c : ruleChips()) {
			int cw = font.width(c.label) + 8;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				cy += 13;
			}
			boolean on = rule != null && rule.code().equals(c.value), over = Draw.inside(mx, my, cx, cy, cw, 11);
			if (over) tip = c.tip;
			float h = hover.to("rule" + c.value, on || over);
			Draw.round(g, cx, cy, cw, 11, on ? Theme.ACCENT_SOFT : Anim.lerpColor(Theme.FIELD, 0x24FFFFFF, h));
			Draw.text(g, font, c.label, cx + 4, cy + 2, on ? Theme.TEXT : Anim.lerpColor(Theme.MUTED, Theme.TEXT, h));
			hits.add(cx, cy, cw, 11, () -> pickRule(on ? null : Ladder.find(rules, c.value)));
			cx += cw + 3;
		}
		return cy + 18;
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

	/** A labelled text field with one-click chips under it. */
	private int field(GuiGraphicsExtractor g, int i, String title, List<Chip> chips, int x, int y, int w, int mx, int my) {
		Draw.text(g, font, title, x, y, Theme.MUTED);
		Draw.round(g, x + 50, y - 2, w - 50, 12, Theme.FIELD);
		if (i == focus) Draw.rect(g, x + 52, y + 9, w - 54, 1, Theme.ACCENT);
		boolean caret = Util.getMillis() / 500 % 2 == 0;
		String v = values[i] + (i == focus && caret ? "_" : "");
		if (values[i].isEmpty() && i != focus) v = "§8type or pick";
		while (font.width(v) > w - 58 && v.length() > 1) v = v.substring(1);
		Draw.text(g, font, v, x + 54, y, Theme.TEXT);
		hits.add(x + 50, y - 2, w - 50, 12, () -> focus = i);
		int cx = x, cy = y + 14;
		for (Chip c : chips) {
			int cw = font.width(c.label) + 8;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				cy += 13;
			}
			boolean on = c.value.equalsIgnoreCase(values[i].trim());
			float h = hover.to("chip" + i + c.label, on || Draw.inside(mx, my, cx, cy, cw, 11));
			Draw.round(g, cx, cy, cw, 11, on ? Theme.ACCENT_SOFT : Anim.lerpColor(Theme.FIELD, 0x24FFFFFF, h));
			Draw.text(g, font, c.label, cx + 4, cy + 2, on ? Theme.TEXT : Anim.lerpColor(Theme.MUTED, Theme.TEXT, h));
			hits.add(cx, cy, cw, 11, () -> {
				values[i] = c.value;
				if (i == DURATION && reason) focus = REASON;
			});
			cx += cw + 3;
		}
		return cy + 18;
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

	/** Handles a key while the card is open: Esc closes, Enter confirms (never a ban), Tab moves on, Backspace edits. */
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
