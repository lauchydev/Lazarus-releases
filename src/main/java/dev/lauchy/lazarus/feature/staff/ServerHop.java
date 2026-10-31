package dev.lauchy.lazarus.feature.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Players;
import dev.lauchy.lazarus.setting.TextSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;

/**
 * Moving between Dragsim's servers in one click: {@code /lazarus hop} (or a key) puts a row of server
 * buttons in chat, each sending you there with the server's {@code /send}, and {@code /lazarus hop <server>}
 * goes straight away. The server you're on is marked.
 */
public final class ServerHop extends Feature {
	private final TextSetting servers = add(new TextSetting("servers", "Servers", "The servers on the card, comma separated, as /send names them.", "hub, end, rift, dungeons, crypts, limbo", 120));

	public ServerHop() {
		super("serverHop", "Server Hop", "/lazarus hop: one click sends you to any Dragsim server.", Category.STAFF, true);
	}

	@Override
	public void init() {
		Commands.add(() -> ClientCommands.literal("lazarus").then(ClientCommands.literal("hop").executes(c -> {
			card(servers(), Area.server(), Players.me()).send();
			return 1;
		}).then(ClientCommands.argument("server", StringArgumentType.word()).executes(c -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.connection.sendCommand(command(Players.me(), StringArgumentType.getString(c, "server")).substring(1));
			return 1;
		}))));
	}

	private List<String> servers() {
		return Arrays.stream(servers.get().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	/** The command that moves {@code me} to {@code server}, with its slash. */
	public static String command(String me, String server) {
		return "/send " + me + " " + server;
	}

	/** The row of server buttons, the one you're on marked and not a button. */
	static Message card(List<String> servers, String here, String me) {
		Message m = Message.lazarus().heading("Go to").info("  ");
		for (String s : servers) {
			if (s.equalsIgnoreCase(here)) m.muted(s + " (here)").info("  ");
			else m.command(s, "Click to go to " + s, command(me, s)).info("  ");
		}
		return m;
	}
}
