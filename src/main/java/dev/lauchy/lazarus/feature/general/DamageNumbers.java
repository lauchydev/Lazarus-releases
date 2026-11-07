package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortens or hides the floating damage popups, on this client only. A popup is decided
 * once, the first frame it exists: the server never updates its text, so the local
 * rewrite sticks and nothing needs restoring.
 */
public final class DamageNumbers extends Feature {
	public enum Mode { COMPACT, HIDE }

	/** Digits with non-letter decoration (crits get a sparkle each side), never a slash (health bars). */
	static final Pattern DAMAGE = Pattern.compile("^[^\\p{L}/]*\\d[\\d,.]*[^\\p{L}/]*$");
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
	/** A popup is caught as it spawns; anything older that reads as a number is a hologram. */
	private static final int NEW_TICKS = 5;

	private final ChoiceSetting<Mode> mode = add(new ChoiceSetting<>("mode", "Mode", "Shorten to 8.49M, or hide them entirely.", Mode.COMPACT));
	private final BoolSetting critsOnly = add(new BoolSetting("critsOnly", "Crits only", "Hide plain and ability hits, keep crits.", false));

	private Set<Integer> decided = new HashSet<>();

	public DamageNumbers() {
		super("damageNumbers", "Damage Numbers", "Shorten damage popups, show crits only, or hide them.", Category.GENERAL, false);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> scan());
		// Also per frame: entities arrive between ticks and would flash full-length first.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "damage_scan"), (g, d) -> scan());
	}

	private void scan() {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || mc.level == null) {
			decided.clear();
			return;
		}
		Set<Integer> still = new HashSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Display.TextDisplay) && !(e instanceof ArmorStand && e.isCustomNameVisible())) continue;
			if (decided.contains(e.getId())) {
				still.add(e.getId());
				continue;
			}
			if (e.tickCount > NEW_TICKS) continue;
			Component text = text(e);
			if (text == null) continue;
			String plain = text.getString().replaceAll("§.", "").trim();
			if (plain.isEmpty() || !DAMAGE.matcher(plain).matches()) continue;
			still.add(e.getId());
			if (mode.get() == Mode.HIDE || critsOnly.on() && !isCrit(plain)) hide(e);
			else rewrite(e, text);
		}
		decided = still;
	}

	static boolean isCrit(String plain) {
		return !plain.replaceAll("[\\d,.\\s]", "").isEmpty();
	}

	private static Component text(Entity e) {
		return e instanceof Display.TextDisplay td ? td.getEntityData().get(Display.TextDisplay.DATA_TEXT_ID) : e.getCustomName();
	}

	/** View range zero skips the whole display, background included; blank text would leave a pill. */
	private static void hide(Entity e) {
		if (e instanceof Display.TextDisplay td) td.getEntityData().set(Display.DATA_VIEW_RANGE_ID, 0f);
		else e.setCustomNameVisible(false);
	}

	private static void rewrite(Entity e, Component original) {
		String out = compact(original.getString());
		if (out == null) return;
		Component c = Component.literal(out).setStyle(original.getStyle());
		if (e instanceof Display.TextDisplay td) td.getEntityData().set(Display.TextDisplay.DATA_TEXT_ID, c);
		else e.setCustomName(c);
	}

	/**
	 * Shortens the number inside a colour-coded string. The server colours each digit as
	 * a gradient, so the codes are kept and spread evenly over the shorter number.
	 */
	static String compact(String raw) {
		StringBuilder plain = new StringBuilder();
		java.util.List<String> codeBefore = new java.util.ArrayList<>();
		StringBuilder pending = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§' && i + 1 < raw.length()) {
				pending.append(c).append(raw.charAt(++i));
				continue;
			}
			codeBefore.add(pending.toString());
			pending.setLength(0);
			plain.append(c);
		}
		Matcher m = NUMBER.matcher(plain);
		if (!m.find()) return null;
		double value;
		try {
			value = Double.parseDouble(m.group().replace(",", ""));
		} catch (NumberFormatException ex) {
			return null;
		}
		if (value < 1000) return null;
		String shortNum = dev.lauchy.lazarus.core.Text.compact(value);
		int start = m.start(), len = m.end() - start;
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < start; i++) out.append(codeBefore.get(i)).append(plain.charAt(i));
		for (int i = 0; i < shortNum.length(); i++) {
			int src = start + Math.min(len - 1, i * len / shortNum.length());
			out.append(codeBefore.get(src)).append(shortNum.charAt(i));
		}
		for (int i = m.end(); i < plain.length(); i++) out.append(codeBefore.get(i)).append(plain.charAt(i));
		out.append(pending);
		return out.toString();
	}
}
