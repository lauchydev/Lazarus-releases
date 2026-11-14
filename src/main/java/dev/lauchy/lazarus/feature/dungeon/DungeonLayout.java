package dev.lauchy.lazarus.feature.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dungeon as its map draws it: a grid of rooms (some spanning several cells), the doors between them, what
 * kind each room is by its colour, and whether it's cleared by the tick drawn on it. Read from the map's 128×128
 * pixels alone, so it knows rooms nobody has walked into yet. No Minecraft types, so it's tested on its own.
 */
public final class DungeonLayout {
	/** The map's side in pixels. */
	public static final int SIZE = 128;

	/** What a room is, by the colour the map fills it with. */
	public enum Kind {
		ENTRANCE("Entrance", 0xFF007C00), NORMAL("Room", 0xFF72431B), PUZZLE("Puzzle", 0xFFB24CD8), MINIBOSS("Mini boss", 0xFFE5E533),
		TRAP("Trap", 0xFFD87F33), FAIRY("Fairy", 0xFFF27FA5), BLOOD("Blood", 0xFFFF0000), UNKNOWN("Unknown", 0xFF8C8C8C);

		public final String label;
		/** The colour its trail and tracker row take. */
		public final int colour;

		Kind(String label, int colour) {
			this.label = label;
			this.colour = colour;
		}

		/** Whether the tracker lists it: every kind but the entrance, plain rooms and rooms not yet seen. */
		public boolean special() {
			return this == PUZZLE || this == MINIBOSS || this == TRAP || this == FAIRY || this == BLOOD;
		}
	}

	/** How far along a room is: not seen into yet (grey, "?"), open, cleared (a tick) or failed (a cross). */
	public enum State {UNSEEN, OPEN, CLEARED, FAILED}

	/** What a door needs: nothing, a Wither Key, or the Blood Key. */
	public enum Lock {NONE, WITHER, BLOOD}

	/** One room: its kind and state, and the grid cells it covers ({column, row} each). */
	public record Room(int id, Kind kind, State state, List<int[]> cells) {
		/** Its middle in map pixels, for a layout with this cell size and origin. */
		double[] centre(DungeonLayout l) {
			double x = 0, y = 0;
			for (int[] c : cells) {
				x += l.originX + c[0] * l.pitch + l.roomSize / 2.0;
				y += l.originY + c[1] * l.pitch + l.roomSize / 2.0;
			}
			return new double[]{x / cells.size(), y / cells.size()};
		}
	}

	/**
	 * A door between two rooms (by id): its lock, its middle in map pixels, and what its own colour gives away. The
	 * map draws a door in the colour of the special room it leads to before that room has been seen into, so a pink
	 * door on a "?" room says a fairy room is behind it; null when the door's colour says nothing.
	 */
	public record Door(int a, int b, Lock lock, double x, double y, Kind hint) {
		public Door(int a, int b, Lock lock, double x, double y) {
			this(a, b, lock, x, y, null);
		}
	}

	/** A pixel's colour as the reader sorts it. */
	enum Ink {EMPTY, BLACK, GREY, WHITE, RED, ORANGE, BROWN, YELLOW, GREEN, PURPLE, PINK, OTHER}

	public final int roomSize, pitch, originX, originY;
	public final List<Room> rooms;
	public final List<Door> doors;

	private DungeonLayout(int roomSize, int pitch, int originX, int originY, List<Room> rooms, List<Door> doors) {
		this.roomSize = roomSize;
		this.pitch = pitch;
		this.originX = originX;
		this.originY = originY;
		this.rooms = List.copyOf(rooms);
		this.doors = List.copyOf(doors);
	}

	/** The room covering a map pixel, or null between rooms and off the dungeon. */
	public Room roomAt(double px, double py) {
		int gx = (int) Math.floor((px - originX) / pitch), gy = (int) Math.floor((py - originY) / pitch);
		for (Room r : rooms) for (int[] c : r.cells()) if (c[0] == gx && c[1] == gy) return r;
		return null;
	}

