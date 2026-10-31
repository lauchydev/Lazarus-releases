package dev.lauchy.lazarus.feature.cosmetic;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Live;
import dev.lauchy.lazarus.core.LiveConnection;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.core.Text;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The brand's name badge: the 8x8 Lazarus mark before the name of everyone online with the mod, in the
 * tab list and on the name tag over their head. Purple for Lazarus users, with a gold plus for Lazarus+,
 * and grey for players who have the mod but aren't whitelisted. It's how players find the mod and the
 * tier, with no text. The glyphs are a bitmap font (assets/lazarus/font/badge.json); who's online with
 * the mod comes from the live connection ({@link LiveConnection}). A name is matched by its account, or,
 * for the tab cells a server draws itself with a player's name in them, by that name (a Lazarus user's
 * name wins over someone grey claiming it, see {@link Live#named}). Servers that float
 * their own name tags over players (Dragsim's are text displays and named armor stands) get the badge
 * on those tags.
 */
public final class TabBadges extends Feature {
	public static TabBadges INSTANCE;
	private static final Style BADGE = Style.EMPTY.withColor(0xFFFFFF)
			.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(Lazarus.ID, "badge")));
	public static final Component LAZARUS = Component.literal("\uE000").withStyle(BADGE);
	public static final Component PLUS = Component.literal("\uE001").withStyle(BADGE);
	/** The mark in grey, for a player who has the mod but isn't whitelisted. */
	public static final Component GREY = Component.literal("\uE002").withStyle(BADGE);
	private static final Pattern WORD = Pattern.compile("\\w{3,16}");

	/** Whether this account is a Lazarus user; if not, Lazarus+ never runs here, and your own badge is grey until the server says otherwise. */
	private final boolean lazarusUser;
	/** Each floating tag's text as last looked at, so an unchanged tag costs one lookup a frame. Client thread only. */
	private final Map<Integer, Component> seen = new HashMap<>();
	private int ticks;

	public TabBadges() {
		this(true);
	}

	/** The badges for a player who isn't whitelisted pass false: the badges are all of Lazarus that runs for them. */
	public TabBadges(boolean lazarusUser) {
		super("tabBadges", "Name Badges", "The Lazarus mark before the names of players with Lazarus, in the tab list and over their heads, with a gold plus for Lazarus+.",
				Category.GENERAL, true);
		this.lazarusUser = lazarusUser;
		INSTANCE = this;
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// Once a second, look at every tag again: who's online or their badge may have changed.
			if (++ticks % 20 == 0) seen.clear();
			floatingTags(mc);
		});
		// Also per frame: the server resends a tag's text when it changes, which would flash it bare.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "badge_scan"), (g, d) -> floatingTags(Minecraft.getInstance()));
	}

	/** The tab list name with its badge in front, or as it was for players without Lazarus. */
	public Component badged(PlayerInfo info, Component name) {
		return badged(info.getProfile().id(), name);
	}

	/** The name tag over a player's head, with the same badge in front. */
	public Component nameTag(Player player, Component tag) {
		return badged(player.getUUID(), tag);
	}

	private Component badged(UUID id, Component name) {
		if (!enabled() || name == null) return name;
		Minecraft mc = Minecraft.getInstance();
		UUID me = mc.getUser().getProfileId();
		UUID who = holder(id, name, me, mc.getUser().getName());
		return who == null ? name : withBadge(who, me, name);
	}

	private Component withBadge(UUID who, UUID me, Component name) {
		Live.Badge badge = who.equals(me) ? own(me) : LiveConnection.LIVE.badge(who);
		Component mark = switch (badge) {
			case GOLD -> PLUS;
			case GREY -> GREY;
			default -> LAZARUS;
		};
		return Component.empty().append(mark).append(" ").append(name);
	}

	/** Your own badge: always shown, as the server last said, or before it has, from what this game knows. */
	private Live.Badge own(UUID me) {
		Live.Badge said = LiveConnection.LIVE.badge(me);
		if (said != Live.Badge.NONE) return said;
		if (!lazarusUser) return Live.Badge.GREY;
		return Plus.active() ? Live.Badge.GOLD : Live.Badge.PURPLE;
	}

	private void floatingTags(Minecraft mc) {
		if (enabled() && mc.level != null) floatingTags(mc.level.entitiesForRendering(), mc.level.players());
	}

	/**
	 * Badges the name tags a server floats over players with the mod itself: a text display or a named
	 * armor stand that names one standing right under it (or carrying it). Tags that already have the
	 * badge are left alone, so a tag is only rewritten when the server changes it.
	 */
	public void floatingTags(Iterable<Entity> entities, List<? extends Player> players) {
		UUID me = Minecraft.getInstance().getUser().getProfileId();
		Map<String, Player> here = new HashMap<>();
		for (Player p : players) {
			if (p.getUUID().equals(me) || LiveConnection.LIVE.badge(p.getUUID()) != Live.Badge.NONE) {
				here.put(p.getGameProfile().name().toLowerCase(Locale.ROOT), p);
			}
		}
		if (here.isEmpty()) return;
		if (seen.size() > 4096) seen.clear();
		for (Entity e : entities) {
			if (e instanceof Player) continue;
			Component text = e instanceof Display.TextDisplay td ? td.getEntityData().get(Display.TextDisplay.DATA_TEXT_ID)
					: e.isCustomNameVisible() ? e.getCustomName() : null;
			if (text == null || seen.get(e.getId()) == text) continue;
			seen.put(e.getId(), text);
			String plain = Text.plain(text);
			if (plain.startsWith(LAZARUS.getString()) || plain.startsWith(PLUS.getString()) || plain.startsWith(GREY.getString())) continue;
			Player wearer = wearer(e, plain, here);
			if (wearer == null) continue;
			Component badged = withBadge(wearer.getUUID(), me, text);
			seen.put(e.getId(), badged);
			if (e instanceof Display.TextDisplay td) td.getEntityData().set(Display.TextDisplay.DATA_TEXT_ID, badged);
			else e.setCustomName(badged);
		}
	}

	/** The player with the mod a floating tag belongs to: named in it, and carrying it or standing just under it. */
	private static Player wearer(Entity tag, String plain, Map<String, Player> here) {
		Matcher m = WORD.matcher(plain);
		while (m.find()) {
			Player p = here.get(m.group().toLowerCase(Locale.ROOT));
			if (p == null) continue;
			if (tag.getRootVehicle() == p) return p;
			double dx = tag.getX() - p.getX(), dz = tag.getZ() - p.getZ(), dy = tag.getY() - p.getY();
			if (dx * dx + dz * dz < 1.5 * 1.5 && dy > -0.5 && dy < 4) return p;
		}
		return null;
	}

	/** Whose name this is: a real player by account, or the first Lazarus user online with the mod named in the text. */
	private static UUID holder(UUID id, Component name, UUID me, String myName) {
		if (id != null && (id.equals(me) || LiveConnection.LIVE.badge(id) != Live.Badge.NONE)) return id;
		Matcher m = WORD.matcher(Text.plain(name));
		while (m.find()) {
			String word = m.group();
			if (me != null && word.equalsIgnoreCase(myName)) return me;
			UUID named = LiveConnection.LIVE.named(word);
			if (named != null) return named;
		}
		return null;
	}
}
