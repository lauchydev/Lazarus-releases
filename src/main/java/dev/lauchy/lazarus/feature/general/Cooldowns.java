package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Events;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ability cooldowns for whatever you use. An ability fires when "-N Mana (Ability)" shows
 * in the action bar or the server says "You used Ability!"; its length is read from the
 * held item's own "Cooldown: Ns" lore line, then the dragsim.co catalog, so new items
 * need no code. The tracked items below can each be switched off, anything else with a
 * cooldown falls under "Other", and the ignored list (guns and bows whose cooldown is
 * noise) is never timed. A press during a cooldown doesn't restart the timer: the mod
 * waits for the server's "on cooldown for Ns" refusal and corrects the timer from it.
 */
public final class Cooldowns extends Feature {
	private static final Pattern MANA = Pattern.compile("-[\\d,.]+ Mana \\((.+?)\\)");
	private static final Pattern WAIT = Pattern.compile("on cooldown for ([\\d.]+)s");
	/** Timers fall off the HUD this long after they run out. */
	private static final long FORGET_TICKS = 20 * 30;

	/** A known item: its name fragment and the cooldown to assume if its lore has none. */
	private record Known(String match, String label, double fallbackSeconds, BoolSetting toggle) {
	}

	private final List<Known> known = new ArrayList<>();
	private final BoolSetting others = add(new BoolSetting("others", "Other items", "Any other item with a Cooldown line in its lore.", true));
	private final BoolSetting riftwalker = add(new BoolSetting("riftwalker", "Riftwalker", "The full set's empowered hit, every 8 seconds.", true));
	private final TextSetting ignored = add(new TextSetting("ignored", "Ignored items",
			"Comma-separated items never to time, whatever their lore says.", "Golden Cannon, Golden Gun, Terminator", 200));
	private final BoolSetting debug = add(new BoolSetting("debug", "Debug", "Say in chat why each timer did or didn't start, to diagnose an item.", false));

	/** Timers by label, in the order they first appeared. */
	private final Map<String, CooldownTimer> timers = new LinkedHashMap<>();
	private long ticks;
	private String lastActionBarAbility = "";
	/** The timer the last use went to: the one a refusal corrects. */
	private String lastLabel;

	public Cooldowns() {
		super("cooldowns", "Ability Cooldowns", "Ready timers for item abilities and Riftwalker armor.", Category.GENERAL, true);
		known("giant's sword", "Giant's Sword", 5);
		known("tuba", "Tuba", 20);
		known("katana", "Katana", 4);
		known("blinding sword", "Blinding Sword", 12); // Final Spark, per the dragsim.co item catalog
	}

