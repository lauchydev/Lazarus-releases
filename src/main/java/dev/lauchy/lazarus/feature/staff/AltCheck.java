package dev.lauchy.lazarus.feature.staff;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs the server's slow {@code /checkalts} quietly, page by page, and shares who it links to a banned
 * account or IP with every staff member ({@link FlagSync#alts}). The roster runs it when the shared result
 * is older than {@link #STALE_MS}, and any staff member can run it from the roster.
 */
final class AltCheck {
	/** How old the shared result may be before opening the roster runs the check again. */
	static final long STALE_MS = 15 * 60_000L;
	/** How long to leave it after the check went unanswered (a rank without the command) before it runs on its own again. */
	private static final long RETRY_MS = 2 * 60_000L;
	/** The most pages read in one run. */
	private static final int MAX_PAGES = 5;

	/** Whether a run is under way. */
	private static boolean running;
	/** When the check last went unanswered, 0 when it hasn't. */
	private static long unansweredAt;

	private AltCheck() {
	}

	/** Whether a run is under way. */
	static boolean running() {
		return running;
	}

	/** Whether the shared result is old enough (or missing) that opening the roster should run the check. */
	static boolean due() {
		long at = FlagSync.state().altsAt();
		return FlagSync.readAt() > 0 && System.currentTimeMillis() - at > STALE_MS && Util.getMillis() - unansweredAt > RETRY_MS;
	}

	/**
	 * Runs the check and shares what it finds; {@code done} hears "" when it was shared, else what went wrong.
	 * Nothing happens while a run, or another quiet command, is under way.
	 */
	static void run(Consumer<String> done) {
		if (Minecraft.getInstance().player == null) return;
		if (running || Reply.busy()) {
			done.accept("Wait a moment while the server answers.");
			return;
		}
		running = true;
		page(1, new ArrayList<>(), done);
	}

	/** Reads one page, following "Next: /checkalts N" until the last or {@link #MAX_PAGES}. */
	private static void page(int n, List<AltPages.Link> links, Consumer<String> done) {
		// The target is a string no chat line contains: every line of the answer is recognised by its shape instead.
		// Coloured: a banned account is told apart from the rest of its group only by its red name.
		Reply.askColoured("checkalts" + (n > 1 ? " " + n : ""), "\0", true, AltPages::looksLike, lines -> {
			if (lines.isEmpty() && n == 1) {
				unansweredAt = Util.getMillis();
				running = false;
				done.accept("The alt check didn't answer.");
				return;
			}
			links.addAll(AltPages.parse(lines));
			int next = AltPages.nextPage(lines);
			if (!lines.isEmpty() && next > n && n < MAX_PAGES) {
				if (Reply.busy()) {
					running = false;
					done.accept("The alt check was cut short.");
					return;
				}
				page(next, links, done);
				return;
			}
			FlagSync.alts(links, ok -> {
				running = false;
				done.accept(ok ? "" : "Lazarus couldn't reach its server.");
			});
		});
	}
}
