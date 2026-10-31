package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Theme;
import moe.nea.libautoupdate.CurrentVersion;
import moe.nea.libautoupdate.GithubReleaseUpdateSource;
import moe.nea.libautoupdate.ReplaceJarUpdateTarget;
import moe.nea.libautoupdate.UpdateContext;
import moe.nea.libautoupdate.UpdateData;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Says when a newer Lazarus build has been published. Every merge to main makes a GitHub
 * release in the public lauchydev/Lazarus-releases repo (the source repo is private); its jar's checksum is compared with the running jar's. The check itself never
 * downloads or changes anything: you get a toast and a chat link, and the Prism pre-launch
 * script (tools/update-lazarus.ps1) installs it on your next launch.
 * <p>
 * {@code /lazarus update} installs it from inside the game instead, through libautoupdate:
 * the newest release is downloaded now and swapped in for the running jar once
 * Minecraft closes.
 */
public final class UpdateNotice extends Feature {
	public static UpdateNotice INSTANCE;

	/** Where the menu's "Check for updates" button stands. */
	public enum Status { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, DOWNLOADED, FAILED, DEV }

	private static final String LATEST = "https://api.github.com/repos/lauchydev/Lazarus-releases/releases/latest";
	private static final String UPDATE_ID = "lazarus";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	private final IntSetting every = add(new IntSetting("interval", "Check every", "How often to look for a new build.", 2, 1, 120, " min"));

	private volatile String announced;
	private UpdateContext updates;
	/** The build /lazarus update has downloaded and will install on exit, or null. */
	private volatile String pending;
	private volatile boolean updating;
	/** A new build found while no world was open (the toast and chat only show in one), held until there is. */
	private volatile String[] waiting;
	private Path self;
	private volatile Status status = Status.IDLE;
	/** The newest release's tag, once a check has seen it. */
	private volatile String latest;
	/** What the last /lazarus update promised to install, so the next start can tell whether it did. */
	private final Path marker = Config.dir().resolve("pending-update.json");
	/** Set at start when the last download was never swapped in: {tag, jar it was to replace}. */
	private volatile String[] swapFailed;

