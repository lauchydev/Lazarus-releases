package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.minecraft.client.Minecraft;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers the Golden Dragon's "Find the answer to ...?" in chat (only you see it) and/or
 * on the clipboard. Exact decimal maths with operator precedence, so 76.2 + 19.6 is 95.8,
 * never 95.80000000000001, and longer sums work too.
 */
public final class GoldenDragonMath extends Feature {
	private static final Pattern QUESTION = Pattern.compile("Find the answer to\\s*(.+?)\\s*\\?", Pattern.CASE_INSENSITIVE);

	private final BoolSetting chat = add(new BoolSetting("chat", "Show in chat", "Print the answer under the question.", true));
	private final BoolSetting clipboard = add(new BoolSetting("clipboard", "Copy to clipboard", "Put the answer on the clipboard to paste.", false));

	public GoldenDragonMath() {
		super("goldenDragonMath", "Golden Dragon Math", "Answers the Golden Dragon's maths question instantly.", Category.END, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (!enabled()) return;
			String question = question(l.text());
			if (question == null) return;
			String answer = solve(question);
			if (answer == null) return;
			if (chat.on()) Message.lazarus().info("Answer: ").success(answer).send();
			if (clipboard.on()) Minecraft.getInstance().keyboardHandler.setClipboard(answer);
		});
	}

	/**
	 * The sum the dragon asks in a chat line, or null. It speaks as "[BOSS] Golden Dragon: ...", which
	 * has a colon like a player's line, so only a player's own line (one without "[BOSS]") is skipped.
	 */
	static String question(String line) {
		if (Chat.fromPlayer(line) && !line.trim().startsWith("[BOSS] ")) return null;
		Matcher m = QUESTION.matcher(line);
		return m.find() ? m.group(1) : null;
	}

	/** Evaluates + - * / x × ÷ with precedence. @return the answer as you'd type it, or null */
	static String solve(String expr) {
		try {
			Parser p = new Parser(expr.replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replace(" ", ""));
			BigDecimal r = p.sum();
			if (p.pos != p.s.length()) return null;
			return r.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static final class Parser {
		final String s;
		int pos;

		Parser(String s) {
			this.s = s;
		}

		BigDecimal sum() {
			BigDecimal v = product();
			while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
				char op = s.charAt(pos++);
				BigDecimal r = product();
				v = op == '+' ? v.add(r) : v.subtract(r);
			}
			return v;
		}

		BigDecimal product() {
			BigDecimal v = number();
			while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
				char op = s.charAt(pos++);
				BigDecimal r = number();
				v = op == '*' ? v.multiply(r) : v.divide(r, MathContext.DECIMAL64);
			}
			return v;
		}

		BigDecimal number() {
			if (pos < s.length() && s.charAt(pos) == '(') {
				pos++;
				BigDecimal v = sum();
				pos++; // ')'
				return v;
			}
			int start = pos;
			if (pos < s.length() && s.charAt(pos) == '-') pos++;
			while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.' || s.charAt(pos) == ',')) pos++;
			return new BigDecimal(s.substring(start, pos).replace(",", ""));
		}
	}
}
