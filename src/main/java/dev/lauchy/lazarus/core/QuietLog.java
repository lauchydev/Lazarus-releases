package dev.lauchy.lazarus.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Keeps one of the game's own warnings out of the log. Dragsim gives its mobs attributes their kind doesn't have,
 * and the game writes a line for each ("Entity Zombie[...] does not have attribute minecraft:attack_speed"):
 * hundreds a second in a dungeon, burying everything else. Nothing is wrong, and nothing can be done about it here,
 * so those lines are dropped. Every other line is left alone.
 */
public final class QuietLog {
	private QuietLog() {
	}

	public static void init() {
		try {
			((Logger) LogManager.getRootLogger()).addFilter(new AbstractFilter() {
				@Override
				public Result filter(LogEvent event) {
					String said = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
					return said != null && said.startsWith("Entity ") && said.contains(" does not have attribute ") ? Result.DENY : Result.NEUTRAL;
				}
			});
		} catch (RuntimeException | LinkageError e) {
			// Another logging setup than the game's own: the lines stay.
		}
	}
}
