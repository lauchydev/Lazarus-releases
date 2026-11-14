package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three Weirdos: three NPCs each say a line, and the reward is in the chest of the one who says a line only
 * the right one says ({@link Weirdos}). That NPC is named in chat and their chest boxed as soon as they speak. The
 * server never says you have entered the room, so every NPC line in a dungeon is listened to.
 */
public final class ThreeWeirdos extends Feature {
	private static final Pattern NPC = Pattern.compile("^\\[NPC] ([A-Za-z][A-Za-z0-9_ -]{0,31}): (.+)$");
	private final BoolSetting say = add(new BoolSetting("say", "Say the answer", "Name the chest in chat.", true));
	private final BoolSetting box = add(new BoolSetting("box", "Box the chest", "Outline the right chest.", true));

	/** The NPC with the reward, once one of them has said a line only that one says; and their chest. */
	private String answer;
	private BlockPos chest;

	public ThreeWeirdos() {
		super("threeWeirdos", "Three Weirdos", "Marks the chest of the NPC who says the line only the right one says.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (!enabled() || !Area.inDungeon()) return;
			String line = l.text().replaceAll("\\s*\\(x\\d+\\)\\s*$", "").replaceAll("\\s+x\\d+\\s*$", "");
			// Over when the server scores it, or says the room will reset.
			if (line.startsWith("PUZZLE SOLVED!") || line.startsWith("PUZZLE FAIL!") || line.startsWith("Three Weirdos will reset")) {
				answer = null;
				chest = null;
				return;
			}
			Matcher m = NPC.matcher(line);
			if (!m.matches() || !Weirdos.right(m.group(2))) return;
			String who = m.group(1).trim();
			if (who.equals(answer)) return;
			answer = who;
			chest = null;
			if (say.on()) say().info("Three Weirdos: open ").success(who).info("'s chest.").send();
		});
		World.paint(() -> {
			if (!enabled() || !box.on() || answer == null) return;
			if (chest == null) chest = findChest(answer);
			if (chest != null) World.block(chest, 0xFF55FF55, true, true);
		});
	}

	/** The chest beside the named NPC. */
	private static BlockPos findChest(String npc) {
		Minecraft mc = Minecraft.getInstance();
		if (npc == null || mc.level == null) return null;
		Entity body = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Player) && Entities.name(e).equals(npc) || e instanceof Player p && !p.equals(mc.player) && p.getName().getString().equals(npc)) {
				body = e;
				break;
			}
		}
		if (body == null) return null;
		BlockPos at = body.blockPosition(), best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 2, 3))) {
			if (!mc.level.getBlockState(p).is(Blocks.CHEST) && !mc.level.getBlockState(p).is(Blocks.TRAPPED_CHEST)) continue;
			double d = p.distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}
}
