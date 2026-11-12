package dev.lauchy.lazarus.feature.dungeon;

import dev.lauchy.lazarus.core.Area;
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
 * The Quiz room. The question and its three answers come in chat ({@link QuizChat}); the
 * answer is named there by its letter, from your own book first, and failing that from the
 * answers that are the same on every server of this kind; the block to click for it is
 * boxed. Your book learns: when an answer's button goes down, yours or a teammate's, and the
 * server says the question was answered correctly, that answer is saved to
 * config/lazarus/quiz.json, and a wrong one is remembered as ruled out; with two of three
 * ruled out, the last one is given by elimination. Answers and ruled-out options are
 * shared through the Lazarus server, so the book holds what every Lazarus user has learned.
 */
public final class Quiz extends Feature {
	private static final String PUZZLE = "Quiz";
	private static final Path FILE = Config.dir().resolve("quiz.json");
	private static final Pattern QUESTION = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)\\s*(.+)$");
	/** An answer's label in the room: "ⓐ 5 Fairy Souls". */
	private static final Pattern OPTION = Pattern.compile("([ⓐⓑⓒ])\\s*(.+)$");
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
	/** The question as chat is asking it, and the answer given for it, to find its label in the room. */
	private final QuizChat chat = new QuizChat();
	private String chatAnswer;
	/** The answer whose button was last seen go down, when, and the buttons down now. */
	private String pressed;
	private long pressedAt, askedAt;
	private final Set<BlockPos> down = new java.util.HashSet<>();
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
			if (enabled() && Area.inDungeon() && chat.read(line)) asked();
			// The server never says you've entered the room: the quiz is on wherever its question shows, and over when it says so.
			if (Puzzles.left(line, PUZZLE) && !line.contains("answered a quiz question wrong")) {
				question = said = pending = chatAnswer = null;
				mark = null;
				return;
			}
			// The answer just given, by you or a teammate: the button you pressed, or the one last seen go down.
			String chosen = pending != null ? pending : pressed != null && System.currentTimeMillis() - pressedAt < 5000 ? pressed : null;
			if (!active || mc.player == null || question == null || chosen == null) return;
			String me = mc.player.getName().getString();
			if (line.equals(me + " answered correctly!") || QuizChat.right(line) != null) {
				entry(question).answer = chosen;
				JsonObject v = new JsonObject();
				v.addProperty("answer", chosen);
				Shared.send("quiz", question, v);
				pending = pressed = null;
				save();
			} else if (line.contains("answered") && (line.contains("incorrectly") || line.contains("quiz question wrong"))) {
				entry(question).wrong.add(chosen);
				pressed = null;
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
			active = Area.inDungeon();
			if (!enabled() || !active || mc.level == null || mc.player == null || ++ticks % 5 != 0) return;
			// Only while a question is on: every label in the room is read to find the answers.
			if (System.currentTimeMillis() - askedAt > 90_000) {
				mark = null;
				return;
			}
			read(mc);
			String answer = question == null ? null : answer(question);
			Entity label = answer == null ? null : options.get(answer);
			// Asked in chat: the answer's label in the room, when it has one, is whatever bears its words.
			if (label == null && chatAnswer != null) {
				for (Entity e : mc.level.entitiesForRendering()) {
					if (!(e instanceof Player) && e.distanceTo(mc.player) <= RANGE && Entities.name(e).contains(chatAnswer)) label = e;
				}
			}
			mark = label == null ? null : button(mc, label);
			// A button going down under an answer is that answer given, whoever pressed it.
			for (var o : options.entrySet()) {
				BlockPos b = button(mc, o.getValue());
				boolean isDown = mc.level.getBlockState(b).getBlock() instanceof ButtonBlock && mc.level.getBlockState(b).getValue(ButtonBlock.POWERED);
				if (isDown && down.add(b)) {
					pressed = o.getKey();
					pressedAt = System.currentTimeMillis();
				} else if (!isDown) down.remove(b);
			}
			if (question != null && say.on() && !question.equals(said)) {
				said = question;
				Message m = say().info("Quiz: ");
				(answer != null ? m.success(answer) : m.warning("Not learned yet. Lazarus will remember your answer.")).send();
			}
		});
		World.paint(() -> {
			if (!enabled() || !box.on() || mark == null) return;
			// The button itself, not the whole block it hangs in: a little box where you click.
			Minecraft mc = Minecraft.getInstance();
			var shape = mc.level.getBlockState(mark).getShape(mc.level, mark);
			World.box(shape.isEmpty() ? new net.minecraft.world.phys.AABB(mark) : shape.bounds().move(mark).inflate(0.06), 0xFF55FF55, true, true, true);
		});
	}

	/** The block to click for an answer: the button nearest its label, or the block under the label when there's none by it. */
	private static BlockPos button(Minecraft mc, Entity label) {
		BlockPos at = label.blockPosition(), best = null;
		List<BlockPos> buttons = new ArrayList<>();
		double nearest = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -5, -3), at.offset(3, 2, 3))) {
			if (!(mc.level.getBlockState(p).getBlock() instanceof ButtonBlock)) continue;
			buttons.add(p.immutable());
			nearest = Math.min(nearest, p.distSqr(at));
		}
		// An answer's block can carry a button on more than one face, all as near its label: yours is the one on your side.
		double mine = Double.MAX_VALUE;
		for (BlockPos p : buttons) {
			if (p.distSqr(at) > nearest + 1) continue;
			double far = mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
			if (far < mine) {
				mine = far;
				best = p;
			}
		}
		return best != null ? best : at.below();
	}

	/** A question has just been asked in chat: its answer, by letter, from your book or from what's known everywhere. */
	private void asked() {
		question = said = chat.question;
		askedAt = System.currentTimeMillis();
		Entry e = book.get(question);
		String answer = e != null && e.answer != null && chat.letter(e.answer) != null ? e.answer : null;
		if (answer == null && e != null) {
			List<String> left = new ArrayList<>(chat.options.values());
			left.removeAll(e.wrong);
			if (left.size() == 1) answer = left.getFirst();
		}
		if (answer == null) answer = chat.known();
		chatAnswer = answer;
		if (!say.on()) return;
		Message m = say().info("Quiz: ");
		(answer != null ? m.success(chat.letter(answer) + " " + answer) : m.warning("Not learned yet. Lazarus will remember your answer.")).send();
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
			else if ((m = OPTION.matcher(n)).find()) options.put(QuizChat.answer(m.group(2)), e);
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
