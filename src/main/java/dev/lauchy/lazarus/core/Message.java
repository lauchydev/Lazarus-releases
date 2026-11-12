package dev.lauchy.lazarus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * One chat message from the mod, built up a piece at a time behind one prefix: "Lazarus >", or
 * "Lazarus+ >" for Lazarus+; "LZ >" and "LZ+ >" for the lines said in the middle of play. Each piece says what it is (success, error, warning, info, muted,
 * highlight, heading, or something clickable) and gets that role's colour. Only two colours are the
 * brand's: Violet on the word "Lazarus" and Halo gold on the plus. Every other colour is one of the
 * game's own sixteen (white text, grey notes, §5 purple, §a green, §c red, §6 gold), and an item or mob
 * keeps the colour its own name has. {@link #line()} starts the next line under a dark grey bar. A button
 * is underlined dark purple text with no brackets (a player's name in white, a punishment in gold), and
 * says what it does in a word or two. Building needs no game running, so messages can be checked in
 * plain tests; only {@link #send()} touches the game.
 */
public final class Message {
	/** The only brand colours in chat: Violet for the word "Lazarus" and Halo for the plus of Lazarus+. */
	private static final int VIOLET = 0xB46CFF, HALO = 0xE9C27D;
	/** The game's grey (§7) for notes and the prefix's mark, dark grey (§8) for the line bar, and white (§f) for plain text. */
	private static final int FOG = 0xAAAAAA, ASH = 0x555555, BONE = 0xFFFFFF;
	/** The status colours: the game's green (§a), red (§c) and gold (§6), which is darker than Halo so a warning never reads as Lazarus+. */
	private static final int GOOD = 0x55FF55, BAD = 0xFF5555, WARNING = 0xFFAA00, MC_DARK_RED = 0xAA0000;
	/** Minecraft's own colours, for headings, details and buttons (dark purple) and for an item's rarity in a row. */
	private static final int MC_BLUE = 0x5555FF, MC_GOLD = 0xFFAA00, MC_AQUA = 0x55FFFF, MC_YELLOW = 0xFFFF55, MC_DARK_GREEN = 0x00AA00;
	private static final int MC_DARK_PURPLE = 0xAA00AA, MC_PINK = 0xFF55FF, MC_DARK_BLUE = 0x0000AA, MC_DARK_AQUA = 0x00AAAA;
	/** The mark after the name, in the game's grey. */
	private static final String MARK = " > ";

	private final MutableComponent out = Component.empty();

	private Message(String name, boolean plus) {
		boolean named = !name.isEmpty();
		if (named) out.append(Component.literal(name).withColor(VIOLET));
		if (plus) out.append(Component.literal("+").withColor(HALO));
		out.append(Component.literal(named ? MARK : MARK.stripLeading()).withColor(FOG));
	}

	/** A normal message: "Lazarus >". */
	public static Message lazarus() {
		return new Message("Lazarus", false);
	}

	/** A message about Lazarus+: "Lazarus+ >", with the plus in Halo gold. */
	public static Message plus() {
		return new Message("Lazarus", true);
	}

	/**
	 * A line said in the middle of play (a solver's answer, a terminal's time, a tracker's reset), which keeps
	 * out of the way behind the short name: "LZ >", or "LZ+ >" when a Lazarus+ feature says it.
	 */
	public static Message brief(boolean plus) {
		return new Message("LZ", plus);
	}

	/** A line said by a player rather than by Lazarus (a member's join message): just ">" in grey, with no name before it. */
	public static Message said() {
		return new Message("", false);
	}

	/**
	 * A HUD panel's rows as one message, for the commands that list a tracker in chat: the panel's
	 * title as a heading, then each row on its own line in the colours the HUD uses.
	 */
	public static Message panel(List<String> rows) {
		Message m = lazarus();
		if (rows.isEmpty()) return m;
		m.heading(rows.getFirst());
		return m.rows(rows.subList(1, rows.size()));
	}

	/** Plain information, in white. */
	public Message info(String text) {
		return add(text, BONE);
	}

	/** Something that worked, in the game's green. */
	public Message success(String text) {
		return add(text, GOOD);
	}

	/** Something that went wrong, in red. */
	public Message error(String text) {
		return add(text, BAD);
	}

	/** A heart, in the game's red, for a thank-you. */
	public Message heart() {
		return add("\u2764", BAD);
	}

	/** Something to be careful about, in the game's gold, darker than the Lazarus+ gold. */
	public Message warning(String text) {
		return add(text, WARNING);
	}

	/** An important detail inside a message (a name, a date, an amount), in dark purple. */
	public Message highlight(String text) {
		return add(text, MC_DARK_PURPLE);
	}

	/** A secondary note (a unit, a rate, a hint), in grey, so the main text stands out. */
	public Message muted(String text) {
		return add(text, FOG);
	}

	/**
	 * A title over the lines that follow, bold in dark purple. A title with § codes of its own (a HUD
	 * panel's, naming a boss in its colour) keeps them, so a mob or item is coloured as it is in the game.
	 */
	public Message heading(String text) {
		legacy(text, MC_DARK_PURPLE, true);
		return this;
	}

	/**
	 * HUD rows, each on its own line, keeping the colours their § codes give them: grey labels,
	 * Bone values, green coins, and an item's rarity colour. Bold and the colour codes are read;
	 * anything else is dropped.
	 */
	public Message rows(List<String> rows) {
		// Chat has no right edge to align a HUD row's tabbed value to, so it follows after a dot.
		for (String row : rows) line().legacy(row.replace("\t", " §8· "), BONE, false);
		return this;
	}

	/** One row of § coded text, starting in {@code colour}, translated to the message's colours. */
	private void legacy(String row, int colour, boolean bold) {
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < row.length(); i++) {
			char c = row.charAt(i);
			if (c != '§' || i + 1 >= row.length()) {
				run.append(c);
				continue;
			}
			flush(run, colour, bold);
			char code = Character.toLowerCase(row.charAt(++i));
			if (code == 'l') bold = true;
			else if (code == 'r') {
				colour = BONE;
				bold = false;
			} else if ("0123456789abcdef".indexOf(code) >= 0) {
				colour = colour(code);
				bold = false;
			}
		}
		flush(run, colour, bold);
	}

	private void flush(StringBuilder run, int colour, boolean bold) {
		if (run.isEmpty()) return;
		out.append(Component.literal(run.toString()).withStyle(s -> s.withColor(colour).withBold(bold)));
		run.setLength(0);
	}

	/**
	 * Which colour a § colour code stands for in a HUD row: the game's own colour for each, so item
	 * names keep their rarity colour.
	 */
	private static int colour(char code) {
		return switch (code) {
			case '7' -> FOG;
			case '8' -> ASH;
			case 'a' -> GOOD;
			case '2' -> MC_DARK_GREEN;
			case 'c' -> BAD;
			case '4' -> MC_DARK_RED;
			case 'e' -> MC_YELLOW;
			case '5' -> MC_DARK_PURPLE;
			case 'd' -> MC_PINK;
			case '6' -> MC_GOLD;
			case '9' -> MC_BLUE;
			case '1' -> MC_DARK_BLUE;
			case 'b' -> MC_AQUA;
			case '3' -> MC_DARK_AQUA;
			default -> BONE;
		};
	}

	/** A button that opens a web page when clicked; {@code hover} says what clicking does ("Click to ..."). */
	public Message link(String text, String hover, String url) {
		return clickable(text, hover, new ClickEvent.OpenUrl(URI.create(url)));
	}

	/** A button that runs a command when clicked; {@code hover} says what clicking does ("Click to ..."). */
	public Message command(String text, String hover, String command) {
		return clickable(text, hover, new ClickEvent.RunCommand(command));
	}

	/** A button that copies {@code value} when clicked; {@code hover} says what clicking does ("Click to ..."). */
	public Message copy(String text, String hover, String value) {
		return clickable(text, hover, new ClickEvent.CopyToClipboard(value));
	}

	/** A button that opens a folder or file on this computer when clicked; {@code hover} says what clicking does ("Click to ..."). */
	public Message folder(String text, String hover, Path path) {
		return clickable(text, hover, new ClickEvent.OpenFile(path));
	}

	/** A player's name as a button, underlined white so it reads apart from the purple actions beside it; clicking opens their profile. */
	public Message player(String name) {
		return clickable(name, "Click to open " + name + "'s profile", new ClickEvent.RunCommand("/l " + name), BONE);
	}

	/** A button for something that punishes (ban, mute, kick, a punish card), in the warning gold so it's never pressed by mistake. */
	public Message danger(String text, String hover, String command) {
		return clickable(text, hover, new ClickEvent.RunCommand(command), WARNING);
	}

	/**
	 * A name in a line the server sent, made clickable as it stands: it keeps its look (the rank colour,
	 * say), runs {@code command} when clicked, and says so on hover.
	 */
	public static Component clickName(Component name, String hover, String command) {
		return name.copy().withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withColor(BONE))));
	}

	/** A quiet mark on a line the server sent, in grey, that runs {@code command} when clicked; {@code hover} says what it does. */
	public static Component mark(String text, String hover, String command) {
		return Component.literal(text).withStyle(s -> s.withColor(FOG)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withColor(BONE))));
	}

	private Message clickable(String text, String hover, ClickEvent click) {
		return clickable(text, hover, click, MC_DARK_PURPLE);
	}

	private Message clickable(String text, String hover, ClickEvent click, int colour) {
		// Players only know text is a button from its look and the hover, so the hover is never left out.
		if (hover == null || hover.isBlank()) throw new IllegalArgumentException("A button needs a hover saying what clicking does: " + text);
		out.append(Component.literal(text).withStyle(s -> s.withColor(colour).withUnderlined(true).withClickEvent(click)
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withColor(BONE)))));
		return this;
	}

	private Message add(String text, int colour) {
		out.append(Component.literal(text).withStyle(Style.EMPTY.withColor(colour)));
		return this;
	}

	/** Starts the next line, marked with a dark grey bar so it's clear it belongs to this message. Twice in a row leaves a spacer line. */
	public Message line() {
		out.append(Component.literal("\n")).append(Component.literal("│ ").withColor(ASH));
		return this;
	}

	/** The finished message, ready for chat. */
	public Component build() {
		return out;
	}

	/** Puts the message in this player's chat; does nothing outside a world. */
	public void send() {
		send(build());
	}

	/** Puts a built message in this player's chat; does nothing outside a world. */
	public static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(message);
	}
}
