package dev.lauchy.lazarus.gametest;

import com.mojang.authlib.GameProfile;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Commands;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.cosmetic.TabBadges;
import dev.lauchy.lazarus.feature.staff.StaffDeskSamples;
import dev.lauchy.lazarus.hud.HudEditorScreen;
import dev.lauchy.lazarus.hud.WelcomeToast;
import dev.lauchy.lazarus.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Boots into a world and opens every Lazarus screen; any hook or render crash fails CI. */
public final class LazarusClientTest implements FabricClientGameTest {
	private static volatile boolean paintBox;

	@Override
	public void runTest(ClientGameTestContext context) {
		World.paint(() -> {
			if (!paintBox) return;
			Minecraft mc = Minecraft.getInstance();
			Vec3 at = mc.player.getEyePosition().add(mc.player.getViewVector(1f).scale(3));
			World.box(new AABB(at, at).inflate(1.5), 0xFFFF00FF, true, true, true);
		});
		// CI has no graphics card, so every frame is drawn on the processor: the game's smallest window, and no
		// menu blur, keep each screen to a fraction of a second. (runClient's 1080p window is for dev sessions.)
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(mc -> mc.options.menuBackgroundBlurriness().set(0));
		try (var world = context.worldBuilder().create()) {
			context.waitTicks(100); // let chunks load before the screens open
			Path before = context.takeScreenshot("lazarus-world");
			paintBox = true;
			context.waitTicks(5);
			Path after = context.takeScreenshot("lazarus-world-box");
			paintBox = false;
			if (!centreChanged(before, after)) throw new AssertionError("A World.box drawn in front of the camera did not show up");

			context.runOnClient(LazarusClientTest::shortLazarus);
			context.runOnClient(LazarusClientTest::tabBadge);
			context.runOnClient(LazarusClientTest::nameTagBadge);
			context.runOnClient(LazarusClientTest::floatingTagBadge);

			context.setScreen(SettingsScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("lazarus-settings");
			// Every page of the menu as a player without Lazarus+ or a staff rank sees it, with the features whose
			// settings matter most opened, so each can be looked over.
			for (Category page : Category.values()) {
				if (page.staffSection()) continue; // the test account isn't staff: the menu would fall back to The End
				String[] open = switch (page) {
					case GENERAL -> new String[]{"commandBinds", "petBinds", "wardrobeBinds", "autoClicker", "dropAlerts", "commandAliases", "ownNameTag"};
					case END -> new String[]{"zealotTracker", "cacheTracker"};
					case CHAT -> new String[]{"messageSound"};
					default -> new String[0];
				};
				context.setScreen(() -> new SettingsScreen(page, "", 0, open));
				context.waitTicks(5);
				context.takeScreenshot("lazarus-settings-" + page.name().toLowerCase(Locale.ROOT));
			}
			// The new features' settings, found by search so they're in view: the binds with their Lazarus+ slots, and the rest.
			String[] features = {"commandBinds", "petBinds", "wardrobeBinds", "autoClicker", "dropAlerts", "joinLines", "ownNameTag"};
			for (String search : new String[]{"bind", "clicker", "drop", "join", "name tag"}) {
				context.setScreen(() -> new SettingsScreen(Category.GENERAL, search, 0, features));
				context.waitTicks(5);
				context.takeScreenshot("lazarus-search-" + search.replace(' ', '-'));
			}
			// Command Binds scrolled past the mod's own binds to the free ones and the first Lazarus+ one.
			context.setScreen(() -> new SettingsScreen(Category.GENERAL, "bind", 330, features));
			context.waitTicks(5);
			context.takeScreenshot("lazarus-search-bind-free");
			// The staff desk's main screens, from made-up players (the test account isn't staff): each tab, a card's
			// details and the ban dialog, so a desk that throws or draws nothing is caught.
			for (String desk : StaffDeskSamples.ALL) {
				context.setScreen(() -> StaffDeskSamples.desk(desk));
				context.waitTicks(5);
				context.takeScreenshot("lazarus-staff-desk-" + desk);
			}
			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("lazarus-hud-editor");
			context.setScreen(() -> null);
			context.runOnClient(mc -> WelcomeToast.show(mc, true, mc.getUser().getName(), "Right Shift"));
			context.waitTicks(15);
			context.takeScreenshot("lazarus-welcome");
			context.waitTicks(20);
		}
	}

	/**
	 * A short name for /lazarus reaches every Lazarus command under it: "/test online" parses through to /lazarus's
	 * online command. Registered here as joining a world would, with the name the Lazarus Command setting gives.
	 */
	private static void shortLazarus(Minecraft mc) {
		Commands.alias(() -> "test");
		var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
		Commands.registerAll(dispatcher);
		for (String typed : new String[]{"test online", "test changelog", "lazarus online", "lazarus index firesale", "lazarus index firesale stop",
				"lazarus index recipes", "test index recipes stop"}) {
			var parsed = dispatcher.parse(typed, null);
			if (parsed.getReader().canRead()) throw new AssertionError("/" + typed + " doesn't reach a Lazarus command");
			if (parsed.getContext().getLastChild().getCommand() == null) throw new AssertionError("/" + typed + " stops short of a command");
		}
		if (dispatcher.getRoot().getChild("lz") != null) throw new AssertionError("/lz is still registered beside the short name set");
		Commands.alias(() -> "");
	}

	/** Your own tab list entry gets the Lazarus badge, drawn from the badge font rather than a missing glyph. */
	private static void tabBadge(Minecraft mc) {
		// The badge goes on getNameForDisplay's answer, so the tab list must still draw names from it.
		try (InputStream in = PlayerTabOverlay.class.getClassLoader().getResourceAsStream("net/minecraft/client/gui/components/PlayerTabOverlay.class")) {
			if (in == null) throw new AssertionError("Could not read PlayerTabOverlay's bytecode");
			boolean draws = ClassFile.of().parse(in.readAllBytes()).methods().stream()
					.filter(m -> m.methodName().equalsString("extractRenderState"))
					.flatMap(m -> m.code().stream()).flatMap(code -> code.elementStream())
					.anyMatch(e -> e instanceof InvokeInstruction call && call.name().equalsString("getNameForDisplay"));
			if (!draws) throw new AssertionError("The tab list no longer draws names from getNameForDisplay");
		} catch (IOException e) {
			throw new AssertionError("Could not read PlayerTabOverlay's bytecode", e);
		}
		PlayerInfo me = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (me == null) throw new AssertionError("The player has no tab list entry");
		String shown = mc.gui.getTabList().getNameForDisplay(me).getString();
		if (!shown.startsWith(TabBadges.LAZARUS.getString()) && !shown.startsWith(TabBadges.PLUS.getString())) {
			throw new AssertionError("The tab list badge is missing: " + shown);
		}
		int badge = mc.font.width(TabBadges.LAZARUS), missing = mc.font.width(TabBadges.LAZARUS.getString());
		if (badge == missing) throw new AssertionError("The badge font didn't load (glyph width " + badge + ")");
	}

	/** A Lazarus user's name tag gets the same badge: a stand-in on your account, three blocks in front of you. */
	private static void nameTagBadge(Minecraft mc) {
		RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(mc.player.getUUID(), "LazarusTest"));
		Vec3 at = mc.player.position().add(mc.player.getViewVector(1f).scale(3));
		other.setPos(at.x, at.y, at.z);
		EntityRenderer<? super RemotePlayer, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(other);
		EntityRenderState state = renderer.createRenderState(other, 1f);
		if (state.nameTag == null) throw new AssertionError("A player three blocks away has no name tag");
		String shown = state.nameTag.getString();
		if (!shown.startsWith(TabBadges.LAZARUS.getString()) && !shown.startsWith(TabBadges.PLUS.getString())) {
			throw new AssertionError("The name tag badge is missing: " + shown);
		}
	}

