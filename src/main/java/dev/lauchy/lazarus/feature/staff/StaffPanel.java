package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Gate;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Ranks;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.feature.profile.Nicks;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.HudElement;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small staff panel on the HUD: whether you're vanished, the nick you go by, which chat channel you're typing in,
 * and how many staff are online, how many online players are flagged, and how many reports are open. You arrive in
 * each lobby visible, unless the server says otherwise ("You are vanished (persisted from last session)."), and then
 * the server's own lines as you toggle vanish, nick or channel say the rest ({@link StaffStatus}); the nick also
 * comes from the nick list Nick Reveal reads.
 */
public final class StaffPanel extends Feature {
	/** How long a nick the server just named outranks the tab list, which catches up a moment later. */
	private static final long SAID_MS = 3000;
	/** How often the counts are worked out again, the shared flags asked for while the desk is shut, and /glist all asked. */
	private static final long COUNT_MS = 1000, SHARED_MS = 60_000, GLIST_MS = 60_000;

	private final BoolSetting showVanish = add(new BoolSetting("vanish", "Vanished", "Whether other players can see you.", true));
	private final BoolSetting showNick = add(new BoolSetting("nick", "Nick", "The nick you go by, if any.", true));
	private final BoolSetting showChannel = add(new BoolSetting("channel", "Chat channel", "Where what you type goes, once the server has said (after /sc).", true));
	private final BoolSetting showStaff = add(new BoolSetting("staff", "Staff online", "How many staff are on the tab list, and how many of them are vanished.", true));
	private final BoolSetting showNames = add(new BoolSetting("names", "Staff by name", "Each staff member online under the count: their rank, whether vanished, and the nick they go by.", true));
	private final BoolSetting network = add(new BoolSetting("network", "Every server's staff",
			"Asks /glist all once a minute, out of chat, so staff on the other servers count too, with the server they're on. ADMIN+, as /glist is.", true));
	private final BoolSetting showFlagged = add(new BoolSetting("flagged", "Flagged online", "How many players online the staff desk would flag.", true));
	private final BoolSetting showReports = add(new BoolSetting("reports", "Open reports", "Reports nobody has handled yet.", true));

	/** The lobby you're in (its connection), so arriving in another is noticed. */
	private ClientPacketListener lobby;
	/** Whether you're vanished (null before you've arrived anywhere); and the server's last vanish line, and when. */
	private Boolean vanished;
	private boolean lastSaidOn;
	private long vanishSaidAt;
	/** The nick you go by ("" none, null not known), its rank, and when the server last said so in chat. */
	private String nick, nickRank = "";
	private long nickSaidAt;
	/** The chat channel the server last said you're in, "" before it has in this lobby. */
	private String channel = "";
	/** The staff seen on the tab list lately, and the flagged players online, as last worked out, and when. */
	private final StaffStatus.Seen staff = new StaffStatus.Seen();
	private int flaggedOnline;
	private long countedAt;
	/** When /glist all was last asked for quietly. */
	private long glistAt;

	public StaffPanel() {
		super("staffPanel", "Staff Status", "A small panel: whether you're vanished, your nick, your chat channel, and how many staff, flagged players and open reports there are.",
				Category.STAFF, true);
	}

