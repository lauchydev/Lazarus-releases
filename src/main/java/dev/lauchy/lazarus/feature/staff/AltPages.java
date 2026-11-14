package dev.lauchy.lazarus.feature.staff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's {@code /checkalts} answer: pages of groups of linked accounts, each a "» Banned account
 * linked" style header with its names on the line below. Only a group with a banned account or a banned IP
 * flags its players; "2 online on the same IP" is a hint, not proof, and is left out. Each player in such a
 * group becomes a link naming the rest. Each name is coloured by its state ({@code §a} online, {@code §7}
 * offline, {@code §c} banned), so read with its colour codes a banned one is named "0rose7 (banned)", first
 * among the rest. No Minecraft types, so it's tested on its own.
 */
public final class AltPages {
	/** One flagged player: why ("Linked to a banned account") and the rest of their group, "" when alone, banned ones first and marked {@link #BANNED}. */
	public record Link(String name, String why, String others) {
	}

	/** A group's header, "» Banned account linked", with any chat repeat counter "(x2)" left off. */
	private static final Pattern HEADER = Pattern.compile("^»\\s*(.+?)(?:\\s*\\(x\\d+\\))?$");
	/** A group's names, "Crimson3944, SillyChicken39". */
	private static final Pattern NAMES = Pattern.compile("^\\w{1,16}(?:\\s*,\\s*\\w{1,16})*$");
	/** The pointer to the next page, "Next: /checkalts 2". */
	private static final Pattern NEXT = Pattern.compile("^Next:\\s*/checkalts\\s+(\\d+)\\s*$");
	/** The summary over each page, "Scanned 4574 players (139 online). 16 matching groups. Page 1/2". */
	private static final Pattern PAGE = Pattern.compile("^Scanned .*Page (\\d+)/(\\d+)\\.?$");

	/** How a banned account is marked among a link's others, "0rose7 (banned)". */
	public static final String BANNED = " (banned)";

	/** A colour or format code, "§c", or a hex colour as Reply writes it, "§#ff5555". */
	private static final Pattern CODES = Pattern.compile("§(?:#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])");
	/** A code (group 1) or a name (group 2), in the order the line has them. */
	private static final Pattern CODE_OR_NAME = Pattern.compile("§(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])|(\\w{1,16})");

	private AltPages() {
	}

	/** The line without its colour codes. */
	public static String plain(String line) {
		return CODES.matcher(line).replaceAll("");
	}

	/** The names a group's line colours red, lower-cased. */
	private static Set<String> redNames(String line) {
		Set<String> out = new HashSet<>();
		String colour = "";
		Matcher m = CODE_OR_NAME.matcher(line);
		while (m.find()) {
			String code = m.group(1);
			if (code == null) {
				if (red(colour)) out.add(m.group(2).toLowerCase(Locale.ROOT));
				continue;
			}
			code = code.toLowerCase(Locale.ROOT);
			if (code.startsWith("#") || code.charAt(0) <= 'f' && Character.isLetterOrDigit(code.charAt(0)) && !"klmno".contains(code)) colour = code;
			else if (code.equals("r")) colour = "";
		}
		return out;
	}

	/** Whether a colour is the red the server gives a banned name: §c or §4, or a hex red. */
	private static boolean red(String colour) {
		if (colour.equals("c") || colour.equals("4")) return true;
		if (!colour.startsWith("#")) return false;
		int rgb = Integer.parseInt(colour.substring(1), 16);
		return (rgb >> 16 & 0xFF) >= 0xAA && (rgb >> 8 & 0xFF) < 0x70 && (rgb & 0xFF) < 0x70;
	}

	/** Every account a link's others name, without the banned mark. */
	public static List<String> names(String others) {
		List<String> out = new ArrayList<>();
		for (String n : others.split("\\s*,\\s*")) if (!n.isBlank()) out.add(n.strip().replace(BANNED.strip(), "").strip());
		return out;
	}

	/** The accounts a link's others mark banned. */
	public static List<String> banned(String others) {
		List<String> out = new ArrayList<>();
		for (String n : others.split("\\s*,\\s*")) if (n.endsWith(BANNED.strip())) out.add(n.strip().replace(BANNED.strip(), "").strip());
		return out;
	}

	/** Whether a server line belongs to a checkalts answer, so it's caught rather than shown. */
	public static boolean looksLike(String text) {
		String t = plain(text).trim();
		return t.startsWith("Scanning all known players") || PAGE.matcher(t).matches() || t.startsWith("Scanned ") || HEADER.matcher(t).matches()
				|| NAMES.matcher(t).matches() || NEXT.matcher(t).matches() || t.startsWith("Shared IPs show");
	}

	/** Every player in a group with a banned account or IP, in the order listed. */
	public static List<Link> parse(List<String> lines) {
		List<Link> out = new ArrayList<>();
		String header = null;
		for (String raw : lines) {
			String line = plain(raw).trim();
			Matcher h = HEADER.matcher(line);
			if (h.matches()) {
				header = h.group(1);
				continue;
			}
			if (header == null || !NAMES.matcher(line).matches()) continue;
			String why = why(header.contains("Banned account linked"), header.contains("Banned IP"));
			header = null;
			if (why == null) continue;
			List<String> names = List.of(line.split("\\s*,\\s*"));
			Set<String> red = redNames(raw);
			for (String name : names) {
				List<String> banned = new ArrayList<>(), rest = new ArrayList<>();
				for (String other : names) {
					if (other.equals(name)) continue;
					if (red.contains(other.toLowerCase(Locale.ROOT))) banned.add(other + BANNED);
					else rest.add(other);
				}
				banned.addAll(rest);
				out.add(new Link(name, why, String.join(", ", banned)));
			}
		}
		return out;
	}

	/** Why a group is worrying, or null when it isn't. */
	private static String why(boolean account, boolean ip) {
		if (account && ip) return "Linked to a banned account on a banned IP";
		if (account) return "Linked to a banned account";
		return ip ? "On a banned IP" : null;
	}

	/** The next page the answer points at ("Next: /checkalts 2"), or 0 when this was the last. */
	public static int nextPage(List<String> lines) {
		for (String l : lines) {
			Matcher m = NEXT.matcher(plain(l).trim());
			if (m.matches()) return Integer.parseInt(m.group(1));
		}
		return 0;
	}
}
