package dev.lauchy.lazarus.feature.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
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
	/** How long a trade's price counts. */
	private static final long TRADE_MS = 30 * 86_400_000L;
	/** How many trades an item keeps, the newest. */
	private static final int TRADES_KEPT = 50;
	/** How long after the last line of a trade summary it is taken as complete. */
	private static final long TRADE_LINES_MS = 1_000;
	/** How many items' upgrades are remembered by name. */
	private static final int HELD_KEPT = 300;
	/** How deep a recipe's parts are priced by their own recipes before giving up. */
	private static final int RECIPE_DEPTH = 4;
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
	/** The fitted shares of each upgrade. */
	private final ItemValue.Model model = ItemValue.model();
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
	/** What one clean item went for in trades, by item id, then by trade key so a trade both sides report counts once: {price, when}. */
	private final Map<String, Map<String, long[]>> traded = new ConcurrentHashMap<>();
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
				.then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> {
					price(StringArgumentType.getString(c, "item"));
					return 1;
				}))));
	}

	/**
	 * What one item is worth by its display name, as the loot trackers count it: the estimate
	 * from NPC, bazaar and auction prices, or the NPC price alone before this has loaded; -1
	 * when nothing is known.
	 */
	public static long worth(String item) {
		String id = Catalog.id(item);
		return INSTANCE == null ? Catalog.npcSell(id) : INSTANCE.estimate(id);
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
	 * What one clean {@code id} is worth and where that came from, best evidence first: a price the
	 * owner forced; the bazaar;
	 * three or more sales read clean; this computer's own record of sales; what Lazarus users traded
	 * it for; what dragsim.co's networth said; one or two sales, capped by the cheapest listing; a
	 * price the owner set by hand; the cheapest listing; the parts of its recipe; what the fire sale
	 * asked, if anyone bought one. Never less than an NPC pays.
	 */
	public Base base(String id, boolean fetch) {
		return base(id, fetch, 0, new HashSet<>());
	}

	private Base base(String id, boolean fetch, int depth, Set<String> pricing) {
		if (id == null) return Base.NONE;
		id = alias(id.toLowerCase(Locale.ROOT));
		long npc = Catalog.npcSell(id);
		Base found = market(id, fetch, depth, pricing);
		String twin = ItemValue.twin(id);
		if (found.value <= 0 && twin != null) found = market(alias(twin), fetch, depth, pricing);
		if (npc > 0 && npc >= found.value) return new Base(npc, "NPC");
		return found;
	}

	/**
	 * {@link #base} before the NPC floor: the best evidence ({@link #ranked}), but unless that is
	 * {@link #settled}, never less than what its recipe's known parts cost, since nobody crafts
	 * something worth less than what goes into it (a Riftwalker Helmet was its 20M NPC price beside
	 * 338M of parts).
	 */
	private Base market(String id, boolean fetch, int depth, Set<String> pricing) {
		boolean inside = pricing.contains(id); // already being priced further up, as part of its own recipe
		Base found = ranked(id, fetch, depth, pricing);
		if (depth >= RECIPE_DEPTH || inside || settled(id, found)) return found;
		Set<String> parts = new HashSet<>(pricing);
		parts.add(id);
		long floor = recipeCost(id, depth, parts, true);
		return floor > found.value ? new Base(floor, "recipe parts") : found;
	}

	/**
	 * Whether a price is settled enough to stand below the recipe's cost: a price set by hand, the
	 * bazaar, three or more clean sales, this computer's own sales or trades. That much trading below
	 * the parts' cost means there are more of the item than people want (Lauchlan's rule); one or two
	 * sales, a listing or an estimate can't say so.
	 */
	private boolean settled(String id, Base found) {
		String s = found.source;
		if (s.equals("set price") || s.equals("bazaar") || s.equals("recent sales") || s.endsWith(" trade") || s.endsWith(" trades")) return true;
		long clean = history(id, false).clean();
		return clean > 0 && found.value == clean;
	}

	/** The best evidence of what one clean {@code id} is worth, in the order {@link #base} lists. */
	private Base ranked(String id, boolean fetch, int depth, Set<String> pricing) {
		Long set = manual.get(id);
		if (set != null && set > 0 && forced.contains(id)) return new Base(set, "set price");
		MarketHistory site = history(id, fetch);
		if (site.bazaar() > 0) return new Base(Math.round(site.bazaar()), "bazaar");
		long local = bazaarEstimate(id);
		if (local > 0) return new Base(local, "bazaar");
		if (site.clean() > 0) return new Base(site.clean(), site.sales() + " sales");
		local = auctionEstimate(id);
		if (local > 0) return new Base(local, "recent sales");
		Base trade = tradePrice(id);
		if (trade != null) return trade;
		Long fromSite = siteBase.get(id);
		if (fromSite != null && fromSite > 0) return new Base(fromSite, "dragsim.co");
		// A sale or two, or a listing, says more than a recipe's parts; nobody pays more than the cheapest listing.
		long thin = site.thin(), listed = site.listed();
		if (thin > 0 && (listed <= 0 || thin <= listed)) return new Base(thin, site.sales() + (site.sales() == 1 ? " sale" : " sales"));
		// The owner's price fills in for an item nothing has sold yet; once real sales or a bazaar price exist, they win.
		if (set != null && set > 0) return new Base(set, "set price");
		if (listed > 0) return new Base(listed, "listed");
		// Something anyone can craft is worth its parts, whatever the fire sale once asked for it (a 100k-gem Atomsplit Katana).
		long recipe = depth < RECIPE_DEPTH && pricing.add(id) ? recipeCost(id, depth, pricing, false) : -1;
		if (recipe > 0) return new Base(recipe, "recipe");
		Long sale = fireSale.get(id);
		if (sale != null && sale > 0) return new Base(FireSale.coins(sale), "fire sale");
		Long craft = siteCraft.get(id);
		if (craft != null && craft > 0) return new Base(craft, "dragsim.co recipe");
		return Base.NONE;
	}

	/**
	 * What {@code id}'s cheapest known recipe costs in parts (and coins) per item made: the recipe
	 * shipped with the mod, or one learned in the recipe menu. -1 when a part has no price, or, with
	 * {@code known}, what just the priced parts cost.
	 */
	private long recipeCost(String id, int depth, Set<String> pricing, boolean known) {
		long best = -1;
		for (ItemValue.Recipe r : recipesFor(id)) {
			long total = r.fee();
			for (var part : r.parts().entrySet()) {
				long each = base(part.getKey(), false, depth + 1, new HashSet<>(pricing)).value;
				if (each <= 0) {
					if (known) continue; // only the parts that have a price, for a floor
					total = -1;
					break;
				}
				total += each * part.getValue();
			}
			if (total > 0 && (best < 0 || total / r.makes() < best)) best = total / r.makes();
		}
		return best;
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

	/** One particular item valued: the clean item plus its upgrades, in the site's shape ({@code entry}, {@code item_modifiers}, {@code extra}). */
	public ItemValue.Valued value(JsonObject item, boolean fetch) {
		String id = item.get("entry") instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsString() : "";
		return ItemValue.value(item, id.isEmpty() ? -1 : base(id, fetch).value, prices, model);
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

	private void fetchHistories() {
		Minecraft mc = Minecraft.getInstance();
		while (inFlight.size() < MAX_FETCHES && !queued.isEmpty()) {
			String id = queued.pollFirst();
			inFlight.add(id);
			Http.json("/api/market/item/" + Http.encode(id) + "?days=" + days.get()).whenComplete((json, error) -> mc.execute(() -> {
				inFlight.remove(id);
				MarketHistory h = error == null && json != null && json.isJsonObject()
						? MarketHistory.read(json.getAsJsonObject(), System.currentTimeMillis(), (price, item) -> ItemValue.clean(price, item, prices, model)) : null;
				if (h == null) Lazarus.LOG.debug("Could not read the dragsim.co price history of {}", id);
				else share(id, h);
				histories.put(id, new Fetched(h, System.currentTimeMillis()));
				List.copyOf(listeners).forEach(Runnable::run);
				fetchHistories();
			}));
		}
	}

	/** Everyone's history reads, for the same auction window: each newer than this computer's own is used. */
	private void shared(Map<String, Shared.Entry> got) {
		got.forEach((id, e) -> {
			JsonObject v = e.value();
			if (v.get("days") == null || v.get("days").getAsInt() != days.get()) return;
			Fetched mine = histories.get(id);
			if (mine != null && mine.at >= e.at()) return;
			if (v.has("thin")) {
				histories.put(id, new Fetched(new MarketHistory(num(v, "bazaar"), (long) num(v, "auction"), (int) num(v, "sales"),
						(long) num(v, "clean"), (long) num(v, "thin"), (long) num(v, "listed")), e.at()));
				return;
			}
			// Older versions share only the median of all sales and no listings, so their read never
			// replaces a fuller one this computer made; the median stands in for the clean one, or for
			// the thin one under three sales (it was dropping a lone 1.25B Atomsplit Katana sale).
			if (mine != null) return;
			long auction = (long) num(v, "auction");
			int sales = (int) num(v, "sales");
			boolean few = sales < PriceMath.MIN_SALES;
			histories.put(id, new Fetched(new MarketHistory(num(v, "bazaar"), auction, sales, few ? -1 : auction, few ? auction : -1, -1), e.at()));
		});
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
	 * not a price, and is left out.
	 */
	private void traded(Trade t) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || !trades.on() || mc.player == null) return;
		remember(mc); // what you got is in your inventory by now
		String key = t.key(mc.player.getName().getString());
		long now = System.currentTimeMillis();
		t.prices(name -> {
			String id = Catalog.id(name);
			return id == null ? -1 : value(tradedItem(name, id), false).total();
		}).forEach((name, unit) -> {
			String id = Catalog.id(name);
			if (id == null) return;
			long clean = Math.round(ItemValue.clean(unit, tradedItem(name, id), prices, model));
			if (clean <= 0 || clean < Catalog.npcSell(id)) return;
			addTrade(alias(id), key, clean, now);
			JsonObject v = new JsonObject();
			v.addProperty("price", clean);
			v.addProperty("key", key);
			Shared.send("trades", alias(id), v);
		});
	}

	/** A traded item as its upgrades were last seen in your inventory, or just its id when they weren't. */
	private JsonObject tradedItem(String name, String id) {
		JsonObject data = held.get(name);
		JsonObject item = data == null ? new JsonObject() : data.deepCopy();
		if (!item.has("entry")) item.addProperty("entry", id);
		return item;
	}

	/** Keeps one trade's price for an item, the newest {@link #TRADES_KEPT} of them. */
	private void addTrade(String id, String key, long price, long at) {
		Map<String, long[]> list = traded.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
		list.put(key, new long[]{price, at});
		if (list.size() > TRADES_KEPT) {
			list.entrySet().stream().min((a, b) -> Long.compare(a.getValue()[1], b.getValue()[1])).ifPresent(e -> list.remove(e.getKey()));
		}
	}

	/** The middle of what an item went for in trades lately, or null when it hasn't been traded. */
	private Base tradePrice(String id) {
		Map<String, long[]> list = traded.get(id);
		if (list == null) return null;
		long since = System.currentTimeMillis() - TRADE_MS;
		List<Long> recent = list.values().stream().filter(p -> p[1] >= since).map(p -> p[0]).toList();
		long median = Trade.median(recent);
		return median > 0 ? new Base(median, recent.size() + (recent.size() == 1 ? " trade" : " trades")) : null;
	}

	/** Everyone's trades, {p: [[price, when, key], …]} by item id. */
	private void sharedTrades(Map<String, Shared.Entry> got) {
		got.forEach((id, e) -> {
			if (!(e.value().get("p") instanceof JsonArray list)) return;
			for (JsonElement t : list) {
				try {
					if (t instanceof JsonArray a && a.size() >= 3) addTrade(id, a.get(2).getAsString(), a.get(0).getAsLong(), a.get(1).getAsLong());
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
		String key = alias(id), name = Catalog.item(id).name();
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
		long since = System.currentTimeMillis() - days.get() * DAY;
		return PriceMath.auctionValue(list.stream().filter(s -> s.soldAt >= since).mapToLong(Sale::unitPrice).toArray());
	}

	/** The latest bazaar bid and ask, or null for a non-bazaar item. */
	public Quote bazaar(String id) {
		Deque<Quote> list = quotes.get(id);
		return list == null || list.isEmpty() ? null : list.peekLast();
	}

	private List<Component> describe(String id, int count, JsonObject item) {
		List<Component> out = new ArrayList<>();
		Base base = base(id, true);
		long npc = Catalog.npcSell(id);
		ItemValue.Valued valued = ItemValue.value(item == null ? new JsonObject() : item, base.value, prices, model);
		if (valued.total() > 0 && !(base.source.equals("NPC") && valued.parts().isEmpty())) { // an NPC price alone has its own row
			out.add(Component.literal("§7Estimated value: §6" + Text.compact(valued.total()) + (count > 1 ? " §8(§6" + Text.compact(valued.total() * count) + "§8)" : "")
					+ (base.source.isEmpty() ? "" : " §8· " + base.source)));
			if (!valued.parts().isEmpty()) {
				boolean shift = Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT) || Keys.down(GLFW.GLFW_KEY_RIGHT_SHIFT);
				out.add(Component.literal("§8 Clean §7" + Text.compact(valued.base()) + " §8+ upgrades §7" + Text.compact(valued.upgrades()) + (shift ? "" : " §8· Shift")));
				if (shift) for (ItemValue.Part p : valued.parts()) {
					Catalog.Item named = p.detail().isEmpty() ? null : Catalog.item(p.detail());
					String detail = named != null ? Text.plain(named.name()) : p.detail();
					String worth = p.value() == ItemValue.Part.UNPRICED ? "§8no price yet" : "§6" + Text.compact(p.value());
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