	@Override
	public void init() {
		Chat.listen(line -> {
			for (String part : line.text().split("\\R")) {
				switch (StaffStatus.read(part)) {
					case StaffStatus.Vanished v -> {
						vanished = v.on();
						lastSaidOn = v.on();
						vanishSaidAt = Util.getMillis();
					}
					case StaffStatus.Nicked n -> {
						nick = n.nick();
						nickRank = n.rank();
						nickSaidAt = Util.getMillis();
					}
					case StaffStatus.Channel c -> channel = c.name();
					case null -> {
					}
				}
				// Every /glist all, asked quietly or typed: its staff on every server.
				StaffStatus.Server server = StaffStatus.glist(part);
				if (server != null) listed(server);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ClientPacketListener now = mc.getConnection();
			if (now == null) return; // between lobbies: what's known stands until the next one
			if (now != lobby) arrived(now);
			if (!enabled() || !Staff.is() || Util.getMillis() - countedAt < COUNT_MS) return;
			countedAt = Util.getMillis();
			look(mc);
			if (showFlagged.on() || showReports.on()) FlagSync.refreshIfOlder(SHARED_MS);
			askGlist();
		});
		Hud.register(new HudElement(this, "staffPanel", "Staff status", 0.80f, 0.06f) {
			@Override
			protected boolean visible() {
				return Staff.is();
			}

			@Override
			public int[] draw(GuiGraphicsExtractor g, Font font, boolean sample) {
				List<String> rows = sample ? sampleRows() : rows();
				return rows.size() <= 1 ? null : Draw.panel(g, font, rows);
			}
		});
	}

	/**
	 * Arriving in a lobby: you're visible there unless the server said in the moments before that you're vanished,
	 * which it does on arriving when it kept you vanished. Its chat channel and its tab list start afresh.
	 */
	private void arrived(ClientPacketListener now) {
		lobby = now;
		vanished = lastSaidOn && StaffStatus.vanishedOnArrival(vanishSaidAt, Util.getMillis());
		channel = "";
		staff.leftServer();
	}

	/**
	 * Asks the proxy for every server's players once a minute, out of chat, when you may (/glist is ADMIN+) and the
	 * server isn't answering another quiet question; the answer is read as it arrives ({@link #listed}).
	 */
	private void askGlist() {
		if (!network.on() || (!showStaff.on() && !showNames.on()) || Util.getMillis() - glistAt < GLIST_MS) return;
		if (!Gate.tier(Staff.rank()).allows(Gate.Tier.ADMIN) || Reply.busy() || DashboardReader.working()) return;
		glistAt = Util.getMillis();
		Reply.ask("glist all", "currently connected to the proxy", true, StaffStatus::isGlist, Reply.WAIT_MS, lines -> {
		});
	}

	/**
	 * One server's players from /glist all: each that Rank Colours knows as staff counts, with the server they're on
	 * (a nicked one by their real name). Anyone it doesn't know yet it looks up, as it does the tab list.
	 */
	private void listed(StaffStatus.Server server) {
		long now = Util.getMillis();
		List<String> unknown = new ArrayList<>();
		for (String name : server.players()) {
			String realName = Nicks.realName(name), who = realName != null ? realName : name;
			String rank = RankColours.known(who);
			if (Ranks.code(rank) != 0) staff.listed(who, rank, realName != null ? name : "", server.name(), now);
			else if (rank.isEmpty()) unknown.add(who);
		}
		RankColours.lookUp(unknown);
	}

	/**
	 * Reads the tab list: the staff on it, the nick your own entry may show, and who's flagged. With many online the
	 * server pages the list with rows of its own, so a player is known by the name a row shows ({@link
	 * StaffStatus#nameIn}) as well as by their own entry, each once; a rank comes from the row's tag while its page is
	 * up, else from what Rank Colours has learnt, and a nicked player's from their real name's. Whether a staff member
	 * is vanished is their page's to say: their own entry carries their rank but never the [V], so a [V] anywhere says
	 * they're vanished, and its absence says they aren't only on a row of the page (an entry for another profile, or
	 * them shown twice at once, row and own entry): another page leaves it as their page last showed it.
	 */
	private void look(Minecraft mc) {
		String real = mc.getUser().getName();
		long now = Util.getMillis();
		// Every entry that's a player: who it names, its text (null when it has none), and whether it's a row of the server's pages.
		record Entry(String name, String shown, boolean row) {
		}
		List<Entry> entries = new ArrayList<>();
		Map<String, Integer> shownTimes = new HashMap<>();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			Component shownName = info.getTabListDisplayName();
			String shown = shownName == null ? null : Text.plain(shownName).trim();
			String name = shown == null ? null : StaffStatus.nameIn(shown);
			if (name == null) {
				name = info.getProfile().name();
				shown = null;
			}
			if (!Players.NAME.matcher(name).matches()) continue; // an info row, or the list's filler
			boolean row = shown != null && !info.getProfile().name().equalsIgnoreCase(name);
			entries.add(new Entry(name, shown, row));
			shownTimes.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
			boolean me = mc.player != null && info.getProfile().id().equals(mc.player.getUUID());
			// Your own entry under a name other than yours is the nick you go by.
			if (me && now - nickSaidAt > SAID_MS && !info.getProfile().name().equalsIgnoreCase(real)) nick = info.getProfile().name();
		}
		// Each player once, by lowercase name: their name as shown, the rank tag on an entry of theirs ("" none), and the vanished tag a page shows (null unseen).
		Map<String, String> names = new LinkedHashMap<>(), tags = new HashMap<>();
		Map<String, Boolean> vanishTags = new HashMap<>();
		for (Entry en : entries) {
			String key = en.name().toLowerCase(Locale.ROOT);
			names.putIfAbsent(key, en.name());
			if (en.shown() == null) continue;
			String tag = Roster.rankOf(en.shown());
			if (!tag.isEmpty()) tags.put(key, tag);
			boolean vanishTag = StaffStatus.vanishTag(en.shown());
			if (vanishTag || StaffStatus.pageRow(en.row(), shownTimes.get(key))) vanishTags.merge(key, vanishTag, Boolean::logicalOr);
		}
		List<Roster.Online> online = new ArrayList<>();
		for (var e : names.entrySet()) {
			String name = e.getValue(), realName = Nicks.realName(name), who = realName != null ? realName : name;
			String tag = tags.getOrDefault(e.getKey(), "");
			// A nicked player's row shows the nick's rank, not theirs.
			if (realName == null && Ranks.code(tag) != 0 && RankColours.INSTANCE != null) RankColours.INSTANCE.learn(who, tag);
			String rank = realName == null && Ranks.code(tag) != 0 ? tag : RankColours.known(who);
			if (Ranks.code(rank) != 0) staff.saw(who, rank, realName != null ? name : "", vanishTags.get(e.getKey()), now);
			online.add(new Roster.Online(who, realName != null ? name : "", rank));
		}
		staff.forget(now);
		// Nick Reveal's list names you too, nicked: newer than what chat last said, it stands.
		if (Nicks.answeredAt() > nickSaidAt) {
			String listed = Nicks.of(real);
			nick = listed == null ? "" : listed;
			if (listed == null) nickRank = "";
		}
		flaggedOnline = (int) Roster.build(online, FlagSync.state(), System.currentTimeMillis()).stream().filter(Roster.Row::flagged).count();
	}

