package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.core.Recolour;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

/**
 * The staff panel from chat: clicking a player's name in any chat line opens their {@link StaffScreen},
 * a report on them beside the ban, mute, kick, teleport, captcha and watch actions and a View profile
 * button. The name looks as it did; hovering it says what a click does. {@code /lazarus staff <player>}
 * (or {@code /lazarus punish <player>}) opens the same panel for anyone.
 */
public final class ChatPunish extends Feature {
	private final BoolSetting names = add(new BoolSetting("names", "Click names in chat", "A player's name in chat opens their staff panel when clicked.", true));
	private final BoolSetting staffToo = add(new BoolSetting("staffToo", "On staff chat too", "Names in staff chat open the panel too.", true));

	public ChatPunish() {
		super("chatPunish", "Staff Panel from Chat", "Click a player's name in chat for their report and the ban, mute, kick and teleport actions.", Category.STAFF, true);
	}

	@Override
	public void init() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay || !enabled() || !names.on()) return message;
			Chat.Speaker who = Chat.speaker(Text.plain(message));
			if (who == null || (who.staffChat() && !staffToo.on()) || who.name().equalsIgnoreCase(Players.me())) return message;
			Component out = Recolour.name(message, who.name(), name -> Message.clickName(name, "Click for staff options on " + who.name(), "/lazarus staff " + who.name()));
			// A name the line reads as the speaker's but can't be found in its pieces: logged so the shape can be matched.
			if (out == message) Lazarus.LOG.info("Staff panel: no name to click in {}", message);
			return out;
		});
		Commands.add(() -> ClientCommands.literal("lazarus")
				.then(ClientCommands.literal("staff").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> open(StringArgumentType.getString(c, "player")))))
				.then(ClientCommands.literal("punish").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> open(StringArgumentType.getString(c, "player"))))));
	}

	private int open(String player) {
		if (!Players.NAME.matcher(player).matches()) {
			Message.lazarus().error("That isn't a player name.").send();
			return 1;
		}
		if (!enabled()) {
			Message.lazarus().error("The staff panel is for Dragsim staff, with Staff Panel from Chat on.").send();
			return 1;
		}
		// Opened from a click in chat, the chat screen is still up; the panel replaces it next tick.
		net.minecraft.client.Minecraft.getInstance().execute(() -> StaffScreen.open(player));
		return 1;
	}
}
