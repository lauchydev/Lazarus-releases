package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.feature.profile.ProfileItems;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A case-opening roll, the CS2 kind, when you open a Bedrock Chest: a strip of items spins past a
 * marker and slows to a stop on the best item in the chest (the dearest that isn't essence). The chest stays hidden behind it until
 * it lands, once per chest each run; a click or Esc skips to the end, and another puts the chest
 * back. The other cards are what that chest can really hold on this floor ({@link ChestPools}, cheap items often,
 * dear ones rarely), the chest's own items among them; essence and the window's buttons are never a card, and a
 * chest holding essence alone has no prize, so it isn't rolled. Nothing is sent to the server, so the chest itself
 * is untouched.
 */
public final class ChestRoll extends Feature {
	/** The one instance, for the tooltip hook. */
	public static ChestRoll INSTANCE;

	/** A card's size on the strip, and the space between cards. */
	private static final int CARD_W = 52, CARD_H = 60, CARD_GAP = 4;
	/** How long to wait for the server to fill the chest, and to let the last slots land once items show. */
	private static final long WAIT_MS = 2500, SETTLE_MS = 250, RESULT_MS = 3500;
	/** How long a roll outlives its window: the server may shut the chest and open it again at once to redraw it. */
	private static final long CARRY_MS = 600;

	/** Where the roll is: not showing, waiting for the chest's items, spinning, or showing what it landed on. */
	private enum State { NONE, WAITING, ROLLING, RESULT }

	/** One card on the strip: the item it shows, its rarity, and what it's worth (-1 unpriced). */
	private record Card(ItemStack stack, String rarity, long value) {
	}

	/** How long the strip spins. */
	private final IntSetting seconds = add(new IntSetting("seconds", "Roll time", "How long the strip spins before it stops.", 6, 3, 12, "s"));
	/** Roll for every reward chest, not just Bedrock. */
	private final BoolSetting everyChest = add(new BoolSetting("everyChest", "Every chest", "Roll for every dungeon reward chest, not just Bedrock.", false));
	/** Tick and chime while rolling. */
	private final BoolSetting sound = add(new BoolSetting("sound", "Sound", "Tick as each card passes the marker, and chime when it stops.", true));

	/** Where the roll is. */
	private State state = State.NONE;
	/** The chest window the roll covers, the chest it's for, and when that window last went away. */
	private AbstractContainerScreen<?> screen;
	private String rolling;
	private long leftAt;
	/** The cards, in order, the winner at {@link Reel#WINNER}. */
	private List<Card> strip = List.of();
	/** The chest's best item, where the strip stops. */
	private Card winner;
	/** Where the strip stops, in pixels. */
	private double stop;
	/** When the chest opened, its items showed, the spin started and it stopped. */
	private long waitAt, itemsAt, startAt, endAt;
	/** The card last under the marker, for the tick as each passes. */
	private int lastUnder = -1;
	/** Chests rolled this run, by material, so opening one again shows it straight away. */
	private final Set<String> rolled = new HashSet<>();
	/** The world of the run, to forget what was rolled when a new one starts. */
	private Object level;
	/** Shuffles the strip and where on the winning card it stops. */
	private final Random rnd = new Random();

	public ChestRoll() {
		super("chestRoll", "Chest Roll", "Plays a CS2-style case roll when you open a Bedrock Chest, landing on its best item.", Category.DUNGEONS, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != level) {
				level = mc.level;
				rolled.clear();
			}
			// A roll whose window went and didn't come straight back is over.
			if (screen == null && state != State.NONE && Util.getMillis() - leftAt > CARRY_MS) state = State.NONE;
		});
		// A new run's chests roll afresh, even when the run is in the same world as the last.
		Chat.listen(l -> {
			if (l.text().startsWith("Reward chests have appeared")) rolled.clear();
		});
		ScreenEvents.AFTER_INIT.register((mc, s, w, h) -> {
			if (!(s instanceof AbstractContainerScreen<?> cs)) return;
			String material = ChestProfit.material(cs);
			if (material == null || !enabled() || (!material.equals("Bedrock") && !everyChest.on())) return;
			if (screen == cs) return; // init runs again on every resize
			// The same chest opened again the moment it shut is the server redrawing it: the roll carries on in the new window.
			boolean carried = screen == null && state != State.NONE && material.equals(rolling) && Util.getMillis() - leftAt < CARRY_MS;
			Lazarus.LOG.info("[Chest Roll] {} Chest opened: {}", material, carried ? "the roll carries on" : rolled.contains(material) ? "rolled already this run" : "waiting for its items");
			if (!carried) {
				if (rolled.contains(material)) return;
				state = State.WAITING;
				waitAt = Util.getMillis();
				itemsAt = 0;
				rolling = material;
			}
			screen = cs;
			ScreenEvents.afterTick(s).register(x -> tick(cs));
			ScreenEvents.afterExtract(s).register((x, g, mx, my, d) -> {
				if (covering(cs)) draw(g, cs);
			});
			ScreenEvents.remove(s).register(x -> {
				if (screen == cs) {
					screen = null;
					leftAt = Util.getMillis();
				}
			});
			ScreenMouseEvents.allowMouseClick(s).register((x, click) -> {
				if (!covering(cs)) return true;
				skip();
				return false;
			});
			ScreenMouseEvents.allowMouseRelease(s).register((x, click) -> !covering(cs));
			ScreenMouseEvents.allowMouseScroll(s).register((x, mx, my, hx, vy) -> !covering(cs));
			ScreenKeyboardEvents.allowKeyPress(s).register((x, key) -> {
				if (!covering(cs)) return true;
				if (key.key() == 256 || key.key() == 257 || key.key() == 32) skip(); // Esc, Enter or Space: Esc stays here rather than closing the chest
				return false;
			});
		});
	}

	/** Whether the roll covers this window (its tooltips are hidden while it does). */
	public static boolean covering(AbstractContainerScreen<?> cs) {
		return INSTANCE != null && INSTANCE.screen == cs && INSTANCE.state != State.NONE;
	}

	/** Waits for the chest's items, then builds the strip and starts it spinning; gives up if none come. */
	private void tick(AbstractContainerScreen<?> cs) {
		long now = Util.getMillis();
		if (state == State.RESULT && now - endAt > RESULT_MS) state = State.NONE;
		if (state != State.WAITING) return;
		ChestProfit.Chest chest = ChestProfit.read(cs);
		if (chest == null || chest.rewards().isEmpty()) {
			if (now - waitAt > WAIT_MS) state = State.NONE;
			return;
		}
		if (itemsAt == 0) itemsAt = now;
		if (now - itemsAt < SETTLE_MS) return;
		start(chest);
	}

	private void start(ChestProfit.Chest chest) {
		rolled.add(chest.material()); // only now: a window that shut before its items came hasn't been rolled
		int won = ChestLoot.winner(chest.rewards().stream().map(r -> Text.plain(r.name())).toList(), chest.rewards().stream().map(ChestProfit.Reward::value).toList());
		if (won < 0) {
			// Essence alone: nothing to roll for, so the chest shows as it is.
			Lazarus.LOG.info("[Chest Roll] The {} Chest holds essence alone: not rolled", chest.material());
			state = State.NONE;
			return;
		}
		int floor = ChestPools.floor(Area.sidebar());
		Lazarus.LOG.info("[Chest Roll] Rolling the {} Chest of floor {}: {} rewards", chest.material(), floor, chest.rewards().size());
		ChestProfit.Reward best = chest.rewards().get(won);
		winner = new Card(best.stack().copyWithCount(1), rarity(best.stack(), best.id()), best.value());
		List<Card> pool = new ArrayList<>();
		// The cards: what this chest holds, and what the wiki says a chest like it can hold. Never essence.
		Set<String> ids = new LinkedHashSet<>(ChestPools.of(floor, chest.material()));
		for (ChestProfit.Reward r : chest.rewards()) {
			if (ChestLoot.essence(Text.plain(r.name()))) continue;
			pool.add(new Card(r.stack().copyWithCount(1), rarity(r.stack(), r.id()), r.value()));
			if (r.id() != null) ids.remove(r.id()); // the chest's own are in already
		}
		for (String id : ids) {
			// Drawn as the wiki says the item looks: the mod's own list knows many of these by name alone.
			ChestLooks.Look look = ChestLooks.of(id);
			if (look == null) continue;
			ItemStack shown = ProfileItems.plain(id, look.name(), look.rarity(), look.material(), look.colour(), look.skin());
			if (shown.is(net.minecraft.world.item.Items.BARRIER)) continue; // no picture for it: no card
			long v = PriceDatabase.INSTANCE == null ? -1 : PriceDatabase.INSTANCE.trackerValue(PriceDatabase.cleanItem(id), false);
			pool.add(new Card(shown, look.rarity(), v));
		}
		long cheapest = pool.stream().mapToLong(Card::value).filter(v -> v > 0).min().orElse(1);
		strip = Reel.fill(pool, c -> c.value <= 0 ? 0.6 : Math.clamp(Math.pow((double) cheapest / c.value, 0.3), 0.04, 1), winner, rnd);
		stop = Reel.stop(CARD_W, CARD_GAP, Reel.LAND_MIN + rnd.nextDouble() * (Reel.LAND_MAX - Reel.LAND_MIN));
		startAt = Util.getMillis();
		lastUnder = -1;
		state = State.ROLLING;
	}

	/** Esc, a click, Enter or Space: to the end of the roll, then away. */
	private void skip() {
		switch (state) {
			case WAITING -> state = State.NONE;
			case ROLLING -> startAt = Util.getMillis() - total();
			case RESULT -> state = State.NONE;
			default -> {
			}
		}
	}

	private long total() {
		return seconds.get() * 1000L;
	}

	private void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> cs) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		long now = Util.getMillis();
		g.nextStratum();
		Draw.rect(g, 0, 0, cs.width, cs.height, 0xFF101014); // hides the chest until it lands: nothing of it shows through
		int cx = cs.width / 2, cy = cs.height / 2;
		if (state == State.WAITING) {
			Draw.textCentered(g, font, "Opening…", cx, cy - 4, Theme.MUTED);
			return;
		}
		long t = now - startAt;
		double offset = Reel.offset(Reel.start(CARD_W, CARD_GAP), stop, t, total());
		if (state == State.ROLLING && t >= total()) {
			state = State.RESULT;
			endAt = now;
			if (sound.on() && mc.player != null) mc.player.playSound(winner.value > 0 && winner.value >= 1_000_000_000L ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 0.8f, 1f);
		}
		int under = Reel.under(offset, CARD_W, CARD_GAP);
		if (state == State.ROLLING && under != lastUnder) {
			if (lastUnder >= 0 && sound.on() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.6f);
			lastUnder = under;
		}

		int stripW = Math.min(cs.width - 32, 7 * (CARD_W + CARD_GAP));
		int left = cx - stripW / 2, top = cy - CARD_H / 2 - 10;
		Draw.round(g, left - 4, top - 4, stripW + 8, CARD_H + 8, Theme.PANEL);
		g.enableScissor(left, top - 2, left + stripW, top + CARD_H + 2);
		float done = state == State.RESULT ? Math.min(1f, (now - endAt) / 400f) : 0f;
		List<int[]> placed = new ArrayList<>();
		for (int i = 0; i < strip.size(); i++) {
			int x = (int) Math.round(cx + i * (CARD_W + CARD_GAP) - offset);
			if (x + CARD_W < left || x > left + stripW) continue;
			Card c = strip.get(i);
			boolean win = i == Reel.WINNER && state == State.RESULT;
			int colour = colour(c.rarity);
			float fade = win ? 1f : 1f - done * 0.6f; // the losers dim as the result shows
			Draw.rect(g, x, top, CARD_W, CARD_H, 0xFF1E1E26);
			if (win) Draw.rect(g, x, top, CARD_W, CARD_H, Draw.alpha(colour, 0.25f * done));
			g.fillGradient(x, top + CARD_H / 2, x + CARD_W, top + CARD_H, 0x00000000, Draw.alpha(colour, (win ? 0.3f + 0.3f * done : 0.3f) * fade));
			Draw.rect(g, x, top + CARD_H - 3, CARD_W, 3, Draw.alpha(colour, fade));
			if (win) Draw.outline(g, x - 1, top - 1, CARD_W + 2, CARD_H + 2, colour);
			placed.add(new int[]{i, x});
		}
		for (int[] p : placed) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(p[1] + CARD_W / 2f - 16, top + CARD_H / 2f - 19);
			pose.scale(2f, 2f);
			g.item(strip.get(p[0]).stack, 0, 0);
			pose.popMatrix();
		}
		if (done > 0) {
			g.nextStratum(); // over the items too
			for (int[] p : placed) if (p[0] != Reel.WINNER) Draw.rect(g, p[1], top, CARD_W, CARD_H, Draw.alpha(0x101014, done * 0.55f));
		}
		g.disableScissor();
		g.nextStratum();
		// The marker: a gold line down the middle with a notch at each end.
		Draw.rect(g, cx - 1, top - 4, 2, CARD_H + 8, Theme.WARN);
		Draw.rect(g, cx - 3, top - 4, 6, 2, Theme.WARN);
		Draw.rect(g, cx - 3, top + CARD_H + 2, 6, 2, Theme.WARN);

		int ty = top + CARD_H + 14;
		if (state == State.RESULT) {
			Draw.textCentered(g, font, Text.plain(winner.stack.getHoverName()), cx, ty, colour(winner.rarity));
			if (winner.value > 0) Draw.textCentered(g, font, "Worth " + Text.compact(winner.value), cx, ty + 12, Theme.WARN);
			Draw.textCentered(g, font, "Click to see the chest", cx, ty + 28, Theme.FAINT);
		} else {
			Draw.textCentered(g, font, "Click or Esc to skip", cx, ty, Theme.FAINT);
		}
	}

	private static String rarity(ItemStack st, String id) {
		String r = Items.rarity(st);
		if (!r.isEmpty()) return r;
		Catalog.Item def = id == null ? null : Catalog.item(id);
		return def == null || def.tier() == null ? "" : def.tier();
	}

	/** A rarity's colour, as the game draws its names. */
	private static int colour(String rarity) {
		return switch (rarity == null ? "" : rarity.replace('_', ' ')) {
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY SPECIAL" -> 0xFFFF5555;
			default -> 0xFFFFFFFF;
		};
	}
}
