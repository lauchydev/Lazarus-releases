package dev.lauchy.lazarus.feature.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Shared;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Keys;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Recipes, learned by looking. Every recipe page you open in the server's recipe menu is
 * remembered in config/lazarus/recipes.json; from then on that item's tooltip lists what
 * it takes, how much of each you carry, and what the parts cost at estimated prices.
 * Nothing is bundled: the book fills in as Lazarus users browse, since every recipe anyone
 * opens is shared through the Lazarus server. {@code /lazarus recipes index} browses the
 * whole menu for you, page by page, opening every recipe once.
 */
public final class Recipes extends Feature {
	/** The one instance, so prices can use what it has learned. */
	public static Recipes INSTANCE;
	/** The recipe page: a 3x3 grid on the left, a crafting table, and the result. */
	private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
	private static final int RESULT = 25, TABLE = 23, SIZE = 54;
	private static final Path FILE = Config.dir().resolve("recipes.json");

	public record Part(String name, int count) {
	}

	public record Recipe(String result, int count, List<Part> grid) {
		/** Each ingredient once, summed over the grid. */
		public Map<String, Integer> needs() {
			Map<String, Integer> out = new LinkedHashMap<>();
			for (Part p : grid) if (p != null) out.merge(p.name, p.count, Integer::sum);
			return out;
		}
	}

	private final BoolSetting shift = add(new BoolSetting("shift", "Only with Shift", "Show the recipe in tooltips only while Shift is held.", true));
	private final BoolSetting cost = add(new BoolSetting("cost", "Craft cost", "Price the ingredients with the price database.", true));

	private final Map<String, Recipe> recipes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private boolean dirty;

	/** Where the indexer is: waiting for the menu, turning to its page, clicking items, on a recipe, or coming back. */
	private enum Step { OFF, OPENING, TURNING, LISTING, ON_RECIPE, RETURNING }

	/** Ticks between the indexer's clicks, so the server is browsed at a person's pace. */
	private static final int CLICK_TICKS = 6;
	/** Ticks the indexer waits for a window before trying again, and how many tries it gets. */
	private static final int WAIT_TICKS = 80, TRIES = 3;
	private Step step = Step.OFF;
	/** The page being indexed, how many there are, and the next item slot on it. */
	private int page = 1, pages = 1, slot;
	/** Ticks until the indexer acts again, ticks it has waited for the window it expects, and tries left. */
	private int delay, waited, tries;
	/** How many recipes there were when indexing began, and how many items it opened. */
	private int before, opened;
	/** Whether the recipe page being waited on has shown. */
	private boolean shown;

