package dev.lauchy.lazarus.feature.staff;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The staff desk's line icons, drawn from the same 24-unit SVG paths as the design (docs/dragsim/desk-design) with a
 * 2-unit round stroke. Each is a list of shapes in the 24×24 box; {@link DeskMasks} strokes them at the size wanted.
 */
enum DeskIcons {
	SHIELD("M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"),
	SEARCH("c11 11 7", "M20 20l-3.5-3.5"),
	REFRESH("M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5"),
	CHEVRON_DOWN("M6 9l6 6 6-6"),
	CHEVRON_UP("M18 15l-6-6-6 6"),
	CHEVRON_RIGHT("M9 6l6 6-6 6"),
	CLOSE("M6 6l12 12M18 6L6 18"),
	BAN("c12 12 9", "M5.6 5.6l12.8 12.8"),
	MIC_OFF("M4 4l16 16", "M9 9v2a3 3 0 0 0 5.1 2.1M15 9.3V5a3 3 0 0 0-5.7-1.3", "M19 11a7 7 0 0 1-1.2 3.9M5 11a7 7 0 0 0 11.1 5.7M12 18v3"),
	WARNING("M12 3L2 20h20z", "M12 10v4M12 17h.01"),
	KICK("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9"),
	FILE("M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M14 3v6h6", "M8 13h8M8 17h5"),
	TREND("M3 17l6-6 4 4 8-8", "M15 7h6v6"),
	COPY("r9 9 12 12 2", "M5 15V5a2 2 0 0 1 2-2h10"),
	LOCK("r5 11 14 10 2", "M8 11V7a4 4 0 0 1 8 0v4"),
	CHECK("M20 6L9 17l-5-5"),
	ALERT("c12 12 9", "M12 8v5M12 16h.01"),
	ARROW_RIGHT("M5 12h14M13 6l6 6-6 6");

	/** Each part: an SVG path ("M…"), a circle ("c cx cy r") or a rounded rectangle ("r x y w h rx"). */
	private final String[] parts;
	private List<Shape> shapes;

	DeskIcons(String... parts) {
		this.parts = parts;
	}

	/** The icon's shapes in its 24×24 box. */
	List<Shape> shapes() {
		if (shapes == null) {
			List<Shape> out = new ArrayList<>();
			for (String p : parts) out.add(shape(p));
			shapes = List.copyOf(out);
		}
		return shapes;
	}

	private static Shape shape(String part) {
		if (part.startsWith("c")) {
			double[] n = numbers(part.substring(1));
			return new Ellipse2D.Double(n[0] - n[2], n[1] - n[2], n[2] * 2, n[2] * 2);
		}
		if (part.startsWith("r")) {
			double[] n = numbers(part.substring(1));
			return new RoundRectangle2D.Double(n[0], n[1], n[2], n[3], n[4] * 2, n[4] * 2);
		}
		return path(part);
	}

	private static double[] numbers(String s) {
		String[] t = s.trim().split("\\s+");
		double[] out = new double[t.length];
		for (int i = 0; i < t.length; i++) out[i] = Double.parseDouble(t[i]);
		return out;
	}

