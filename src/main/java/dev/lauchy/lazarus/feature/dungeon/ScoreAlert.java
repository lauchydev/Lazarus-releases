package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.IntSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Tells you the moment a dungeon run's score reaches the score you're after (300 unless you change it): a line in
 * your chat, a toast, and (unless you switch it off) a title across the screen with a sound, once a run. The score is the one the sidebar shows beside how much is cleared. A message
 * or command of your own can be sent for you at the same moment, to tell your party.
 */
public final class ScoreAlert extends Feature {
	private final IntSetting target = add(new IntSetting("target", "Score", "The score that sets the alert off.", 300, 50, 500, ""));
	private final BoolSetting title = add(new BoolSetting("title", "Title and sound", "Show the score across the middle of the screen, with a sound, so it isn't missed in a fight.", true));
	private final TextSetting send = add(new TextSetting("send", "Also send", "A message or command sent for you when the score is reached, to tell your party. Empty sends nothing.", "", 100));

	private final RunScore run = new RunScore();
	private int ticks;

	public ScoreAlert() {
		super("scoreAlert", "Score Alert", "A title, a sound and a chat line when your dungeon run reaches 300 score.", Category.DUNGEONS, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// The sidebar is read again once a second, so that's as often as the score can change.
			if (mc.player == null || ++ticks % 20 != 0) return;
			boolean reached = run.reached(RunScore.read(Area.sidebar()), Area.inDungeon(), target.get());
			if (!reached || !enabled()) return;
			say().success(target.get() + " score reached.").send();
			Toast.show("§f" + target.get() + " score §7reached", Theme.GOOD);
			if (title.on()) {
				mc.gui.setTimes(0, 50, 10);
				mc.gui.setSubtitle(Component.literal("§7score reached"));
				mc.gui.setTitle(Component.literal("§a" + target.get()));
				mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
			}
			String text = send.get().trim();
			if (text.isEmpty() || mc.getConnection() == null) return;
			if (text.startsWith("/")) mc.getConnection().sendCommand(text.substring(1));
			else mc.getConnection().sendChat(text);
		});
	}
}