	private List<String> rows() {
		List<String> out = new ArrayList<>();
		out.add("§d§lStaff");
		if (showVanish.on() && vanished != null) out.add("§7Vanished\t" + (vanished ? "§aYes" : "§cNo"));
		if (showNick.on()) out.add("§7Nick\t" + (nick == null ? "§8not known" : nick.isEmpty() ? "§fNone" : (nickRank.isEmpty() ? "" : "§7[" + nickRank + "] ") + "§e" + nick));
		if (showChannel.on() && !channel.isEmpty()) out.add("§7Chat\t" + (channel.equals("STAFF") ? "§b" : "§f") + StaffStatus.channelName(channel));
		if (showStaff.on()) out.add("§7Staff online\t§f" + staff.count() + (staff.vanished() > 0 ? " §8(" + staff.vanished() + " vanished)" : ""));
		if (showNames.on()) {
			List<StaffStatus.Seen.Member> members = staff.members();
			for (int i = 0; i < Math.min(NAMES_SHOWN, members.size()); i++) out.add(member(members.get(i), Util.getMillis()));
			if (members.size() > NAMES_SHOWN) out.add(" §8+" + (members.size() - NAMES_SHOWN) + " more");
		}
		if (showFlagged.on()) out.add("§7Flagged online\t" + (flaggedOnline > 0 ? "§c" : "§a") + flaggedOnline);
		if (showReports.on()) {
			int n = FlagSync.state().reports().size();
			out.add("§7Open reports\t" + (n > 0 ? "§e" : "§a") + n);
		}
		return out;
	}

	/**
	 * One staff member under the count: " ADMIN Lauchy vanished", " MOD Fexous as petseklapsas", and for one on another
	 * server, where: " MOD Gurrnos · rift".
	 */
	private static String member(StaffStatus.Seen.Member m, long now) {
		String tag = Ranks.tag(m.rank());
		String where = m.here(now) || m.server().isEmpty() || m.server().equals(dev.lauchy.lazarus.core.Area.server()) ? "" : " §8· §7" + m.server();
		return " " + (tag == null ? "§7" + m.rank() : tag) + " §f" + m.name() + (m.as().isEmpty() ? "" : " §7as §e" + m.as()) + (m.vanished() ? " §8vanished" : "") + where;
	}

	/** The most staff listed by name; the rest are counted. */
	private static final int NAMES_SHOWN = 8;

	/** What the HUD editor shows, wherever you're standing. */
	private List<String> sampleRows() {
		List<String> out = new ArrayList<>();
		out.add("§d§lStaff");
		if (showVanish.on()) out.add("§7Vanished\t§aYes");
		if (showNick.on()) out.add("§7Nick\t§7[DRAGON] §eGhostRogue56");
		if (showChannel.on()) out.add("§7Chat\t§bStaff");
		if (showStaff.on()) out.add("§7Staff online\t§f4 §8(1 vanished)");
		if (showNames.on()) {
			long now = Util.getMillis();
			out.add(member(new StaffStatus.Seen.Member("Porier", "OWNER", "", true, now, "", 0), now));
			out.add(member(new StaffStatus.Seen.Member("Lauchy", "ADMIN", "", false, now, "", 0), now));
			out.add(member(new StaffStatus.Seen.Member("Fexous", "MOD", "petseklapsas", false, now, "", 0), now));
			out.add(member(new StaffStatus.Seen.Member("Gurrnos", "MOD", "", false, 0, "rift", now), now));
		}
		if (showFlagged.on()) out.add("§7Flagged online\t§c3");
		if (showReports.on()) out.add("§7Open reports\t§e1");
		return out;
	}
}
