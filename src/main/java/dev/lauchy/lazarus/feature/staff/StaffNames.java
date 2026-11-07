package dev.lauchy.lazarus.feature.staff;

import dev.lauchy.lazarus.core.Text;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * What the staff features add after a name in the tab list and over a player's head: the real name of
 * a nicked player ({@link NickReveal}) and the ⚠ of a watched one ({@link WatchList}). Called by the
 * badge mixins after the badge.
 */
public final class StaffNames {
	private StaffNames() {
	}

	/** A tab list name with the staff marks after it. */
	public static Component tab(PlayerInfo info, Component name) {
		return decorate(name);
	}

	/** A name tag with the staff marks after it. */
	public static Component tag(Player player, Component tag) {
		return decorate(tag);
	}

	private static Component decorate(Component text) {
		if (text == null) return null;
		NickReveal nicks = NickReveal.INSTANCE;
		WatchList watch = WatchList.INSTANCE;
		boolean reveal = nicks != null && nicks.revealsNames(), mark = watch != null && watch.marksNames();
		if (!reveal && !mark) return text;
		String plain = Text.plain(text);
		if (plain.isEmpty()) return text;
		Component out = text;
		if (reveal) {
			String real = NickReveal.realIn(plain);
			if (real != null) out = Component.empty().append(out).append(Component.literal(" (" + real + ")").withColor(0x8C8C9A));
		}
		if (mark && watch.namedIn(plain) != null) out = Component.empty().append(out).append(Component.literal(WatchList.MARK).withColor(0xFF6B6B));
		return out;
	}
}