	/** An SVG path's M, L, H, V, C, A and Z commands, absolute or relative. */
	static Path2D path(String d) {
		Path2D.Double p = new Path2D.Double();
		Tokens t = new Tokens(d);
		double x = 0, y = 0, sx = 0, sy = 0;
		char cmd = 'M';
		while (t.more()) {
			if (t.isCommand()) cmd = t.command();
			boolean rel = Character.isLowerCase(cmd);
			switch (Character.toUpperCase(cmd)) {
				case 'M' -> {
					x = t.num() + (rel ? x : 0);
					y = t.num() + (rel ? y : 0);
					p.moveTo(x, y);
					sx = x;
					sy = y;
					cmd = rel ? 'l' : 'L'; // further pairs are lines
				}
				case 'L' -> {
					x = t.num() + (rel ? x : 0);
					y = t.num() + (rel ? y : 0);
					p.lineTo(x, y);
				}
				case 'H' -> {
					x = t.num() + (rel ? x : 0);
					p.lineTo(x, y);
				}
				case 'V' -> {
					y = t.num() + (rel ? y : 0);
					p.lineTo(x, y);
				}
				case 'C' -> {
					double x1 = t.num() + (rel ? x : 0), y1 = t.num() + (rel ? y : 0), x2 = t.num() + (rel ? x : 0), y2 = t.num() + (rel ? y : 0);
					double ex = t.num() + (rel ? x : 0), ey = t.num() + (rel ? y : 0);
					p.curveTo(x1, y1, x2, y2, ex, ey);
					x = ex;
					y = ey;
				}
				case 'A' -> {
					double rx = t.num(), ry = t.num(), rot = t.num(), large = t.num(), sweep = t.num();
					double ex = t.num() + (rel ? x : 0), ey = t.num() + (rel ? y : 0);
					arc(p, x, y, rx, ry, rot, large != 0, sweep != 0, ex, ey);
					x = ex;
					y = ey;
				}
				case 'Z' -> {
					p.closePath();
					x = sx;
					y = sy;
				}
				default -> throw new IllegalArgumentException("Unsupported path command " + cmd + " in " + d);
			}
		}
		return p;
	}

	/** An SVG elliptical arc (endpoint form) from (x0, y0) to (x, y), appended to {@code p}. Rotation is ignored: the icons' arcs are circular. */
	private static void arc(Path2D p, double x0, double y0, double rx, double ry, double rot, boolean large, boolean sweep, double x, double y) {
		if (rx == 0 || ry == 0) {
			p.lineTo(x, y);
			return;
		}
		double dx = (x0 - x) / 2, dy = (y0 - y) / 2;
		double lambda = dx * dx / (rx * rx) + dy * dy / (ry * ry);
		if (lambda > 1) {
			rx *= Math.sqrt(lambda);
			ry *= Math.sqrt(lambda);
		}
		double num = rx * rx * ry * ry - rx * rx * dy * dy - ry * ry * dx * dx;
		double den = rx * rx * dy * dy + ry * ry * dx * dx;
		double coef = (large == sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den));
		double cxp = coef * rx * dy / ry, cyp = -coef * ry * dx / rx;
		double cx = cxp + (x0 + x) / 2, cy = cyp + (y0 + y) / 2;
		double start = Math.atan2((dy - cyp) / ry, (dx - cxp) / rx);
		double end = Math.atan2((-dy - cyp) / ry, (-dx - cxp) / rx);
		double delta = end - start;
		if (sweep && delta < 0) delta += Math.PI * 2;
		if (!sweep && delta > 0) delta -= Math.PI * 2;
		// Java's arcs run counterclockwise on screen, SVG angles clockwise: both signs flip.
		Arc2D.Double a = new Arc2D.Double(cx - rx, cy - ry, rx * 2, ry * 2, -Math.toDegrees(start), -Math.toDegrees(delta), Arc2D.OPEN);
		p.append(a, true);
	}

	/** Reads an SVG path's commands and numbers ("M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z", ".01", "-6.7"). */
	private static final class Tokens {
		private final String s;
		private int i;

		Tokens(String s) {
			this.s = s;
		}

		private void skip() {
			while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',')) i++;
		}

		boolean more() {
			skip();
			return i < s.length();
		}

		boolean isCommand() {
			skip();
			return i < s.length() && Character.isLetter(s.charAt(i));
		}

		char command() {
			return s.charAt(i++);
		}

		double num() {
			skip();
			int start = i;
			if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
			boolean dot = false;
			while (i < s.length()) {
				char c = s.charAt(i);
				if (Character.isDigit(c)) i++;
				else if (c == '.' && !dot) {
					dot = true;
					i++;
				} else break;
			}
			return Double.parseDouble(s.substring(start, i));
		}
	}
}
