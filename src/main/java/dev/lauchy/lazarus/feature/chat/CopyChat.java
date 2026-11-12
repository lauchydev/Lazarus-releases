package dev.lauchy.lazarus.feature.chat;

import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;

import java.util.List;

/** Right-click a chat line (with chat open) to copy the whole message it belongs to. */
public final class CopyChat extends Feature {
	private final BoolSetting keepColours = add(new BoolSetting("codes", "Keep colour codes", "Copy with § colour codes instead of plain text.", false));

	public CopyChat() {
		super("copyChat", "Copy Chat", "Right-click any chat line to copy it.", Category.CHAT, true);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) return;
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) ->
					!(enabled() && click.button() == 1 && copyAt(mc, click.x(), click.y())));
		});
	}

	/** Finds the row the way vanilla lays chat out: upward from 40px above the bottom. */
	private boolean copyAt(Minecraft mc, double x, double y) {
		ChatComponent chat = mc.gui.getChat();
		double scale = mc.options.chatScale().get();
		int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		int row = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40.0 - y) / (scale * lineHeight));
		if (row < 0 || row >= chat.getLinesPerPage()) return false;
		if (x < 0 || x > ChatComponent.getWidth(mc.options.chatWidth().get()) + 4) return false;
		int index = row + chat.chatScrollbarPos;
		List<GuiMessage.Line> lines = chat.trimmedMessages;
		if (index >= lines.size()) return false;
		String raw = lines.get(index).parent().content().getString();
		String text = keepColours.on() ? raw.trim() : Text.plain(raw);
		if (text.isEmpty()) return false;
		mc.keyboardHandler.setClipboard(text);
		Toast.show("Copied: " + (text.length() > 30 ? text.substring(0, 29) + "…" : text), Theme.ACCENT);
		return true;
	}
}
