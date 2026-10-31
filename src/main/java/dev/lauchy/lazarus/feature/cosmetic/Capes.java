package dev.lauchy.lazarus.feature.cosmetic;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.Live;
import dev.lauchy.lazarus.core.LiveConnection;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wear any cape Mojang ever made. It's cosmetic: the server and players without Lazarus
 * still see your real one, while Lazarus users see each other's picks, shared through the
 * Lazarus worker ({@link LazarusServer}) and heard over the live connection ({@link LiveConnection})
 * as soon as they change. Only connected players' capes show.
 * Textures come from Mojang's texture server (or the wiki, for the few that were never
 * uploaded there), are kept under config/lazarus/capes, and are swapped into the player's
 * skin wherever the game draws them.
 *
 * <p>Wearing one is Lazarus+. Everyone with the mod, whitelisted or not, still sees members' capes (the worker
 * only lists members), and each cape carries a small Halo plus on its front so it reads as a
 * Lazarus cape rather than the real thing.
 */
public final class Capes extends Feature {
	public static Capes INSTANCE;
	private static final Path DIR = Config.dir().resolve("capes");
	private static final Path FILE = Config.dir().resolve("capes.json");
	private static final String MOJANG = "https://textures.minecraft.net/texture/";
	private static final String WIKI = "https://minecraft.wiki/w/Special:FilePath/";
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public record Cape(String key, String name, String group, String url) {
	}

	private static Cape mojang(String key, String name, String group, String hash) {
		return new Cape(key, name, group, MOJANG + hash);
	}

	private static Cape wiki(String key, String name, String group, String file) {
		return new Cape(key, name, group, WIKI + file.replace(' ', '_').replace("(", "%28").replace(")", "%29"));
	}

