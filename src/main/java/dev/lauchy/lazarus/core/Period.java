package dev.lauchy.lazarus.core;

/** Which of a tracker's two counts a readout shows: this game session, or everything saved so far. */
public enum Period {
	SESSION, ALL_TIME;

	/** How the HUD titles it: "Session" or "All time". */
	public String label() {
		return this == SESSION ? "Session" : "All time";
	}
}
