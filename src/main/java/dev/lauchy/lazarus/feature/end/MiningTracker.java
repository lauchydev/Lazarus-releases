package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.LootFeed;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Period;
import dev.lauchy.lazarus.core.Tallies;
import dev.lauchy.lazarus.core.Tally;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.end.MiningMaterials.Material;
import dev.lauchy.lazarus.feature.end.MiningMaterials.Piece;
import dev.lauchy.lazarus.feature.slayer.SlayerTracker;
import dev.lauchy.lazarus.feature.end.MiningMaterials.Units;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Mining Profit: what you mine in the End and what it's worth. The clock only runs while you
 * mine (a pickaxe or drill swinging at a block, plus a short grace for walking to the next one),
 * so menus and trips to the Forge don't dilute the rate. Material is counted from your bag in
 * raw blocks, so the Personal Compactor turning 160 Obsidian into one Enchanted Obsidian
 * changes nothing, and drops while you mine (Void Fragments) are added at their price.
 */
public final class MiningTracker extends Feature {
	/** How long after the last swing the clock keeps running: the walk to the next block. */
	private static final long GRACE_MS = 10_000;
	/** How long after a mining tool was last in hand the readout stays up. */
	private static final long SHOW_MS = 15_000;
	/** A drop with no staff roll naming its mob is yours from mining if it lands this soon after a swing. */
	private static final long DROP_MS = 2_000;
	/** Void Fragments and Void Cores come from mining amethyst; they count this long after a swing. */
	private static final long RARE_MS = 60_000;
	private static final List<String> MINING_RARES = List.of("Void Fragment", "Void Core");

	/** How the readout prices material: what it sells for, or only what an NPC pays. */
	public enum Prices {
		MARKET, NPC
	}

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or everything you have mined.", Period.SESSION));
	private final ChoiceSetting<Units> units = add(new ChoiceSetting<>("units", "Material units", "Count material in raw blocks, Enchanted or Refined.", Units.ENCHANTED));
	private final ChoiceSetting<Prices> prices = add(new ChoiceSetting<>("prices", "Prices", "Value material at market prices, or at what an NPC pays.", Prices.MARKET));

	private final Tallies tallies = new Tallies(Config.dir().resolve("mining.json"));
	private final MiningMaterials.Gains gains = new MiningMaterials.Gains();
	/** The bag at the last look: raw blocks of each material, and of those in compacted forms. */
	private final Map<Material, Long> bag = new EnumMap<>(Material.class), compacted = new EnumMap<>(Material.class);
	private boolean baseline;
	private long lastTick, lastSwing, toolHeldAt;
	private ClientLevel level;
	private boolean inEnd;

	public MiningTracker() {
		super("miningTracker", "Mining Profit", "What you mine in the End, its value and coins per hour; the clock runs only while you mine.", Category.END, true);
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LootFeed.listen(this::loot);
		Hud.register(new HudElement(this, "miningTracker", "Mining Profit", 0.006f, 0.26f) {
			@Override
			protected boolean visible() {
				return Area.inEnd() && System.currentTimeMillis() - toolHeldAt < SHOW_MS && tallies.shown(period.get()).sum("ms") > 0;
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				if (sample) return Draw.panel(g, font, List.of("§b§lMining §7Session", "§5Obsidian §8· §f48 Ench\t§a3.07M",
						"§dAmethyst §8· §f12 Fine\t§a15.7M", "§f1x §5Void Fragment\t§a80M", "§7Profit\t§a98.8M",
						"§7Per hour\t§a40.2M", "§7Time mined §f12m 34s"));
				return Draw.panel(g, font, rows(period.get()));
			}
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("mining")
				.executes(c -> list(Period.ALL_TIME))
				.then(ClientCommands.literal("session").executes(c -> list(Period.SESSION)))
				.then(ClientCommands.literal("reset").executes(c -> {
					tallies.resetSession();
					gains.clear();
					Message.lazarus().success("Mining session reset. All-time totals are kept.").send();
					return 1;
				}))));
	}