	public UpdateNotice() {
		super("updateNotice", "Update Notice", "Tells you in game when a newer Lazarus build is out.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		// Registered even when the notice is off: updating by hand shouldn't depend on it.
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("update").executes(c -> {
			update();
			return 1;
		})));
		self = ownJar();
		if (self == null) {
			status = Status.DEV;
			return;
		}
		// Some launchers (Lunar) run a copy of the mods folder; this says which file an update replaces.
		Lazarus.LOG.info("Lazarus is running from {}", self); // running from a dev workspace, not an installed jar
		updates = new UpdateContext(new Releases(), new ReplaceJarUpdateTarget(self.toFile()), CurrentVersion.of(build()), UPDATE_ID);
		Lazarus.LOG.info("Lazarus updates stage in {}", Path.of(".autoupdates").toAbsolutePath());
		updates.cleanup(); // leftovers from the last /lazarus update
		swapFailed = checkLastUpdate();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (swapFailed != null && mc.player != null) {
				tellSwapFailed(swapFailed);
				swapFailed = null;
			}
			String[] notice = waiting;
			if (notice == null || mc.player == null) return;
			waiting = null;
			announce(mc, notice[0], notice[1]);
		});
		ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "Lazarus update check");
			t.setDaemon(true);
			return t;
		});
		timer.schedule(new Runnable() {
			@Override
			public void run() {
				if (enabled()) check();
				timer.schedule(this, every.get(), TimeUnit.MINUTES);
			}
		}, 20, TimeUnit.SECONDS);
	}

	public Status status() {
		return pending != null ? Status.DOWNLOADED : updating ? Status.DOWNLOADING : status;
	}

	public String latest() {
		return latest == null ? null : shown(latest);
	}

	/** The menu's button: looks now, off the game thread, whether or not the notice is on. */
	public void checkNow() {
		if (self == null || status == Status.CHECKING) return;
		status = Status.CHECKING;
		CompletableFuture.runAsync(this::check);
	}

	/** The menu's button once a newer build is found: the same as /lazarus update. */
	public void install() {
		update();
	}

	private void check() {
		Status result = Status.FAILED;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST)).timeout(Duration.ofSeconds(30))
					.header("User-Agent", "Lazarus-update-check").header("Accept", "application/vnd.github+json").GET().build();
			HttpResponse<String> r = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				Lazarus.LOG.info("Lazarus update check: GitHub answered {}", r.statusCode());
				return;
			}
			JsonObject release = JsonParser.parseString(r.body()).getAsJsonObject();
			String tag = release.get("tag_name").getAsString();
			latest = tag;
			if (tag.equals(announced)) {
				result = Status.AVAILABLE;
				return;
			}
			String digest = null;
			for (JsonElement a : release.getAsJsonArray("assets")) {
				JsonObject o = a.getAsJsonObject();
				String name = o.get("name").getAsString();
				if (name.startsWith("lazarus-") && name.endsWith(".jar") && o.has("digest") && !o.get("digest").isJsonNull()) {
					digest = o.get("digest").getAsString();
				}
			}
			boolean current = digest != null && digest.equalsIgnoreCase("sha256:" + sha256(self));
			Lazarus.LOG.info("Lazarus update check: latest is {}, {}", tag, digest == null ? "no checksum to compare" : current ? "already running it" : "newer than this jar");
			if (digest == null || current) {
				result = Status.CURRENT;
				return;
			}
			result = Status.AVAILABLE;
			announced = tag;
			// Held for the next tick in a world: on the title screen the toast never draws and
			// there is no chat, so announcing there would be lost.
			waiting = new String[]{tag, release.get("html_url").getAsString()};
		} catch (IOException | RuntimeException e) {
			Lazarus.LOG.info("Lazarus update check failed: {}", e.toString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			status = result;
		}
	}

	private static void announce(Minecraft mc, String tag, String page) {
		Toast.show("§fLazarus " + shown(tag) + " §7is available", Theme.ACCENT);
		Message.lazarus().info("Lazarus ").highlight(shown(tag)).info(" is available.").line()
				.command("Update now", "Click to download it. It installs when you close Minecraft.", "/lazarus update")
				.info("  ").link("Release notes", "Click to open the release page", page).send();
	}

	/** /lazarus update: downloads the newest release and installs it when the game closes. */
	private void update() {
		if (updates == null) {
			Message.lazarus().error("Updates need a released Lazarus jar.").send();
			return;
		}
		if (pending != null) {
			Message.lazarus().highlight(shown(pending)).info(" is ready. It installs when you close Minecraft.").send();
			return;
		}
		if (updating) {
			Message.lazarus().info("Still checking…").send();
			return;
		}
		updating = true;
		Message.lazarus().info("Checking for updates…").send();
		updates.checkUpdate("full").thenCompose(u -> {
			if (!u.isUpdateAvailable()) {
				Message m = Message.lazarus().success("Lazarus is up to date.");
				tellLater(build() > 0 ? m.muted(" " + version()) : m);
				return CompletableFuture.completedFuture(null);
			}
			String tag = Releases.tag(u.getUpdate().getVersionNumber().getAsInt());
			tellLater(Message.lazarus().info("Downloading ").highlight(shown(tag)).info("…"));
			return u.launchUpdate().thenRun(() -> {
				pending = tag;
				announced = tag;
				JsonObject m = new JsonObject();
				m.addProperty("tag", tag);
				m.addProperty("build", u.getUpdate().getVersionNumber().getAsInt());
				m.addProperty("jar", self.toAbsolutePath().toString());
				Config.writeJson(marker, m);
				waiting = null;
				tellLater(Message.lazarus().highlight(shown(tag)).success(" is ready.").info(" It installs when you close Minecraft."));
			});
		}).whenComplete((v, e) -> {
			updating = false;
			if (e == null) return;
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			Lazarus.LOG.warn("Lazarus update failed", cause);
			tellLater(Message.lazarus().error("The update didn't finish. Try again.").muted(" " + cause.getMessage()));
		});
	}

	/**
	 * The swap happens in a small process libautoupdate starts as Minecraft shuts down. Some
	 * launchers (Lunar) end the game without running shutdown hooks, so it never starts and
	 * the old jar stays. Caught here on the next start, from the marker the download left.
	 * @return {tag, jar} when the last update did not install, else null
	 */
	private String[] checkLastUpdate() {
		JsonElement json = Config.readJson(marker);
		if (json == null || !json.isJsonObject()) return null;
		try {
			Files.deleteIfExists(marker);
		} catch (IOException ignored) {
			// Told at most once per start anyway.
		}
		try {
			JsonObject m = json.getAsJsonObject();
			if (build() >= m.get("build").getAsInt()) {
				Lazarus.LOG.info("Lazarus update to {} installed", m.get("tag").getAsString());
				return null;
			}
			Lazarus.LOG.warn("Lazarus update to {} was downloaded but never swapped in for {}", m.get("tag").getAsString(), m.get("jar").getAsString());
			return new String[]{m.get("tag").getAsString(), m.get("jar").getAsString()};
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static void tellSwapFailed(String[] failed) {
		Path jar = Path.of(failed[1]);
		Message.lazarus().error(shown(failed[0]) + " didn't install.").info(" Your launcher closed Minecraft too soon.")
				.line().info("Replace ").highlight(jar.getFileName().toString()).info(" with the new jar while the game is closed.")
				.line().folder("Open mods folder", "Click to open the folder the jar is in", jar.getParent()).info("  ")
				.link("Open release", "Click to open the release page", "https://github.com/lauchydev/Lazarus-releases/releases/tag/" + failed[0]).send();
	}

	/** Says the message from any thread, once the game thread gets to it. */
	private static void tellLater(Message message) {
		Component built = message.build();
		Minecraft.getInstance().execute(() -> Message.send(built));
	}

	/** This jar's version, e.g. 0.2.75. */
	public static String version() {
		return FabricLoader.getInstance().getModContainer(Lazarus.ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	/**
	 * A release tag as players see it: "v0.3.0-171" reads "v0.3.0". The CI run number on the
	 * end only orders releases (and keeps builds from before semver updating).
	 */
	static String shown(String tag) {
		return tag.replaceFirst("-\\d+$", "");
	}

	/** The CI run number this jar was built by (the last part of its version), or 0 for a local build. */
	private static int build() {
		return FabricLoader.getInstance().getModContainer(Lazarus.ID)
				.map(m -> m.getMetadata().getCustomValue("lazarus:build"))
				.filter(v -> v.getType() == CustomValue.CvType.STRING)
				.map(v -> Releases.number(v.getAsString()))
				.orElse(0);
	}

	/**
	 * Lazarus releases, ordered by the CI run number at the end of the tag (v0.3.0-171, or the
	 * older v0.2.75 and build-N forms) so an update is never a downgrade.
	 */
	private static final class Releases extends GithubReleaseUpdateSource {
		Releases() {
			super("lauchydev", "Lazarus-releases");
		}

		/** Release tags by change number, to name a release in chat. */
		private static final java.util.Map<Integer, String> TAGS = new java.util.concurrent.ConcurrentHashMap<>();

		static String tag(int number) {
			return TAGS.getOrDefault(number, "build " + number);
		}

		/**
		 * The newest release by run number. The library picks by a release's date, but every
		 * Lazarus release tags the same releases-repo commit, so the dates tie and it could
		 * hand back the release one before the newest: updating went one version at a time.
		 */
		@Override
		protected UpdateData selectUpdate(String stream, java.util.List<GithubRelease> releases) {
			return releases.stream()
					.filter(r -> !r.isDraft() && !r.isPrerelease() && r.getTagName() != null)
					.map(this::findAsset)
					.filter(java.util.Objects::nonNull)
					.max(java.util.Comparator.comparingInt(d -> d.getVersionNumber().getAsInt()))
					.orElse(null);
		}

		@Override
		protected UpdateData findAsset(GithubRelease release) {
			UpdateData data = super.findAsset(release);
			if (data == null) return null;
			int number = number(release.getTagName().replaceFirst("^.*[.-]", ""));
			TAGS.put(number, release.getTagName());
			data.setVersionNumber(new JsonPrimitive(number));
			return data;
		}

		static int number(String s) {
			try {
				return Math.max(0, Integer.parseInt(s.trim()));
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	}

	/** The installed Lazarus jar, or null when not running from one. */
	private static Path ownJar() {
		return FabricLoader.getInstance().getModContainer(Lazarus.ID)
				.flatMap(m -> m.getOrigin().getPaths().stream().findFirst())
				.filter(p -> p.toString().endsWith(".jar") && Files.isRegularFile(p))
				.orElse(null);
	}

	private static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
