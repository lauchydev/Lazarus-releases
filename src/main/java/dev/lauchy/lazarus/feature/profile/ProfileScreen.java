package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.feature.staff.Ladder;
import dev.lauchy.lazarus.feature.staff.PunishCard;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static dev.lauchy.lazarus.feature.profile.ProfileItems.Container;
import static dev.lauchy.lazarus.feature.profile.ProfileItems.PAGE;
import static dev.lauchy.lazarus.feature.profile.ProfileItems.label;
import static dev.lauchy.lazarus.feature.profile.ProfileItems.obj;
import static dev.lauchy.lazarus.feature.profile.ProfileItems.pretty;
import static dev.lauchy.lazarus.feature.profile.ProfileItems.str;

/**
 * One player's profile. A header with their head, name, rank and profile, then icon tabs:
 * an Overview of skills, slayers and dungeons as progress bars, and one tab per item
 * container drawn as slot grids with full tooltips. The inventory is laid out like the
 * real one, armour beside it and the hotbar below. The Overview has the player standing at
 * an angle in their armour, holding their first hotbar item, turning to follow the mouse.
 * <p>
 * Staff tools live in a drawer that slides out from the right, grouped by what they do.
 * Ban, mute and kick open a card with one-click duration and reason chips, a preview of
 * the exact command, and (for bans) a hold-to-confirm button so a stray click can't ban.
 */
final class ProfileScreen extends Screen {
	private static final int COLS = 9, CELL = 18, HEADER_H = 32, TABS_H = 22, DRAWER_W = 112, MODEL_W = 76, SIDE_W = 120, SIDE_GAP = 6;
	/** Tabs whose grid is drawn smaller; see {@link #container}. */
	private static final Set<String> SMALL_GRIDS = Set.of("inventory", "ender_chest", "accessory_bag", "pets");
	private static final Map<String, String> SLAYERS = Map.of("zombie", "Revenant", "enderman", "Voidgloom", "spider", "Tarantula",
			"wolf", "Sven", "blaze", "Inferno", "vampire", "Riftstalker");

	/** Whether the staff drawer was left open; it stays that way for the next profile. */
	private static boolean drawerOpen = true;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	/** A row with a progress bar: a skill, slayer or catacombs level. */
	private record Bar(String name, int level, int max, float progress, double xp, double next) {
	}

	private record Tab(String id, String title, ItemStack icon) {
	}

	private enum Group {
		PUNISH("Punish"), TELEPORT("Teleport"), SERVER("Server"), CHECK("Check"), COPY("Copy");

		final String title;

		Group(String title) {
			this.title = title;
		}
	}

	/** A staff action. {@code fields} are what it asks for before running. */
	private enum Staff {
		BAN("Ban", Group.PUNISH, "ban %p %d %r", true, true),
		MUTE("Mute", Group.PUNISH, "mute %p %d %r", true, true),
		KICK("Kick", Group.PUNISH, "kick %p %r", false, true),
		TP_TO("To them", Group.TELEPORT, "tp %n", false, false),
		TP_HERE("Here", Group.TELEPORT, "tp %n %me", false, false),
		TUNNEL("Tunnel", Group.SERVER, "join %n", false, false),
		SEND_HERE("To mine", Group.SERVER, "send %p %s", false, false),
		LIMBO("Limbo", Group.SERVER, "send %p limbo", false, false),
		CAPTCHA("Captcha", Group.CHECK, "captcha %p", false, false),
		HISTORY("History", Group.CHECK, "history %p", false, false),
		ALTS("Alts", Group.CHECK, "alts %p", false, false),
		REFUND("Refund", Group.CHECK, "refund %p", false, false),
		COPY_NAME("Name", Group.COPY, "", false, false),
		COPY_UUID("UUID", Group.COPY, "", false, false);

		final String label, command;
		final Group group;
		final boolean duration, reason;

		Staff(String label, Group group, String command, boolean duration, boolean reason) {
			this.label = label;
			this.group = group;
			this.command = command;
			this.duration = duration;
			this.reason = reason;
		}

		boolean punish() {
			return group == Group.PUNISH;
		}
	}

	private final String player, uuid, rank, profileLine;
	private final boolean staffTools;
	private final List<String> durations, reasons;
	private final ItemStack head;
	private final List<String[]> stats = new ArrayList<>(); // {label, value}
	/** The networth's biggest items and the ones nothing prices, shown when the headline networth is hovered. */
	private final List<Component> worthTip = new ArrayList<>();
	/** How many of the biggest items have their own price history fetched when the profile opens. */
	private static final int TOP_FETCHES = 40;
	/** How many unpriced items the networth's tooltip names before it just counts the rest. */
	private static final int UNPRICED_SHOWN = 15;
	/** Works the networth out again; run whenever a price history arrives while the profile is open. */
	private Runnable repriceNetworth;
	private final List<Bar> skills = new ArrayList<>(), slayers = new ArrayList<>();
	private final List<String[]> dungeonFacts = new ArrayList<>();
	private Bar catacombs;
	private final List<Tab> tabs = new ArrayList<>();
	private final Map<String, Container> containers = new LinkedHashMap<>();
	private final Map<Integer, ItemStack> armour = new TreeMap<>(), equipment = new TreeMap<>();
	/** The standing figure on the Overview, made when first drawn (it needs a world). */
	private RemotePlayer model;
	private final Anim.Smooth lookX = new Anim.Smooth(6f, 0f), lookY = new Anim.Smooth(6f, 0f);

	private final List<Hit> hits = new ArrayList<>();
	/** A page chip's name under the mouse, drawn once the scaled grid is done. */
	private String pagerTip;
	private String tab = "overview";
	private int page;
	private double scroll;
	private int contentH, viewH;
	private int bodyX, bodyY, bodyW, bodyH;
	private final long openedAt = Util.getMillis();
	private final Anim.Group<Object> hover = new Anim.Group<>(14f);
	private final Anim.Group<Integer> cells = new Anim.Group<>(20f);
	private final Anim.Smooth tabX = new Anim.Smooth(16f, -1f), tabW = new Anim.Smooth(16f, 0f);
	private final Anim.Smooth shownScroll = new Anim.Smooth(18f, 0f);
	/** The staff drawer scrolls on its own when its buttons don't fit. */
	private double drawerScroll;
	private int drawerContentH, drawerX, drawerY, drawerW, drawerH;
	private final Anim.Smooth shownDrawerScroll = new Anim.Smooth(18f, 0f);
	private final Anim.Smooth drawer = new Anim.Smooth(14f, drawerOpen ? 1f : 0f);
	private final Anim.Group<Object> bars = new Anim.Group<>(6f);

