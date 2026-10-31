package dev.lauchy.lazarus.feature.end;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.LootFeed;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Period;
import dev.lauchy.lazarus.core.Tallies;
import dev.lauchy.lazarus.core.Tally;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.feature.slayer.SlayerTracker;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zealot kills and drops, this session and all time, with profit valued at what an NPC pays.
 *
 * Drops are announced to you in chat, so they are exact; one that a staff roll or the timing
 * says came from a slayer boss is left to the slayer tracker. Kills are not announced at all,
 * and several people farm one nest, so a death is only yours when something you did
 * explains it. Mainly that is the server saying you hurt that zealot in the last few
 * seconds, whatever you hit it with. Damage the server doesn't put your name on is caught
 * by the rest: a melee hit, a right-click ability aimed along a line it died near, a
 * projectile of yours passing where it died, or a Giant's Sword landing. Each strike can explain a few deaths for a
 * second and a half, and an ability the server refused explains none.
 */
public final class ZealotTracker extends Feature {
	public static ZealotTracker INSTANCE;
	/** Drops that come from zealots and bruisers; other End loot is the boss drops tracker's. */
	static final List<String> DROPS = List.of("Summoning Eye", "Golden Eye", "Warden Catalyst", "Rift Pass",
			"Enderman", "Spicy Wart", "Warped Stone", "Wolf", "InfiniEye™", "Null Ovoid");
	private static final String SWORD_LANDS = "You hear something falling from the sky";
	private static final String REFUSED = "This ability is on cooldown";
	/** How long after you last hurt a zealot its death still counts as yours. */
	private static final long HURT_MS = 5_000;
	private static final long STRIKE_MS = 1_500, REFUSE_MS = 400, IDLE_MS = 180_000;
	/** A drop with no roll to name its mob waits this long, in case the SLAIN line says a slayer boss dropped it. */
	private static final long HOLD_MS = SlayerTracker.BEFORE_MS;
	private static final String SPECIAL = "Special Zealot";
	private static final double AIM_RANGE = 30;

	/** Something you did: deaths within {@code radius} of the segment a-b, soon after, are yours. */
	private static final class Strike {
		final long at;
		final Vec3 a, b;
		final double radius;
		int credits;

		Strike(long at, Vec3 a, Vec3 b, double radius, int credits) {
			this.at = at;
			this.a = a;
			this.b = b;
			this.radius = radius;
			this.credits = credits;
		}

		boolean explains(Vec3 p, long now) {
			return credits > 0 && now - at <= STRIKE_MS && distance(p, a, b) <= radius;
		}
	}

	private final ChoiceSetting<Period> period = add(new ChoiceSetting<>("period", "Period", "This session, or every zealot so far.", Period.SESSION));
	// A new key, so the rates start off for everyone rather than keeping the old saved "on".
	private final BoolSetting perHour = add(new BoolSetting("perHourRates", "Per-hour rates", "Show kills, drops and profit per hour beside this session's counts.", false));
	private final BoolSetting debug = add(new BoolSetting("debug", "Debug", "Print in chat how each zealot kill is (or isn't) credited, to diagnose tracking.", false));

	private final List<Strike> strikes = new ArrayList<>();
	private Strike lastCast;
	private final Map<Integer, Long> dead = new HashMap<>();
	/** Entities the server says you hurt, by id, and when. */
	private final Map<Integer, Long> hurtByMe = new HashMap<>();
	/** Your arrows in flight, each its own strike along the path it covered last tick. */
	private final Map<Integer, Strike> arrows = new HashMap<>();
	private final Map<Integer, Vec3> arrowWas = new HashMap<>();
	/** Arrows that already explained a death; one arrow is one kill. */
	private final Set<Integer> spentArrows = new HashSet<>();
	private final Tallies tallies = new Tallies(Config.dir().resolve("zealots.json"));
	/** Drops with no mob named, waiting to see whether a slayer kill claims them. */
	private final List<LootFeed.Loot> held = new ArrayList<>();
	private long elapsed, lastTick, lastActivity;

	public ZealotTracker() {
		super("zealotTracker", "Zealot Tracker", "Zealot kills, eyes and other drops, with NPC-value profit.", Category.END, true);
		INSTANCE = this;
	}

