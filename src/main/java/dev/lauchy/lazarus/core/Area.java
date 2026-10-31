package dev.lauchy.lazarus.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the player is, read from the server's sidebar once a second. Features ask this
 * before drawing so End readouts stay out of dungeons and the other way round.
 */
public final class Area {
	public enum Place { END, CRYPTS, VOID_SEPULTURE, DUNGEON, HUB, RIFT, UNKNOWN }

	private static Place place = Place.UNKNOWN;
	private static List<String> sidebar = List.of();
	private static int ticks;

	private Area() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) {
				place = Place.UNKNOWN;
				sidebar = List.of();
				return;
			}
			if (++ticks % 20 != 0) return;
			sidebar = readSidebar(mc);
			// Unknown when no line names a place, so leaving for somewhere unlisted doesn't
			// leave the last place stuck (and End or dungeon readouts showing elsewhere).
			Place found = Place.UNKNOWN;
			for (String line : sidebar) {
				Place p = classify(line);
				if (p != Place.UNKNOWN) {
					found = p;
					break;
				}
			}
			place = found;
		});
	}

	public static Place place() {
		return place;
	}

	/** The End's islands, or still unknown: readouts are never hidden on a guess. */
	public static boolean inEnd() {
		return place == Place.END || place == Place.VOID_SEPULTURE || place == Place.UNKNOWN;
	}

	public static boolean inCrypts() {
		return place == Place.CRYPTS;
	}

	public static boolean inDungeon() {
		return place == Place.DUNGEON;
	}

	/** The dragsim server you're on, as /send names it (end, rift, dungeons, hub, crypts), or null when unknown. */
	public static String server() {
		return switch (place) {
			case END, VOID_SEPULTURE -> "end";
			case CRYPTS -> "crypts";
			case DUNGEON -> "dungeons";
			case HUB -> "hub";
			case RIFT -> "rift";
			case UNKNOWN -> null;
		};
	}

	/** Sidebar lines as plain text, top to bottom, refreshed every second. */
	public static List<String> sidebar() {
		return sidebar;
	}

	static Place classify(String line) {
		String s = line.replaceAll("^[^\\p{L}]+", "").trim().toLowerCase(Locale.ROOT);
		if (s.startsWith("the catacombs") || s.startsWith("catacombs")) return Place.DUNGEON;
		if (s.equals("crypts") || s.startsWith("crypts ")) return Place.CRYPTS;
		if (s.startsWith("void sepulture")) return Place.VOID_SEPULTURE;
		if (s.equals("dragon's den") || s.equals("dragons den") || s.equals("the end")) return Place.END;
		if (s.equals("rift") || s.startsWith("the rift") || s.startsWith("rift ")) return Place.RIFT;
		if (s.equals("village") || s.equals("hub") || s.equals("spawn")) return Place.HUB;
		return Place.UNKNOWN;
	}

	private static List<String> readSidebar(Minecraft mc) {
		List<String> out = new ArrayList<>();
		try {
			Scoreboard board = mc.level.getScoreboard();
			Objective obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (obj == null) {
				for (DisplaySlot slot : DisplaySlot.values()) {
					if (slot.name().startsWith("TEAM_") && (obj = board.getDisplayObjective(slot)) != null) break;
				}
			}
			if (obj == null) return out;
			for (PlayerScoreEntry e : board.listPlayerScores(obj)) {
				PlayerTeam team = board.getPlayersTeam(e.owner());
				out.add(Text.plain(PlayerTeam.formatNameForTeam(team, e.ownerName())));
			}
		} catch (RuntimeException ignored) {
			// Sidebar changing under us; try again next second.
		}
		return out;
	}
}
