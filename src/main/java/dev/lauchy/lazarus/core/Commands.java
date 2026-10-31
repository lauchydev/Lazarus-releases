package dev.lauchy.lazarus.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client commands contributed by features, registered together. */
public final class Commands {
	private static final List<Supplier<LiteralArgumentBuilder<FabricClientCommandSource>>> ROOTS = new ArrayList<>();

	private Commands() {
	}

	public static void add(Supplier<LiteralArgumentBuilder<FabricClientCommandSource>> command) {
		ROOTS.add(command);
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
			for (var root : ROOTS) dispatcher.register(root.get());
		});
	}
}
