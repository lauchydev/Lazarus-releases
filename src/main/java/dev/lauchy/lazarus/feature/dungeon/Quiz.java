package dev.lauchy.lazarus.feature.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Chat;
import dev.lauchy.lazarus.core.Config;
import dev.lauchy.lazarus.core.Entities;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Message;
import dev.lauchy.lazarus.core.Shared;
import dev.lauchy.lazarus.core.World;
import dev.lauchy.lazarus.setting.BoolSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Quiz room, answered from your own book. Nothing is bundled: when you press an
 * answer's button and the server says you answered correctly, that question's answer is
 * saved to config/lazarus/quiz.json, and a wrong answer is remembered as ruled out. Next
 * time the question comes up its answer is named in chat and its pillar boxed; with two
 * of three ruled out, the last one is given by elimination. Answers and ruled-out options are
 * shared through the Lazarus server, so the book holds what every Lazarus user has learned.
 */
public final class Quiz extends Feature {
	private static final String PUZZLE = "Quiz";
	private static final Path FILE = Config.dir().resolve("quiz.json");
	private static final Pattern QUESTION = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)\\s*(.+)$");
	private static final Pattern OPTION = Pattern.compile("^([ABC])\\s+(.+)$");
	private static final double RANGE = 20;

	private static final class Entry {
		String answer;
		final Set<String> wrong = new LinkedHashSet<>();
	}

	private final BoolSetting say = add(new BoolSetting("say", "Say the answer", "Name the answer in chat for each question.", true));
	private final BoolSetting box = add(new BoolSetting("box", "Box the answer", "Outline the answer's pillar.", true));

	private final Map<String, Entry> book = new HashMap<>();
	private boolean active;
	private String question, said, pending;
	private final Map<String, Entity> options = new HashMap<>();
	private BlockPos mark;
	private int ticks;

	public Quiz() {
		super("quiz", "Quiz", "Learns quiz answers as you get them right, then gives them.", Category.DUNGEONS, true);
	}

	@Override
	public void init() {
		load();
		Shared.follow("quiz", Duration.ofMinutes(30), got -> {
			got.forEach((q, e) -> {
				Entry entry = entry(q);
				JsonObject v = e.value();
				if (v.has("answer")) entry.answer = v.get("answer").getAsString();
				if (v.get("wrong") instanceof JsonArray wrong) for (JsonElement w : wrong) entry.wrong.add(w.getAsString());
			});
			save();
		});
		Chat.listen(l -> {
			String line = l.text();
			Minecraft mc = Minecraft.getInstance();
			if (Puzzles.entered(line, PUZZLE)) {
				active = true;
				question = said = pending = null;
				return;
			}
			if (Puzzles.left(line, PUZZLE) && !line.contains("answered a quiz question wrong")) {
				active = false;
				mark = null;
				return;
			}
			if (!active || mc.player == null || question == null || pending == null) return;
			String me = mc.player.getName().getString();
			if (line.equals(me + " answered correctly!")) {
				entry(question).answer = pending;
				JsonObject v = new JsonObject();
				v.addProperty("answer", pending);
				Shared.send("quiz", question, v);
				pending = null;
				save();
			} else if (line.startsWith("PUZZLE FAIL!") && line.contains(me) && line.contains("answered a quiz question wrong")) {
				entry(question).wrong.add(pending);
				JsonObject v = new JsonObject();
				JsonArray wrong = new JsonArray();
				entry(question).wrong.forEach(wrong::add);
				v.add("wrong", wrong);
				Shared.send("quiz", question, v);
				pending = null;
				save();
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (enabled() && active && level.isClientSide() && level.getBlockState(hit.getBlockPos()).getBlock() instanceof ButtonBlock) {
				String option = nearestOption(Vec3.atCenterOf(hit.getBlockPos()));
				if (option != null) pending = option;
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!enabled() || !active || mc.level == null || mc.player == null || ++ticks % 5 != 0) return;
			read(mc);
			String answer = question == null ? null : answer(question);
			Entity label = answer == null ? null : options.get(answer);
			mark = label == null ? null : label.blockPosition().below();
			if (question != null && say.on() && !question.equals(said)) {
				said = question;
				Message m = Message.lazarus().info("Quiz: ");
				(answer != null ? m.success(answer) : m.warning("Not learned yet. Lazarus will remember your answer.")).send();
			}
		});
		World.paint(() -> {
			if (enabled() && box.on() && mark != null) World.block(mark, 0xFF55FF55, true, false);
		});
	}

	/** The question hologram and the three answer labels near you. */
	private void read(Minecraft mc) {
		options.clear();
		String q = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Player || e.distanceTo(mc.player) > RANGE) continue;
			String n = Entities.name(e).replaceAll("\\s+", " ");
			Matcher m;
			if ((m = QUESTION.matcher(n)).matches()) q = normal(m.group(3));
			else if ((m = OPTION.matcher(n)).matches()) options.put(m.group(2).trim(), e);
		}
		if (q != null) question = q;
	}

	/** The known answer, or the only option not yet ruled out. */
	private String answer(String q) {
		Entry e = book.get(q);
		if (e == null) return null;
		if (e.answer != null) return e.answer;
		List<String> left = new ArrayList<>(options.keySet());
		left.removeAll(e.wrong);
		return options.size() == 3 && left.size() == 1 ? left.getFirst() : null;
	}

	private String nearestOption(Vec3 button) {
		String best = null;
		double bestD = 36;
		for (var o : options.entrySet()) {
			double d = o.getValue().position().distanceToSqr(button);
			if (d < bestD) {
				bestD = d;
				best = o.getKey();
			}
		}
		return best;
	}

	private Entry entry(String q) {
		return book.computeIfAbsent(q, k -> new Entry());
	}

	private static String normal(String q) {
		return q.replaceAll("[.?!]+$", "").trim().toLowerCase(Locale.ROOT);
	}

	private void load() {
		JsonElement json = Config.readJson(FILE);
		if (json == null || !json.isJsonObject()) return;
		for (var e : json.getAsJsonObject().entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			Entry entry = entry(e.getKey());
			if (o.has("answer")) entry.answer = o.get("answer").getAsString();
			if (o.has("wrong")) for (JsonElement w : o.getAsJsonArray("wrong")) entry.wrong.add(w.getAsString());
		}
	}

	private void save() {
		JsonObject root = new JsonObject();
		book.forEach((q, e) -> {
			JsonObject o = new JsonObject();
			if (e.answer != null) o.addProperty("answer", e.answer);
			JsonArray wrong = new JsonArray();
			e.wrong.forEach(wrong::add);
			o.add("wrong", wrong);
			root.add(q, o);
		});
		Config.writeJson(FILE, root);
	}
}
