package dev.lauchy.lazarus.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Client commands contributed by features, registered together, with a short name for /lazarus if one is set. */
public final class Commands {
	/** The command every feature hangs its subcommands on. */
	public static final String ROOT = "lazarus";
	private static final List<Supplier<LiteralArgumentBuilder<FabricClientCommandSource>>> ROOTS = new ArrayList<>();
	private static Supplier<String> alias = () -> "";

	private Commands() {
	}

	public static void add(Supplier<LiteralArgumentBuilder<FabricClientCommandSource>> command) {
		ROOTS.add(command);
	}

	/** Where the short name for /lazarus comes from (a setting); read each time the commands are registered, on joining. */
	public static void alias(Supplier<String> shortName) {
		alias = shortName;
	}

	/**
	 * A typed short name as a command name: lower case, without the slash, letters, digits, _ and - only, and
	 * never "lazarus" itself; "" when it's nothing usable. So "/LZ" is "lz" and "la zarus" is "".
	 */
	public static String aliasName(String typed) {
		String name = typed == null ? "" : typed.trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
		return name.matches("[a-z0-9_-]{1,16}") && !name.equals(ROOT) ? name : "";
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerAll(dispatcher));
	}

	/** Every feature's commands into {@code dispatcher}, then the short name for /lazarus if one is set. */
	public static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		for (var root : ROOTS) dispatcher.register(root.get());
		registerAlias(dispatcher, aliasName(alias.get()));
	}

	/** /&lt;name&gt; as another way in to /lazarus: every subcommand and its completions, and /lazarus alone. */
	private static void registerAlias(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		CommandNode<FabricClientCommandSource> lazarus = dispatcher.getRoot().getChild(ROOT);
		if (name.isEmpty() || lazarus == null || dispatcher.getRoot().getChild(name) != null) return;
		LiteralArgumentBuilder<FabricClientCommandSource> shortcut = literal(name).redirect(lazarus);
		if (lazarus.getCommand() != null) shortcut.executes(lazarus.getCommand());
		dispatcher.register(shortcut);
	}
}