	public Room room(int id) {
		for (Room r : rooms) if (r.id() == id) return r;
		return null;
	}

	/**
	 * The doors to go through from one room to another, in order: the fewest, and through a locked one only when
	 * there's no other way. Empty when they're the same room; null when no doors join them.
	 */
	public static List<Door> route(List<Door> doors, int from, int to) {
		if (from == to) return List.of();
		Map<Integer, Integer> cost = new HashMap<>();
		Map<Integer, Door> by = new HashMap<>();
		java.util.PriorityQueue<int[]> open = new java.util.PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
		cost.put(from, 0);
		open.add(new int[]{from, 0});
		while (!open.isEmpty()) {
			int[] o = open.poll();
			if (o[1] > cost.get(o[0])) continue;
			if (o[0] == to) break;
			for (Door d : doors) {
				if (d.a() != o[0] && d.b() != o[0]) continue;
				int next = d.a() == o[0] ? d.b() : d.a(), c = o[1] + (d.lock() == Lock.NONE ? 1 : 100);
				if (cost.containsKey(next) && cost.get(next) <= c) continue;
				cost.put(next, c);
				by.put(next, d);
				open.add(new int[]{next, c});
			}
		}
		if (!by.containsKey(to)) return null;
		List<Door> out = new ArrayList<>();
		for (int at = to; at != from; ) {
			Door d = by.get(at);
			out.addFirst(d);
			at = d.a() == at ? d.b() : d.a();
		}
		return out;
	}

	/** What a room is, as far as the map tells: its own colour, or for one not seen into yet, what the colour of a door into it gives away. */
	public Kind kindOf(Room r) {
		if (r.kind() != Kind.UNKNOWN) return r.kind();
		for (Door d : doors) if ((d.a() == r.id() || d.b() == r.id()) && d.hint() != null) return d.hint();
		return Kind.UNKNOWN;
	}

	/** Whether a room needs nothing more: ticked, the entrance, or a puzzle failed (which can't be tried again). */
	public static boolean done(Room r) {
		return r.state() == State.CLEARED || r.state() == State.FAILED || r.kind() == Kind.ENTRANCE;
	}

	/**
	 * The rooms to point the way to from the room you're in: every special room still to do, wherever it is; the
	 * plain rooms still to do that are next door, once your own room is done (or, when none next door is left, the
	 * nearest one that is, by doors); and when every room is done, the blood room alone. Special rooms first.
	 */
	public List<Room> targets(int here) {
		Room at = room(here);
		List<Room> out = new ArrayList<>();
		if (at == null) return out;
		if (allCleared()) {
			for (Room r : rooms) if (r.kind() == Kind.BLOOD && r.id() != here) out.add(r);
			return out;
		}
		for (Room r : rooms) if (r.id() != here && !done(r) && r.kind() != Kind.BLOOD && kindOf(r).special()) out.add(r);
		if (!done(at)) return out;
		Room nearest = null;
		int nearestDoors = Integer.MAX_VALUE;
		boolean nextDoor = false;
		for (Room r : rooms) {
			if (r.id() == here || done(r) || r.kind() == Kind.BLOOD || kindOf(r).special()) continue;
			List<Door> way = route(doors, here, r.id());
			if (way == null) continue;
			if (way.size() == 1) {
				out.add(r);
				nextDoor = true;
			} else if (way.size() < nearestDoors) {
				nearestDoors = way.size();
				nearest = r;
			}
		}
		// Nothing next door left to clear: the nearest plain room that is, however many doors away.
		if (!nextDoor && nearest != null) out.add(nearest);
		return out;
	}

	/** Whether every room seen is cleared (the blood room aside): time to head back to it. */
	public boolean allCleared() {
		boolean any = false;
		for (Room r : rooms) {
			if (r.kind() == Kind.BLOOD || r.kind() == Kind.ENTRANCE) continue;
			if (r.state() != State.CLEARED) return false;
			any = true;
		}
		return any;
	}