	/** The action whose punishment card is open, and the card itself. */
	private Staff form;
	private final PunishCard card;

	ProfileScreen(String requested, JsonObject root, boolean staffTools, List<String> durations, List<String> reasons,
			List<Ladder.Rule> banRules, List<Ladder.Rule> muteRules) {
		super(Component.literal("Profile"));
		this.card = new PunishCard(Minecraft.getInstance().font, hover, (x, y, w, h, action) -> hits.add(new Hit(x, y, w, h, action)),
				durations, reasons, banRules, muteRules);
		this.staffTools = staffTools;
		this.durations = durations;
		this.reasons = reasons;
		JsonObject p = obj(root, "player");
		this.player = p.has("player") ? str(p, "player") : requested;
		this.uuid = p.has("uuid") ? str(p, "uuid") : "";
		this.rank = p.has("rank") && !p.get("rank").isJsonNull() ? str(p, "rank") : "";
		JsonObject profile = obj(root, "profile");
		String name = profile.has("profile_name") ? Text.plain(str(profile, "profile_name")) : "";
		String mode = profile.has("profile_mode") ? pretty(str(profile, "profile_mode")) : "";
		this.profileLine = name + (mode.isEmpty() || mode.equalsIgnoreCase("normal") ? "" : (name.isEmpty() ? "" : " · ") + mode);
		this.head = head(player);
		readStats(profile);
		readContainers(profile);
		siteNetworth(profile.has("id") ? str(profile, "id") : "");
	}

	/**
	 * Asks dragsim.co for this profile's networth breakdown, every item it holds with its
	 * upgrades, and prices each again ({@link Networth}): the clean item plus what the market pays
	 * for its upgrades. The biggest items' own histories are fetched, and the figure is worked out
	 * again as each arrives. If the site doesn't answer, the local estimate stays.
	 */
	private void siteNetworth(String profileId) {
		String path = "/api/networth/" + Http.encode(player) + "?mode=main" + (profileId.isEmpty() ? "" : "&profile=" + Http.encode(profileId));
		Minecraft mc = Minecraft.getInstance();
		Http.json(path).whenComplete((json, error) -> mc.execute(() -> {
			SiteNetworth w = error == null && json != null && json.isJsonObject() ? SiteNetworth.read(json.getAsJsonObject()) : null;
			if (w == null) return;
			if (w.hidden()) {
				stats.removeIf(r -> r[0].equals("Networth"));
				stats.addFirst(new String[]{"Networth", "§8Hidden"});
				worthTip.clear();
				return;
			}
			JsonObject site = json.getAsJsonObject();
			List<Networth.Held> held = Networth.held(site);
			PriceDatabase prices = PriceDatabase.INSTANCE;
			if (prices == null) {
				showNetworth(Networth.value(site, held, item -> -1), w);
				return;
			}
			for (Networth.Held h : held) {
				if (h.item().get("entry") instanceof JsonElement e && e.isJsonPrimitive()) {
					prices.siteBase(e.getAsString(), h.siteBase());
					prices.siteCraft(e.getAsString(), h.siteCraft());
				}
			}
			Runnable reprice = () -> showNetworth(Networth.value(site, held, item -> prices.value(item, false).total()), w);
			reprice.run();
			// The biggest items' own sale histories, read clean, outweigh the site's figure for them.
			held.stream().sorted((a, b) -> Long.compare(prices.value(b.item(), false).total() * b.quantity(), prices.value(a.item(), false).total() * a.quantity()))
					.map(h -> h.item().get("entry") instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsString() : "")
					.filter(id -> !id.isEmpty()).distinct().limit(TOP_FETCHES).forEach(id -> prices.estimate(id, true));
			prices.offHistory(repriceNetworth);
			repriceNetworth = reprice;
			prices.onHistory(reprice);
		}));
	}

	/** Shows a networth as the headline, with its biggest items and the unpriced ones on hover, and the site's own figure under them. */
	private void showNetworth(Networth n, SiteNetworth site) {
		int unpriced = n.unpriced();
		String shown = "≈ " + Text.compact(n.total()) + (unpriced > 0 ? " §8(" + unpriced + " unpriced)" : "");
		stats.removeIf(r -> r[0].equals("Networth"));
		stats.addFirst(new String[]{"Networth", shown});
		worthTip.clear();
		worthTip.add(Component.literal("§7Biggest items"));
		for (SiteNetworth.Row r : n.top(8)) {
			worthTip.add(Component.literal("§f" + r.name() + (r.quantity() > 1 ? " §8×" + r.quantity() : "") + "  §6" + Text.compact(r.value())));
		}
		if (!n.missing().isEmpty()) {
			worthTip.add(Component.empty());
			worthTip.add(Component.literal("§7Unpriced"));
			for (SiteNetworth.Row r : n.missing().subList(0, Math.min(UNPRICED_SHOWN, n.missing().size()))) {
				worthTip.add(Component.literal("§f" + r.name() + (r.quantity() > 1 ? " §8×" + r.quantity() : "")));
			}
			if (n.missing().size() > UNPRICED_SHOWN) worthTip.add(Component.literal("§8and " + (n.missing().size() - UNPRICED_SHOWN) + " more"));
			worthTip.add(Component.empty());
		}
		worthTip.add(Component.literal("§8dragsim.co says " + Text.compact(site.total())));
	}

	@Override
	public void removed() {
		super.removed();
		if (PriceDatabase.INSTANCE != null && repriceNetworth != null) PriceDatabase.INSTANCE.offHistory(repriceNetworth);
	}

