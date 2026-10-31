package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Puts each screenshot you take on the clipboard as an image, ready to paste into Discord. The
 * game runs without a desktop toolkit, so Java can't hold an image on the clipboard itself; the
 * system's own tool does it instead (PowerShell on Windows, osascript on macOS, wl-copy or xclip
 * on Linux). New files in the screenshots folder are watched for, so it works however the
 * screenshot was taken, and without hooking into the game's screenshot code.
 */
public final class ScreenshotCopy extends Feature {
	/** The environment variable the copy command reads the screenshot's path from, so the path is never part of a script. */
	static final String PATH_VAR = "LAZARUS_SHOT";
	/** How long a new screenshot gets to finish being written before it's copied anyway. */
	private static final long SETTLE_MS = 5_000;
	/** How long the copy command gets before it's given up on. */
	private static final long COPY_S = 10;

	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "Show a toast once the screenshot is on your clipboard.", true));

	public ScreenshotCopy() {
		super("screenshotCopy", "Copy Screenshots", "Copies each screenshot you take to your clipboard, ready to paste.", Category.GENERAL, true);
	}

	@Override
	public void init() {
		Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
		Thread t = new Thread(() -> watch(folder), "Lazarus screenshot copy");
		t.setDaemon(true);
		t.start();
	}

	/** Waits for new pictures in the screenshots folder and copies each one while the feature is on. */
	private void watch(Path folder) {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			Files.createDirectories(folder);
			folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
			while (true) {
				WatchKey key = watcher.take();
				for (WatchEvent<?> event : key.pollEvents()) {
					if (!(event.context() instanceof Path name) || !name.toString().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
					if (enabled()) copy(folder.resolve(name));
				}
				if (!key.reset()) return;
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			Lazarus.LOG.warn("Can't watch the screenshots folder", e);
		}
	}

	/** Copies one screenshot once the game has finished writing it, and says so with a toast. */
	private void copy(Path file) throws InterruptedException {
		settle(file);
		List<String> command = command(System.getProperty("os.name", ""), System.getenv("WAYLAND_DISPLAY") != null);
		boolean ok = false;
		try {
			ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pb.environment().put(PATH_VAR, file.toAbsolutePath().toString());
			Process p = pb.start();
			ok = p.waitFor(COPY_S, TimeUnit.SECONDS) && p.exitValue() == 0;
			if (!ok) p.destroyForcibly();
		} catch (IOException e) {
			Lazarus.LOG.warn("Can't copy the screenshot with {}", command.getFirst(), e);
		}
		if (!ok) Lazarus.LOG.warn("Copying {} to the clipboard didn't work", file.getFileName());
		boolean copied = ok;
		if (toast.on()) Minecraft.getInstance().execute(() -> {
			if (copied) Toast.show("§fScreenshot §7copied", Theme.GOOD);
			else Toast.show("§fScreenshot §7couldn't be copied", Theme.BAD);
		});
	}

	/** Waits until the file stops growing, or {@link #SETTLE_MS} passes. */
	private static void settle(Path file) throws InterruptedException {
		long last = -1, until = System.currentTimeMillis() + SETTLE_MS;
		while (System.currentTimeMillis() < until) {
			Thread.sleep(150);
			long size;
			try {
				size = Files.size(file);
			} catch (IOException e) {
				continue;
			}
			if (size > 0 && size == last) return;
			last = size;
		}
	}

	/**
	 * The command that puts the PNG named by {@link #PATH_VAR} on the clipboard on this system:
	 * PowerShell on Windows, osascript on macOS, and on Linux wl-copy under Wayland or xclip otherwise.
	 */
	static List<String> command(String os, boolean wayland) {
		String name = os.toLowerCase(Locale.ROOT);
		if (name.contains("win")) return List.of("powershell", "-NoProfile", "-NonInteractive", "-STA", "-Command",
				"Add-Type -AssemblyName System.Windows.Forms, System.Drawing; "
						+ "$i = [System.Drawing.Image]::FromFile($env:" + PATH_VAR + "); [System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()");
		if (name.contains("mac")) return List.of("osascript", "-e",
				"set the clipboard to (read (POSIX file (system attribute \"" + PATH_VAR + "\")) as «class PNGf»)");
		if (wayland) return List.of("sh", "-c", "wl-copy --type image/png < \"$" + PATH_VAR + "\"");
		return List.of("sh", "-c", "xclip -selection clipboard -t image/png -i \"$" + PATH_VAR + "\"");
	}
}
