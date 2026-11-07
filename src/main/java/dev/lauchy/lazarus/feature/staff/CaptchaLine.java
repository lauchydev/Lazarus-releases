package dev.lauchy.lazarus.feature.staff;

import java.util.regex.Matcher;

/**
 * A captcha (macro check) result as the server announces it: "[Captcha] Steve failed the check Lauchy sent,
 * in 8s." Every result staff hear is shared, so the roster flags the player whoever sent the check. No
 * Minecraft types, so it's tested on its own.
 */
public final class CaptchaLine {
	/** One result: who took the check, whether they passed, who sent it, and how long they took (-1 when not said). */
	public record Result(String player, boolean passed, String by, int seconds) {
	}

	private CaptchaLine() {
	}

	/** The result a server line gives, or null when it isn't one. */
	public static Result parse(String line) {
		Matcher m = CaptchaKey.RESULT.matcher(line.trim());
		if (!m.matches()) return null;
		return new Result(m.group(1), m.group(2).equals("passed"), m.group(3), m.group(4) == null ? -1 : Integer.parseInt(m.group(4)));
	}
}