	/**
	 * Reads a map: {@code argb} is its 128×128 pixels, row by row, 0 where nothing is drawn. Null when there's no
	 * dungeon on it yet (no entrance room to measure the grid by).
	 */
	public static DungeonLayout read(int[] argb) {
		if (argb.length != SIZE * SIZE) return null;
		Ink[] ink = new Ink[argb.length];
		for (int i = 0; i < argb.length; i++) ink[i] = ink(argb[i]);
		// The entrance is one cell of solid green: its width is a room's, and its corner sets the grid.
		int ex = -1, ey = -1, size = 0;
		find:
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (ink[y * SIZE + x] != Ink.GREEN) continue;
				int w = 0;
				while (x + w < SIZE && ink[y * SIZE + x + w] == Ink.GREEN) w++;
				int h = 0;
				while (y + h < SIZE && ink[(y + h) * SIZE + x] == Ink.GREEN) h++;
				if (w >= 10 && h >= 10) {
					ex = x;
					ey = y;
					size = w;
					break find;
				}
				x += w;
			}
		}
		if (size == 0) return null;
		int pitch = size + 4, ox = ex % pitch, oy = ey % pitch;
		int cols = (SIZE - ox + 4) / pitch, rows = (SIZE - oy + 4) / pitch;
		Ink[][] fill = new Ink[cols][rows];
		for (int gx = 0; gx < cols; gx++) {
			for (int gy = 0; gy < rows; gy++) {
				int x = ox + gx * pitch, y = oy + gy * pitch;
				// A corner pixel: a tick or "?" sits in the middle, never there.
				fill[gx][gy] = x + size <= SIZE && y + size <= SIZE ? at(ink, x + 1, y + 1) : Ink.EMPTY;
			}
		}
		// Cells joined into one room where the gap between them is filled edge to edge.
		int[] parent = new int[cols * rows];
		for (int i = 0; i < parent.length; i++) parent[i] = i;
		record Gap(int a, int b, Ink ink, double x, double y) {
		}
		List<Gap> gaps = new ArrayList<>();
		for (int gx = 0; gx < cols; gx++) {
			for (int gy = 0; gy < rows; gy++) {
				if (fill[gx][gy] == Ink.EMPTY) continue;
				int x = ox + gx * pitch, y = oy + gy * pitch, me = gx * rows + gy;
				if (gx + 1 < cols && fill[gx + 1][gy] != Ink.EMPTY) {
					Ink edge = at(ink, x + size + 1, y + 1), mid = at(ink, x + size + 1, y + size / 2);
					if (edge != Ink.EMPTY && edge == fill[gx][gy]) union(parent, me, (gx + 1) * rows + gy);
					else if (mid != Ink.EMPTY) gaps.add(new Gap(me, (gx + 1) * rows + gy, mid, x + size + 2, y + size / 2.0));
				}
				if (gy + 1 < rows && fill[gx][gy + 1] != Ink.EMPTY) {
					Ink edge = at(ink, x + 1, y + size + 1), mid = at(ink, x + size / 2, y + size + 1);
					if (edge != Ink.EMPTY && edge == fill[gx][gy]) union(parent, me, gx * rows + gy + 1);
					else if (mid != Ink.EMPTY) gaps.add(new Gap(me, gx * rows + gy + 1, mid, x + size / 2.0, y + size + 2));
				}
			}
		}
		Map<Integer, List<int[]>> cellsOf = new HashMap<>();
		for (int gx = 0; gx < cols; gx++) {
			for (int gy = 0; gy < rows; gy++) {
				if (fill[gx][gy] != Ink.EMPTY) cellsOf.computeIfAbsent(find(parent, gx * rows + gy), k -> new ArrayList<>()).add(new int[]{gx, gy});
			}
		}
		List<Room> rooms = new ArrayList<>();
		Map<Integer, Integer> idOf = new HashMap<>();
		for (var e : cellsOf.entrySet()) {
			int[] first = e.getValue().getFirst();
			Kind kind = kind(fill[first[0]][first[1]]);
			idOf.put(e.getKey(), rooms.size());
			rooms.add(new Room(rooms.size(), kind, state(ink, kind, e.getValue(), ox, oy, pitch, size), List.copyOf(e.getValue())));
		}
		List<Door> doors = new ArrayList<>();
		for (Gap g : gaps) {
			int a = idOf.get(find(parent, g.a())), b = idOf.get(find(parent, g.b()));
			if (a == b) continue;
			Lock lock = g.ink() == Ink.BLACK ? Lock.WITHER : g.ink() == Ink.RED ? Lock.BLOOD : Lock.NONE;
			Kind hint = switch (g.ink()) {
				case PINK -> Kind.FAIRY;
				case PURPLE -> Kind.PUZZLE;
				case YELLOW -> Kind.MINIBOSS;
				case ORANGE -> Kind.TRAP;
				default -> null;
			};
			doors.add(new Door(a, b, lock, g.x(), g.y(), hint));
		}
		return new DungeonLayout(size, pitch, ox, oy, rooms, doors);
	}

	/** A room's state by what's drawn over its fill: a green or white tick, a red cross, or the grey of one not seen into. */
	private static State state(Ink[] ink, Kind kind, List<int[]> cells, int ox, int oy, int pitch, int size) {
		if (kind == Kind.UNKNOWN) return State.UNSEEN;
		int ticks = 0, crosses = 0;
		for (int[] c : cells) {
			// The cell itself, not the gaps round it: a blood door beside a room is red too.
			for (int y = oy + c[1] * pitch; y < oy + c[1] * pitch + size && y < SIZE; y++) {
				for (int x = ox + c[0] * pitch; x < ox + c[0] * pitch + size && x < SIZE; x++) {
					Ink i = ink[y * SIZE + x];
					if (i == Ink.WHITE || i == Ink.GREEN && kind != Kind.ENTRANCE) ticks++;
					else if (i == Ink.RED && kind != Kind.BLOOD) crosses++;
				}
			}
		}
		if (ticks >= 4) return State.CLEARED;
		return crosses >= 4 ? State.FAILED : State.OPEN;
	}

	private static Kind kind(Ink fill) {
		return switch (fill) {
			case GREEN -> Kind.ENTRANCE;
			case PURPLE -> Kind.PUZZLE;
			case YELLOW -> Kind.MINIBOSS;
			case ORANGE -> Kind.TRAP;
			case PINK -> Kind.FAIRY;
			case RED -> Kind.BLOOD;
			case GREY, BLACK -> Kind.UNKNOWN;
			default -> Kind.NORMAL;
		};
	}

	private static Ink at(Ink[] ink, int x, int y) {
		return x < 0 || y < 0 || x >= SIZE || y >= SIZE ? Ink.EMPTY : ink[y * SIZE + x];
	}

	/** A pixel sorted by its hue, so a server's own shade of a colour still reads as that colour. */
	static Ink ink(int argb) {
		if (argb >>> 24 == 0) return Ink.EMPTY;
		int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		double v = max / 255.0, s = max == 0 ? 0 : (max - min) / (double) max;
		if (v < 0.16) return Ink.BLACK;
		if (s < 0.2) return v > 0.8 ? Ink.WHITE : Ink.GREY;
		double h;
		if (max == r) h = 60 * ((g - b) / (double) (max - min)) + (g < b ? 360 : 0);
		else if (max == g) h = 60 * ((b - r) / (double) (max - min)) + 120;
		else h = 60 * ((r - g) / (double) (max - min)) + 240;
		if (h < 12 || h >= 350) return Ink.RED;
		if (h < 45) return v > 0.7 && s > 0.6 ? Ink.ORANGE : Ink.BROWN;
		if (h < 75) return Ink.YELLOW;
		if (h < 170) return Ink.GREEN;
		if (h >= 255 && h < 320) return Ink.PURPLE;
		if (h >= 320) return Ink.PINK;
		return Ink.OTHER;
	}

	private static int find(int[] p, int i) {
		while (p[i] != i) i = p[i] = p[p[i]];
		return i;
	}

	private static void union(int[] p, int a, int b) {
		p[find(p, a)] = find(p, b);
	}
}
