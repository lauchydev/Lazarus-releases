package dev.lauchy.lazarus.feature.dungeon;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three Weirdos: three NPCs each make one statement about whose chest holds the reward,
 * and exactly one of them is lying. Once all three have spoken, every chest is tried
 * against every choice of liar; the one chest that fits is named in chat and boxed.
 */
public final class ThreeWeirdos extends Feature {
	private static final String PUZZLE = "Three Weirdos";
	private static final Pattern NPC = Pattern.compile("^\\[NPC] ([A-Za-z][A-Za-z0-9_ -]{0,31}): (.+)$");
	private static final String WHO = "([a-z][a-z0-9_ -]{0,31}?)";
	private static final Pattern THEIRS = Pattern.compile("^(?:whatever you do, )?" + WHO + "'s chest is (not )?the one$");
	private static final Pattern IN_THEIRS = Pattern.compile("^the (?:reward|treasure) (is|is not|isn't) in " + WHO + "'s chest$");
	private static final Pattern DONT_TRUST = Pattern.compile("^don't trust " + WHO + ", (?:they|he|she)(?:'re|'s| are| is) lying$");
	private static final Pattern ABOUT = Pattern.compile("^" + WHO + " (is lying|is telling the truth|tells the truth)$");

	/** One statement: "the reward is (not) in X's chest" or "X is (not) lying". */
	private record Claim(boolean aboutChest, String subject, boolean positive) {
	}

	private final BoolSetting say = add(new BoolSetting("say", "Say the answer", "Name the chest in chat.", true));
	private final BoolSetting box = add(new BoolSetting("box", "Box the chest", "Outline the right chest.", true));

	private final Map<String, Claim> claims = new LinkedHashMap<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private boolean active;
	private String answer;
	private BlockPos chest;

	public ThreeWeirdos() {
		super("threeWeirdos", "Three Weirdos", "Works out which NPC is lying and marks the right chest.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		Chat.listen(l -> {
			if (!enabled()) return;
			String line = l.text();
			if (Puzzles.entered(line, PUZZLE)) {
				reset();
				active = true;
				return;
			}
			if (Puzzles.left(line, PUZZLE) || line.matches("^You picked .+'s chest\\. Correct!$")) {
				reset();
				return;
			}
			Matcher m = NPC.matcher(line);
			if (!active || !m.matches()) return;
			String speaker = m.group(1).trim(), key = speaker.toLowerCase(Locale.ROOT);
			if (claims.containsKey(key) || claims.size() >= 3) return;
			Claim c = parse(key, normal(m.group(2)));
			if (c == null) return;
			claims.put(key, c);
			names.put(key, speaker);
			if (claims.size() == 3) solve();
		});
		World.paint(() -> {
			if (!enabled() || !box.on() || answer == null) return;
			if (chest == null) chest = findChest(names.get(answer));
			if (chest != null) World.block(chest, 0xFF55FF55, true, true);
		});
	}

	private void reset() {
		active = false;
		claims.clear();
		names.clear();
		answer = null;
		chest = null;
	}

	private void solve() {
		List<String> who = new ArrayList<>(claims.keySet());
		String found = null;
		for (String owner : who) {
			for (String liar : who) {
				boolean fits = true;
				for (String speaker : who) {
					if (claimTrue(claims.get(speaker), owner, liar) != !speaker.equals(liar)) {
						fits = false;
						break;
					}
				}
				if (!fits) continue;
				if (found != null && !found.equals(owner)) return; // two chests fit: don't guess
				found = owner;
			}
		}
		answer = found;
		if (found != null && say.on()) Message.lazarus().info("Three Weirdos: open ").success(names.get(found)).info("'s chest.").send();
	}

	private static boolean claimTrue(Claim c, String owner, String liar) {
		boolean fact = c.aboutChest ? owner.equals(c.subject) : liar.equals(c.subject);
		return fact == c.positive;
	}

	/** aboutChest: positive means "the reward is in subject's chest"; otherwise positive means "subject is lying". */
	private static Claim parse(String self, String text) {
		if (text.matches("^the (?:reward|treasure) is in my chest$") || text.matches("^my chest (?:has|contains) the (?:reward|treasure)$"))
			return new Claim(true, self, true);
		if (text.matches("^the (?:reward|treasure) (?:is not|isn't) in my chest$") || text.matches("^my chest is empty(?:, i promise)?$"))
			return new Claim(true, self, false);
		Matcher m;
		if ((m = THEIRS.matcher(text)).matches()) return new Claim(true, m.group(1), m.group(2) == null);
		if ((m = IN_THEIRS.matcher(text)).matches()) return new Claim(true, m.group(2), m.group(1).equals("is"));
		if ((m = DONT_TRUST.matcher(text)).matches()) return new Claim(false, m.group(1), true);
		if ((m = ABOUT.matcher(text)).matches()) return new Claim(false, m.group(1), m.group(2).equals("is lying"));
		return null;
	}

	private static String normal(String s) {
		return s.replace('’', '\'').replaceAll("\\s+", " ").replaceAll("[.!]+$", "").trim().toLowerCase(Locale.ROOT);
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
