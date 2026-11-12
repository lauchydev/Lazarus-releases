package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.feature.dungeon.TrailFinder.Spot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dungeons saved from the game by {@code /lazarus snap}, planned again here exactly as the game planned them. Every
 * snap in {@code src/test/resources/dungeon-snaps} (and in the folder {@code LAZARUS_SNAPS} names, for ones not yet
 * kept) is drawn from above to {@code build/snaps/<name>.png}: walls, the map's grid, where the map puts each door,
 * where the trail finder put it, and the trail. A snap kept in the repo is a real room a trail must keep getting right.
 */
class SnapReplayTest {
	/** For each door of the snap last drawn: how far the doorway found in the wall was from where the grid puts the door (99 when none was found). */
	static final List<Double> doorsOff = new ArrayList<>();

	/** Pixels to a block in the picture. */
	private static final int ZOOM = 5;

	/** A trail as planned again from a snap: where each stop was placed, and what was planned. */
	record Replayed(List<Spot> placed, TrailPlan.Route route) {
	}

	static List<Replayed> replay(DungeonSnap snap) {
		TrailPlan plan = new TrailPlan(snap.blocks(), new TrailPlan.Fix());
		Spot start = plan.start((int) Math.floor(snap.x), (int) Math.floor(snap.y + 0.2), (int) Math.floor(snap.z));
		List<Replayed> out = new ArrayList<>();
		if (start == null) return out;
		for (DungeonSnap.Shown shown : snap.trails) {
			List<TrailPlan.Stop> stops = shown.stops.stream().map(s -> new TrailPlan.Stop(s[0], s[1], (char) s[2])).toList();
			List<Spot> placed = new ArrayList<>();
			for (TrailPlan.Stop s : stops) {
				Spot p = plan.place(s, start.y());
				if (p != null) placed.add(p);
			}
			out.add(new Replayed(placed, plan.route(start, stops, shown.whole)));
		}
		return out;
	}

