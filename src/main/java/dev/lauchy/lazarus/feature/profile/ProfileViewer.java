package dev.lauchy.lazarus.feature.profile;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Http;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Staff;
import dev.lauchy.lazarus.feature.staff.AltFlag;
import dev.lauchy.lazarus.feature.staff.StaffTools;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

/**
 * {@code /l <player>}: a player's Dragsim profile, read from dragsim.co's public player
 * API: skills, slayers, dungeons and coins, and their inventory, armour, ender chest,
 * storage, accessory bag, wardrobe and pets as real item grids with tooltips.
 */
public final class ProfileViewer extends Feature {

	public ProfileViewer() {
		super("profileViewer", "Profile Viewer", "/l <player> opens anyone's profile: stats and every item container.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public void init() {
		StaffOutcome.init();
		Nicks.init();
		ItemLooks.init();
		Commands.add(() -> ClientCommands.literal("l")
				.executes(c -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null) open(mc.player.getName().getString());
					return 1;
				})
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					open(StringArgumentType.getString(c, "player"));
					return 1;
				})));
	}

	public static ProfileViewer INSTANCE;

	/** Opens a player's profile from another feature, as {@code /l <player>} would. */
	public static void show(String name) {
		if (INSTANCE != null) INSTANCE.open(name);
	}

	private void open(String name) {
		if (!enabled()) {
			Message.lazarus().error("Profile Viewer is off. Turn it on in the Lazarus menu.").send();
			return;
		}
		if (!name.matches("\\w{1,16}")) {
			Message.lazarus().error("That isn't a player name.").send();
			return;
		}
		if (!StaffTools.on()) {
			load(name, false);
			return;
		}
		// The staff drawer is for staff only, by your rank on dragsim.co. Staff can be nicked:
		// /l <nick> opens the real player's profile, read from /nicklist.
		Staff.check(isStaff -> {
			if (isStaff) Nicks.refresh(() -> load(name, true));
			else load(name, false);
		});
	}

	private void load(String typed, boolean staffTools) {
		String real = Nicks.realName(typed);
		String name = real != null ? real : typed;
		Message.lazarus().info("Loading ").highlight(name).info("'s profile" + (real != null ? " (nicked as " + typed + ")" : "") + "…").send();
		Minecraft mc = Minecraft.getInstance();
		Http.json("/api/player/" + Http.encode(name) + "?mode=main").whenComplete((json, error) -> mc.execute(() -> {
			if (error != null || json == null || !json.isJsonObject() || !json.getAsJsonObject().has("profile")) {
				Message.lazarus().error("Couldn't load ").highlight(name).error("'s profile" + (error != null ? ": " + rootMessage(error) : ".")).send();
				return;
			}
			JsonObject root = json.getAsJsonObject();
			// The last profile opened, as the site sent it, for checking what it does and doesn't include.
			Config.writeJson(Config.dir().resolve("cache").resolve("last-profile.json"), root);
			StaffTools tools = StaffTools.INSTANCE;
			mc.setScreen(new ProfileScreen(name, root, staffTools, tools.durations(), tools.reasons(), tools.banRules(), tools.muteRules()));
			if (staffTools) AltFlag.profileOpened(name);
		}));
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		String m = t.getMessage();
		return m == null ? t.getClass().getSimpleName() : m.contains("404") ? "no such player" : m;
	}
}
