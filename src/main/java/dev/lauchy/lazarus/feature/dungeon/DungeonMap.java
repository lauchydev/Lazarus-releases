package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's live dungeon map on screen, without holding it. The map item the server puts in your inventory keeps
 * updating as rooms open; this draws its pixels in a movable frame, and hides it once the boss fight starts. The
 * markers are the mod's own once the map is placed on the world ({@link Dungeon}): you and every teammate your game
 * can see are drawn from where you really are, which is smoother than the server's markers. A teammate too far off
 * to see is drawn from the server's marker for them ({@link MapMarkers}), so they keep moving on the map. With
 * Lazarus+ the markers move every frame; without, a few times a second, as the server's own did.
 */
public final class DungeonMap extends Feature {
	private static final int SIZE = 128, BORDER = 2;
	/** How often markers move without Lazarus+. */
	private static final long STEP_MS = 250;
	/** A teammate's line on the sidebar: "[M] Gurrnos 7,549❤". */
	private static final Pattern MATE = Pattern.compile("^\\[\\w+] (\\w{1,16})\\b");

	private final BoolSetting hideInBoss = add(new BoolSetting("hideInBoss", "Hide in boss room", "Hide the map once the boss speaks.", true));
	private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth markers", "Markers move every frame instead of a few times a second.", true)).plusOnly();

	/** A marker as drawn: its map pixel and the way it faces (degrees, 0 up the map), and whether it's you. */
	private record Marker(double x, double y, float facing, boolean me) {
	}

	/** The team as last read, and the reading of the sidebar it came from. */
	private static List<String> partyOf;
	private static Set<String> party = Set.of();

	/** The map's picture as last taken from the map item, the tick it was taken, and the map it was taken from. */
	private final MapRenderState state = new MapRenderState();
	private long stateAt = -1;
	private Object stateOf;

	private boolean bossStarted;
	private Object level;
	/** Each player's marker by name as last worked out, kept so one out of sight stays put, and when they were last stepped. Yours is under "". */
	private final Map<String, Marker> markers = new HashMap<>();
	private long steppedAt;

	public DungeonMap() {
		super("dungeonMap", "Dungeon Map", "The live dungeon map on screen while you explore, with you and your teammates on it.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Dungeon.init();
		Chat.listen(l -> {
			if (l.text().startsWith("[BOSS] ") && !l.text().startsWith("[BOSS] The Watcher")) bossStarted = true;
		});
		Hud.register(new HudElement(this, "dungeonMap", "Dungeon Map", 0.006f, 0.02f) {
			@Override
			protected boolean visible() {
				Minecraft mc = Minecraft.getInstance();
				if (mc.level != level) {
					level = mc.level;
					bossStarted = false;
					markers.clear();
				}
				return Area.inDungeon() && !(bossStarted && hideInBoss.on()) && Dungeon.mapId(mc) != null;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				int full = SIZE + BORDER * 2;
				if (g == null) return new int[]{full, full};
				Draw.round(g, 0, 0, full, full, Theme.HUD_BG);
				Minecraft mc = Minecraft.getInstance();
				MapId id = Dungeon.mapId(mc);
				MapItemSavedData data = id == null ? null : mc.level.getMapData(id);
				if (data == null) {
					Draw.textCentered(g, font, sample ? "Dungeon map" : "Waiting for map", full / 2, full / 2 - 4, Theme.MUTED);
					return new int[]{full, full};
				}
				// The map's picture changes with the game, not the frame: taken once a tick. Only the markers move every frame.
				if (stateAt != dev.lauchy.lazarus.core.PerTick.now() || stateOf != data) {
					stateAt = dev.lauchy.lazarus.core.PerTick.now();
					stateOf = data;
					mc.getMapRenderer().extractRenderState(id, data, state);
				}
				boolean own = Dungeon.placed();
				if (own) state.decorations.clear();
				var pose = g.pose();
				pose.pushMatrix();
				pose.translate(BORDER, BORDER);
				g.map(state);
				if (own) {
					step(mc, data);
					for (Marker m : markers.values()) if (!m.me()) marker(g, m);
					for (Marker m : markers.values()) if (m.me()) marker(g, m); // yours on top
				}
				pose.popMatrix();
				return new int[]{full, full};
			}
		});
	}

	/** Moves each marker to where its player is now: every frame with Lazarus+, else a few times a second. */
	private void step(Minecraft mc, MapItemSavedData data) {
		long now = Util.getMillis();
		if (!(smooth.on() && Plus.unlocked()) && now - steppedAt < STEP_MS) return;
		steppedAt = now;
		MapFit fit = Dungeon.fit();
		// One marker for each teammate the sidebar lists, and no one else: the dungeon is full of things the game counts as players.
		Set<String> party = party(Area.sidebar());
		// A sidebar that lists no one is a run on your own (it says "Solo"): your marker alone.
		markers.keySet().removeIf(name -> !name.isEmpty() && !party.contains(name));
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Set<String> inSight = new HashSet<>();
		List<double[]> taken = new ArrayList<>();
		for (Player p : mc.level.players()) {
			String name = p == mc.player ? "" : p.getName().getString();
			if (p != mc.player && !party.contains(name)) continue;
			var at = p.getPosition(partial);
			markers.put(name, new Marker(fit.mapX(at.x), fit.mapY(at.z), p.getViewYRot(partial) + 180, p == mc.player));
			inSight.add(name);
			taken.add(new double[]{fit.mapX(at.x), fit.mapY(at.z)});
		}
		// Teammates your game can't see: the server's map still marks them.
		Map<String, double[]> outOfSight = new HashMap<>();
		for (String name : party) {
			if (inSight.contains(name)) continue;
			Marker last = markers.get(name);
			outOfSight.put(name, last == null ? null : new double[]{last.x(), last.y()});
		}
		if (outOfSight.isEmpty()) return;
		List<double[]> served = new ArrayList<>();
		for (MapDecoration d : data.getDecorations()) served.add(new double[]{Dungeon.pixel(d.x()), Dungeon.pixel(d.y()), d.rot() * 22.5 + 180});
		MapMarkers.place(served, taken, outOfSight).forEach((name, m) -> markers.put(name, new Marker(m[0], m[1], (float) m[2], false)));
	}

	/** The teammates the sidebar lists, by name. */
	static Set<String> party(List<String> sidebar) {
		if (sidebar == partyOf) return party; // the same reading of the sidebar: the same team
		Set<String> out = new HashSet<>();
		for (String line : sidebar) {
			Matcher m = MATE.matcher(line.trim());
			if (m.find()) out.add(m.group(1));
		}
		partyOf = sidebar;
		party = out;
		return out;
	}

	/** A marker: a small arrow in a dark edge, green for you and blue for a teammate. */
	private static void marker(GuiGraphicsExtractor g, Marker m) {
		if (m.x() < 0 || m.y() < 0 || m.x() > SIZE || m.y() > SIZE) return;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) m.x(), (float) m.y());
		pose.rotate((float) Math.toRadians(m.facing()));
		pose.scale(0.5f, 0.5f);
		int edge = 0xFF101010, fill = m.me() ? 0xFF2BE35A : 0xFF4AA8FF;
		g.fill(-5, -3, 5, 6, edge);
		g.fill(-3, -7, 3, -3, edge);
		g.fill(-4, -2, 4, 5, fill);
		g.fill(-2, -6, 2, -2, fill);
		pose.popMatrix();
	}
}
