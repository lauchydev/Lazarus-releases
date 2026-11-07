package dev.lauchy.lazarus;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.lauchy.lazarus.core.Access;
import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Catalog;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Features;
import dev.lauchy.lazarus.core.JoinMessages;
import dev.lauchy.lazarus.core.LazarusServer;
import dev.lauchy.lazarus.core.LootFeed;
import dev.lauchy.lazarus.core.LiveConnection;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Plus;
import dev.lauchy.lazarus.hud.Hud;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.hud.WelcomeToast;
import dev.lauchy.lazarus.ui.Keys;
import dev.lauchy.lazarus.ui.SettingsScreen;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Lazarus implements ClientModInitializer {
	public static final String ID = "lazarus";
	public static final Logger LOG = LoggerFactory.getLogger("Lazarus");
	/**
	 * Kept in the compiled class (and printed to the log at start) so the terms travel with the
	 * jar: comments don't survive compiling, this does. The full text is LICENSE_lazarus in the jar.
	 */
	public static final String NOTICE = "Lazarus (c) 2026 lauchydev. All rights reserved. Proprietary and confidential: licensed to every player"
			+ " who uses it, for their own private use with the Dragon Simulator server. Copying, redistributing, decompiling, reverse engineering (by any tool or"
			+ " service, AI assistants included), modifying, bypassing its access checks or reproducing its code or features"
			+ " without the author's written permission is prohibited. See LICENSE_lazarus in this jar.";

	/** A real KeyMapping so it can be rebound in Controls; polled so it works from any screen state. */
	private static final KeyMapping MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lazarus.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "lazarus"))));

	private boolean menuWasDown;
	private final Set<Integer> held = new HashSet<>();

	@Override
	public void onInitializeClient() {
		LOG.info(NOTICE);
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			start();
			return;
		}
		// Everyone with the mod runs it; only an account the admins blocked doesn't. The account is only known once the client has started.
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			if (Access.blocked()) {
				session(false);
				blockedCommand();
			} else {
				start();
			}
		});
	}

	/** How long after joining the welcome comes, so it lands after the server's own join lines rather than lost above them. */
	private static final long WELCOME_DELAY_MS = 1500;

	/**
	 * Says on the first world or server join of every game start where this account stands with Lazarus: welcomed,
	 * or blocked, in chat and as a card in the top right, a moment after the server has said its piece.
	 */
	private static void session(boolean ok) {
		boolean[] said = {false};
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			if (said[0]) return;
			said[0] = true;
			CompletableFuture.delayedExecutor(WELCOME_DELAY_MS, TimeUnit.MILLISECONDS).execute(() -> client.execute(() -> {
				if (client.player == null) return; // left again before the welcome was due
				String name = client.getUser().getName(), menuKey = MENU_KEY.getTranslatedKeyMessage().getString();
				Message.send(ok ? JoinMessages.welcome(name, menuKey, Plus.active() ? Plus.leftText() : null, Boolean.FALSE.equals(LazarusServer.discordLinked()))
						: JoinMessages.blocked());
				if (ok && Access.keyTaken()) Message.send(JoinMessages.keyTaken());
				WelcomeToast.show(client, ok, name, menuKey);
			}));
		}));
	}

	/** All a blocked account gets: {@code /lazarus}, which says it's blocked. Nothing else of Lazarus starts. */
	private static void blockedCommand() {
		Commands.add(() -> ClientCommands.literal("lazarus").executes(c -> {
			Message.send(JoinMessages.blocked());
			return 1;
		}));
		Commands.init();
	}

	private void start() {
		session(true);
		FeatureList.registerAll();
		Area.init();
		Catalog.init();
		Chat.init();
		LootFeed.init();
		World.init();
		Hud.init();
		Plus.init();
		Config.load();
		for (Feature f : Features.all()) {
			try {
				f.init();
			} catch (RuntimeException e) {
				LOG.error("Feature {} failed to start", f.id, e);
			}
		}
		LiveConnection.start();

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());
		// /lazarus only: a short name for it is the Lazarus Command setting, which reaches every subcommand.
		for (String name : new String[]{"lazarus"}) {
			Commands.add(() -> ClientCommands.literal(name).executes(c -> {
				Minecraft mc = Minecraft.getInstance();
				mc.schedule(() -> mc.setScreen(new SettingsScreen()));
				return 1;
			}).then(ClientCommands.literal("changelog").executes(c -> {
				changelog();
				return 1;
			})).then(ClientCommands.literal("upgrade").executes(c -> {
				Plus.upgrade();
				return 1;
			})).then(plusCommand()).then(ClientCommands.literal("discord").executes(c -> {
				LazarusServer.discord();
				return 1;
			})).then(accessCommand()));
		}
		Commands.init();
		LOG.info("Lazarus loaded {} features", Features.all().size());
	}

	/** {@code /lazarus access block|unblock|resetkey <player>}: the owner's tools for who may run Lazarus and whose key stands. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> accessCommand() {
		LiteralArgumentBuilder<FabricClientCommandSource> access = ClientCommands.literal("access").requires(src -> LazarusServer.admin());
		for (String action : new String[]{"block", "unblock", "resetkey"}) {
			access.then(ClientCommands.literal(action).then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
				LazarusServer.access(action, StringArgumentType.getString(c, "player"));
				return 1;
			})));
		}
		return access;
	}

	/** {@code /lazarus plus}: your Lazarus+ in chat, the site's manage page, and the owner's tools. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> plusCommand() {
		return ClientCommands.literal("plus").executes(c -> {
			Plus.status();
			return 1;
		}).then(ClientCommands.literal("manage").executes(c -> {
			Plus.manage();
			return 1;
		})).then(ClientCommands.literal("grant").requires(src -> LazarusServer.admin())
				.then(ClientCommands.argument("player", StringArgumentType.word())
						.then(ClientCommands.argument("days", IntegerArgumentType.integer(1, 365)).executes(c -> {
							LazarusServer.plusAdmin("grant", StringArgumentType.getString(c, "player"), IntegerArgumentType.getInteger(c, "days"));
							return 1;
						})))).then(ClientCommands.literal("remove").requires(src -> LazarusServer.admin())
				.then(ClientCommands.argument("player", StringArgumentType.word())
						.then(ClientCommands.argument("days", IntegerArgumentType.integer(1, 365)).executes(c -> {
							LazarusServer.plusAdmin("remove", StringArgumentType.getString(c, "player"), IntegerArgumentType.getInteger(c, "days"));
							return 1;
						})))).then(ClientCommands.literal("revoke").requires(src -> LazarusServer.admin())
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					LazarusServer.plusAdmin("revoke", StringArgumentType.getString(c, "player"), 0);
					return 1;
				}))).then(plusLookup("lookup")).then(plusLookup("check")).then(ClientCommands.literal("giftall").requires(src -> LazarusServer.admin())
				// The gift ID makes it safe to run again: the same ID only fills in accounts it missed.
				.then(ClientCommands.argument("gift", StringArgumentType.word())
						.then(ClientCommands.argument("days", IntegerArgumentType.integer(1, 365)).executes(c -> {
							LazarusServer.plusAdmin("giftAll", StringArgumentType.getString(c, "gift"), IntegerArgumentType.getInteger(c, "days"));
							return 1;
						})))).then(ClientCommands.literal("unblacklist").requires(src -> LazarusServer.admin())
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					LazarusServer.plusAdmin("unblacklist", StringArgumentType.getString(c, "player"), 0);
					return 1;
				}))).then(ClientCommands.literal("unblacklistemail").requires(src -> LazarusServer.admin())
				// An email has an @, which a single word can't hold, so it takes the rest of the line.
				.then(ClientCommands.argument("email", StringArgumentType.greedyString()).executes(c -> {
					LazarusServer.plusAdmin("unblacklistEmail", StringArgumentType.getString(c, "email"), 0);
					return 1;
				})));
	}

	/** {@code /lazarus plus lookup <player>}, or {@code check}, its other name: the owner's look at a player's Lazarus+. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> plusLookup(String literal) {
		return ClientCommands.literal(literal).requires(src -> LazarusServer.admin())
				.then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
					LazarusServer.plusAdmin("status", StringArgumentType.getString(c, "player"), 0);
					return 1;
				}));
	}

	/** Prints what this version changed, from lazarus-changelog.txt in the jar. */
	private static void changelog() {
		String version = FabricLoader.getInstance().getModContainer(ID).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		List<String> lines;
		try (InputStream in = Lazarus.class.getResourceAsStream("/lazarus-changelog.txt")) {
			lines = in == null ? List.of() : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
					.filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
		} catch (IOException e) {
			lines = List.of();
		}
		Message m = Message.lazarus().heading("What's new in " + version);
		if (lines.isEmpty()) m.line().info("Nothing is listed for this build.");
		// Grouped by what kind of change it is: "+" lines are new, "*" improved, "-" fixed. Each line is
		// "Feature: one sentence", and the feature stands out so a reader can skim for the ones they use.
		String[][] groups = {{"+", "New"}, {"*", "Improved"}, {"-", "Fixed"}};
		// A line without a marker is still shown, first and under no heading, rather than lost.
		for (String l : lines) {
			if (!l.startsWith("+") && !l.startsWith("*") && !l.startsWith("-")) m.line().muted("• ").info(l.trim());
		}
		for (String[] group : groups) {
			boolean started = false;
			for (String l : lines) {
				if (!l.startsWith(group[0])) continue;
				if (!started) m.line().line().muted(group[1]);
				started = true;
				String body = l.substring(1).trim();
				int colon = body.indexOf(": ");
				m.line().muted("• ");
				if (colon > 0) m.highlight(body.substring(0, colon)).info("  " + body.substring(colon + 2));
				else m.info(body);
			}
		}
		m.send();
	}

	private void tick(Minecraft mc) {
		if (mc.getWindow() == null) return;
		InputConstants.Key menu = KeyMappingHelper.getBoundKeyOf(MENU_KEY);
		boolean down = !MENU_KEY.isUnbound() && menu.getType() == InputConstants.Type.KEYSYM && Keys.down(menu.getValue());
		if (down && !menuWasDown && mc.screen == null && mc.level != null) mc.setScreen(new SettingsScreen());
		menuWasDown = down;

		// Feature toggle keys only fire in game, never while typing in a screen.
		if (mc.screen != null || mc.level == null) {
			held.clear();
			return;
		}
		for (Feature f : Features.all()) {
			int key = f.toggleKey().get();
			if (!f.toggleKey().bound() || !f.allowed()) continue; // a feature this player may not use (staff, the owner's own) has no key
			if (Keys.down(f.toggleKey())) {
				if (held.add(key)) {
					if (f.locked()) {
						Toast.show(f.name + " is part of Lazarus+", Theme.ACCENT);
						continue;
					}
					f.setEnabled(!f.enabledSetting().on());
					Toast.toggled(f.name, f.enabledSetting().on());
				}
			} else {
				held.remove(key);
			}
		}
	}
}