	/** The player's head with their real skin when they're on the server, else a plain one. */
	private static ItemStack head(String name) {
		ItemStack s = new ItemStack(Items.PLAYER_HEAD);
		var mc = net.minecraft.client.Minecraft.getInstance();
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(name);
		if (info != null) s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(info.getProfile()));
		return s;
	}

	private void readStats(JsonObject profile) {
		Map<String, long[]> parts = new HashMap<>();
		long[] worth = ProfileItems.networth(profile, parts);
		worthTip.clear();
		worthTip.add(Component.literal("§7Biggest items"));
		ProfileItems.topWorth(parts, 8).forEach(l -> worthTip.add(Component.literal(l)));
		if (worth[0] > 0) stats.add(new String[]{"Networth", "≈ " + Text.compact(worth[0]) + (worth[1] * 5 < worth[2] * 4 ? " §8(" + worth[1] + "/" + worth[2] + " priced)" : "")});
		for (String k : new String[]{"coins", "bits", "motes", "dragon_essence", "haunted_candy_earned"}) {
			if (isNumber(profile.get(k))) stats.add(new String[]{label(k), Text.compact(profile.get(k).getAsDouble())});
		}
		if (profile.has("accessory_power") && profile.get("accessory_power").isJsonPrimitive()) {
			stats.add(new String[]{"Accessory power", pretty(str(profile, "accessory_power"))});
		}
		JsonObject skillXp = obj(profile, "skills"), slayer = obj(profile, "slayer"), dungeons = obj(profile, "dungeons");
		// Combat goes past 50 with Revenant and Voidgloom levels above 4, five each at most.
		int combatCap = 50 + Math.clamp(num(slayer, "zombie_level") - 4, 0, 5) + Math.clamp(num(slayer, "enderman_level") - 4, 0, 5);
		for (var e : skillXp.entrySet()) {
			if (e.getKey().equals("catacombs") || !isNumber(e.getValue())) continue;
			skills.add(bar(pretty(e.getKey()), e.getKey(), e.getValue().getAsDouble(), e.getKey().equals("combat") ? combatCap : 50));
		}
		Map<String, double[]> bosses = new LinkedHashMap<>(); // {level, xp}
		for (var e : slayer.entrySet()) {
			String k = e.getKey();
			if (!isNumber(e.getValue())) continue;
			if (k.endsWith("_level")) bosses.computeIfAbsent(k.substring(0, k.length() - 6), b -> new double[]{-1, -1})[0] = e.getValue().getAsDouble();
			else if (k.endsWith("_xp")) bosses.computeIfAbsent(k.substring(0, k.length() - 3), b -> new double[]{-1, -1})[1] = e.getValue().getAsDouble();
		}
		bosses.forEach((boss, v) -> {
			int lv = (int) Math.max(0, v[0]);
			slayers.add(new Bar(SLAYERS.getOrDefault(boss, pretty(boss)), lv, 9, lv / 9f, Math.max(0, v[1]), -1));
		});
		double xp = -1;
		for (String k : new String[]{"catacombs_xp", "catacombs", "experience", "xp"}) {
			if (isNumber(dungeons.get(k))) {
				xp = dungeons.get(k).getAsDouble();
				break;
			}
		}
		if (xp < 0 && isNumber(skillXp.get("catacombs"))) xp = skillXp.get("catacombs").getAsDouble();
		if (xp >= 0) catacombs = bar("Catacombs", "catacombs", xp, 50);
		if (dungeons.has("class") && dungeons.get("class").isJsonPrimitive()) dungeonFacts.add(new String[]{"Class", pretty(str(dungeons, "class"))});
		for (var e : dungeons.entrySet()) {
			String k = e.getKey();
			if (List.of("class", "catacombs_xp", "catacombs", "experience", "xp").contains(k)) continue;
			if (isNumber(e.getValue())) dungeonFacts.add(new String[]{label(k), Text.compact(e.getValue().getAsDouble())});
		}
	}

	/** A level read off the catalog's XP curve for {@code skill}, with progress to the next one. */
	private static Bar bar(String name, String skill, double xp, int cap) {
		double[] curve = Catalog.curve(skill);
		if (curve.length == 0) return new Bar(name, 0, 0, 0f, xp, -1);
		int max = Math.min(cap, curve.length), lv = 0;
		while (lv < max && xp >= curve[lv]) lv++;
		if (lv >= max) return new Bar(name, lv, max, 1f, xp, -1);
		double from = lv == 0 ? 0 : curve[lv - 1], to = curve[lv];
		return new Bar(name, lv, max, (float) Math.clamp((xp - from) / Math.max(1, to - from), 0, 1), xp, to);
	}

	private void readContainers(JsonObject profile) {
		tabs.add(new Tab("overview", "Overview", new ItemStack(Items.WRITABLE_BOOK)));
		ProfileItems.wearing(profile.get("inventory"));
		Map<Integer, ItemStack> inv = ProfileItems.slots(profile.get("inventory"));
		Map<Integer, ItemStack> main = new TreeMap<>();
		// Slots 0-35 are the hotbar and main inventory, 41-44 helmet to boots; anything else is equipment.
		inv.forEach((slot, s) -> {
			if (slot < 36) main.put(slot, s);
			else if (slot >= 41 && slot <= 44) armour.put(slot - 41, s);
			else equipment.put(slot, ItemLooks.item(s));
		});
		addTab("inventory", "Inventory", Items.CHEST, new Container(main, List.of()), !inv.isEmpty());
		addTab("ender_chest", "Ender Chest", Items.ENDER_CHEST, new Container(ProfileItems.slots(profile.get("ender_chest")), List.of()), false);
		addTab("storage", "Storage", Items.BARREL, ProfileItems.storage(profile.get("storage")), false);
		addTab("accessory_bag", "Accessories", Items.EMERALD, new Container(ProfileItems.slots(profile.get("accessory_bag")), List.of()), false);
		addTab("wardrobe", "Wardrobe", Items.LEATHER_CHESTPLATE, new Container(ProfileItems.wardrobe(profile.get("wardrobe"), profile.get("inventory")), List.of()), false);
		addTab("pets", "Pets", Items.BONE, new Container(ProfileItems.pets(profile.get("pets")), List.of()), false);
	}

	private void addTab(String id, String title, net.minecraft.world.item.Item icon, Container c, boolean force) {
		tabs.add(new Tab(id, title, new ItemStack(icon)));
		if (force || !c.items().isEmpty()) containers.put(id, c);
	}

	// ---- drawing -------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		hits.clear();
		float open = Anim.since(openedAt, 260);
		Draw.opacity = open;
		try {
			draw(g, mouseX, mouseY, Math.round((1f - open) * 10), open);
		} finally {
			Draw.opacity = 1f;
		}
	}

	private void draw(GuiGraphicsExtractor g, int mx, int my, int rise, float open) {
		int pw = panelW(), ph = panelH();
		int px = panelX(), py = (height - ph) / 2 + rise;
		// The player stands in their own card to the left, NameMC style, on every tab.
		if (side()) {
			int sx = px - SIDE_GAP - SIDE_W;
			Draw.round(g, sx, py, SIDE_W, ph, Theme.PANEL);
			figure(g, sx + 6, py + 6, SIDE_W - 4, ph - 12, form != null ? -1 : mx, form != null ? -1 : my);
		}
		if (form != null && !card.isOpen()) form = null; // the card closed itself (Esc or Cancel)
		boolean modal = form != null;
		int fmx = modal ? -1 : mx, fmy = modal ? -1 : my; // the card blocks hover underneath it

		Draw.round(g, px, py, pw, ph, Theme.PANEL);
		header(g, px, py, pw, fmx, fmy, open);
		int ty = py + HEADER_H;
		tabStrip(g, px + 8, ty, pw - 16, fmx, fmy);
		Draw.rect(g, px + 8, ty + TABS_H, pw - 16, 1, Theme.LINE);

		// The staff drawer takes room from the body rather than covering it, so nothing hides under it.
		float d = staffTools ? drawer.to(drawerOpen ? 1f : 0f) : 0f;
		bodyX = px + 10;
		bodyY = ty + TABS_H + 6;
		bodyW = pw - 20 - Math.round(DRAWER_W * d);
		bodyH = py + ph - 6 - bodyY;
		if (tab.equals("overview")) overview(g, fmx, fmy);
		else if (containers.containsKey(tab)) container(g, fmx, fmy, open);
		else Draw.textCentered(g, font, "Nothing here in this profile's data.", bodyX + bodyW / 2, bodyY + bodyH / 2 - 4, Theme.MUTED);
		drawerW = 0;
		if (d > 0.01f) drawerPanel(g, px + pw - Math.round(DRAWER_W * d), ty + TABS_H + 1, Math.round(DRAWER_W * d), py + ph - ty - TABS_H - 1, fmx, fmy, d);
		outcome(g, px, py, pw, ph);
		if (modal) {
			hits.clear();
			card.draw(g, px, py, pw, ph, mx, my, player, command(form), this::confirm);
		}
	}

	/** The last staff action's result along the bottom of the panel: waiting, then the server's reply. */
	private void outcome(GuiGraphicsExtractor g, int px, int py, int pw, int ph) {
		StaffOutcome.Shown s = StaffOutcome.shown();
		if (s == null) return;
		long now = Util.getMillis();
		float in = Anim.since(s.sentAt(), 180), out = Math.clamp((s.until() - now) / 300f, 0f, 1f);
		List<String> lines = new ArrayList<>(s.lines());
		if (s.waits() && lines.isEmpty()) lines.add(s.waiting() ? "§7Waiting for the server…" : "§7No reply from the server.");
		int w = Math.min(300, pw - 40), x = px + (pw - w) / 2;
		int h = 22 + lines.size() * 10;
		int y = py + ph - h - 10 + Math.round((1f - in) * 6);
		float before = Draw.opacity;
		Draw.opacity *= Math.min(in, out);
		try {
			int color = s.color();
			Draw.round(g, x, y, w, h, 0xF81B1B23);
			Draw.outline(g, x, y, w, h, Draw.alpha(color, 0.35f));
			// A waiting dot breathes; a settled one holds its colour.
			float pulse = s.waiting() ? 0.55f + 0.45f * (float) Math.sin(now / 140.0) : 1f;
			Draw.round(g, x + 8, y + 8, 4, 4, Draw.alpha(color, pulse));
			String head = "§l" + s.label() + " §r§7" + s.detail();
			while (font.width(head) > w - 26 && head.length() > 4) head = head.substring(0, head.length() - 2);
			Draw.text(g, font, head, x + 16, y + 6, Theme.TEXT);
			int ly = y + 18;
			for (String line : lines) {
				String shown = line;
				while (font.width(shown) > w - 20 && shown.length() > 4) shown = shown.substring(0, shown.length() - 2);
				Draw.text(g, font, shown, x + 10, ly, Theme.TEXT);
				ly += 10;
			}
			if (s.waits() && s.waiting()) {
				float left = 1f - (now - s.sentAt()) / (float) StaffOutcome.WAIT_MS;
				Draw.rect(g, x + 1, y + h - 1, Math.round((w - 2) * left), 1, Draw.alpha(color, 0.6f));
			}
		} finally {
			Draw.opacity = before;
		}
	}

	/** Whether there is room for the player's card beside the panel. */
	private boolean side() {
		return width - panelW() >= 2 * (SIDE_W + SIDE_GAP) + 10;
	}

	/** The main panel's left edge: centred, or shifted right to leave room for the player card. */
	private int panelX() {
		int pw = panelW();
		return side() ? (width - pw + SIDE_W + SIDE_GAP) / 2 : (width - pw) / 2;
	}

	private int panelW() {
		return Math.min(staffTools ? 550 : 460, width - 10);
	}

	private int panelH() {
		return Math.min(250, height - 10);
	}

	private void header(GuiGraphicsExtractor g, int px, int py, int pw, int mx, int my, float open) {
		if (open > 0.5f) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(px + 8, py + 6);
			pose.scale(1.25f, 1.25f);
			g.item(head, 0, 0);
			pose.popMatrix();
		}
		int nx = px + 32;
		Draw.text(g, font, "§l" + player, nx, py + 7, Theme.TEXT);
		int after = nx + font.width("§l" + player) + 5;
		if (!rank.isEmpty()) {
			String tag = rankTag(rank);
			int tint = rankColour(rank);
			after = tag == null ? chip(g, rank, after, py + 6, Theme.ACCENT_SOFT, Theme.ACCENT) + 4
					: chip(g, tag, after, py + 6, 0x40000000 | tint & 0xFFFFFF, 0xFF000000 | tint) + 4;
		}
		if (!profileLine.isEmpty()) Draw.text(g, font, profileLine, nx, py + 18, Theme.MUTED);
		hits.add(new Hit(nx, py + 5, font.width("§l" + player), 10, () -> copy(player, "name")));

		int right = px + pw - 8;
		if (staffTools) {
			String label = drawerOpen ? "Staff ▸" : "◂ Staff";
			int w = font.width(label) + 12;
			float h = hover.to("staffToggle", drawerOpen || Draw.inside(mx, my, right - w, py + 8, w, 15));
			Draw.round(g, right - w, py + 8, w, 15, Anim.lerpColor(Theme.FIELD, Theme.ACCENT_SOFT, h));
			Draw.textCentered(g, font, label, right - w / 2, py + 12, Anim.lerpColor(Theme.MUTED, Theme.TEXT, h));
			hits.add(new Hit(right - w, py + 8, w, 15, () -> drawerOpen = !drawerOpen));
			right -= w + 8;
		}
		// The headline numbers, right-aligned: coins first, as far as there's room.
		for (int i = 0; i < Math.min(2, stats.size()); i++) {
			String[] s = stats.get(i);
			int w = Math.max(font.width(s[1]), font.width(s[0])) + 4;
			if (right - w < after + 10) break;
			Draw.textRight(g, font, s[0], right, py + 7, Theme.FAINT);
			Draw.textRight(g, font, "§6" + s[1], right, py + 18, Theme.TEXT);
			if (s[0].equals("Networth") && worthTip.size() > 1 && Draw.inside(mx, my, right - w, py + 6, w, 22)) g.setTooltipForNextFrame(font, worthTip, java.util.Optional.empty(), mx, my);
			right -= w + 10;
		}
	}

	/**
	 * A rank as it looks in game, colour codes and all: "§aVIP§6+", "§bMVP§c+". Null for a
	 * rank not listed here, which keeps the plain accent chip. The site sends only the rank's
	 * name ("MVP+", sometimes "MVP_PLUS"), no colours, so these are dragsim's own.
	 */
	static String rankTag(String rank) {
		String staff = dev.lauchy.lazarus.core.Ranks.tag(rank);
		if (staff != null) return staff;
		return switch (rankKey(rank)) {
			case "VIP" -> "§aVIP";
			case "VIP+" -> "§aVIP§6+";
			case "MVP" -> "§bMVP";
			case "MVP+" -> "§bMVP§c+";
			case "DRAGON" -> "§dDRAGON";
			default -> null;
		};
	}

	/** The rank's main colour (0xRRGGBB), to tint its chip. */
	private static int rankColour(String rank) {
		String key = rankKey(rank);
		switch (dev.lauchy.lazarus.core.Ranks.code(rank)) {
			case '9' -> { return 0x5555FF; }
			case 'b' -> { return 0x55FFFF; }
			case '2' -> { return 0x00AA00; }
			case 'a' -> { return 0x55FF55; }
			case '4' -> { return 0xAA0000; }
			case 'c' -> { return 0xFF5555; }
			default -> { }
		}
		if (key.startsWith("VIP")) return 0x55FF55;
		if (key.startsWith("MVP")) return 0x55FFFF;
		return switch (key) {
			case "DRAGON" -> 0xFF55FF;
			case "MOD" -> 0x00AA00;
			default -> 0xFF5555;
		};
	}

	static String rankKey(String rank) {
		return dev.lauchy.lazarus.core.Staff.rankKey(rank);
	}

	private int chip(GuiGraphicsExtractor g, String text, int x, int y, int bg, int fg) {
		int w = font.width(text) + 8;
		Draw.round(g, x, y, w, 11, bg);
		Draw.text(g, font, text, x + 4, y + 2, fg);
		return x + w;
	}

	/** Icon tabs: the open one shows its name, the rest just their icon (name on hover). */
	private void tabStrip(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		int tx = x;
		Tab hovered = null;
		for (Tab t : tabs) {
			boolean sel = t.id.equals(tab), has = t.id.equals("overview") || containers.containsKey(t.id);
			int tw = sel ? 22 + font.width(t.title) + 6 : 22;
			boolean over = Draw.inside(mx, my, tx, y + 2, tw, 18);
			float h = hover.to("tab:" + t.id, sel || over);
			if (sel) {
				if (tabX.get() < 0) tabX.snap(tx);
				tabX.to(tx);
				tabW.to(tw);
			}
			if (!sel && h > 0f && has) Draw.round(g, tx, y + 2, tw, 18, Draw.alpha(0xFFFFFF, h * 0.06f));
			// Items can't be drawn see-through, so a tab with no data gets its icon dimmed by a
			// veil of the panel colour instead of being hidden.
			g.item(t.icon, tx + 3, y + 3);
			if (!has) Draw.rect(g, tx + 3, y + 3, 16, 16, Draw.alpha(Theme.PANEL & 0xFFFFFF, 0.7f));
			if (sel) Draw.text(g, font, t.title, tx + 22, y + 7, Theme.TEXT);
			if (over && !sel) hovered = t;
			if (has) hits.add(new Hit(tx, y + 2, tw, 18, () -> {
				tab = t.id;
				page = 0;
				resetScroll();
			}));
			tx += tw + 3;
		}
		Draw.round(g, Math.round(tabX.get()) + 3, y + TABS_H - 2, Math.round(tabW.get()) - 6, 2, Theme.ACCENT);
		if (hovered != null) {
			boolean has = hovered.id.equals("overview") || containers.containsKey(hovered.id);
			g.setTooltipForNextFrame(font, Component.literal(has ? hovered.title : hovered.title + " §8· not in this profile's data"), mx, my);
		}
	}

	private void resetScroll() {
		scroll = 0;
		shownScroll.snap(0);
	}

	/** Skills on the left; slayers, dungeons and currencies on the right. Scrolls if it runs long. */
	private void overview(GuiGraphicsExtractor g, int mx, int my) {
		viewH = bodyH;
		scroll = Math.clamp(scroll, 0, Math.max(0, contentH - viewH));
		int off = Math.round(shownScroll.to((float) scroll));
		int modelW = !side() && bodyW >= 300 ? MODEL_W : 0; // inline only when the side card has no room
		if (modelW > 0) figure(g, bodyX, bodyY, modelW, bodyH, mx, my);
		int colW = (bodyW - modelW - 12) / 2, lx = bodyX + modelW, rx = lx + colW + 12;
		g.enableScissor(bodyX - 2, bodyY - 2, bodyX + bodyW + 2, bodyY + bodyH);
		int ly = bodyY - off, ry = bodyY - off;

		ly = section(g, "Skills", lx, ly);
		for (Bar b : skills) ly = barRow(g, b, lx, ly, colW, mx, my, true);
		if (skills.isEmpty()) ly = muted(g, "No skills shared.", lx, ly);

		if (catacombs != null || !dungeonFacts.isEmpty()) {
			ry = section(g, "Dungeons", rx, ry);
			if (catacombs != null) ry = barRow(g, catacombs, rx, ry, colW, mx, my, true);
			ry = facts(g, dungeonFacts, rx, ry, colW);
		}
		if (!slayers.isEmpty()) {
			ry = section(g, "Slayer", rx, ry + 4);
			for (Bar b : slayers) ry = barRow(g, b, rx, ry, colW, mx, my, false);
		}
		if (!stats.isEmpty()) {
			ry = section(g, "Purse", rx, ry + 4);
			ry = facts(g, stats, rx, ry, colW);
		}
		g.disableScissor();
		contentH = Math.max(ly, ry) + off - bodyY + 4;
		if (contentH > viewH) {
			int barH = Math.max(14, viewH * viewH / contentH);
			int barY = bodyY + Math.round((viewH - barH) * (off / (float) (contentH - viewH)));
			Draw.round(g, bodyX + bodyW + 3, barY, 2, barH, Theme.LINE);
		}
	}

	/**
	 * The player standing at an angle, NameMC style, in a soft spotlight. They face a little
	 * off to the side at rest and turn toward the mouse while it's over them.
	 */
	private void figure(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		int fw = w - 8, fh = side() ? h : Math.min(h, 118);
		Draw.round(g, x, y, fw, fh, 0x18FFFFFF);
		Draw.round(g, x + fw / 2 - 18, y + fh - 12, 36, 4, 0x30000000); // the shadow at their feet
		RemotePlayer p = model();
		if (p == null || form != null) return;
		boolean over = Draw.inside(mx, my, x, y, fw, fh);
		int cx = x + fw / 2, cy = y + fh / 3;
		// At rest they look a little left and down, like a NameMC render.
		float tx = lookX.to(over ? mx : cx - 40), ty = lookY.to(over ? my : cy + 12);
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 4, x + fw - 2, y + fh - 8, Math.round(Math.min(fh * 0.38f, fw * 0.62f)), 0.0625f, tx, ty, p);
	}

	private RemotePlayer model() {
		if (model != null) return model;
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.level == null) return null;
		// Their real skin comes from the tab list, so only players on the server show it.
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player);
		GameProfile profile = info != null ? info.getProfile() : new GameProfile(uuid(), player);
		model = new RemotePlayer(mc.level, profile);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		armour.forEach((i, s) -> {
			if (i >= 0 && i < 4) model.setItemSlot(slots[i], s.copy());
		});
		Container inv = containers.get("inventory");
		if (inv != null) {
			ItemStack held = inv.items().get(0);
			for (int i = 0; held == null && i < 9; i++) held = inv.items().get(i);
			if (held != null) model.setItemSlot(EquipmentSlot.MAINHAND, held.copy());
		}
		return model;
	}

	/** Their UUID from the profile data, with or without dashes; an offline-style one if it has none. */
	/**
	 * The name the player goes by on the server right now: a nicked staff member's nick, from
	 * /nicklist, else the tab list entry with their UUID. Teleports and tunnels need it; everything else takes
	 * the real username.
	 */
	private String shownName() {
		String nick = Nicks.of(player);
		if (nick != null) return nick;
		UUID id = uuid();
		var connection = minecraft == null ? null : minecraft.getConnection();
		PlayerInfo info = id == null || connection == null ? null : connection.getPlayerInfo(id);
		String name = info == null ? "" : info.getProfile().name();
		return name == null || name.isBlank() ? player : name;
	}

	private UUID uuid() {
		String u = uuid.replace("-", "");
		if (u.length() == 32) {
			try {
				return new UUID(Long.parseUnsignedLong(u.substring(0, 16), 16), Long.parseUnsignedLong(u.substring(16), 16));
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private int section(GuiGraphicsExtractor g, String title, int x, int y) {
		Draw.text(g, font, title.toUpperCase(java.util.Locale.ROOT), x, y, Theme.ACCENT);
		return y + 12;
	}

	private int muted(GuiGraphicsExtractor g, String text, int x, int y) {
		Draw.text(g, font, text, x, y, Theme.FAINT);
		return y + 12;
	}

	/** Name and level on one line, a thin bar under it that fills in when the screen opens. */
	private int barRow(GuiGraphicsExtractor g, Bar b, int x, int y, int w, int mx, int my, boolean xpCurve) {
		boolean maxed = b.max > 0 && b.level >= b.max;
		Draw.text(g, font, b.name, x, y, Theme.TEXT);
		String lv = b.max == 0 ? Text.compact(b.xp) + " xp" : String.valueOf(b.level);
		Draw.textRight(g, font, (maxed ? "§6" : "§f") + lv, x + w, y, Theme.TEXT);
		float fill = bars.to(b, b.progress);
		Draw.round(g, x, y + 10, w, 3, Theme.OFF);
		Draw.round(g, x, y + 10, Math.max(3, Math.round(w * fill)), 3, maxed ? 0xFFFFB84D : Theme.ACCENT);
		if (Draw.inside(mx, my, x, y - 1, w, 15) && my >= bodyY && my < bodyY + bodyH) {
			String tip = xpCurve && b.next > 0
					? b.name + " " + b.level + " §8· §f" + Text.compact(b.xp) + "§8 / " + Text.compact(b.next) + " xp to " + (b.level + 1)
					: b.name + " §8· §f" + Text.compact(b.xp) + " xp" + (maxed ? " §6max" : "");
			g.setTooltipForNextFrame(font, Component.literal(tip), mx, my);
		}
		return y + 18;
	}

	private int facts(GuiGraphicsExtractor g, List<String[]> rows, int x, int y, int w) {
		for (String[] r : rows) {
			Draw.text(g, font, r[0], x, y, Theme.MUTED);
			Draw.textRight(g, font, r[1], x + w, y, Theme.TEXT);
			y += 11;
		}
		return y + 2;
	}

	/**
	 * An item grid, scaled to sit comfortably in the body: it is laid out at the vanilla 18px
	 * slot in its own coordinates, then drawn at about 80% of the size that would fill the body
	 * (1x to 2x), centred, so a big panel isn't mostly empty or crammed. The short grids
	 * (inventory, ender chest, accessories, pets) stop at 60% and 1.5x, as at full size their few
	 * rows of slots looked oversized.
	 * The mouse and click areas are mapped into those coordinates so hover and pages still line up.
	 */
	private void container(GuiGraphicsExtractor g, int mx, int my, float open) {
		Container c = containers.get(tab);
		int gridW = COLS * CELL;
		boolean inventory = tab.equals("inventory");
		int pages = 1, contentW, contentH;
		if (inventory) {
			contentW = gridW + 24;
			contentH = 4 * CELL + 6 + (equipment.isEmpty() ? 0 : 8 + CELL);
		} else {
			int last = c.items().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
			pages = Math.max(c.pageTitles().size(), last / PAGE + 1);
			page = Math.clamp(page, 0, pages - 1);
			int rows = Math.max(1, ((pages == 1 ? last : PAGE - 1) / COLS) + 1); // a single page only as tall as it's filled
			int pagerRows = pages > 1 ? (pages + gridW / 15 - 1) / (gridW / 15) : 0;
			contentW = gridW;
			contentH = (pages > 1 ? (pagerRows - 1) * 14 + 28 : 0) + rows * CELL;
		}
		// Most of the body, not all of it: a grid filling every pixel reads as overwhelming.
		float fit = Math.min((bodyW - 8) / (float) contentW, (bodyH - 4) / (float) contentH);
		boolean small = SMALL_GRIDS.contains(tab);
		float k = Math.clamp(fit * (small ? 0.6f : 0.8f), 1f, small ? 1.5f : 2f);
		int ox = bodyX + Math.round((bodyW - contentW * k) / 2), oy = bodyY + Math.max(0, Math.round((bodyH - 4 - contentH * k) / 2));
		int lmx = Math.round((mx - ox) / k), lmy = Math.round((my - oy) / k);
		int firstHit = hits.size();
		pagerTip = null;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(ox, oy);
		pose.scale(k, k);
		int gx = inventory ? 24 : 0, gy = 0;
		ItemStack[] hovered = new ItemStack[1];
		if (inventory) {
			// Armour in a column at the left, the main inventory, then the hotbar a little apart.
			for (int i = 0; i < 4; i++) cell(g, gx - 24, gy + i * CELL, 100 + i, armour.get(i), lmx, lmy, open, hovered);
			for (int i = 0; i < 27; i++) cell(g, gx + i % COLS * CELL, gy + i / COLS * CELL, i, c.items().get(9 + i), lmx, lmy, open, hovered);
			for (int i = 0; i < 9; i++) cell(g, gx + i * CELL, gy + 3 * CELL + 6, 27 + i, c.items().get(i), lmx, lmy, open, hovered);
			if (!equipment.isEmpty()) {
				int ey = gy + 4 * CELL + 14, i = 0;
				Draw.text(g, font, "Equipment", gx - 24, ey + 5, Theme.FAINT);
				for (ItemStack s : equipment.values()) cell(g, gx + 36 + i++ * CELL, ey, 200 + i, s, lmx, lmy, open, hovered);
			}
		} else {
			if (pages > 1) gy = pager(g, c, pages, gx, gy, gridW, lmx, lmy);
			int shown = pages == 1 ? Math.max(1, (c.items().keySet().stream().mapToInt(Integer::intValue).max().orElse(0) / COLS + 1)) * COLS : PAGE;
			for (int i = 0; i < Math.min(shown, PAGE); i++) cell(g, gx + i % COLS * CELL, gy + i / COLS * CELL, i, c.items().get(page * PAGE + i), lmx, lmy, open, hovered);
		}
		pose.popMatrix();
		// Click areas added while scaled were in grid coordinates; put them back on screen.
		for (int i = firstHit; i < hits.size(); i++) {
			Hit h = hits.get(i);
			hits.set(i, new Hit(ox + Math.round(h.x() * k), oy + Math.round(h.y() * k), Math.round(h.w() * k), Math.round(h.h() * k), h.action()));
		}
		if (hovered[0] != null) g.setTooltipForNextFrame(font, hovered[0], mx, my);
		else if (pagerTip != null) g.setTooltipForNextFrame(font, Component.literal(pagerTip), mx, my);
	}

	private void cell(GuiGraphicsExtractor g, int x, int y, int key, ItemStack s, int mx, int my, float open, ItemStack[] hovered) {
		boolean over = Draw.inside(mx, my, x, y, CELL - 1, CELL - 1);
		float h = cells.to(key, over);
		Draw.round(g, x, y, CELL - 1, CELL - 1, Anim.lerpColor(0x26000000, 0x30FFFFFF, h));
		if (s == null || open < 0.5f || form != null) return; // items can't fade, so they wait until the panel is mostly in
		g.item(s, x, y);
		g.itemDecorations(font, s, x, y);
		if (over) hovered[0] = s;
	}

	/** Numbered page chips above the grid (storage has one per backpack), with the page's name. */
	private int pager(GuiGraphicsExtractor g, Container c, int pages, int gx, int gy, int gridW, int mx, int my) {
		int x = gx, y = gy, perRow = gridW / 15;
		for (int i = 0; i < pages; i++) {
			if (i > 0 && i % perRow == 0) {
				x = gx;
				y += 14;
			}
			int p = i;
			boolean sel = p == page;
			float h = hover.to("page" + p, sel || Draw.inside(mx, my, x, y, 13, 12));
			Draw.round(g, x, y, 13, 12, sel ? Theme.ACCENT : Anim.lerpColor(Theme.FIELD, Theme.ACCENT_SOFT, h));
			Draw.textCentered(g, font, String.valueOf(p + 1), x + 7, y + 2, sel ? Theme.TEXT : Theme.MUTED);
			if (!sel && Draw.inside(mx, my, x, y, 13, 12) && p < c.pageTitles().size()) {
				pagerTip = c.pageTitles().get(p); // shown by container() at the real mouse position
			}
			hits.add(new Hit(x, y, 13, 12, () -> page = p));
			x += 15;
		}
		String title = page < c.pageTitles().size() ? c.pageTitles().get(page) : "Page " + (page + 1);
		Draw.text(g, font, title, gx, y + 16, Theme.MUTED);
		return y + 28;
	}

	// ---- staff ---------------------------------------------------------------------------

	private void drawerPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float d) {
		Draw.rect(g, x, y, w, h - 6, Theme.PANEL_HI);
		Draw.rect(g, x, y, 1, h - 6, Theme.LINE);
		float before = Draw.opacity;
		Draw.opacity *= d;
		int viewH = h - 6;
		drawerX = x;
		drawerY = y;
		drawerW = w;
		drawerH = viewH;
		drawerScroll = Math.clamp(drawerScroll, 0, Math.max(0, drawerContentH - viewH));
		int off = Math.round(shownDrawerScroll.to((float) drawerScroll));
		int firstHit = hits.size();
		g.enableScissor(x, y, x + w, y + viewH);
		int bx = x + 8, bw = DRAWER_W - 16, by = y + 6 - off;
		Group group = null;
		List<Staff> row = new ArrayList<>();
		for (Staff s : Staff.values()) {
			if (s.group != group) {
				if (!row.isEmpty()) by = buttonRow(g, row, bx, by, bw, mx, my) + 4;
				row.clear();
				group = s.group;
				Draw.text(g, font, group.title, bx, by, Theme.FAINT);
				by += 11;
			}
			row.add(s);
		}
		by = buttonRow(g, row, bx, by, bw, mx, my);
		drawerContentH = by + off - y + 6;
		g.disableScissor();
		// Buttons scrolled out of view can't be clicked.
		for (int i = hits.size() - 1; i >= firstHit; i--) {
			Hit hit = hits.get(i);
			if (hit.y() < y || hit.y() + hit.h() > y + viewH) hits.remove(i);
		}
		if (drawerContentH > viewH) {
			int barH = Math.max(12, viewH * viewH / drawerContentH);
			int barY = y + Math.round((viewH - barH) * (off / (float) (drawerContentH - viewH)));
			Draw.rect(g, x + w - 3, barY, 2, barH, Theme.LINE);
		}
		Draw.opacity = before;
	}

	/**
	 * One group's buttons in a grid: punishments one per line, the rest side by side as
	 * many to a line as fit their longest label, wrapping onto more lines when they don't.
	 */
	private int buttonRow(GuiGraphicsExtractor g, List<Staff> row, int x, int y, int w, int mx, int my) {
		int gap = 3, widest = 0;
		for (Staff s : row) widest = Math.max(widest, font.width(s.label) + 6);
		int perLine = row.getFirst().punish() ? 1 : Math.clamp((w + gap) / (widest + gap), 1, row.size());
		int lines = (row.size() + perLine - 1) / perLine;
		for (int i = 0; i < row.size(); i++) {
			Staff s = row.get(i);
			int line = i / perLine, col = i % perLine;
			// Each line shares the full width, so a short last line still fills it.
			int onLine = Math.min(perLine, row.size() - line * perLine);
			int bw = (w - gap * (onLine - 1)) / onLine;
			int bx = x + col * (bw + gap), by = y + line * 17;
			float h = hover.to(s, Draw.inside(mx, my, bx, by, bw, 15));
			int base = s.punish() ? 0x2AFF6B6B : Theme.FIELD, hot = s.punish() ? 0xCCE05555 : Theme.ACCENT_SOFT;
			Draw.round(g, bx, by, bw, 15, Anim.lerpColor(base, hot, h));
			Draw.textCentered(g, font, s.label, bx + bw / 2, by + 4, s.punish() ? Anim.lerpColor(0xFFFF9C9C, Theme.TEXT, h) : Theme.TEXT);
			hits.add(new Hit(bx, by, bw, 15, () -> pick(s)));
		}
		return y + lines * 17 - 2;
	}

	private void pick(Staff s) {
		switch (s) {
			case COPY_NAME -> copy(player, "name");
			case COPY_UUID -> copy(uuid.isEmpty() ? player : uuid, uuid.isEmpty() ? "name (no UUID shared)" : "UUID");
			default -> {
				if (!s.duration && !s.reason) {
					run(s);
					return;
				}
				form = s;
				card.open(s.label, s.duration, s.reason, s == Staff.BAN ? PunishCard.Kind.BAN : s == Staff.MUTE ? PunishCard.Kind.MUTE : PunishCard.Kind.OTHER);
			}
		}
	}

	private void copy(String text, String what) {
		if (minecraft == null) return;
		minecraft.keyboardHandler.setClipboard(text);
		StaffOutcome.done("Copied " + what, text);
	}

	private boolean ready() {
		return form != null && card.ready();
	}

	private String command(Staff s) {
		String me = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
		String server = Area.server();
		return s.command.replace("%n", shownName()).replace("%p", player).replace("%me", me).replace("%s", server == null ? "" : server)
				.replace("%d", card.duration()).replace("%r", card.reason()).replaceAll(" +", " ").trim();
	}

	private void confirm() {
		if (form == null || !ready()) return;
		run(form);
		form = null;
		card.close();
	}

	/** Sends the action's command for this player, filling in the card's answers. */
	private void run(Staff s) {
		if (minecraft == null || minecraft.player == null) return;
		if (s == Staff.SEND_HERE && Area.server() == null) {
			StaffOutcome.failed(s.label, "Can't tell which server you're on from the sidebar.");
			return;
		}
		String cmd = command(s);
		minecraft.player.connection.sendCommand(cmd);
		StaffOutcome.sent(s.label, cmd, player);
	}

	// ---- input ---------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (card.mousePressed(panelX(), (height - panelH()) / 2, panelW(), panelH(), event.x(), event.y())) return true;
		for (int i = hits.size() - 1; i >= 0; i--) { // later hits sit on top
			Hit h = hits.get(i);
			if (Draw.inside(event.x(), event.y(), h.x, h.y, h.w, h.h)) {
				h.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		card.mouseReleased();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (form != null) return true;
		if (drawerW > 0 && Draw.inside(mx, my, drawerX, drawerY, drawerW, drawerH)) {
			drawerScroll -= dy * 20;
			return true;
		}
		if (tab.equals("overview")) scroll -= dy * 20;
		else page += dy < 0 ? 1 : -1;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!card.isOpen()) return super.keyPressed(event);
		card.keyPressed(event.key(), this::confirm);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!card.isOpen()) return super.charTyped(event);
		card.charTyped(event.codepointAsString());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean isNumber(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
	}

	private static int num(JsonObject o, String key) {
		return isNumber(o.get(key)) ? o.get(key).getAsInt() : 0;
	}
}