	/** A damage event from the server; called on the game thread. */
	public void onDamage(int entityId, int causeId, int directId) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || mc.player == null) return;
		int me = mc.player.getId();
		if (causeId != me && directId != me || mc.level == null) return;
		Entity e = mc.level.getEntity(entityId);
		// Judged while it's alive and tagged: by the time it's gone its tag may be too.
		if (e != null && tracked(e)) {
			hurtByMe.put(entityId, now());
			if (debug.on()) Message.lazarus().muted("[zealot] The server says you hurt a zealot (id " + entityId + ").").send();
		}
	}

	/**
	 * A zealot you hurt that leaves the world near you was killed. Servers often remove a
	 * dead mob at once, with no dying ticks for the tick check to see.
	 */
	private void unloaded(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || mc.player == null || dead.containsKey(e.getId()) || !tracked(e)) return;
		long now = now();
		Long hurt = hurtByMe.remove(e.getId());
		boolean mineHurt = hurt != null && now - hurt <= HURT_MS && e.distanceToSqr(mc.player) <= 32 * 32;
		// Anything you did to it counts, not only damage the server put your name on: a melee
		// swing, a right-click ability aimed where it died, or an arrow of yours that passed
		// through. The tick path already does this; the unload path did not, so on servers that
		// delete a dead mob instantly (no dying ticks to see) those kills were silently missed.
		boolean mine = mineHurt || credit(strikes, e.position(), now) || credit(arrows.values(), e.position(), now);
		if (debug.on()) {
			Message m = Message.lazarus().muted("[zealot] Unloaded, id " + e.getId() + ", hurt by server: " + (hurt != null ? "yes" : "no") + " → ");
			(mine ? m.success("counted") : m.error("dropped")).send();
		}
		if (!mine) return;
		dead.put(e.getId(), now);
		killed(isSpecial(e));
	}

	private static boolean isSpecial(Entity e) {
		String n = Entities.name(e);
		return (n.isEmpty() ? tagAbove(e) : n).contains("Special Zealot");
	}

	@Override
	public void init() {
		tallies.load();
		Config.saveOn(tallies::save);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (enabled() && level.isClientSide() && tracked(entity)) {
				strikes.add(new Strike(now(), entity.position(), entity.position(), 4, 3));
				if (debug.on()) Message.lazarus().muted("[zealot] A melee swing at a zealot was registered.").send();
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (enabled() && level.isClientSide() && Area.inEnd() && hasRightClickAbility(player)) {
				Vec3 eye = player.getEyePosition();
				lastCast = new Strike(now(), eye, player.pick(AIM_RANGE, 1f, false).getLocation(), 5, 6);
				strikes.add(lastCast);
			}
			return InteractionResult.PASS;
		});
		Chat.listen(l -> {
			if (!enabled()) return;
			String line = l.text();
			if (line.startsWith(REFUSED)) {
				if (lastCast != null && now() - lastCast.at < REFUSE_MS) lastCast.credits = 0;
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (line.startsWith(SWORD_LANDS) && mc.player != null) {
				Vec3 at = mc.player.pick(AIM_RANGE, 1f, false).getLocation();
				strikes.add(new Strike(now(), at, at, 8, 8));
				return;
			}
		});
		LootFeed.listen(l -> {
			if (enabled() && Area.inEnd()) loot(l);
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> unloaded(entity));
		Hud.register(new HudElement(this, "zealotTracker", "Zealot Tracker", 0.006f, 0.30f) {
			@Override
			protected boolean visible() {
				return Area.inEnd() && !tallies.shown(period.get()).empty();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				return Draw.panel(g, font, sample ? sampleRows() : rows());
			}
		});
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("zealots")
				.then(ClientCommands.literal("reset").executes(c -> {
					reset();
					Message.lazarus().success("Zealot session reset. All-time totals are kept.").send();
					return 1;
				}))));
	}

	private void tick(Minecraft mc) {
		long now = now();
		if (lastTick != 0 && Area.inEnd() && now - lastActivity < IDLE_MS) elapsed += Math.min(now - lastTick, 1_000);
		lastTick = now;
		strikes.removeIf(s -> now - s.at > STRIKE_MS || s.credits <= 0);
		dead.values().removeIf(t -> now - t > 10_000);
		hurtByMe.values().removeIf(t -> now - t > HURT_MS);
		arrows.values().removeIf(s -> now - s.at > STRIKE_MS);
		arrowWas.keySet().retainAll(arrows.keySet());
		if (arrows.isEmpty()) spentArrows.clear();
		flushHeld(now);
		if (!enabled() || mc.level == null || mc.player == null) return;
		if (Area.inEnd()) trackArrows(mc, now);
		if (strikes.isEmpty() && arrows.isEmpty() && hurtByMe.isEmpty()) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EnderMan man) || !man.isDeadOrDying() || dead.containsKey(e.getId()) || !tracked(e)) continue;
			dead.put(e.getId(), now);
			boolean mine = hurtByMe.remove(e.getId()) != null || credit(strikes, e.position(), now) || credit(arrows.values(), e.position(), now);
			if (debug.on()) {
				Message m = Message.lazarus().muted("[zealot] Dying zealot seen → ");
				(mine ? m.success("counted") : m.error("dropped")).send();
			}
			if (mine) killed(isSpecial(e));
		}
	}

	private static boolean credit(Iterable<Strike> from, Vec3 p, long now) {
		for (Strike s : from) {
			if (!s.explains(p, now)) continue;
			s.credits--;
			return true;
		}
		return false;
	}

	/**
	 * Arrows are fast, a few blocks a tick, and vanish on the tick they hit, so each one is
	 * remembered as the stretch it covered last tick and kept for a moment after it's gone.
	 */
	private void trackArrows(Minecraft mc, long now) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Projectile shot) || shot.getOwner() != mc.player) continue;
			int id = e.getId();
			Vec3 at = e.position(), was = arrowWas.getOrDefault(id, at);
			arrowWas.put(id, at);
			Strike old = arrows.get(id);
			if (spentArrows.contains(id) || old != null && old.credits <= 0) {
				spentArrows.add(id);
				continue;
			}
			arrows.put(id, new Strike(now, was, at, 2.5, 1));
		}
	}

	private void killed(boolean special) {
		lastActivity = now();
		tallies.both(t -> {
			t.add("kills", 1);
			if (special) {
				t.add("specials", 1);
				t.hit(SPECIAL);
			} else if (t.since().containsKey(SPECIAL)) {
				t.miss(SPECIAL); // the count only starts once a Special has been seen
			}
		});
	}

	/**
	 * A drop from the loot feed. A staff roll naming a zealot settles it; one naming anything
	 * else (a slayer boss, the Protector) rules it out. Without a roll the drop waits a moment,
	 * because a slayer boss's drops land just before its SLAIN line.
	 */
	private void loot(LootFeed.Loot l) {
		if (!zealotDrop(l.drop().item())) return;
		if (l.mob() != null) {
			if (l.mob().contains("Zealot") || l.mob().contains("Bruiser") || l.mob().contains("Enderman")) credit(l);
			return;
		}
		held.add(l);
	}

	/** Held drops whose wait is over: a slayer kill within the wait claims them, the rest are zealots'. */
	private void flushHeld(long now) {
		if (held.isEmpty()) return;
		for (var it = held.iterator(); it.hasNext(); ) {
			LootFeed.Loot l = it.next();
			if (now - l.at() < HOLD_MS) continue;
			it.remove();
			if (!SlayerTracker.claims(l.at())) credit(l);
			else if (debug.on()) Message.lazarus().muted("[zealot] " + l.drop().item() + " left to the slayer boss killed at the same moment.").send();
		}
	}

	private void credit(LootFeed.Loot l) {
		String name = zealotName(l.drop().item());
		tallies.both(t -> t.drop(name, l.drop().count()));
		lastActivity = now();
	}

	/** Whether an item is something zealots drop, so another tracker leaves it to this one. */
	static boolean zealotDrop(String item) {
		return zealotName(item) != null;
	}

	/** The drop's name as the list spells it, or null for loot that isn't a zealot's. */
	private static String zealotName(String item) {
		for (String name : DROPS) if (name.equalsIgnoreCase(item)) return name;
		return null;
	}

	private List<String> rows() {
		Period p = period.get();
		Tally t = tallies.shown(p);
		boolean session = p == Period.SESSION;
		List<String> out = new ArrayList<>();
		out.add("§5§lZealots §7" + p.label());
		out.add("§7Kills §f" + String.format(Locale.ROOT, "%,d", t.count("kills")) + rate(t.count("kills"), session));
		Integer sinceSpecial = t.since().get(SPECIAL);
		if (sinceSpecial != null) out.add("§7Since Special §f" + sinceSpecial);
		for (var e : LootRows.byValue(t.drops(), PriceDatabase::worth)) {
			out.add(colour(e.getKey()) + e.getKey() + " §f" + e.getValue() + rate(e.getValue(), session) + "\t" + LootRows.worth(e.getKey(), e.getValue(), PriceDatabase::worth));
		}
		out.add("§7Profit\t§a" + LootRows.value(t.drops(), PriceDatabase::worth));
		if (session && perHour.on() && elapsed >= 60_000) out.add("§7Profit/h\t§a" + Text.compact(LootRows.total(t.drops(), PriceDatabase::worth) * 3_600_000.0 / elapsed));
		if (session) out.add("§7Time §f" + Text.clock(elapsed / 1000));
		return out;
	}

	private List<String> sampleRows() {
		return List.of("§5§lZealots §7Session", "§7Kills §f1,482", "§7Since Special §f318", "§5Summoning Eye §f7\t§a1.2M",
				"§6Golden Eye §f1\t§a200k", "§7Profit\t§a1.4M", "§7Time §f1:15:20");
	}

	/** " · 5.6/h" for this session's counts once a minute has passed; all-time counts have no rate. */
	private String rate(int n, boolean session) {
		if (!session || !perHour.on() || elapsed < 60_000) return "";
		double h = n * 3_600_000.0 / elapsed;
		return " §8· " + (h < 10 ? String.format(Locale.ROOT, "%.1f", h) : Text.compact(h)) + "/h";
	}

	/** A drop's colour: its rarity when the catalogue knows it, else the colour this tracker always gave it. */
	private static String colour(String drop) {
		String rarity = LootRows.colour(drop);
		if (!rarity.equals("§7")) return rarity;
		return switch (drop) {
			case "Summoning Eye", "Warden Catalyst" -> "§5";
			case "Golden Eye", "Rift Pass", "Enderman", "Wolf" -> "§6";
			default -> "§9";
		};
	}

	private void reset() {
		tallies.resetSession();
		elapsed = 0;
		held.clear();
		strikes.clear();
		arrows.clear();
		arrowWas.clear();
		hurtByMe.clear();
		spentArrows.clear();
	}

	/**
	 * Zealots and bruisers: any enderman in the End, named or not, since the nest is all
	 * zealots, and elsewhere one named so. The name is on the enderman itself or on a tag
	 * floating just above it. Seraph, the Voidgloom boss, never counts.
	 */
	private static boolean tracked(Entity e) {
		if (!(e instanceof EnderMan)) return false;
		String n = Entities.name(e);
		if (n.isEmpty()) n = tagAbove(e);
		if (n.contains("Seraph") || n.contains("Voidgloom")) return false;
		// Whatever its tag says (level, health), an enderman in the End is a zealot or bruiser.
		return Area.inEnd() || n.contains("Zealot") || n.contains("Bruiser") || n.equals("Dinnerbone");
	}

	/** The name on a tag (text display or named stand) floating over the entity, or "". */
	private static String tagAbove(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return "";
		for (Entity t : mc.level.getEntities(e, e.getBoundingBox().inflate(0.6, 1.5, 0.6))) {
			if (t instanceof LivingEntity && !(t instanceof net.minecraft.world.entity.decoration.ArmorStand)) continue;
			String n = Entities.name(t);
			if (!n.isEmpty()) return n;
		}
		return "";
	}

	private static boolean hasRightClickAbility(Player player) {
		for (String line : Items.lore(player.getMainHandItem())) {
			if (line.contains("RIGHT CLICK")) return true;
		}
		return false;
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	/** Distance from p to the segment a-b. */
	static double distance(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len == 0 ? 0 : Math.clamp(p.subtract(a).dot(ab) / len, 0, 1);
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