	/** The snap from above at the height you stood: dark where a wall stands, light where it's open with a floor, black where there's no floor. */
	static BufferedImage draw(DungeonSnap snap, List<Replayed> replayed) {
		BufferedImage img = new BufferedImage(snap.sizeX * ZOOM, snap.sizeZ * ZOOM, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		TrailPlan.Blocks blocks = snap.blocks();
		// The height looked at: yours, or the one LAZARUS_SNAP_Y names (a door's, say, when you stood in a pit).
		int feet = System.getenv("LAZARUS_SNAP_Y") != null ? Integer.parseInt(System.getenv("LAZARUS_SNAP_Y")) : (int) Math.floor(snap.y + 0.2);
		for (int dz = 0; dz < snap.sizeZ; dz++) {
			for (int dx = 0; dx < snap.sizeX; dx++) {
				int x = snap.minX + dx, z = snap.minZ + dz;
				Color c;
				String at = snap.block(x, feet, z), above = snap.block(x, feet + 1, z);
				if (!blocks.loaded(x, z)) c = new Color(60, 0, 60);
				else if (DungeonSnap.doorBlock(at) || DungeonSnap.doorBlock(above)) c = at.contains("coal") || above.contains("coal") ? new Color(90, 90, 160) : at.contains("terracotta") || above.contains("terracotta") ? new Color(170, 40, 40) : new Color(120, 160, 200);
				else if (blocks.solid(x, feet, z) || blocks.solid(x, feet + 1, z)) c = new Color(45, 45, 45);
				else {
					int drop = 0;
					while (drop < 12 && !blocks.solid(x, feet - 1 - drop, z)) drop++;
					c = drop >= 12 ? Color.BLACK : new Color(200 - drop * 10, 200 - drop * 10, 190 - drop * 10);
				}
				g.setColor(c);
				g.fillRect(dx * ZOOM, dz * ZOOM, ZOOM, ZOOM);
			}
		}
		// The map's grid, as the fit lays it on the world: each room's square in yellow, each door the map shows in red.
		DungeonLayout layout = snap.map.length == DungeonLayout.SIZE * DungeonLayout.SIZE ? DungeonLayout.read(snap.map) : null;
		if (layout != null && snap.scale > 0) {
			MapFit fit = snap.fit();
			doorsOff.clear();
			g.setColor(new Color(255, 220, 0));
			for (DungeonLayout.Room r : layout.rooms) {
				for (int[] c : r.cells()) {
					double px = layout.originX + c[0] * layout.pitch, py = layout.originY + c[1] * layout.pitch;
					int x0 = px(snap, fit.worldX(px), true), z0 = px(snap, fit.worldZ(py), false), x1 = px(snap, fit.worldX(px + layout.roomSize), true), z1 = px(snap, fit.worldZ(py + layout.roomSize), false);
					g.drawRect(x0, z0, x1 - x0, z1 - z0);
					g.drawString(r.kind().label + " " + r.id(), x0 + 3, z0 + 12);
				}
			}
			g.setColor(Color.RED);
			for (DungeonLayout.Door d : layout.doors) g.fillRect(px(snap, fit.worldX(d.x()), true) - 3, px(snap, fit.worldZ(d.y()), false) - 3, 7, 7);
			// Where each door is placed from the grid (magenta), and the doorway found in the wall there (green ring; none when it isn't found).
			TrailPlan plan = new TrailPlan(snap.blocks(), new TrailPlan.Fix());
			for (DungeonLayout.Door d : layout.doors) {
				int[] at = fit.door(layout, d);
				g.setColor(Color.MAGENTA);
				g.fillRect(px(snap, at[0] + 0.5, true) - 2, px(snap, at[1] + 0.5, false) - 2, 5, 5);
				Spot found = plan.place(new TrailPlan.Stop(at[0], at[1], at[2] == 1 ? 'x' : 'z'), feet);
				if (found != null) {
					g.setColor(Color.GREEN);
					g.drawOval(px(snap, found.x() + 0.5, true) - 5, px(snap, found.z() + 0.5, false) - 5, 10, 10);
				}
				System.out.println("door " + d.a() + "-" + d.b() + " " + d.lock() + " grid " + at[0] + "," + at[1] + (at[2] == 1 ? " x" : " z") + " found " + found);
				// Only a door whose blocks were saved can be looked for.
				if (blocks.loaded(at[0], at[1])) doorsOff.add(found == null ? 99.0 : Math.max(Math.abs(found.x() - at[0]), Math.abs(found.z() - at[1])));
			}
		}
		// The trail as the game drew it (white), and as planned again here (cyan), with where each stop was placed (green).
		g.setStroke(new BasicStroke(1));
		g.setColor(Color.WHITE);
		for (DungeonSnap.Shown shown : snap.trails) {
			for (int i = 0; i + 1 < shown.points.size(); i++) {
				double[] a = shown.points.get(i), b = shown.points.get(i + 1);
				g.drawLine(px(snap, a[0], true), px(snap, a[2], false), px(snap, b[0], true), px(snap, b[2], false));
			}
			g.setColor(Color.ORANGE);
			for (int[] s : shown.stops) g.drawOval(px(snap, s[0] + 0.5, true) - 4, px(snap, s[1] + 0.5, false) - 4, 8, 8);
			g.setColor(Color.WHITE);
		}
		g.setStroke(new BasicStroke(2));
		for (Replayed r : replayed) {
			g.setColor(Color.CYAN);
			for (int i = 0; i + 1 < r.route().line().size(); i++) {
				Spot a = r.route().line().get(i), b = r.route().line().get(i + 1);
				g.drawLine(px(snap, a.x() + 0.5, true), px(snap, a.z() + 0.5, false), px(snap, b.x() + 0.5, true), px(snap, b.z() + 0.5, false));
			}
			if (r.route().marker() != null) {
				g.setColor(Color.MAGENTA);
				g.drawRect(px(snap, r.route().marker().x() + 0.5, true) - 5, px(snap, r.route().marker().z() + 0.5, false) - 5, 10, 10);
			}
			g.setColor(Color.GREEN);
			for (Spot p : r.placed()) g.fillOval(px(snap, p.x() + 0.5, true) - 4, px(snap, p.z() + 0.5, false) - 4, 8, 8);
		}
		g.setColor(new Color(40, 120, 255));
		g.fillOval(px(snap, snap.x, true) - 5, px(snap, snap.z, false) - 5, 10, 10);
		g.setColor(Color.WHITE);
		g.drawString("y=" + feet + "  origin=" + snap.origin + "  corner=" + Math.round(snap.offsetX) + "," + Math.round(snap.offsetZ) + "  scale=" + snap.scale, 6, snap.sizeZ * ZOOM - 8);
		g.dispose();
		return img;
	}

	private static int px(DungeonSnap snap, double world, boolean x) {
		return (int) Math.round((world - (x ? snap.minX : snap.minZ)) * ZOOM);
	}

	/** Every snap kept in the repo, and any in the folder LAZARUS_SNAPS names. */
	private static List<Path> snaps() throws IOException {
		List<Path> out = new ArrayList<>();
		for (String dir : new String[]{"src/test/resources/dungeon-snaps", System.getenv("LAZARUS_SNAPS")}) {
			if (dir == null || !Files.isDirectory(Path.of(dir))) continue;
			try (Stream<Path> files = Files.list(Path.of(dir))) {
				files.filter(f -> f.getFileName().toString().endsWith(".json.gz")).sorted().forEach(out::add);
			}
		}
		return out;
	}

	@Test
	void everySnapIsPlannedAgainAndDrawn() throws IOException {
		Path dir = Path.of("build/snaps");
		Files.createDirectories(dir);
		for (Path file : snaps()) {
			DungeonSnap snap = DungeonSnap.read(file);
			List<Replayed> replayed = replay(snap);
			ImageIO.write(draw(snap, replayed), "png", dir.resolve(file.getFileName().toString().replace(".json.gz", ".png")).toFile());
			StringBuilder said = new StringBuilder(file.getFileName() + ": you at " + (int) snap.x + "," + (int) snap.y + "," + (int) snap.z + ", origin " + snap.origin + ", map corner " + snap.offsetX + "," + snap.offsetZ + ", scale " + snap.scale);
			for (int i = 0; i < replayed.size(); i++) said.append("\n  trail ").append(i).append(": stops ").append(snap.trails.get(i).stops.stream().map(s -> s[0] + "," + s[1] + (char) s[2]).toList()).append(" placed ").append(replayed.get(i).placed()).append(" route ").append(replayed.get(i).route());
			System.out.println(said);
		}
	}

	@Test
	void everyDoorOfARealDungeonIsPlacedInItsDoorwayByTheGrid() throws IOException {
		// A floor 1 dungeon saved on Dragsim (2026-11), ten doors in sight. The map draws each door two or three
		// blocks from its doorway; the grid puts every one of them in it.
		DungeonSnap snap = DungeonSnap.read(Path.of("src/test/resources/dungeon-snaps/f1-blaze-room.json.gz"));
		doorsOff.clear();
		draw(snap, replay(snap));
		assertTrue(doorsOff.size() >= 10, "the doors in the saved blocks: " + doorsOff);
		for (double off : doorsOff) assertTrue(off <= 1, "a doorway found within a block of where the grid puts its door: " + doorsOff);
	}

	@Test
	void aSnapSavedIsTheSameDungeonReadBack(@TempDir Path dir) throws IOException {
		// Two rooms with a wall between along x = 10 and a doorway three wide in it; you in the first, a trail asked through the door.
		DungeonSnap snap = new DungeonSnap();
		snap.minX = -5;
		snap.minY = 60;
		snap.minZ = -5;
		snap.sizeX = 40;
		snap.sizeY = 12;
		snap.sizeZ = 30;
		int air = snap.kindOf("minecraft:air", false), stone = snap.kindOf("minecraft:stone", true), bars = snap.kindOf("minecraft:iron_bars", true);
		int[] kinds = new int[snap.sizeX * snap.sizeY * snap.sizeZ];
		int i = 0;
		for (int y = 60; y < 72; y++) {
			for (int z = -5; z < 25; z++) {
				for (int x = -5; x < 35; x++) {
					boolean doorway = z >= 9 && z <= 11 && y <= 67;
					kinds[i++] = y <= 64 ? stone : x == 10 ? (doorway ? (y <= 66 ? bars : air) : stone) : air;
				}
			}
		}
		snap.pack(kinds);
		snap.x = 2.5;
		snap.y = 65;
		snap.z = 3.5;
		DungeonSnap.Shown shown = new DungeonSnap.Shown();
		shown.stops.add(new int[]{12, 8, 'x'}); // the map puts the door two blocks off
		snap.trails.add(shown);
		snap.write(dir.resolve("made.json.gz"));
		DungeonSnap back = DungeonSnap.read(dir.resolve("made.json.gz"));
		assertEquals("minecraft:iron_bars", back.block(10, 65, 10));
		assertEquals("minecraft:stone", back.block(10, 65, 3));
		assertEquals("", back.block(100, 65, 3));
		List<Replayed> replayed = replay(back);
		assertEquals(List.of(new Spot(10, 65, 10)), replayed.getFirst().placed(), "the shut door's own middle, not where the map put it");
		Spot end = replayed.getFirst().route().line().getLast();
		assertTrue(end.x() < 10 && end.across(new Spot(10, 65, 10)) <= 2.5, "the trail leads up to the shut door: " + replayed);
		org.junit.jupiter.api.Assertions.assertNull(replayed.getFirst().route().marker());
		assertEquals(40 * ZOOM, draw(back, replayed).getWidth());
	}
}
