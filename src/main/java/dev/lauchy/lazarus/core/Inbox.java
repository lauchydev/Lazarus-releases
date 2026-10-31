package dev.lauchy.lazarus.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The in-game messages the Lazarus server keeps for this account, each shown once: the server forgets one
 * when the mod says it was shown, and until it hears that, this keeps the same one from showing twice here.
 */
final class Inbox {
	/** One message from the server: what it's about (its id), its kind, the toast's title and the chat text. */
	record Message(String id, String kind, String title, String text) {
	}

	private final Map<String, Message> waiting = new LinkedHashMap<>();
	private final Set<String> shown = new HashSet<>();

	/** The server's unseen messages, from {@code /verify} or {@code /plus/me}. */
	synchronized void offer(List<Message> messages) {
		for (Message m : messages) if (!shown.contains(m.id())) waiting.putIfAbsent(m.id(), m);
	}

	/** The messages to show now, oldest first; each comes out once. */
	synchronized List<Message> take() {
		List<Message> out = new ArrayList<>(waiting.values());
		waiting.clear();
		for (Message m : out) shown.add(m.id());
		return out;
	}
}