	private void known(String match, String label, double fallback) {
		String key = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		known.add(new Known(match, label, fallback, add(new BoolSetting(key, label, "Track " + label + ".", true))));
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ticks++;
			for (var e : timers.entrySet()) {
				if (e.getValue().tick(ticks) == CooldownTimer.Outcome.STARTED && debug.on()) {
					Message.lazarus().muted("[cooldowns] ").highlight(e.getKey()).muted(" wasn't refused, so its timer restarted from the press.").send();
				}
			}
			timers.values().removeIf(t -> t.idle(ticks) > FORGET_TICKS);
		});
		Events.ACTION_BAR.add(text -> {
			if (!enabled()) return;
			Matcher m = MANA.matcher(text);
			String used = m.find() ? m.group(1) : "";
			// The bar repeats the same text across several updates; a use is it appearing.
			if (!used.isEmpty() && !used.equals(lastActionBarAbility)) used(used, "the action bar");
			lastActionBarAbility = used;
		});
		Chat.listen(l -> {
			if (!enabled()) return;
			String line = l.text();
			// Player chat always has a colon, so nobody can start or stop a timer by typing.
			if (Chat.fromPlayer(line)) return;
			Matcher w = WAIT.matcher(line);
			if (w.find()) refused(Double.parseDouble(w.group(1)));
			else if (line.startsWith("You used ")) used(line.substring(9).replace("!", "").trim(), "chat");
			else if (line.startsWith("HOWL!")) used("Howl", "chat");
			else if (line.contains("Soulcry! +")) used("Soulcry", "chat");
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (enabled() && riftwalker.on() && level.isClientSide() && entity instanceof LivingEntity
					&& !(entity instanceof ArmorStand) && player.getMainHandItem().is(ItemTags.SWORDS) && fullRiftwalker(player)) {
				// Ordinary hits while the bonus charges don't restart it.
				CooldownTimer t = timers.computeIfAbsent("Riftwalker", k -> new CooldownTimer());
				if (!t.running(ticks)) t.use(ticks, 160);
			}
			return InteractionResult.PASS;
		});
		Hud.register(new HudElement(this, "cooldowns", "Ability Cooldowns", 0.52f, 0.62f) {
			@Override
			protected boolean visible() {
				return !rows(false).isEmpty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = rows(sample);
				return rows.isEmpty() ? null : Draw.panel(g, font, rows);
			}
		});
	}

	/** An ability fired, named as {@code source} reported it; the timer is the held item's. */
	private void used(String ability, String source) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ItemStack hand = mc.player.getMainHandItem();
		String name = Items.name(hand);
		if (name.isEmpty()) {
			debug(ability + " fired (" + source + ") with nothing in hand, so there's no timer to start.");
			return;
		}
		if (CooldownLore.ignored(name, ignored.get())) {
			debug(name + " is on the ignored list.");
			return;
		}
		Known k = knownFor(name);
		if (k != null && !k.toggle.on() || k == null && !others.on()) {
			debug(name + " is switched off in the Cooldowns settings.");
			return;
		}
		boolean sneaking = mc.player.isShiftKeyDown();
		String from = "its lore";
		Double seconds = CooldownLore.cooldown(Items.lore(hand), ability, sneaking);
		if (seconds == null) {
			seconds = catalogCooldown(Catalog.item(Items.id(hand)), ability, sneaking);
			from = "the catalogue";
		}
		if (seconds == null && k != null) {
			seconds = k.fallbackSeconds;
			from = "the built-in fallback";
		}
		if (seconds == null || seconds <= 0) {
			debug(ability + " fired (" + source + ") but neither " + name + "'s lore nor the catalogue gives it a cooldown.");
			return;
		}
		String label = k != null ? k.label : name;
		CooldownTimer t = timers.computeIfAbsent(label, l -> new CooldownTimer());
		CooldownTimer.Outcome outcome = t.use(ticks, Math.round(seconds * 20));
		lastLabel = label;
		switch (outcome) {
			case STARTED -> debug(label + ": " + ability + " fired (" + source + "), " + seconds + "s from " + from + ".");
			case WAITING -> debug(label + ": " + ability + " fired (" + source + ") while on cooldown. Waiting for the server to refuse it.");
			case REPEAT -> debug(label + ": " + ability + " reported again by " + source + ", same use.");
		}
	}

	/** "This ability is on cooldown for 3.7s" corrects the timer the last press went to. */
	private void refused(double remaining) {
		if (lastLabel == null) return;
		CooldownTimer t = timers.get(lastLabel);
		if (t == null) return;
		t.refused(ticks, Math.round(remaining * 20));
		debug(lastLabel + ": the server refused the press, " + remaining + "s left.");
	}

	private void debug(String what) {
		if (debug.on()) Message.lazarus().muted("[cooldowns] " + what).send();
	}

	private Known knownFor(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		for (Known k : known) if (n.contains(k.match)) return k;
		return null;
	}

	/** The catalog's cooldown for the ability that fired, chosen the same way as the lore's; null when it can't say. */
	private static Double catalogCooldown(Catalog.Item item, String ability, boolean sneaking) {
		if (item == null || item.abilities().isEmpty()) return null;
		for (Catalog.Ability a : item.abilities()) if (a.name().equalsIgnoreCase(ability)) return positive(a.cooldown());
		for (Catalog.Ability a : item.abilities()) if (upper(a.trigger()).contains("SNEAK") == sneaking) return positive(a.cooldown());
		return null;
	}

	private static Double positive(double seconds) {
		return seconds > 0 ? seconds : null;
	}

	private static String upper(String s) {
		return s.toUpperCase(Locale.ROOT);
	}

	private boolean fullRiftwalker(Player p) {
		ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
		boolean helmet = piece(head, "helmet") || Items.name(head).toLowerCase(Locale.ROOT).contains("warden helmet")
				|| String.join(" ", Items.lore(head)).toLowerCase(Locale.ROOT).contains("counts as the helmet of any armor set");
		return helmet && piece(p.getItemBySlot(EquipmentSlot.CHEST), "chestplate")
				&& piece(p.getItemBySlot(EquipmentSlot.LEGS), "leggings")
				&& piece(p.getItemBySlot(EquipmentSlot.FEET), "boots");
	}

	private static boolean piece(ItemStack s, String part) {
		return Items.name(s).toLowerCase(Locale.ROOT).contains("riftwalker " + part);
	}

	private List<String> rows(boolean sample) {
		List<String> out = new ArrayList<>();
		if (sample) {
			out.add("§fGiant's Sword §e2.4s");
			out.add("§fBlinding Sword §e9.1s");
			return out;
		}
		for (var e : timers.entrySet()) {
			long left = e.getValue().left(ticks);
			if (left > 0) out.add("§f" + e.getKey() + " §e" + String.format(Locale.ROOT, "%.1fs", left / 20.0)); // ready ones drop off
		}
		return out;
	}
}
