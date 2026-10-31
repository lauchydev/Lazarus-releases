package dev.lauchy.lazarus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Events Fabric API doesn't provide, raised by the mixins. Each listener list is
 * appended to at startup and only read afterwards.
 */
public final class Events {
	/** The action bar text was set (plain text). Fires for every update, repeats included. */
	public static final List<Consumer<String>> ACTION_BAR = new ArrayList<>();

	private Events() {
	}

	public static void actionBar(String text) {
		for (Consumer<String> l : ACTION_BAR) l.accept(text);
	}
}