	public static final List<Cape> ALL = List.of(
			mojang("minecon2011", "Minecon 2011", "Minecon", "953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6"),
			mojang("minecon2012", "Minecon 2012", "Minecon", "a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6"),
			mojang("minecon2013", "Minecon 2013", "Minecon", "153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da"),
			mojang("minecon2015", "Minecon 2015", "Minecon", "b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635"),
			mojang("minecon2016", "Minecon 2016", "Minecon", "e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980"),

			mojang("mojang", "Mojang", "Mojang", "5786fe99be377dfb6858859f926c4dbc995751e91cee373468c5fbf4865e7151"),
			mojang("mojangClassic", "Mojang Classic", "Mojang", "8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761"),
			mojang("mojangStudios", "Mojang Studios", "Mojang", "9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7"),
			mojang("mojangOffice", "Mojang Office", "Mojang", "5c29410057e32abec02d870ecb52ec25fb45ea81e785a7854ae8429d7236ca26"),
			mojang("moderator", "Mojira Moderator", "Mojang", "ae677f7d98ac70a533713518416df4452fe5700365c09cf45d0d156ea9396551"),
			mojang("translator", "Translator", "Mojang", "1bf91499701404e21bd46b0191d63239a4ef76ebde88d27e4d430ac211df681e"),
			mojang("translatorChinese", "Chinese Translator", "Mojang", "2262fb1d24912209490586ecae98aca8500df3eff91f2a07da37ee524e7e3cb6"),
			mojang("mapmaker", "Realms MapMaker", "Mojang", "17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56"),
			mojang("cobalt", "Cobalt", "Mojang", "ca35c56efe71ed290385f4ab5346a1826b546a54d519e6a3ff01efa01acce81"),
			mojang("scrolls", "Scrolls Champion", "Mojang", "3efadf6510961830f9fcc077f19b4daf286d502b5f5aafbd807c7bbffcaca245"),

			mojang("migrator", "Migrator", "Events", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"),
			mojang("vanilla", "Vanilla", "Events", "f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4"),
			mojang("common", "Common", "Events", "5ec930cdd2629c8771655c60eebeb867b4b6559b0e6d3bc71c40c96347fa03f0"),
			mojang("hero", "Hero", "Events", "bb384a1079b9a6f2811520c7991b6e8150d02e5a96457e44509e30822f72f38e"),
			mojang("twisted", "Twisted", "Events", "24aafc451aa2cc34ddc7265211678585c0ef4da4d32edb75ecec1bd8b5408381"),
			mojang("experience", "Minecraft Experience", "Events", "7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434"),
			mojang("moonlight", "Moonlight Trail", "Events", "fe8a02dfe9e390e44ff33d69feef9d3943f76d3901015bbd50f0b67722d288bd"),
			mojang("crafter", "Crafter", "Events", "479eacefa3cdd7aca94207f36c0dd449653ddf259daf40544a5866baf05eee22"),
			mojang("founders", "Founder's", "Events", "99aba02ef05ec6aa4d42db8ee43796d6cd50e4b2954ab29f0caeb85f96bf52a1"),
			mojang("cherry", "Cherry Blossom", "Events", "afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb"),
			mojang("followers", "Follower's", "Events", "569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350"),
			mojang("purpleHeart", "Purple Heart", "Events", "cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde"),
			mojang("anniversary15", "15th Anniversary", "Events", "cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625"),
			mojang("mcc15", "MCC 15th Year", "Events", "56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df"),
			mojang("home", "Home", "Events", "1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd"),
			mojang("menace", "Menace", "Events", "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"),
			mojang("yearn", "Yearn", "Events", "308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89"),
			mojang("copper", "Copper", "Events", "5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"),
			mojang("zombieHorse", "Zombie Horse", "Events", "a3f6e4f14801f3ea55e3d95b9b4ef3b5e8802d947f669de93d6ec4b9354a436b"),
			mojang("builder", "Builder", "Events", "2c579968c64c1719740fd8c2a451461879b238002574fce48f7d1a7c36a1c7d4"),
			mojang("aurora", "Aurora", "Events", "7c418dfbd37412a55e9f1425c9807a591ad10cf71b1edb576a587be9114c277f"),

			mojang("prismarine", "Prismarine", "Test & unused", "d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac"),
			mojang("turtle", "Turtle", "Test & unused", "5048ea61566353397247d2b7d946034de926b997d5e66c86483dfb1e031aee95"),
			mojang("birthday", "Birthday", "Test & unused", "2056f2eebd759cce93460907186ef44e9192954ae12b227d817eb4b55627a7fc"),
			mojang("valentine", "Valentine", "Test & unused", "e578ef995fabcf0a94768f9651ac3aaba30c59ef85d2438e9b3e0cc1d810652b"),
			mojang("oxeye", "Oxeye", "Test & unused", "7706b5f5fc90329691e59277dcc66ba20572219fa8e5da472afd5235fad12cc8"),
			mojang("blueprint", "Blueprint", "Test & unused", "fdcf48f01ec480d1d7cbec27f7ddce48c9da2be6724641109444dae58d4cd013"),
			mojang("db", "dB", "Test & unused", "bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac"),
			mojang("snowman", "Snowman", "Test & unused", "23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe"),
			mojang("spade", "Spade", "Test & unused", "2e002d5e1758e79ba51d08d92a0f3a95119f2f435ae7704916507b6c565a7da8"),
			mojang("bacon", "Bacon", "Test & unused", "fd14214cd8073059e93d9c626260f5df85e5a959181537119df56cadaf5002cc"),
			mojang("millionth", "Millionth Customer", "Test & unused", "70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf"),
			mojang("cheapsh0t", "cheapsh0t's", "Test & unused", "ca29f5dd9e94fb1748203b92e36b66fda80750c87ebc18d6eafdb0e28cc1d05f"),
			wiki("christmas2010", "Christmas 2010", "Test & unused", "Christmas 2010 Cape (texture).png"),
			wiki("newYears2011", "New Year's 2011", "Test & unused", "New Years 2011 Cape (texture).png"),
			wiki("pan", "The Pan", "Test & unused", "The Pan Cape (texture).png"),
			wiki("snail", "Snail", "Test & unused", "SnailCape_texture.png"),
			wiki("icu1", "ICU", "Test & unused", "ICU1_texture.png"),
			wiki("icu2", "ICU (second)", "Test & unused", "ICU2_texture.png"),
			wiki("frog", "Frog", "Test & unused", "FrogAPI_texture.png"),
			wiki("unknown1", "Unknown", "Test & unused", "UnknownCape1_texture.png"));

	public enum State { MISSING, LOADING, READY, FAILED }

	private final Map<String, State> states = new ConcurrentHashMap<>();
	private String selected = "";
	/** Whether your pick has been sent since Lazarus+ last changed. */
	private boolean shared;

	private record Dressed(PlayerSkin in, String key, PlayerSkin out) {
	}

	/** Each player's last dressed skin: the game asks for it every frame. */
	private final Map<UUID, Dressed> dressed = new HashMap<>();

	/** Whether this account may wear a cape of its own; if not, it only sees other players' capes. */
	private final boolean wearer;

	/** The capes for a Lazarus user, who wears one of their own with Lazarus+. */
	public Capes() {
		this(true);
	}

	/** The capes for a player who isn't whitelisted pass false: other players' capes show, and nothing is picked, saved or shared. */
	public Capes(boolean wearer) {
		super("capes", "Capes", "Wear any official cape; other Lazarus users see it too.", Category.CAPES, true);
		this.wearer = wearer;
		INSTANCE = this;
	}

	@Override
	public boolean plus() {
		return true;
	}

	@Override
	public void init() {
		// Drawing other players' capes needs nothing started: dress() loads each texture the first time it shows.
		if (!wearer) return;
		Plus.onChange(() -> {
			shared = false; // share again now that Lazarus+ has changed
			shareOnce();
		});
		if (Config.readJson(FILE) instanceof JsonObject o && o.has("cape")) selected = o.get("cape").getAsString();
		if (!selected.isEmpty()) load(byKey(selected));
		if (!LazarusServer.configured()) return;
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> shareOnce());
	}

