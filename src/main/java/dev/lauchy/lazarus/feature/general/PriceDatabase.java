package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Shared;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.inventory.PricePaid;
import dev.lauchy.lazarus.feature.inventory.Recipes;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Keys;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Item prices from dragsim.co. An item's value comes from the site's own history of it
 * (every auction sale and bazaar quote it has recorded, fetched when the item is first priced
 * and again every half hour); until that arrives, or when the site can't be reached, from a
 * local history: every few minutes the market feed is read and each finished auction (by unit
 * price) and a bazaar bid/ask snapshot kept, for 30 days, in config/lazarus/prices.json. The
 * value is shown in tooltips, by {@code /lazarus price <item>} and in the loot trackers.
 * <p>
 * The site's own figure mixes clean and upgraded sales, so each sale is read as the clean item
 * it would have been ({@link ItemValue}), and one particular item is valued as the clean item
 * plus what its upgrades add. An item that never sold is priced by what Lazarus users traded it
 * for ({@link Trade}), then by a listing, then by its recipe's parts, then by what an NPC pays.
 * A price the owner set by hand ({@code /lazarus price set}) comes before all of them; what the
 * fire sale sold an item for in gems ({@link FireSaleIndex}) comes after a listing, before a recipe;
 * what the site reckons an item costs to craft comes last, for items whose recipe Lazarus lacks.
 * <p>
 * Each history read is shared through the Lazarus server, and everyone's are fetched every few
 * minutes, so an item someone else priced lately has its value straight away.
 */
public final class PriceDatabase extends Feature {
	public static PriceDatabase INSTANCE;