	public Recipes() {
		super("recipes", "Recipes", "Learns recipes as you open them and shows them on items.", Category.INVENTORY, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		load();
		Shared.follow("recipes", Duration.ofMinutes(30), got -> {
			got.forEach((result, e) -> {
				Recipe r = read(result, e.value());
				if (r != null && !r.equals(recipes.get(result))) {
					recipes.put(result, r);
					dirty = true;
				}
			});
			if (dirty) save();
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::index);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			ScreenEvents.afterTick(screen).register(s -> capture(cs));
			ScreenEvents.remove(screen).register(s -> {
				if (dirty) save();
			});
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!enabled() || shift.on() && !Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT) && !Keys.down(GLFW.GLFW_KEY_RIGHT_SHIFT)) return;
			Recipe r = recipes.get(Items.name(stack));
			if (r == null) return;
			lines.add(Component.empty());
			lines.addAll(describe(r));
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("recipes")
				.then(ClientCommands.literal("index").executes(c -> {
					startIndex();
					return 1;
				}))
				.then(ClientCommands.literal("stop").executes(c -> {
					stopIndex("Recipe indexing stopped.");
					return 1;
				}))));
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("recipe")
				.then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> {
					String q = StringArgumentType.getString(c, "item");
					Recipe r = recipes.get(q);
					if (r == null) Message.lazarus().error("No recipe for ").highlight(q).error(" yet.").muted(" Open it in the recipe menu once.").send();
					else tell(r);
					return 1;
				}))));
	}

	/** The recipe learned for an item by its name, or null. */
	public Recipe recipe(String itemName) {
		return recipes.get(itemName);
	}

	/** Starts browsing the whole recipe menu from its first page. */
	private void startIndex() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || step != Step.OFF) return;
		if (!enabled()) {
			Message.lazarus().error("Turn on Recipes first.").send();
			return;
		}
		step = Step.OPENING;
		page = 1;
		pages = 1;
		slot = 0;
		waited = 0;
		tries = TRIES;
		before = recipes.size();
		opened = 0;
		delay = CLICK_TICKS;
		mc.player.connection.sendCommand("recipes");
		Message.lazarus().info("Indexing every recipe. ").command("Stop", "Stop indexing", "/lazarus recipes stop").send();
	}

	/** Stops the indexer and says how it went. */
	private void stopIndex(String why) {
		if (step == Step.OFF) return;
		step = Step.OFF;
		if (dirty) save();
		int learned = recipes.size() - before;
		Message.lazarus().info(why + " ").highlight(opened + " opened").info(", ").highlight(Math.max(0, learned) + " new").info(".").send();
	}

	/**
	 * One step of the indexer each tick: open the menu, turn to the page being indexed, click
	 * the next item whose lore offers its recipe, wait for the recipe page (which {@link #capture}
	 * learns), go back, and turn the page when it's done. Closing the menu stops it.
	 */
	private void index(Minecraft mc) {
		if (step == Step.OFF) return;
		if (mc.player == null || mc.gameMode == null) {
			stopIndex("Recipe indexing stopped.");
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		AbstractContainerScreen<?> screen = mc.screen instanceof AbstractContainerScreen<?> cs ? cs : null;
		int[] at = screen == null ? null : RecipeMenu.page(Text.plain(screen.getTitle()));
		switch (step) {
			case OPENING, RETURNING -> {
				if (at != null) {
					pages = at[1];
					step = Step.TURNING;
					waited = 0;
				} else if (step == Step.RETURNING && screen != null && waited % 20 == 0 && waited > 0) {
					click(mc, screen, RecipeMenu.BACK); // the back arrow didn't take; press it again
				} else if (screen == null && waited > 20 && step == Step.RETURNING) {
					stopIndex("Recipe indexing stopped."); // the menu was closed
					return;
				}
				waitFor(mc);
			}
			case TURNING -> {
				if (at == null) {
					waitFor(mc);
					return;
				}
				int arrow = RecipeMenu.toward(at[0], page);
				if (arrow < 0) {
					step = Step.LISTING;
					waited = 0;
					tries = TRIES;
				} else if (waited % 20 == 0) {
					click(mc, screen, arrow);
				}
				waited++;
				if (waited > WAIT_TICKS) stopIndex("The recipe menu didn't turn its page.");
			}
			case LISTING -> {
				if (at == null) {
					step = Step.RETURNING;
					return;
				}
				while (slot < RecipeMenu.ITEMS.length) {
					ItemStack s = screen.getMenu().getSlot(RecipeMenu.ITEMS[slot++]).getItem();
					if (s.isEmpty() || !RecipeMenu.opensRecipe(Items.lore(s))) continue;
					click(mc, screen, RecipeMenu.ITEMS[slot - 1]);
					opened++;
					shown = false;
					step = Step.ON_RECIPE;
					waited = 0;
					return;
				}
				if (page >= pages) {
					stopIndex("Recipe indexing finished.");
					return;
				}
				page++;
				slot = 0;
				step = Step.TURNING;
				waited = 0;
				Toast.show("§fRecipes §7page " + page + "/" + pages, Theme.ACCENT);
			}
			case ON_RECIPE -> {
				ItemStack result = screen == null || at != null || screen.getMenu().slots.size() <= RESULT ? ItemStack.EMPTY : screen.getMenu().getSlot(RESULT).getItem();
				boolean loaded = !result.isEmpty() && !BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().endsWith("glass_pane")
						&& Items.name(screen.getMenu().getSlot(TABLE).getItem()).equals("Crafting Table");
				if (loaded && !shown) {
					// Give the page's own tick listener a moment to learn it before leaving.
					shown = true;
					delay = 2;
					return;
				}
				if (loaded || waited > WAIT_TICKS) {
					shown = false;
					// The page's own tick listener has learned it by now; a page that never came is passed over.
					if (screen != null && at == null) click(mc, screen, RecipeMenu.BACK);
					step = Step.RETURNING;
					waited = 0;
					tries = TRIES;
					return;
				}
				waited++;
			}
			default -> {
			}
		}
	}

	/** Counts a tick spent waiting for the menu; past the limit it asks for the menu again, and gives up after a few tries. */
	private void waitFor(Minecraft mc) {
		if (++waited < WAIT_TICKS) return;
		waited = 0;
		if (--tries <= 0) {
			stopIndex("The recipe menu stopped answering.");
			return;
		}
		step = Step.OPENING;
		mc.player.connection.sendCommand("recipes");
	}

	/** Left-clicks one of the open window's slots, then waits a moment before the next step. */
	private void click(Minecraft mc, AbstractContainerScreen<?> screen, int index) {
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, index, 0, ContainerInput.PICKUP, mc.player);
		delay = CLICK_TICKS;
	}

	private void capture(AbstractContainerScreen<?> screen) {
		if (!enabled()) return;
		List<ItemStack> slots = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) break;
			slots.add(s.getItem());
		}
		if (slots.size() < SIZE || !Items.name(slots.get(TABLE)).equals("Crafting Table")) return;
		ItemStack result = slots.get(RESULT);
		if (result.isEmpty() || BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().endsWith("glass_pane")) return; // not arrived yet
		List<Part> grid = new ArrayList<>();
		boolean any = false;
		for (int i : GRID) {
			ItemStack s = slots.get(i);
			boolean filler = s.isEmpty() || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("glass_pane");
			grid.add(filler ? null : new Part(Items.name(s), s.getCount()));
			any |= !filler;
		}
		if (!any) return;
		Recipe r = new Recipe(Items.name(result), result.getCount(), grid);
		if (!r.equals(recipes.get(r.result))) {
			recipes.put(r.result, r);
			dirty = true;
			Shared.send("recipes", r.result, json(r));
		}
	}

	private List<Component> describe(Recipe r) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("§5Recipe" + (r.count > 1 ? " §7(makes " + r.count + ")" : "")));
		Minecraft mc = Minecraft.getInstance();
		long total = 0;
		boolean unpriced = false;
		for (var e : r.needs().entrySet()) {
			int have = mc.player == null ? 0 : carried(mc.player.getInventory(), e.getKey());
			String colour = have >= e.getValue() ? "§a" : "§c";
			out.add(Component.literal("§7 " + e.getValue() + "x §f" + e.getKey() + " " + colour + "(" + Math.min(have, e.getValue()) + "/" + e.getValue() + ")"));
			long each = PriceDatabase.INSTANCE == null ? -1 : PriceDatabase.INSTANCE.estimate(Catalog.id(e.getKey()));
			if (each < 0) unpriced = true;
			else total += each * e.getValue();
		}
		if (cost.on() && total > 0) out.add(Component.literal("§7 Parts cost §6" + Text.compact(total) + (unpriced ? " §7+ ?" : "")));
		return out;
	}

	/** The recipe in chat: what it makes, then each part with how many you carry, green when you have enough. */
	private void tell(Recipe r) {
		Minecraft mc = Minecraft.getInstance();
		Message m = Message.lazarus().info("Recipe for ").highlight(r.result).info(r.count > 1 ? " (makes " + r.count + ")" : "");
		long total = 0;
		boolean unpriced = false;
		for (var e : r.needs().entrySet()) {
			int have = mc.player == null ? 0 : carried(mc.player.getInventory(), e.getKey());
			String got = "(" + Math.min(have, e.getValue()) + "/" + e.getValue() + ")";
			m.line().info(e.getValue() + "x ").highlight(e.getKey()).info(" ");
			if (have >= e.getValue()) m.success(got);
			else m.error(got);
			long each = PriceDatabase.INSTANCE == null ? -1 : PriceDatabase.INSTANCE.estimate(Catalog.id(e.getKey()));
			if (each < 0) unpriced = true;
			else total += each * e.getValue();
		}
		if (cost.on() && total > 0) m.line().info("Parts cost ").highlight(Text.compact(total)).info(unpriced ? " + ?" : "");
		m.send();
	}

	private static int carried(Inventory inv, String name) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && Items.name(s).equals(name)) n += s.getCount();
		}
		return n;
	}

	private void load() {
		JsonElement json = Config.readJson(FILE);
		if (json == null || !json.isJsonObject()) return;
		for (var e : json.getAsJsonObject().entrySet()) {
			Recipe r = e.getValue() instanceof JsonObject o ? read(e.getKey(), o) : null;
			if (r != null) recipes.put(e.getKey(), r); // one bad entry shouldn't lose the rest of the book
		}
	}

	/** A recipe as saved and shared, {count, grid: [{name, count} or null]}; null when it can't be read. */
	private static Recipe read(String result, JsonObject o) {
		try {
			List<Part> grid = new ArrayList<>();
			for (JsonElement cell : o.getAsJsonArray("grid")) {
				grid.add(cell.isJsonNull() ? null : new Part(cell.getAsJsonObject().get("name").getAsString(), cell.getAsJsonObject().get("count").getAsInt()));
			}
			return new Recipe(result, o.get("count").getAsInt(), grid);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** A recipe as saved and shared. */
	private static JsonObject json(Recipe r) {
		JsonObject o = new JsonObject();
		o.addProperty("count", r.count);
		JsonArray grid = new JsonArray();
		for (Part p : r.grid) {
			if (p == null) {
				grid.add(JsonNull.INSTANCE);
				continue;
			}
			JsonObject cell = new JsonObject();
			cell.addProperty("name", p.name);
			cell.addProperty("count", p.count);
			grid.add(cell);
		}
		o.add("grid", grid);
		return o;
	}

	private void save() {
		JsonObject root = new JsonObject();
		for (Recipe r : recipes.values()) root.add(r.result, json(r));
		Config.writeJson(FILE, root);
		dirty = false;
	}
}