	/** Sends your pick once while you have Lazarus+, unless the server already has it; it then tells everyone. */
	private void shareOnce() {
		if (shared || !switchedOn() || !Plus.unlocked()) return;
		shared = true;
		UUID me = Minecraft.getInstance().getUser().getProfileId();
		if (me != null && LiveConnection.LIVE.badge(me) != Live.Badge.NONE && LiveConnection.LIVE.cape(me).equals(selected)) return;
		share();
	}

	/** Tells the worker which cape you wear, so other Lazarus users see it. */
	private void share() {
		if (!LazarusServer.configured()) return;
		String cape = selected;
		CompletableFuture.runAsync(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("cape", cape);
				int status = LazarusServer.post("/cape", body).statusCode();
				if (status != 200) Lazarus.LOG.warn("The Lazarus server didn't take your cape (HTTP {})", status);
			} catch (Exception e) {
				Lazarus.LOG.warn("Could not share your cape: {}", e.toString());
			}
		});
	}

	public static Cape byKey(String key) {
		for (Cape c : ALL) if (c.key.equals(key)) return c;
		return null;
	}

	public String selected() {
		return selected;
	}

	/** Wears the cape, or takes it off for an empty key. Lazarus+ only, and never for a player who isn't whitelisted. */
	public void select(String key) {
		if (!wearer || !Plus.unlocked()) return;
		selected = key;
		JsonObject o = new JsonObject();
		o.addProperty("cape", key);
		Config.writeJson(FILE, o);
		if (!key.isEmpty()) load(byKey(key));
		share();
	}

	public static Identifier id(Cape c) {
		return Identifier.fromNamespaceAndPath(Lazarus.ID, "capes/" + c.key.toLowerCase());
	}

	/** Where the cape's texture stands, starting its download the first time it's asked for. */
	public State state(Cape c) {
		State s = states.getOrDefault(c.key, State.MISSING);
		if (s == State.MISSING) load(c);
		return states.getOrDefault(c.key, State.MISSING);
	}

	private void load(Cape c) {
		if (c == null || states.getOrDefault(c.key, State.MISSING) != State.MISSING) return;
		states.put(c.key, State.LOADING);
		Path cached = DIR.resolve(c.key + ".png");
		if (Files.exists(cached)) {
			try {
				upload(c, Files.readAllBytes(cached));
				return;
			} catch (IOException e) {
				Lazarus.LOG.warn("Could not read cached cape {}", cached, e);
			}
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(c.url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Lazarus (Minecraft mod)")
				.GET().build();
		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((r, error) -> {
			if (error != null || r.statusCode() != 200) {
				Lazarus.LOG.warn("Could not download cape {} ({})", c.key, error != null ? error.toString() : "HTTP " + r.statusCode());
				states.put(c.key, State.FAILED);
				return;
			}
			try {
				Files.createDirectories(DIR);
				Files.write(cached, r.body());
			} catch (IOException e) {
				Lazarus.LOG.warn("Could not cache cape {}", c.key, e);
			}
			upload(c, r.body());
		});
	}

	/** Textures can only be made on the render thread. */
	private void upload(Cape c, byte[] png) {
		Minecraft.getInstance().execute(() -> {
			try {
				NativeImage image = decal(standard(NativeImage.read(png)));
				Minecraft.getInstance().getTextureManager().register(id(c), new DynamicTexture(() -> "Lazarus cape " + c.key, image));
				states.put(c.key, State.READY);
			} catch (IOException | RuntimeException e) {
				Lazarus.LOG.warn("Could not load cape {}", c.key, e);
				states.put(c.key, State.FAILED);
			}
		});
	}

	/** The oldest capes are 22x17; the cape model wants the 64x32 layout, so they're padded into one. */
	private static NativeImage standard(NativeImage image) {
		int w = image.getWidth(), h = image.getHeight();
		if (w == h * 2) return image;
		int k = Math.max(1, w / 22);
		NativeImage out = new NativeImage(64 * k, 32 * k, true);
		for (int x = 0; x < Math.min(w, 64 * k); x++) {
			for (int y = 0; y < Math.min(h, 32 * k); y++) out.setPixel(x, y, image.getPixel(x, y));
		}
		image.close();
		return out;
	}

	/**
	 * The Lazarus mark's plus in Halo, low on the cape's front where the brand guide puts it:
	 * a 3x3 plus with one pixel of the cape left clear on the right and below. No outline.
	 */
	private static NativeImage decal(NativeImage image) {
		int k = image.getWidth() / 64;
		if (k < 1 || image.getHeight() < 32 * k) return image;
		int[][] plus = {{8, 13}, {7, 14}, {8, 14}, {9, 14}, {8, 15}};
		for (int[] p : plus) {
			for (int dx = 0; dx < k; dx++) {
				for (int dy = 0; dy < k; dy++) image.setPixel(p[0] * k + dx, p[1] * k + dy, Theme.HALO);
			}
		}
		return image;
	}

	/** The skin with the player's Lazarus cape on: yours as picked, other Lazarus users' as the live connection says. */
	public PlayerSkin dress(Player player, PlayerSkin skin) {
		if (!switchedOn()) return skin;
		Minecraft mc = Minecraft.getInstance();
		UUID uuid = player.getUUID();
		boolean mine = mc.getUser().getProfileId() != null && uuid.equals(mc.getUser().getProfileId())
				|| mc.player != null && uuid.equals(mc.player.getUUID());
		String key = mine ? Plus.unlocked() ? selected : "" : LiveConnection.LIVE.cape(uuid);
		Cape c = key.isEmpty() ? null : byKey(key);
		if (c == null || state(c) != State.READY) return skin;
		Dressed last = dressed.get(uuid);
		if (last != null && last.in == skin && last.key.equals(key)) return last.out;
		ClientAsset.Texture cape = new ClientAsset.ResourceTexture(id(c), id(c));
		// A real cape doubles as the elytra's texture; keep that true for the new one.
		ClientAsset.Texture elytra = skin.elytra() == null || skin.elytra().equals(skin.cape()) ? null : skin.elytra();
		PlayerSkin out = new PlayerSkin(skin.body(), cape, elytra, skin.model(), skin.secure());
		if (dressed.size() > 256) dressed.clear();
		dressed.put(uuid, new Dressed(skin, key, out));
		return out;
	}
}