	private static final Path FILE = Config.dir().resolve("prices.json");
	private static final long DAY = 86_400_000L;
	private static final long KEEP = 30 * DAY;
	private static final long SNAPSHOT_GAP = 15 * 60_000L;
	private static final int MAX_SALES = 300;
	/** How long the site's history of an item is used before it is fetched again, and how long after a failed fetch to wait. */
	private static final long HISTORY_TTL = 30 * 60_000L;
	private static final long RETRY_MS = 5 * 60_000L;
	/** Item histories fetched at once; the rest wait their turn, so a long loot list doesn't flood the site. */
	private static final int MAX_FETCHES = 2;
	/** How many trades an item keeps, the newest. */
	private static final int TRADES_KEPT = 50;
	/** How long after the last line of a trade summary it is taken as complete. */
	private static final long TRADE_LINES_MS = 1_000;
	/** How many items' upgrades are remembered by name. */
	private static final int HELD_KEPT = 300;
	/** Upgrades whose prices every item's value leans on, fetched as soon as the game starts. */
	private static final List<String> UPGRADES = List.of("hot_potato_book", "golden_hot_potato_book", "fuming_potato_book", "recombobulator_3000",
			"art_of_war", "implosion_scroll", "shadow_warp_scroll", "wither_shield_scroll", "magic_find_enrichment", "ritual_residue");
	/** A pet's id ("enderdragon_pet_legendary"): its type and rarity. */
	private static final java.util.regex.Pattern PET = java.util.regex.Pattern.compile("(.+)_pet_(common|uncommon|rare|epic|legendary|mythic)");
	/** One writer, so two saves never race on the temp file. */
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Lazarus price writer");
		t.setDaemon(true);
		return t;
	});

	public record Sale(String id, long unitPrice, long soldAt) {
	}

	public record Quote(long at, double bid, double ask) {
	}

	/** The site's history of an item and when it was fetched; a null history is a failed fetch. */
	private record Fetched(MarketHistory history, long at) {
	}

	/** A median of recent sales and how many it is drawn from. */
	public record Median(long price, int sales) {
	}

	/** A clean item's value and where it came from ("3 sales", "recipe"), for the tooltip; -1 when nothing is known. */
	public record Base(long value, String source) {
		/** Nothing known. */
		static final Base NONE = new Base(-1, "");
	}

	private final IntSetting refresh = add(new IntSetting("refresh", "Refresh every", "How often to read the market feed.", 5, 1, 60, " min"));
	private final IntSetting days = add(new IntSetting("days", "Auction window", "Days of sales the auction median uses.", 7, 1, 30, " days"));
	private final BoolSetting tooltip = add(new BoolSetting("tooltip", "Tooltip prices", "Show NPC, auction and bazaar prices on items.", true));
	private final BoolSetting trades = add(new BoolSetting("trades", "Learn from trades", "Price items by what you and other Lazarus users pay for them in trades.", true));

	private final Map<String, Deque<Sale>> sales = new HashMap<>();
	private final Map<String, Deque<Quote>> quotes = new HashMap<>();
	private final Set<String> seen = new HashSet<>();
	private final Map<String, Fetched> histories = new HashMap<>();
	private final Deque<String> queued = new ArrayDeque<>();
	private final Set<String> inFlight = new HashSet<>();
	private long nextFetch;
	private int ticks;
	private boolean fetching;
	/** The recipes shipped with the mod, by result id. */
	private final Map<String, ItemValue.Recipe> shippedRecipes = ItemValue.recipes();
	/** The stones that apply each reforge, worked out from the catalogue once it has loaded. */
	private Map<String, List<String>> stones = Map.of();
	/** What dragsim.co's networth said a clean item is worth, kept for the session, for items with nothing better. */
	private final Map<String, Long> siteBase = new ConcurrentHashMap<>();
	/** What dragsim.co's networth reckoned a clean item costs to craft, by id, for items whose recipe Lazarus doesn't know. */
	private final Map<String, Long> siteCraft = new ConcurrentHashMap<>();
	/** What to run when a history arrives, so an open profile can price again. */
	private final List<Runnable> listeners = new ArrayList<>();
	/** Whether the upgrades' histories were asked for this session. */
	private boolean upgradesFetched;
	/** What one clean item went for in trades, by item id, then by trade key so a trade both sides report counts once. */
	private final Map<String, Map<String, Known.Traded>> traded = new ConcurrentHashMap<>();
	/** Every Lazarus user's history reads of each item, as the server hands them out ({@link Fresh} weighs them), by item id. */
	private final Map<String, Shared.Entry> sharedPrices = new ConcurrentHashMap<>();
	/** The bazaar's buy orders for each item as the market feed last listed them, for what a stack fetches sold now. */
	private final Map<String, List<PriceMath.Order>> orders = new ConcurrentHashMap<>();
	/** The trade summary being read from chat. */
	private final Trade.Reader tradeReader = new Trade.Reader();
	/** When the last chat line came while a trade summary may be open; 0 once it has been ended. */
	private long tradeLineAt;
	/** What the fire sale sold each item for, in gems, by item id ({@link FireSaleIndex}); worth {@link FireSale#coins} each. */
	private final Map<String, Long> fireSale = new ConcurrentHashMap<>();
	/** The owner's shared setting for what a gem is worth in coins ({@code /lazarus price gems}). */
	private static final String GEM_RATE = "gem_coins";
	/** Prices the owner set by hand for everyone ({@code /lazarus price set}), by item id; they come before any other. */
	private final Map<String, Long> manual = new ConcurrentHashMap<>();
	/** The set prices that win over the market too ({@code /lazarus price force}), for sales that can't be trusted. */
	private final Set<String> forced = ConcurrentHashMap.newKeySet();
	/** What each item in your inventory carried (its upgrades), by name, kept after it leaves so an item traded away is still known. */
	private final Map<String, JsonObject> held = new HashMap<>();
	/** Prices as the upgrade reader asks for them. */
	private final ItemValue.Prices prices = new ItemValue.Prices() {
		@Override
		public long of(String id) {
			return estimate(id, false);
		}

		@Override
		public List<String> stones(String reforge) {
			return reforgeStones(reforge);
		}
	};

	public PriceDatabase() {
		super("priceDatabase", "Price Database", "Records auction house and bazaar prices from dragsim.co.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		Shared.follow("prices", Duration.ofMinutes(10), this::shared);
		Shared.follow("trades", Duration.ofMinutes(10), this::sharedTrades);
		Shared.follow("manual", Duration.ofMinutes(10), this::sharedManual);
		Shared.follow("firesale", Duration.ofMinutes(30), this::sharedFireSale);
		Shared.follow("settings", Duration.ofMinutes(10), this::sharedSettings);
		Shared.follow("shares", Duration.ofHours(1), this::sharedShares);
		Chat.listen(l -> {
			Trade t = tradeReader.feed(l.text());
			tradeLineAt = System.currentTimeMillis();
			if (t != null) traded(t);
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long now = System.currentTimeMillis();
			if (mc.player != null && ++ticks % 20 == 0) {
				// Items say what they are (name, rarity, NPC price), which the catalog sometimes doesn't:
				// learn it from the open menu each second (the /slayer drops menu, a shop) and from what you hold every five.
				if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> menu) {
					for (var slot : menu.getMenu().slots) learn(slot.getItem());
				}
				if (ticks % 100 == 0) {
					var inventory = mc.player.getInventory();
					for (int i = 0; i < inventory.getContainerSize(); i++) learn(inventory.getItem(i));
				}
				if (trades.on()) remember(mc);
			}
			if (tradeLineAt > 0 && now - tradeLineAt > TRADE_LINES_MS) {
				tradeLineAt = 0;
				Trade t = tradeReader.finish();
				if (t != null) traded(t);
			}
			if (enabled() && mc.level != null && !upgradesFetched && Catalog.loaded()) {
				upgradesFetched = true;
				for (String id : UPGRADES) history(id, true);
			}
			if (!enabled() || fetching || mc.level == null || now < nextFetch) return;
			fetching = true;
			nextFetch = now + refresh.get() * 60_000L;
			Http.json("/api/market").whenComplete((json, error) -> mc.execute(() -> {
				fetching = false;
				if (error != null) {
					Lazarus.LOG.warn("Could not read the dragsim.co market: {}", error.getMessage());
					return;
				}
				paid(json);
				if (merge(json)) save();
			}));
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!enabled() || !tooltip.on()) return;
			String id = Items.id(stack);
			if (id == null) return;
			learn(stack);
			List<Component> rows = describe(id, stack.getCount(), Items.data(stack));
			if (rows.isEmpty()) return;
			lines.add(Component.empty()); // a gap between the item's rarity line and the prices
			lines.addAll(rows);
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("price")
				.then(ClientCommands.literal("set").requires(src -> LazarusServer.pricer())
						.then(ClientCommands.argument("price", StringArgumentType.word())
								.executes(c -> setPrice(StringArgumentType.getString(c, "price"), null, false))
								.then(ClientCommands.argument("item", StringArgumentType.greedyString())
										.executes(c -> setPrice(StringArgumentType.getString(c, "price"), StringArgumentType.getString(c, "item"), false)))))
				.then(ClientCommands.literal("force").requires(src -> LazarusServer.admin())
						.then(ClientCommands.argument("price", StringArgumentType.word())
								.executes(c -> setPrice(StringArgumentType.getString(c, "price"), null, true))
								.then(ClientCommands.argument("item", StringArgumentType.greedyString())
										.executes(c -> setPrice(StringArgumentType.getString(c, "price"), StringArgumentType.getString(c, "item"), true)))))
				.then(ClientCommands.literal("gems")
						.executes(c -> {
							Message.lazarus().highlight("1,000 gems").info(" are worth ").highlight(Text.compact(FireSale.coins(1000))).info(".").send();
							return 1;
						})
						.then(ClientCommands.argument("coins", StringArgumentType.word()).requires(src -> LazarusServer.admin()).executes(c -> {
							long coins = PriceMath.coins(StringArgumentType.getString(c, "coins"));
							if (coins < 1000) Message.lazarus().error("That isn't a price for 1,000 gems. Try ").highlight("2.3b").error(".").send();
							else setGemRate(coins / 1000);
							return 1;
						})))
				.then(ClientCommands.literal("clear").requires(src -> LazarusServer.admin())
						.then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> {
							setPrice(StringArgumentType.getString(c, "item"), 0, false);
							return 1;
						})))
				.then(ClientCommands.argument("item", new ItemName(Set.of("set", "force", "gems", "clear"))).executes(c -> {
					price(c.getArgument("item", String.class));
					return 1;
				}))));
	}

	/**
	 * An item's name: the rest of the command, but never just one of the words the command takes itself ("set",
	 * "gems"), which go to their own branch anyway. Brigadier then sees no clash between them and the name, and says
	 * nothing about one in the log.
	 */
	private record ItemName(Set<String> words) implements ArgumentType<String> {
		private static final SimpleCommandExceptionType NOT_AN_ITEM = new SimpleCommandExceptionType(new LiteralMessage("That's one of the command's own words, not an item."));

		@Override
		public String parse(StringReader reader) throws CommandSyntaxException {
			String rest = reader.getRemaining();
			if (words.contains(rest.trim().toLowerCase(Locale.ROOT))) throw NOT_AN_ITEM.createWithContext(reader);
			reader.setCursor(reader.getTotalLength());
			return rest;
		}

		@Override
		public Collection<String> getExamples() {
			return List.of("Summoning Eye", "Golden Dragon Helmet");
		}
	}

	/**
	 * What one item is worth by its display name, as the loot trackers count it ({@link
	 * PriceMath#trackerValue}): its Estimated value when that's over 100M, else what it fetches sold
	 * now; the NPC price alone before this has loaded; -1 when nothing is known.
	 */
	public static long worth(String item) {
		String id = Catalog.id(item);
		if (INSTANCE == null || id == null) return Catalog.npcSell(id);
		return INSTANCE.trackerValue(cleanItem(id), true);
	}

	/** A clean one of an item, in the site's shape, for pricing by id alone. */
	public static JsonObject cleanItem(String id) {
		JsonObject clean = new JsonObject();
		clean.addProperty("entry", id);
		return clean;
	}

	/** What a profit tracker counts one of this item at ({@link PriceMath#trackerValue}); -1 when nothing prices it. */
	public long trackerValue(JsonObject item, boolean fetch) {
		Valuer.Valuation v = valuation(item, fetch, me(), 1, null);
		return v.unit() > 0 ? PriceMath.trackerValue(v.unit(), sellNow(item, 1, v)) : -1;
	}

	/** Median unit price of auctions sold in the window, or null with no sales. */
	public Median auction(String id) {
		Deque<Sale> list = sales.get(id);
		if (list == null) return null;
		long since = System.currentTimeMillis() - days.get() * DAY;
		long[] prices = list.stream().filter(s -> s.soldAt >= since).mapToLong(Sale::unitPrice).sorted().toArray();
		if (prices.length == 0) return null;
		int mid = prices.length / 2;
		long median = prices.length % 2 == 1 ? prices[mid] : (prices[mid - 1] + prices[mid]) / 2;
		return new Median(median, prices.length);
	}

	/**
	 * What one of {@code id} is worth, or -1 if nothing is known. A bazaar item is the median
	 * over the last day of what it actually trades at (see {@link PriceMath#bazaarValue}); anything else is
	 * the median of recent sales once anything over 3x or under a third of the first median is
	 * dropped, and only from {@link PriceMath#MIN_SALES} sales up, so one joke listing or coin transfer
	 * can't move it. Never less than an NPC pays.
	 */
	public long estimate(String id) {
		return estimate(id, true);
	}

	/**
	 * {@link #estimate(String)}, fetching the site's history of the item only when {@code fetch}:
	 * a networth over a whole profile prices hundreds of items and asks for none.
	 */
	public long estimate(String id, boolean fetch) {
		return base(id, fetch).value;
	}

	/**
	 * What one clean {@code id} is worth and where that came from, by {@link Valuer}'s order of
	 * evidence. Never less than an NPC pays.
	 */
	public Base base(String id, boolean fetch) {
		Valuer.Base b = new Valuer(live(fetch, me())).base(id);
		return new Base(b.value(), b.source());
	}

	/** What the valuer prices from, as this game knows it now; {@code fetch} asks for the histories it reads, {@code owner} whose items are priced. */
	private Known live(boolean fetch, String owner) {
		return new Known() {
			@Override
			public long now() {
				return System.currentTimeMillis();
			}

			@Override
			public String alias(String id) {
				return PriceDatabase.alias(id);
			}

			@Override
			public long npc(String id) {
				long npc = Catalog.npcSell(id);
				if (npc > 0) return npc;
				java.util.regex.Matcher m = PET.matcher(id);
				Catalog.Pet pet = m.matches() ? Catalog.pet(m.group(1)) : null;
				Long byRarity = pet == null ? null : pet.npc().get(m.group(2));
				return byRarity != null && byRarity > 0 ? byRarity : -1;
			}

			@Override
			public Fresh prices(String id) {
				return fresh(id, fetch, owner);
			}

			@Override
			public Local local(String id) {
				return new Local(bazaarEstimate(id), auctionEstimate(id));
			}

			@Override
			public long manual(String id) {
				Long set = PriceDatabase.this.manual.get(id);
				return set == null ? -1 : set;
			}

			@Override
			public boolean forced(String id) {
				return PriceDatabase.this.forced.contains(id);
			}

			@Override
			public List<Traded> trades(String id) {
				Map<String, Traded> list = traded.get(id);
				return list == null ? List.of() : List.copyOf(list.values());
			}

			@Override
			public long fireSale(String id) {
				Long gems = PriceDatabase.this.fireSale.get(id);
				return gems != null && gems > 0 ? FireSale.coins(gems) : -1;
			}

			@Override
			public List<ItemValue.Recipe> recipes(String id) {
				return recipesFor(id);
			}

			@Override
			public List<String> stones(String reforge) {
				return reforgeStones(reforge);
			}

			@Override
			public long siteBase(String id) {
				Long v = PriceDatabase.this.siteBase.get(id);
				return v == null ? -1 : v;
			}

			@Override
			public long siteCraft(String id) {
				Long v = PriceDatabase.this.siteCraft.get(id);
				return v == null ? -1 : v;
			}

			@Override
			public List<Listing> listings(String id) {
				return history(id, false).listings();
			}

			@Override
			public ItemValue.Model shares() {
				return ItemValue.model();
			}
		};
	}

	/**
	 * Everyone's history reads of an item together with this computer's own, weighed by age ({@link
	 * Fresh}); reads for another auction window than this computer's are left out, as they aren't comparable.
	 */
	private Fresh fresh(String id, boolean fetch, String owner) {
		MarketHistory own = history(id, fetch);
		Fetched mine = histories.get(id);
		Shared.Entry shared = sharedPrices.get(id);
		JsonObject entry = new JsonObject();
		com.google.gson.JsonArray reads = new com.google.gson.JsonArray();
		if (shared != null) {
			JsonObject v = shared.value();
			if (v.get("reads") instanceof com.google.gson.JsonArray list) {
				for (JsonElement r : list) if (r instanceof JsonObject o && sameWindow(o, v)) reads.add(o);
			} else if (sameWindow(v, v)) {
				JsonObject one = v.deepCopy();
				one.addProperty("at", shared.at());
				reads.add(one);
			}
			if (v.get("pending") instanceof com.google.gson.JsonArray pending) entry.add("pending", pending);
		}
		if (mine != null && mine.history != null) reads.add(own.asRead(mine.at, "me"));
		if (reads.isEmpty()) return Fresh.NONE;
		entry.add("reads", reads);
		Long site = siteBase.get(id);
		return Fresh.of(entry, 0, System.currentTimeMillis(), site == null ? -1 : site);
	}

	/** Whether a shared read is for this computer's auction window (a read without one is the entry's own). */
	private boolean sameWindow(JsonObject read, JsonObject entry) {
		JsonElement d = read.has("days") ? read.get("days") : entry.get("days");
		return d == null || !d.isJsonPrimitive() || d.getAsInt() == days.get();
	}

	/** The player's own name, whose listings never price their items; "" before the game has one. */
	private static String me() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.player == null ? "" : mc.player.getName().getString();
	}

	/** Every known recipe for {@code id}: the shipped one, and the one learned in game with its parts' names read as ids. */
	private List<ItemValue.Recipe> recipesFor(String id) {
		List<ItemValue.Recipe> out = new ArrayList<>();
		ItemValue.Recipe shipped = shippedRecipes.get(id);
		if (shipped != null) out.add(shipped);
		Catalog.Item item = Catalog.item(id);
		Recipes.Recipe learned = item == null || Recipes.INSTANCE == null ? null : Recipes.INSTANCE.recipe(item.name());
		if (learned != null) {
			Map<String, Integer> parts = new HashMap<>();
			for (var e : learned.needs().entrySet()) {
				String part = Catalog.id(e.getKey());
				if (part == null) return out; // a part with no id can't be priced
				parts.merge(part, e.getValue(), Integer::sum);
			}
			if (!parts.isEmpty()) out.add(new ItemValue.Recipe(Math.max(1, learned.count()), 0, parts));
		}
		return out;
	}

	/**
	 * The id the market knows an item by: a learned item ("~ultimate_hyperion_skin") by its bare id,
	 * and a pet by its name, since profiles name pets after their type ("enderdragon_pet_legendary")
	 * and the market after their name ("ender_dragon_pet_legendary").
	 */
	private static String alias(String id) {
		if (id.startsWith("~")) id = id.substring(1); // a learned item is priced by its bare id, as the game and server name it
		if (Catalog.item(id) != null) return id;
		java.util.regex.Matcher m = PET.matcher(id);
		Catalog.Pet pet = m.matches() ? Catalog.pet(m.group(1)) : null;
		if (pet == null) return id;
		String named = Text.lower(Text.plain(pet.name())).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "") + "_pet_" + m.group(2);
		return Catalog.item(named) != null ? named : id;
	}

	/** The stones that apply a reforge ("renowned"), from "Applies the Renowned reforge" in their descriptions. */
	private List<String> reforgeStones(String reforge) {
		if (stones.isEmpty() && Catalog.loaded()) stones = Catalog.reforgeStones();
		return stones.getOrDefault(reforge.toLowerCase(Locale.ROOT), List.of());
	}

	/**
	 * One stack of an item (in the site's shape: {@code entry}, {@code item_modifiers}, {@code extra})
	 * valued by {@link Valuer}: what the networth row on dragsim.co says of it ({@code site}, null when
	 * there's none), whose it is ({@code owner}, so their own listings don't price it).
	 */
	public Valuer.Valuation valuation(JsonObject item, boolean fetch, String owner, long quantity, Valuer.Site site) {
		return new Valuer(live(fetch, owner)).value(item, site, owner, quantity);
	}

	/**
	 * What a stack of this item fetches sold now, never part of a networth: a bazaar item into the buy
	 * orders up now ({@link PriceMath#bazaarSellNow}); anything else at the lower of its clean sales' lower
	 * quartile and just under the cheapest other listing of exactly it, its upgrades counted as {@code v}
	 * counts them ({@link PriceMath#auctionSellNow}). -1 when nothing says.
	 */
	public long sellNow(JsonObject item, long quantity, Valuer.Valuation v) {
		if (v == null || v.unit() <= 0) return -1;
		String id = v.id();
		long npc = Math.max(0, Catalog.npcSell(id));
		List<PriceMath.Order> buys = orders.get(id);
		if (buys != null && !buys.isEmpty()) return PriceMath.bazaarSellNow(buys, quantity, npc);
		MarketHistory h = history(id, false);
		String variant = ItemValue.variant(item), mine = me().toLowerCase(Locale.ROOT);
		long listed = h.listings().stream().filter(l -> l.variant().equals(variant) && !l.seller().toLowerCase(Locale.ROOT).equals(mine))
				.mapToLong(Known.Listing::price).min().orElse(-1);
		long quartile = h.quartile() > 0 ? h.quartile() + v.upgrades() : -1;
		if (v.kind().equals("variant") && quartile > v.unit()) quartile = v.unit(); // the site's sales of exactly it already say
		long each = PriceMath.auctionSellNow(quartile, listed, v.kind().equals("floor") || "Floor".equals(v.confidence()) ? v.unit() : npc);
		// Selling to an NPC is always there: an item never fetches less, whatever a cheap listing or transfer says.
		each = Math.max(each, npc);
		return each > 0 ? each * Math.max(1, quantity) : -1;
	}

	/** Notes what dragsim.co's networth said a clean item sells for, used when nothing better is known. */
	public void siteBase(String id, long value) {
		if (id != null && value > 0) siteBase.put(alias(id.toLowerCase(Locale.ROOT)), value);
	}

	/** Notes what dragsim.co's networth reckoned a clean item costs to craft, used after Lazarus's own recipe. */
	public void siteCraft(String id, long value) {
		if (id != null && value > 0) siteCraft.put(alias(id.toLowerCase(Locale.ROOT)), value);
	}

	/** Runs {@code r} each time an item's history arrives, until it returns. */
	public void onHistory(Runnable r) {
		listeners.add(r);
	}

	/** Stops running {@code r} when histories arrive. */
	public void offHistory(Runnable r) {
		listeners.remove(r);
	}

	/** The site's history of an item as last fetched, queueing a fetch when it is missing or old. */
	private MarketHistory history(String id, boolean fetch) {
		Fetched f = histories.get(id);
		long now = System.currentTimeMillis();
		boolean due = f == null || now - f.at > (f.history == null ? RETRY_MS : HISTORY_TTL);
		if (fetch && due && enabled() && !inFlight.contains(id) && !queued.contains(id)) {
			queued.addLast(id);
			fetchHistories();
		}
		return f == null || f.history == null ? MarketHistory.NONE : f.history;
	}

	/** Hands a feed from dragsim.co to Price Paid, which picks your own purchases out of its sales. */
	private static void paid(JsonElement feed) {
		if (PricePaid.INSTANCE != null) PricePaid.INSTANCE.feed(feed);
	}

	private void fetchHistories() {
		Minecraft mc = Minecraft.getInstance();
		while (inFlight.size() < MAX_FETCHES && !queued.isEmpty()) {
			String id = queued.pollFirst();
			inFlight.add(id);
			Http.json("/api/market/item/" + Http.encode(id) + "?days=" + days.get()).whenComplete((json, error) -> mc.execute(() -> {
				inFlight.remove(id);
				if (error == null) paid(json);
				MarketHistory h = error == null && json != null && json.isJsonObject()
						? MarketHistory.read(json.getAsJsonObject(), System.currentTimeMillis(), (price, item) -> ItemValue.clean(price, item, prices, ItemValue.model()), me()) : null;
				if (h == null) Lazarus.LOG.debug("Could not read the dragsim.co price history of {}", id);
				else share(id, h);
				histories.put(id, new Fetched(h, System.currentTimeMillis()));
				List.copyOf(listeners).forEach(Runnable::run);
				fetchHistories();
			}));
		}
	}

	/**
	 * Everyone's history reads, kept as the server hands them out (each read with when it was made, and
	 * those held back as far off what's known), and weighed with this computer's own by {@link Fresh}.
	 */
	private void shared(Map<String, Shared.Entry> got) {
		sharedPrices.putAll(got);
		if (!got.isEmpty()) List.copyOf(listeners).forEach(Runnable::run);
	}

	/** The upgrade shares the server last fitted, {kind: {share, support}}: they replace the shipped ones. */
	private void sharedShares(Map<String, Shared.Entry> got) {
		if (got.isEmpty()) return;
		JsonObject share = new JsonObject();
		got.forEach((kind, e) -> {
			if (e.value().get("share") instanceof JsonElement s && s.isJsonPrimitive()) share.add(kind, s);
		});
		ItemValue.useShares(ItemValue.Model.shares(share));
		List.copyOf(listeners).forEach(Runnable::run);
	}

	/** Notes the upgrades of everything in your inventory by name, so a trade can read them. */
	private void remember(Minecraft mc) {
		var inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			JsonObject data = Items.data(stack);
			if (data.isEmpty()) continue;
			if (held.size() >= HELD_KEPT && !held.containsKey(Items.name(stack))) held.clear(); // a session's worth, then start over
			held.put(Items.name(stack), data);
		}
	}

	/**
	 * Learns from a trade you made: what it says each item went for, read clean of the item's
	 * upgrades, kept and shared under the trade's key. A price under what an NPC pays is a gift,
	 * not a price, and is left out. A price the owner set by hand only stands in until the market
	 * says otherwise, so the trade first prices the one item that has only that; when that can't
	 * be worked out, the set price counts as known.
	 */
	private void traded(Trade t) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || !trades.on() || mc.player == null) return;
		remember(mc); // what you got is in your inventory by now
		String key = t.key(mc.player.getName().getString());
		String pair = t.pair(mc.player.getName().getString());
		long now = System.currentTimeMillis();
		Map<String, Long> learned = t.prices(name -> {
			String id = Catalog.id(name);
			return id == null || onlySetPrice(id) ? -1 : valuation(tradedItem(name, id), false, me(), 1, null).unit();
		});
		if (learned.isEmpty()) learned = t.prices(name -> {
			String id = Catalog.id(name);
			return id == null ? -1 : valuation(tradedItem(name, id), false, me(), 1, null).unit();
		});
		learned.forEach((name, unit) -> {
			String id = Catalog.id(name);
			if (id == null) return;
			long clean = Math.round(ItemValue.clean(unit, tradedItem(name, id), prices, ItemValue.model()));
			if (clean <= 0 || clean < Catalog.npcSell(id)) return;
			addTrade(alias(id), key, clean, now, pair);
			JsonObject v = new JsonObject();
			v.addProperty("price", clean);
			v.addProperty("key", key);
			v.addProperty("pair", pair);
			Shared.send("trades", alias(id), v);
		});
	}

	/** Whether all that prices {@code id} is a price the owner set by hand without forcing it. */
	private boolean onlySetPrice(String id) {
		String key = alias(id.toLowerCase(Locale.ROOT));
		return !forced.contains(key) && base(key, false).source.equals("set price");
	}

	/** A traded item as its upgrades were last seen in your inventory, or just its id when they weren't. */
	private JsonObject tradedItem(String name, String id) {
		JsonObject data = held.get(name);
		JsonObject item = data == null ? new JsonObject() : data.deepCopy();
		if (!item.has("entry")) item.addProperty("entry", id);
		return item;
	}

	/** Keeps one trade's price for an item, the newest {@link #TRADES_KEPT} of them. */
	private void addTrade(String id, String key, long price, long at, String pair) {
		Map<String, Known.Traded> list = traded.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
		list.put(key, new Known.Traded(price, at, pair));
		if (list.size() > TRADES_KEPT) {
			list.entrySet().stream().min((a, b) -> Long.compare(a.getValue().at(), b.getValue().at())).ifPresent(e -> list.remove(e.getKey()));
		}
	}

	/** Everyone's trades, {p: [[price, when, key, pair?], …]} by item id. */
	private void sharedTrades(Map<String, Shared.Entry> got) {
		got.forEach((id, e) -> {
			if (!(e.value().get("p") instanceof JsonArray list)) return;
			for (JsonElement t : list) {
				try {
					if (t instanceof JsonArray a && a.size() >= 3) addTrade(id, a.get(2).getAsString(), a.get(0).getAsLong(), a.get(1).getAsLong(), a.size() > 3 ? a.get(3).getAsString() : "");
				} catch (RuntimeException ignored) {
					// one bad entry shouldn't lose the rest
				}
			}
		});
	}

	/** Notes what the fire sale sold an item for, keeps it, and shares it. */
	void fireSale(String id, FireSale.Listing listing) {
		String key = alias(id.toLowerCase(Locale.ROOT));
		if (!FireSale.priced(listing.gems())) return;
		if (listing.bought()) fireSale.put(key, listing.gems());
		else fireSale.remove(key); // nobody bought one, so none are out there to be worth anything
		JsonObject v = new JsonObject();
		v.addProperty("coins", listing.coins());
		v.addProperty("gems", listing.gems());
		if (listing.quantity() > 0) v.addProperty("quantity", listing.quantity());
		if (listing.buyers() >= 0) v.addProperty("buyers", listing.buyers());
		Shared.send("firesale", key, v);
	}

	/** A page of fire sales has been read: anything open prices again, and the prices are saved. */
	void fireSaleRead() {
		List.copyOf(listeners).forEach(Runnable::run);
		save();
	}

	/**
	 * Everyone's fire sale reads, {coins, gems, quantity, buyers} by item id; one nobody bought from is dropped.
	 * The gems are kept rather than the coins, which older mods worked out at an older gem rate.
	 */
	private void sharedFireSale(Map<String, Shared.Entry> got) {
		boolean changed = false;
		for (var e : got.entrySet()) {
			JsonObject v = e.getValue().value();
			long gems = (long) num(v, "gems");
			if (v.has("buyers") && num(v, "buyers") == 0 || !FireSale.priced(gems)) { // nobody bought one, or a 1-gem placeholder
				if (fireSale.remove(e.getKey()) != null) changed = true;
			} else if (!Long.valueOf(gems).equals(fireSale.put(e.getKey(), gems))) changed = true;
		}
		if (changed) {
			List.copyOf(listeners).forEach(Runnable::run);
			save();
		}
	}

	/** The owner's settings for every Lazarus user, {value} by name: {@code gem_coins} is what a gem is worth in coins. */
	private void sharedSettings(Map<String, Shared.Entry> got) {
		Shared.Entry gem = got.get(GEM_RATE);
		if (gem == null) return;
		long before = FireSale.coinsPerGem();
		FireSale.coinsPerGem((long) num(gem.value(), "value"));
		if (FireSale.coinsPerGem() != before) List.copyOf(listeners).forEach(Runnable::run);
	}

	/** {@code /lazarus price gems <coins>}: sets what 1,000 gems are worth for every Lazarus user, then says so. The owner only. */
	private void setGemRate(long perGem) {
		if (!LazarusServer.configured()) {
			Message.lazarus().error("The Lazarus server isn't set up yet.").send();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject v = new JsonObject();
				v.addProperty("value", perGem);
				JsonObject entries = new JsonObject();
				entries.add(GEM_RATE, v);
				JsonObject body = new JsonObject();
				body.add("entries", entries);
				HttpResponse<String> r = LazarusServer.post("/shared/settings", body);
				if (r.statusCode() != 200) said = Message.lazarus().error(r.body()).build();
				else said = Message.lazarus().highlight("1,000 gems").info(" are now worth ").highlight(Text.compact(perGem * 1000)).info(" for everyone.").build();
				if (r.statusCode() == 200) mc.execute(() -> {
					FireSale.coinsPerGem(perGem);
					List.copyOf(listeners).forEach(Runnable::run);
				});
			} catch (Exception e) {
				said = LazarusServer.unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	/** The prices the owner set by hand, {price, force?} by item id; 0 is one that was cleared. */
	private void sharedManual(Map<String, Shared.Entry> got) {
		manual.clear();
		forced.clear();
		got.forEach((id, e) -> {
			long price = (long) num(e.value(), "price");
			if (price <= 0) return;
			manual.put(id, price);
			if (e.value().get("force") instanceof JsonElement f && f.isJsonPrimitive() && f.getAsBoolean()) forced.add(id);
		});
		List.copyOf(listeners).forEach(Runnable::run);
	}

	/**
	 * {@code /lazarus price set|force <price> [item]}: reads the price and sets it for the item named,
	 * or the one in your hand when none is.
	 */
	private int setPrice(String price, String item, boolean force) {
		long coins = PriceMath.coins(price);
		if (coins < 0) {
			Message.lazarus().error("That isn't a price. Try ").highlight("400m").error(" or ").highlight("1.5b").error(".").send();
			return 1;
		}
		if (item != null) {
			setPrice(item, coins, force);
			return 1;
		}
		Minecraft mc = Minecraft.getInstance();
		ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
		String id = Items.id(held);
		if (id == null) Message.lazarus().error("Hold the item to price, or name it.").send();
		else setPrice(id, coins, force);
		return 1;
	}

	/**
	 * {@code /lazarus price set|force|clear}: sets what one clean item is worth for every Lazarus user
	 * until real sales or a bazaar price exist, or, {@code force}d, over them too (0 clears it), then
	 * says so. The owner only.
	 */
	private void setPrice(String query, long price, boolean force) {
		String id = itemId(query);
		if (id == null) return;
		if (!LazarusServer.configured()) {
			Message.lazarus().error("The Lazarus server isn't set up yet.").send();
			return;
		}
		String key = alias(id), name = Catalog.item(id).name() + (Catalog.sameName(id).isEmpty() ? "" : " (" + key + ")"); // which Baby Skin
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			Component said;
			try {
				JsonObject v = new JsonObject();
				v.addProperty("price", price);
				if (force) v.addProperty("force", true);
				JsonObject entries = new JsonObject();
				entries.add(key, v);
				JsonObject body = new JsonObject();
				body.add("entries", entries);
				HttpResponse<String> r = LazarusServer.post("/shared/manual", body);
				if (r.statusCode() != 200) said = Message.lazarus().error(r.body()).build();
				else if (price > 0) said = Message.lazarus().highlight(name).info(" is now worth ").highlight(Text.compact(price)).info(force ? " for everyone, whatever it sells for." : " for everyone, until it sells.").build();
				else said = Message.lazarus().highlight(name).info(" no longer has a set price.").build();
				if (r.statusCode() == 200) mc.execute(() -> {
					if (price > 0) manual.put(key, price);
					else manual.remove(key);
					if (price > 0 && force) forced.add(key);
					else forced.remove(key);
					List.copyOf(listeners).forEach(Runnable::run);
				});
			} catch (Exception e) {
				said = LazarusServer.unreachable(e);
			}
			Component message = said;
			mc.execute(() -> Message.send(message));
		});
	}

	/** Shares a history this computer read, so others have the item's value before they read it themselves. */
	private void share(String id, MarketHistory h) {
		JsonObject v = new JsonObject();
		v.addProperty("bazaar", h.bazaar());
		v.addProperty("auction", h.auction());
		v.addProperty("sales", h.sales());
		v.addProperty("clean", h.clean());
		v.addProperty("thin", h.thin());
		v.addProperty("listed", h.listed());
		v.addProperty("days", days.get());
		Shared.send("prices", id, v);
	}

	private long bazaarEstimate(String id) {
		Deque<Quote> list = quotes.get(id);
		if (list == null) return -1;
		long since = System.currentTimeMillis() - DAY;
		double[] values = list.stream().filter(q -> q.at >= since).mapToDouble(q -> PriceMath.bazaarValue(q.bid, q.ask)).filter(v -> v > 0).sorted().toArray();
		if (values.length == 0) return -1;
		return Math.round(values[values.length / 2]);
	}

	private long auctionEstimate(String id) {
		Deque<Sale> list = sales.get(id);
		if (list == null) return -1;
		long now = System.currentTimeMillis(), since = now - days.get() * DAY;
		Sale[] recent = list.stream().filter(s -> s.soldAt >= since).toArray(Sale[]::new);
		return PriceMath.auctionValue(Arrays.stream(recent).mapToLong(Sale::unitPrice).toArray(), Arrays.stream(recent).mapToLong(s -> now - s.soldAt).toArray());
	}

	/** The latest bazaar bid and ask, or null for a non-bazaar item. */
	public Quote bazaar(String id) {
		Deque<Quote> list = quotes.get(id);
		return list == null || list.isEmpty() ? null : list.peekLast();
	}

	private List<Component> describe(String id, int count, JsonObject item) {
		List<Component> out = new ArrayList<>();
		JsonObject it = item == null ? new JsonObject() : item.deepCopy();
		if (!it.has("entry")) it.addProperty("entry", id);
		Valuer.Valuation valued = valuation(it, true, me(), 1, null);
		long npc = Catalog.npcSell(id);
		if (valued.unit() > 0 && !("NPC".equals(valued.source()) && valued.parts().isEmpty())) { // an NPC price alone has its own row
			out.add(Component.literal("§7Estimated value: §6" + Text.compact(valued.unit()) + (count > 1 ? " §8(§6" + Text.compact(valued.unit() * count) + "§8)" : "")
					+ (valued.source().isEmpty() ? "" : " §8· " + valued.source()) + (valued.confidence() == null ? "" : " §8· " + valued.confidence().toLowerCase(Locale.ROOT))));
			long now = sellNow(it, count, valued);
			if (now > 0) out.add(Component.literal("§7Sell now: §6" + Text.compact(now / Math.max(1, count)) + (count > 1 ? " §8(§6" + Text.compact(now) + "§8)" : "")));
			if (!valued.parts().isEmpty()) {
				boolean shift = Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT) || Keys.down(GLFW.GLFW_KEY_RIGHT_SHIFT);
				out.add(Component.literal("§8 Clean §7" + Text.compact(valued.base()) + " §8+ upgrades §7" + Text.compact(valued.upgrades()) + (shift ? "" : " §8· Shift")));
				if (shift) for (ItemValue.Part p : valued.parts()) {
					Catalog.Item named = p.detail().isEmpty() ? null : Catalog.item(p.detail());
					String detail = named != null ? Text.plain(named.name()) : p.detail();
					String worth = p.value() == ItemValue.Part.UNPRICED ? "§8no price yet" : p.value() == 0 ? "§8nothing on an NPC-priced item" : "§6" + Text.compact(p.value());
					out.add(Component.literal("§8  + §7" + ItemValue.partName(p.kind(), detail) + ": " + worth));
				}
			}
		}
		if (npc > 0) out.add(row("NPC", npc, count));
		Quote q = bazaar(id);
		if (q != null) {
			out.add(row("Bazaar sell", Math.round(q.bid), count));
			out.add(row("Bazaar buy", Math.round(q.ask), count));
		}
		MarketHistory site = history(id, false);
		Median m = site.auction() > 0 ? new Median(site.auction(), site.sales()) : auction(id);
		if (m != null) {
			out.add(Component.literal("§7Auction median: §6" + Text.compact(m.price) + (count > 1 ? " §8(§6" + Text.compact(m.price * count) + "§8)" : "")
					+ " §8· " + m.sales + (m.sales == 1 ? " sale" : " sales") + ", " + days.get() + "d"));
		}
		return out;
	}

	private static Component row(String label, long each, int count) {
		return Component.literal("§7" + label + ": §6" + Text.compact(each) + (count > 1 ? " §8(§6" + Text.compact(each * count) + "§8)" : ""));
	}

	private void price(String query) {
		String id = itemId(query);
		if (id == null) return;
		List<Component> lines = describe(id, 1, null);
		Message m = Message.lazarus().highlight(Catalog.item(id).name());
		if (lines.isEmpty()) m.info(" has no recorded price yet.");
		for (Component c : lines) {
			// The tooltip rows read "Label: price"; in chat the price is the detail that stands out.
			String row = Text.plain(c);
			int colon = row.indexOf(": ");
			if (colon < 0) m.line().info(row);
			else m.line().info(row.substring(0, colon + 2)).highlight(row.substring(colon + 2));
		}
		m.send();
	}

	/** The item a command names, by id ("gold_claws") or name ("Gold Claws"); null, having said why, when there's none. */
	private static String itemId(String query) {
		if (!Catalog.loaded()) {
			Message.lazarus().warning("The item catalogue hasn't loaded yet. Try again in a moment.").send();
			return null;
		}
		String id = Catalog.item(query.replace(' ', '_')) != null ? query.replace(' ', '_').toLowerCase(Locale.ROOT) : Catalog.id(query);
		if (id == null) Message.lazarus().error("No item is called ").highlight(query).error(".").send();
		else if (!query.replace(' ', '_').equalsIgnoreCase(id) && Catalog.sameName(id).size() > 1) {
			// "Baby Skin" is seven skins, one per dragon armour set; a name alone can't say which.
			Message.lazarus().error(Catalog.sameName(id).size() + " skins are called ").highlight(query)
					.error(". Hold the one you mean, or name it: ").highlight(String.join(", ", Catalog.sameName(id))).error(".").send();
			return null;
		}
		return id;
	}

	/** Folds one market feed in; true when anything new was recorded. */
	private boolean merge(JsonElement json) {
		if (json == null || !json.isJsonObject()) return false;
		JsonObject root = json.getAsJsonObject();
		long now = System.currentTimeMillis();
		boolean changed = false;
		if (root.has("sales")) for (JsonElement e : root.getAsJsonArray("sales")) {
			if (!e.isJsonObject()) continue;
			JsonObject s = e.getAsJsonObject();
			long soldAt = Http.time(s.get("sold_at"));
			if (soldAt <= 0 || !s.has("id") || !s.has("item")) continue;
			String saleId = s.get("id").getAsString();
			if (!seen.add(saleId)) continue;
			JsonObject item = s.getAsJsonObject("item");
			if (!item.has("entry")) continue;
			learn(item);
			long count = item.has("count") ? Math.max(1, item.get("count").getAsLong()) : 1;
			long unit = s.has("unit_price") && !s.get("unit_price").isJsonNull() ? s.get("unit_price").getAsLong()
					: s.get("price").getAsLong() / count;
			add(sales, item.get("entry").getAsString().toLowerCase(Locale.ROOT), new Sale(saleId, unit, soldAt), MAX_SALES);
			changed = true;
		}
		if (root.has("auctions")) for (JsonElement e : root.getAsJsonArray("auctions")) {
			if (e.isJsonObject() && e.getAsJsonObject().get("item") instanceof JsonObject item) learn(item);
		}
		if (root.has("products")) for (JsonElement e : root.getAsJsonArray("products")) {
			if (!e.isJsonObject()) continue;
			JsonObject p = e.getAsJsonObject();
			if (!p.has("id")) continue;
			String id = p.get("id").getAsString().toLowerCase(Locale.ROOT);
			List<PriceMath.Order> buys = new ArrayList<>();
			if (p.get("buys") instanceof JsonArray book) {
				for (JsonElement b : book) if (b instanceof JsonObject o) buys.add(new PriceMath.Order(num(o, "price"), (long) num(o, "amount")));
			}
			orders.put(id, List.copyOf(buys));
			Quote last = bazaar(id);
			if (last != null && now - last.at < SNAPSHOT_GAP) continue;
			add(quotes, id, new Quote(now, num(p, "best_bid"), num(p, "best_ask")), Integer.MAX_VALUE);
			changed = true;
		}
		return changed;
	}

	/** Passes on what a market item says about itself (name, rarity, NPC price), which the catalog lacks for some items. */
	private static void learn(JsonObject item) {
		if (!item.has("entry") || !(item.get("item_data") instanceof JsonObject data)) return;
		Catalog.learn(item.get("entry").getAsString(), data.has("item_name") ? data.get("item_name").getAsString() : "",
				data.has("rarity") && !data.get("rarity").isJsonNull() ? data.get("rarity").getAsString() : "", (long) num(data, "npc_sell_price"));
	}

	/**
	 * Passes on what an item in game says about itself. One with no id of its own (a menu's
	 * preview of a drop) counts only when it shows a rarity, so buttons and panes aren't taken
	 * for items, and is given to the item of that name, or else known by its name ("~" and the name).
	 */
	private static void learn(ItemStack stack) {
		if (stack.isEmpty()) return;
		String rarity = Items.rarity(stack);
		String id = Items.id(stack);
		String name = Items.itemName(stack); // the server's own name; the shown one can carry a reforge and stars
		if (id == null) {
			if (rarity.isEmpty()) return;
			name = Catalog.itemName(Items.name(stack));
			Catalog.Item known = Catalog.item(Catalog.id(name));
			id = known != null && known.name().equalsIgnoreCase(name) ? known.id()
					: "~" + Text.lower(name).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		}
		Catalog.learn(id, name, rarity, Items.npcSell(stack));
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
	}

	private static <T> void add(Map<String, Deque<T>> map, String id, T value, int max) {
		Deque<T> list = map.computeIfAbsent(id, k -> new ArrayDeque<>());
		list.addLast(value);
		while (list.size() > max) list.removeFirst();
	}

	private void load() {
		JsonElement json = Config.readJson(FILE);
		if (json == null || !json.isJsonObject()) return;
		long cutoff = System.currentTimeMillis() - KEEP;
		try {
			JsonObject root = json.getAsJsonObject();
			for (var e : root.getAsJsonObject("auction").entrySet()) {
				for (JsonElement r : e.getValue().getAsJsonArray()) {
					JsonArray a = r.getAsJsonArray();
					Sale s = new Sale(a.get(0).getAsString(), a.get(1).getAsLong(), a.get(2).getAsLong());
					if (s.soldAt < cutoff) continue;
					seen.add(s.id);
					add(sales, e.getKey(), s, MAX_SALES);
				}
			}
			for (var e : root.getAsJsonObject("bazaar").entrySet()) {
				for (JsonElement r : e.getValue().getAsJsonArray()) {
					JsonArray a = r.getAsJsonArray();
					Quote q = new Quote(a.get(0).getAsLong(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
					if (q.at >= cutoff) add(quotes, e.getKey(), q, Integer.MAX_VALUE);
				}
			}
			if (root.get("firesaleGems") instanceof JsonObject sale) {
				for (var e : sale.entrySet()) if (e.getValue().isJsonPrimitive() && FireSale.priced(e.getValue().getAsLong())) fireSale.put(alias(e.getKey()), e.getValue().getAsLong());
			} else if (root.get("firesale") instanceof JsonObject sale) {
				// saved before 1.29.0 in coins at 1,000 gems = 3B; before 1.28.0 a learned item may sit under its "~" id
				for (var e : sale.entrySet()) {
					if (!e.getValue().isJsonPrimitive()) continue;
					long gems = e.getValue().getAsLong() / FireSale.OLD_COINS_PER_GEM;
					if (FireSale.priced(gems)) fireSale.put(alias(e.getKey()), gems);
				}
			}
		} catch (RuntimeException e) {
			Lazarus.LOG.warn("Could not read {}, starting a fresh price history", FILE, e);
		}
	}

	private void save() {
		long cutoff = System.currentTimeMillis() - KEEP;
		JsonObject auction = new JsonObject();
		for (var e : sales.entrySet()) {
			e.getValue().removeIf(s -> s.soldAt < cutoff);
			JsonArray list = new JsonArray();
			for (Sale s : e.getValue()) list.add(entry(s.id, s.unitPrice, s.soldAt));
			auction.add(e.getKey(), list);
		}
		JsonObject bazaar = new JsonObject();
		for (var e : quotes.entrySet()) {
			e.getValue().removeIf(q -> q.at < cutoff);
			JsonArray list = new JsonArray();
			for (Quote q : e.getValue()) list.add(entry(q.at, q.bid, q.ask));
			bazaar.add(e.getKey(), list);
		}
		JsonObject root = new JsonObject();
		root.add("auction", auction);
		root.add("bazaar", bazaar);
		JsonObject sale = new JsonObject();
		fireSale.forEach(sale::addProperty);
		root.add("firesaleGems", sale);
		WRITER.execute(() -> Config.writeJson(FILE, root));
	}

	private static JsonArray entry(Object a, Number b, Number c) {
		JsonArray out = new JsonArray();
		if (a instanceof Number n) out.add(n);
		else out.add(String.valueOf(a));
		out.add(b);
		out.add(c);
		return out;
	}
}
