package dev.lauchy.lazarus.feature.dungeon;

/**
 * Where the moving pane of the "Click the button on time!" terminal will be by the time a click of yours lands. What
 * you see is the server's board from half your ping ago, and your click takes the other half to get there, so a
 * click made when the pane looks right arrives a whole ping late. The pane steps along its row at a steady beat and
 * turns back at each end: watching it gives the beat, and from that where it'll be a ping from now. No Minecraft
 * types, so it's tested on its own.
 */
final class MelodyTiming {
	/** The row's first and last columns. */
	private static final int FIRST = 1, LAST = 5;

	private int row = -1, col = -1, way;
	private long movedAt;
	private double beat;

	/** Notes where the pane is seen now: its row and column, at this time in milliseconds. */
	void see(int row, int col, long now) {
		if (row != this.row) {
			// Another row is another tune: its beat is learnt afresh.
			this.row = row;
			this.col = col;
			way = 0;
			beat = 0;
			movedAt = now;
			return;
		}
		if (col == this.col) return;
		long took = now - movedAt;
		if (took > 50 && took < 3000) beat = beat == 0 ? took : beat * 0.5 + took * 0.5;
		way = Integer.signum(col - this.col);
		this.col = col;
		movedAt = now;
	}

	/** The column the pane will be in {@code ahead} milliseconds from now; where it is, until its beat is known. */
	int at(long now, long ahead) {
		if (beat == 0 || way == 0 || ahead <= 0) return col;
		int steps = (int) ((now - movedAt + ahead) / beat), c = col, w = way;
		for (int i = 0; i < steps; i++) {
			if (c + w < FIRST || c + w > LAST) w = -w;
			c += w;
		}
		return c;
	}
}