	private void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		// A warp or a server change starts the bag over, so what was carried in isn't counted as mined.
		// Moving about within the End (islands to the Void Sepulture, or a sidebar read that briefly says nothing) doesn't.
		if (mc.level != level || Area.inEnd() != inEnd) forget();
		level = mc.level;
		inEnd = Area.inEnd();
		if (!enabled() || mc.player == null || mc.level == null || !Area.inEnd()) {
			lastTick = 0;
			return;
		}
		if (tool(mc.player.getMainHandItem())) toolHeldAt = now;
		if (swinging(mc)) lastSwing = now;
		boolean mining = lastSwing > 0 && now - lastSwing <= GRACE_MS;
		if (mining && lastTick > 0) {
			long dt = Math.min(now - lastTick, 1_000);
			tallies.both(t -> t.add("ms", (double) dt));
		}
		lastTick = now;
		// Not with a menu open: what comes out of a chest or the storage just after a swing wasn't mined.
		look(mc, mining && mc.screen == null, now);
	}

	/** Whether you're swinging a mining tool at a block right now. */
	private static boolean swinging(Minecraft mc) {
		if (mc.screen != null || !mc.options.keyAttack.isDown()) return false;
		if (!(mc.hitResult instanceof BlockHitResult hit) || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
		return tool(mc.player.getMainHandItem()) && !mc.level.getBlockState(hit.getBlockPos()).isAir();
	}

	/** A pickaxe or a drill; a Drill Motor or other drill part is not one. */
	private static boolean tool(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (stack.is(ItemTags.PICKAXES)) return true;
		String name = Items.name(stack).toLowerCase(Locale.ROOT);
		return name.contains("pickaxe") || name.contains("drill") && !name.contains("motor");
	}

	/** Reads the bag and, while mining, books what it gained since the last look. */
	private void look(Minecraft mc, boolean count, long now) {
		Map<Material, Long> nowBag = new EnumMap<>(Material.class), nowCompacted = new EnumMap<>(Material.class);
		var inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			count(stack, nowBag, nowCompacted);
		}
		count(mc.player.containerMenu.getCarried(), nowBag, nowCompacted); // a stack on the cursor is still in the bag
		if (!count) gains.clear();
		if (baseline && count) {
			Tally session = tallies.session("");
			for (Material m : Material.values()) {
				long change = nowBag.getOrDefault(m, 0L) - bag.getOrDefault(m, 0L);
				if (change == 0) continue;
				long rise = Math.max(0, nowCompacted.getOrDefault(m, 0L) - compacted.getOrDefault(m, 0L));
				long mined = gains.apply(m, (long) session.sum(key(m)), change, rise, now);
				// All time follows the session's change, floored at zero the same way.
				if (mined != 0) tallies.both(t -> t.add(key(m), Math.max(-t.sum(key(m)), (double) mined)));
			}
		}
		bag.clear();
		bag.putAll(nowBag);
		compacted.clear();
		compacted.putAll(nowCompacted);
		baseline = true;
	}

	/** Adds one stack's material to the bag being counted, and to its compacted part when it's a compacted form. */
	private static void count(ItemStack stack, Map<Material, Long> bag, Map<Material, Long> compacted) {
		if (stack.isEmpty()) return;
		String id = Items.id(stack);
		Piece p = MiningMaterials.piece(id != null ? id : Items.name(stack), stack.getCount());
		if (p == null) return;
		bag.merge(p.material(), p.units(), Long::sum);
		if (p.compacted()) compacted.merge(p.material(), p.units(), Long::sum);
	}

	private void forget() {
		baseline = false;
		bag.clear();
		compacted.clear();
		gains.clear();
		lastSwing = 0;
		toolHeldAt = 0;
		lastTick = 0;
	}

	/**
	 * A drop is mined when no staff roll names a mob and it lands just after a swing; Void Fragments and
	 * Cores within a minute. What a zealot or a slayer boss drops is theirs, however close to a swing.
	 */
	private void loot(LootFeed.Loot l) {
		if (!enabled() || lastSwing == 0 || l.mob() != null) return;
		String item = l.drop().item();
		if (ZealotTracker.zealotDrop(item) || SlayerTracker.claims(l.at())) return;
		long since = l.at() - lastSwing;
		boolean rare = MINING_RARES.stream().anyMatch(item::equalsIgnoreCase);
		if (since < 0 || since > (rare ? RARE_MS : DROP_MS)) return;
		tallies.both(t -> t.drop(item, l.drop().count()));
	}

	private static String key(Material m) {
		return "units." + m.name();
	}

	/** One item's worth by name under the chosen prices, or -1 when unknown. */
	private ToLongFunction<String> price() {
		return prices.get() == Prices.NPC ? name -> Catalog.npcSell(Catalog.id(name)) : PriceDatabase::worth;
	}

	private List<String> rows(Period p) {
		Tally t = tallies.shown(p);
		ToLongFunction<String> price = price();
		List<String> out = new ArrayList<>();
		out.add("§b§lMining §7" + p.label());
		long profit = 0;
		List<long[]> order = new ArrayList<>();
		for (Material m : Material.values()) {
			long raw = (long) t.sum(key(m));
			if (raw <= 0) continue;
			long worth = MiningMaterials.value(m, raw, price);
			profit += worth;
			order.add(new long[]{m.ordinal(), raw, worth});
		}
		order.sort((a, b) -> Long.compare(b[2], a[2]));
		for (long[] o : order) {
			Material m = Material.values()[(int) o[0]];
			out.add(m.colour + m.label + " §8· §f" + MiningMaterials.amount(m, o[1], units.get()) + "\t" + (o[2] > 0 ? "§a" + Text.compact(o[2]) : "§8?"));
		}
		for (var e : LootRows.byValue(t.drops(), price)) {
			out.add("§f" + e.getValue() + "x " + LootRows.colour(e.getKey()) + e.getKey() + "\t" + LootRows.worth(e.getKey(), e.getValue(), price));
		}
		profit += LootRows.total(t.drops(), price);
		if (out.size() == 1) out.add("§7Nothing mined yet");
		long ms = (long) t.sum("ms");
		out.add("§7Profit\t§a" + Text.compact(profit));
		out.add("§7Per hour\t" + (ms < 5_000 || profit <= 0 ? "§8—" : "§a" + Text.compact(profit * 3_600_000.0 / ms)));
		out.add("§7Time mined §f" + MiningMaterials.time(ms));
		return out;
	}

	private int list(Period p) {
		if (tallies.shown(p).sum("ms") <= 0) {
			Message.lazarus().info(p == Period.SESSION ? "Nothing mined this session." : "Nothing mined yet.").send();
			return 1;
		}
		Message.panel(rows(p)).send();
		return 1;
	}
}
