package dev.lauchy.lazarus.gametest;

import com.mojang.authlib.GameProfile;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.feature.cosmetic.TabBadges;
import dev.lauchy.lazarus.hud.HudEditorScreen;
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
		try (var world = context.worldBuilder().create()) {
			context.waitTicks(100); // let chunks load before the screens open
			Path before = context.takeScreenshot("lazarus-world");
			paintBox = true;
			context.waitTicks(5);
			Path after = context.takeScreenshot("lazarus-world-box");
			paintBox = false;
			if (!centreChanged(before, after)) throw new AssertionError("A World.box drawn in front of the camera did not show up");

			context.runOnClient(LazarusClientTest::tabBadge);
			context.runOnClient(LazarusClientTest::nameTagBadge);
			context.runOnClient(LazarusClientTest::floatingTagBadge);

			context.setScreen(SettingsScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("lazarus-settings");
			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("lazarus-hud-editor");
			context.setScreen(() -> null);
			context.waitTicks(20);
		}
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
