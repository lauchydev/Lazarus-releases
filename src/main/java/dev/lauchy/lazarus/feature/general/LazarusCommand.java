package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.TextSetting;

/**
 * A name of your own for /lazarus: set it to "test" and /test online runs /lazarus online, /test changelog
 * runs /lazarus changelog, and so on for every Lazarus command, with tab completion. It's a real command that
 * points at /lazarus ({@link Commands}), registered as you join a world. Empty, the default, adds nothing.
 */
public final class LazarusCommand extends Feature {
	private final TextSetting name = add(new TextSetting("name", "Short name", "What you type instead of lazarus, without the /: test gives /test online. Letters, digits, _ and -. Takes effect when you next join a world.", "", 16));

	public LazarusCommand() {
		super("lazarusCommand", "Lazarus Command", "A short name of your own for /lazarus and every command under it: set it to test and /test online works.", Category.GENERAL, true);
		Commands.alias(() -> enabled() ? name.get() : "");
	}

	@Override
	public void init() {
		// Nothing to start: the name is read each time the commands are registered.
	}
}