	/** A server's own name tag (a text display naming you, over your head) gets the badge; one across the room doesn't. */
	private static void floatingTagBadge(Minecraft mc) {
		String name = mc.player.getGameProfile().name();
		Display.TextDisplay over = tag(mc, "[Lv 12] " + name, 2.3);
		Display.TextDisplay away = tag(mc, "[Lv 12] " + name, 10);
		TabBadges.INSTANCE.floatingTags(List.of(over, away), List.of(mc.player));
		String shown = over.getEntityData().get(Display.TextDisplay.DATA_TEXT_ID).getString();
		if (!shown.startsWith(TabBadges.LAZARUS.getString()) && !shown.startsWith(TabBadges.PLUS.getString())) {
			throw new AssertionError("The floating name tag badge is missing: " + shown);
		}
		String far = away.getEntityData().get(Display.TextDisplay.DATA_TEXT_ID).getString();
		if (!far.startsWith("[Lv 12]")) throw new AssertionError("A tag ten blocks above the player got a badge: " + far);
	}

	private static Display.TextDisplay tag(Minecraft mc, String text, double above) {
		Display.TextDisplay tag = new Display.TextDisplay(EntityType.TEXT_DISPLAY, mc.level);
		tag.setPos(mc.player.getX(), mc.player.getY() + above, mc.player.getZ());
		tag.getEntityData().set(Display.TextDisplay.DATA_TEXT_ID, Component.literal(text));
		return tag;
	}

	/** Whether the middle of the frame looks different, i.e. the box was drawn over it. */
	private static boolean centreChanged(Path a, Path b) {
		try {
			BufferedImage x = ImageIO.read(a.toFile()), y = ImageIO.read(b.toFile());
			int cx = x.getWidth() / 2, cy = x.getHeight() / 2, diff = 0;
			for (int dx = -10; dx <= 10; dx++) {
				for (int dy = -10; dy <= 10; dy++) {
					int p = x.getRGB(cx + dx, cy + dy), q = y.getRGB(cx + dx, cy + dy);
					diff += Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
				}
			}
			return diff > 21 * 21 * 20;
		} catch (IOException e) {
			throw new AssertionError("Could not read the screenshots", e);
		}
	}
}
