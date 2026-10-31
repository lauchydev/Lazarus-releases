package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prices skins, dyes, runes and the rest of what only the fire sale sells, by reading the
 * fire sale history. {@code /lazarus firesale index} opens {@code /firesale}, clicks the
 * clock to the history and reads every page of it: each past sale says what the item cost
 * in gems, and a gem is worth what the owner set ({@link FireSale#coinsPerGem()}). The
 * prices go to the {@link PriceDatabase}, and through the Lazarus server to everyone, so an
 * item with a skin or dye on it is valued with it.
 */
public final class FireSaleIndex extends Feature {
	/** Where the indexer is: waiting for the sale menu, for the history, reading a page, or turning it. */
	private enum Step { OFF, OPENING, TO_HISTORY, READING, TURNING }

	/** Ticks between the indexer's clicks, so the server is browsed at a person's pace. */
	private static final int CLICK_TICKS = 6;
	/** Ticks the indexer waits for a window before trying again, and how many tries it gets. */
	private static final int WAIT_TICKS = 80, TRIES = 3;
	private Step step = Step.OFF;
	/** Ticks until the indexer acts again, ticks it has waited for the window it expects, and tries left. */
	private int delay, waited, tries;
	/** The pages read so far, by what was on them, so the last page (whose arrow turns nowhere) ends the run. */
	private final Set<String> pagesSeen = new HashSet<>();
	/** What the page read last showed, so the next is read only once the server has swapped the items. */
	private String lastPage = "";
	/** How many sales were read, how many of them nothing could price, and how many pages were read. */
	private int read, unpriced, pages;

	public FireSaleIndex() {
		super("fireSaleIndex", "Fire Sale Index", "Prices skins, dyes and runes from the fire sale history.", Category.GENERAL, true);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::index);
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("firesale")
				.then(ClientCommands.literal("index").executes(c -> {
					start();
					return 1;
				}))
				.then(ClientCommands.literal("stop").executes(c -> {
					stop("Fire sale indexing stopped.");
					return 1;
				}))));
	}

	/** Opens the fire sale menu and starts reading its history. */
	private void start() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || step != Step.OFF) return;
		if (!enabled()) {
			Message.lazarus().error("Turn on Fire Sale Index first.").send();
			return;
		}
		step = Step.OPENING;
		waited = 0;
		tries = TRIES;
		read = unpriced = pages = 0;
		pagesSeen.clear();
		lastPage = "";
		delay = CLICK_TICKS;
		mc.player.connection.sendCommand("firesale");
		Message.lazarus().info("Indexing the fire sale history. ").command("Stop", "Stop indexing", "/lazarus firesale stop").send();
	}

	/** Stops the indexer and says how it went. */
	private void stop(String why) {
		if (step == Step.OFF) return;
		step = Step.OFF;
		Message m = Message.lazarus().info(why + " ").highlight(read + (read == 1 ? " sale" : " sales")).info(" on ").highlight(pages + (pages == 1 ? " page" : " pages")).info(".");
		if (unpriced > 0) m.muted(" " + unpriced + " said no gem price; the log has their lore.");
		m.send();
	}

	/**
	 * One step of the indexer each tick: wait for the sale menu, click its clock, read every
	 * sale on the history page, turn to the next, and finish when a page repeats or the
	 * arrow is gone. Closing the menu stops it.
	 */
	private void index(Minecraft mc) {
		if (step == Step.OFF) return;
		if (mc.player == null || mc.gameMode == null) {
			stop("Fire sale indexing stopped.");
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		AbstractContainerScreen<?> screen = mc.screen instanceof AbstractContainerScreen<?> cs ? cs : null;
		String title = screen == null ? null : Text.plain(screen.getTitle());
		switch (step) {
			case OPENING -> {
				if (FireSale.isSale(title)) {
					int clock = find(screen, FireSale.HISTORY);
					if (clock < 0) {
						stop("The fire sale menu has no history clock.");
						return;
					}
					click(mc, screen, clock);
					step = Step.TO_HISTORY;
					waited = 0;
					return;
				}
				waitFor(mc);
			}
			case TO_HISTORY -> {
				if (FireSale.isHistory(title)) {
					step = Step.READING;
					waited = 0;
					return;
				}
				if (screen == null && waited > 20) {
					stop("Fire sale indexing stopped."); // the menu was closed
					return;
				}
				waitFor(mc);
			}
			case READING -> {
				if (!FireSale.isHistory(title)) {
					stop("Fire sale indexing stopped.");
					return;
				}
				if (screen.getMenu().slots.size() <= FireSale.NEXT) {
					stop("The fire sale history isn't laid out the way Lazarus knows.");
					return;
				}
				List<ItemStack> items = new ArrayList<>();
				for (int slot : FireSale.ITEMS) items.add(screen.getMenu().getSlot(slot).getItem());
				StringBuilder key = new StringBuilder();
				for (ItemStack s : items) key.append(Items.name(s)).append('|').append(String.join("/", Items.lore(s))).append('\n');
				String page = key.toString();
				if (items.stream().allMatch(ItemStack::isEmpty) || page.equals(lastPage)) {
					// The page's items haven't arrived, or the server hasn't swapped them yet; give them a moment.
					if (++waited > WAIT_TICKS) stop(pages == 0 ? "The fire sale history showed nothing." : "Fire sale indexing finished.");
					return;
				}
				lastPage = page;
				if (!pagesSeen.add(page)) {
					stop("Fire sale indexing finished."); // back at a page already read: the history wrapped round
					return;
				}
				pages++;
				for (ItemStack s : items) learn(s);
				if (PriceDatabase.INSTANCE != null) PriceDatabase.INSTANCE.fireSaleRead();
				Toast.show("§fFire sale §7page " + pages, Theme.ACCENT);
				ItemStack next = screen.getMenu().getSlot(FireSale.NEXT).getItem();
				if (next.isEmpty() || !FireSale.turns(Items.name(next))) {
					stop("Fire sale indexing finished.");
					return;
				}
				click(mc, screen, FireSale.NEXT);
				step = Step.TURNING;
				waited = 0;
			}
			case TURNING -> {
				if (FireSale.isHistory(title)) {
					step = Step.READING;
					waited = 0;
					delay = CLICK_TICKS; // let the new page's items arrive
					return;
				}
				if (++waited > WAIT_TICKS) stop("The fire sale history didn't turn its page.");
			}
			default -> {
			}
		}
	}

	/** Prices one past sale from its lore; a sale with no gem price is counted and logged. */
	private void learn(ItemStack stack) {
		if (stack.isEmpty()) return;
		String name = Items.name(stack);
		List<String> lore = Items.lore(stack);
		if (name.isBlank() && lore.isEmpty()) return; // the last page's spare slots hold blank filler, not sales
		String id = Items.id(stack);
		FireSale.Listing listing = FireSale.read(lore);
		if (id == null || listing == null) {
			unpriced++;
			Lazarus.LOG.info("Fire sale item not priced: {} ({}) {}", name, id, lore);
			return;
		}
		read++;
		if (listing.buyers() < 0) Lazarus.LOG.info("Fire sale buyers not read: {} ({}) {}", name, id, lore);
		if (PriceDatabase.INSTANCE != null) PriceDatabase.INSTANCE.fireSale(id, listing);
	}

	/** The slot of the open window's item called {@code name}, or -1. */
	private static int find(AbstractContainerScreen<?> screen, String name) {
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).container instanceof net.minecraft.world.entity.player.Inventory) break;
			if (Items.name(slots.get(i).getItem()).equalsIgnoreCase(name)) return i;
		}
		return -1;
	}

	/** Counts a tick spent waiting for the menu; past the limit it asks for the menu again, and gives up after a few tries. */
	private void waitFor(Minecraft mc) {
		if (++waited < WAIT_TICKS) return;
		waited = 0;
		if (--tries <= 0) {
			stop("The fire sale menu stopped answering.");
			return;
		}
		step = Step.OPENING;
		mc.player.connection.sendCommand("firesale");
	}

	/** Left-clicks one of the open window's slots, then waits a moment before the next step. */
	private void click(Minecraft mc, AbstractContainerScreen<?> screen, int index) {
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, index, 0, ContainerInput.PICKUP, mc.player);
		delay = CLICK_TICKS;
	}
}
