package dev.lauchy.lazarus.feature.general;

import dev.lauchy.lazarus.Lazarus;
import dev.lauchy.lazarus.core.Category;
import dev.lauchy.lazarus.core.DropColours;
import dev.lauchy.lazarus.core.Drops;
import dev.lauchy.lazarus.core.Feature;
import dev.lauchy.lazarus.core.Items;
import dev.lauchy.lazarus.core.LootFeed;
import dev.lauchy.lazarus.core.LootRows;
import dev.lauchy.lazarus.core.Text;
import dev.lauchy.lazarus.hud.Toast;
import dev.lauchy.lazarus.setting.ActionSetting;
import dev.lauchy.lazarus.setting.BoolSetting;
import dev.lauchy.lazarus.setting.ChoiceSetting;
import dev.lauchy.lazarus.setting.TextSetting;
import dev.lauchy.lazarus.ui.Anim;
import dev.lauchy.lazarus.ui.Draw;
import dev.lauchy.lazarus.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A sound, a flash over the screen and a toast when a drop you get is worth more than a value you set, or
 * the server announces it at a tier you choose. The flash takes the drop's rarity colour, and an RNGESUS drop
 * (the server's word for cores, scrolls and the like) flashes rainbow for a few seconds. Announced drops can
 * also pop up the way the server pops up its biggest ones: the item big in the middle of the screen on the
 * totem animation, with "✦ RARE DROP ✦" and its name above. Items you list never alert. A Preview button runs
 * through the rarities for anyone yet to see one. Lazarus+.
 */
public final class DropAlerts extends Feature {
	public static DropAlerts INSTANCE;

	/** The sound played, or none. */
	public enum Sound {LEVEL_UP, PLING, BELL, CHIME, NONE}

	/** The lowest announcement tier that counts, or never. */
	public enum From {NEVER, RARE, EPIC, LEGENDARY, RNGESUS}

	/** What happens when the server shows its own pop-up: keep the server's, or put the Lazarus one in its place. */
	public enum ServerPopUp {KEEP, REPLACE}

	/** How close together the server's pop-up and the drop's chat line land, either way round. */
	private static final long SERVER_MS = 3000;

	/** How long a flash fades over; a rainbow flash stays longer. The pop-up's words last as long as the totem animation. */
	private static final long FLASH_MS = 700, RAINBOW_MS = 3000, POPUP_MS = 2000;
	/** The rarities the preview runs through: the item colour, then the rainbow of an RNGESUS drop. */
	private static final List<String[]> PREVIEWS = List.of(new String[]{"Common", "§f"}, new String[]{"Uncommon", "§a"}, new String[]{"Rare", "§9"},
			new String[]{"Epic", "§5"}, new String[]{"Legendary", "§6"}, new String[]{"Mythic", "§d"}, new String[]{"Divine", "§b"}, new String[]{"RNGesus", "§6"});

	private final TextSetting above = add(new TextSetting("above", "Alert above", "The value a drop must beat, in coins: 500k, 2.5m, 1b.", "1m", 12));
	private final ChoiceSetting<From> from = add(new ChoiceSetting<>("from", "Also alert from", "Drops the server announces at this tier or above alert whatever they're worth. RNGESUS is its tier for cores, scrolls and the like.", From.RNGESUS));
	private final TextSetting names = add(new TextSetting("names", "Always alert for", "Items that alert and pop up whatever they're worth or however they're announced, separated by commas. Part of a name is enough: core matches every core.", "", 200));
	private final TextSetting ignore = add(new TextSetting("ignore", "Never alert for", "Items that never alert or pop up, whatever they're worth or however they're announced, separated by commas. Each in full: Summoning Eye.", "", 200));
	private final ChoiceSetting<Sound> sound = add(new ChoiceSetting<>("sound", "Sound", "Played when a drop alerts.", Sound.LEVEL_UP));
	private final BoolSetting flash = add(new BoolSetting("flash", "Flash", "A flash over the screen in the drop's rarity colour; rainbow for an RNGESUS drop.", true));
	private final BoolSetting toast = add(new BoolSetting("toast", "Toast", "The drop and what it's worth, as a toast.", true));
	private final BoolSetting popup = add(new BoolSetting("popup", "Pop-up", "The drop big in the middle of the screen on the totem animation, with its tier and name above, as the server shows its biggest drops.", true));
	private final ChoiceSetting<From> popupFrom = add(new ChoiceSetting<>("popupFrom", "Pop up from", "The lowest announcement tier that pops up. The server pops up its biggest drops itself; this covers the rest.", From.RARE));
	private final ChoiceSetting<ServerPopUp> serverPopUp = add(new ChoiceSetting<>("serverPopUp", "Server's pop-up", "When the server shows its own pop-up for a drop: Keep shows the server's and no Lazarus one; Replace hides the server's and shows the Lazarus one.", ServerPopUp.KEEP));
	private final ActionSetting preview = add(new ActionSetting("preview", "Preview", "Shows the flash and pop-up for each rarity in turn, so you know them before you see one.", "Show next", this::preview));

	/** The flash: when it began (-1 for none), its colour, and whether it cycles the rainbow. */
	private long flashedAt = -1;
	private int flashColour = Theme.HALO;
	private boolean rainbow;
	/** The pop-up's words: when they began (-1 for none) and end, the tier line, the name, their colour, and whether it cycles the rainbow. */
	private long poppedAt = -1, popUntil;
	private String popTier = "", popName = "";
	private int popColour = Theme.TEXT;
	private boolean popRainbow;
	private int previewed = -1;
	/** When the server last showed its own pop-up, -1 for never; and whether the totem animation now starting is ours. */
	private long serverAt = -1;
	private boolean own;
	/** The last drop's pop-up, kept a moment so Replace can show it when the server's arrives after the chat line. */
	private Runnable lastPopUp;
	private long lastDropAt = -1;
	private boolean lastPopped;
	/** The pop-up's drop, looked for in your inventory until it lands there for the totem animation. */
	private final Landing landing = new Landing();

	public DropAlerts() {
		super("dropAlerts", "Drop Alerts", "A sound, a flash in the drop's rarity colour and a pop-up when a drop is worth more than you set or announced at a tier you choose.", Category.GENERAL, true);
		INSTANCE = this;
	}

	@Override
	public boolean plus() {
		return true;
	}

	@Override
	public void init() {
		LootFeed.listen(loot -> {
			if (!enabled()) return;
			Drops.Drop drop = loot.drop();
			if (ignored(drop.item(), ignore.get())) {
				// Nothing of ours. Replace still hides the server's pop-up, which often comes before the line naming the drop.
				lastPopUp = null;
				return;
			}
			long value = PriceDatabase.worth(drop.item()) * drop.count();
			int colour = colour(DropColours.of(drop.item()));
			boolean big = Drops.tierRank(loot.tier()) == Drops.tierRank("RNGESUS");
			boolean named = named(drop.item(), names.get());
			boolean alerts = named || beats(value, above.get()) || tiered(loot.tier(), from.get());
			if (alerts) alert(drop, value, colour, big);
			boolean wanted = popup.on() && (named || alerts && !loot.tier().isEmpty() || tiered(loot.tier(), popupFrom.get()));
			long now = Util.getMillis();
			boolean server = serverAt >= 0 && now - serverAt < SERVER_MS;
			lastPopUp = () -> popUp(loot.tier(), drop, colour, big);
			lastDropAt = now;
			// The server's own pop-up for this drop already showed: Keep leaves it be, Replace shows ours in its place.
			lastPopped = server ? serverPopUp.get() == ServerPopUp.REPLACE && popup.on() : wanted;
			if (lastPopped) lastPopUp.run();
		});
		// A drop announced before it reaches your inventory plays its animation on the tick it lands.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!landing.waiting(Util.getMillis())) return;
			if (!enabled() || !popup.on()) {
				landing.cancel();
				return;
			}
			ItemStack stack = landed();
			if (!stack.isEmpty()) totem(stack);
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "drop_flash"), (g, delta) -> drawFlash(g));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Lazarus.ID, "drop_popup"), (g, delta) -> drawPopup(g));
	}

	/** Whether a drop worth {@code value} coins beats the value set ("1m"); never when the setting isn't a number. */
	static boolean beats(long value, String above) {
		double limit = Text.number(above);
		return limit >= 0 && value > limit;
	}

	/**
	 * Whether {@code item} is one of {@code list}, names separated by commas: each entry matches any item whose name
	 * holds it, case and surrounding spaces aside, so "core" matches Frozen Core and Judgement Core. Empty entries
	 * match nothing.
	 */
	static boolean named(String item, String list) {
		String name = item.toLowerCase(Locale.ROOT);
		for (String entry : list.split(",")) {
			String e = entry.trim().toLowerCase(Locale.ROOT);
			if (!e.isEmpty() && name.contains(e)) return true;
		}
		return false;
	}

	/**
	 * Whether {@code item} is one of {@code list}, names separated by commas: each entry is a whole name as a drop's
	 * line gives it ("Summoning Eye", "[Lvl 1] Enderman"), case, colour codes and surrounding spaces aside.
	 */
	static boolean ignored(String item, String list) {
		for (String entry : LootRows.names(Text.plain(list))) {
			Drops.Drop d = Drops.item(entry);
			if (d != null && Drops.same(d.item(), Text.plain(item))) return true;
		}
		return false;
	}

	/** Whether a drop announced at {@code tier} reaches {@code from}: never for NEVER, and never for a line with no tier. */
	static boolean tiered(String tier, From from) {
		if (from == From.NEVER) return false;
		int rank = Drops.tierRank(tier);
		return rank >= 0 && rank >= Drops.tierRank(from.name());
	}

	/** The colour an item's name was written in ("§6") as a colour to draw with; Halo when it hasn't been seen. */
	static int colour(String code) {
		if (code == null || code.length() != 2) return Theme.HALO;
		ChatFormatting f = ChatFormatting.getByCode(code.charAt(1));
		return f == null || f.getColor() == null ? Theme.HALO : 0xFF000000 | f.getColor();
	}

	/** The rainbow at this moment: round the hue wheel every second and a half. */
	static int rainbow(long millis) {
		return 0xFF000000 | Mth.hsvToRgb(millis % 1500 / 1500f, 0.75f, 1f);
	}

	/** Says, sounds and flashes for a drop that alerts. */
	private void alert(Drops.Drop drop, long value, int colour, boolean big) {
		Minecraft mc = Minecraft.getInstance();
		if (toast.on()) Toast.show("§6✦ §f" + (drop.count() > 1 ? drop.count() + "x " : "") + drop.item() + " §7· §e" + Text.compact(value), colour);
		SoundEvent s = switch (sound.get()) {
			case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
			case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
			case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
			case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case NONE -> null;
		};
		if (s != null && mc.player != null) mc.player.playSound(s, 1f, 1f);
		if (flash.on()) flash(colour, big);
	}

	private void flash(int colour, boolean big) {
		flashedAt = Util.getMillis();
		flashColour = colour;
		rainbow = big;
	}

	/**
	 * The pop-up for a drop: its tier and name, over the item from your inventory on the totem animation. A drop you
	 * already carry plays at once; one still on its way plays as it lands ({@link Landing}).
	 */
	private void popUp(String tier, Drops.Drop drop, int colour, boolean big) {
		landing.expect(drop.item(), Util.getMillis());
		popUp((tier.isEmpty() ? "" : tier.toUpperCase(Locale.ROOT) + " ") + "DROP", (drop.count() > 1 ? "§7" + drop.count() + "x §r" : "") + drop.item(), colour, big, landed());
	}

	/** The pop-up's drop from your inventory once it has landed there, or an empty stack while it hasn't. */
	private ItemStack landed() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return ItemStack.EMPTY;
		List<ItemStack> held = mc.player.getInventory().getNonEquipmentItems();
		int i = landing.find(held.stream().map(Items::name).toList(), Util.getMillis());
		return i < 0 ? ItemStack.EMPTY : held.get(i).copy();
	}

	/** Puts the words up and plays the totem animation with {@code stack} under them (none for an empty one). */
	private void popUp(String tier, String name, int colour, boolean big, ItemStack stack) {
		poppedAt = Util.getMillis();
		popUntil = poppedAt + POPUP_MS;
		popTier = "✦ " + tier + " ✦";
		popName = name;
		popColour = colour;
		popRainbow = big;
		if (!stack.isEmpty()) totem(stack);
	}

	/** Plays the totem animation with {@code stack}, keeping the words up until it ends, or bringing them back for a drop that landed late. */
	private void totem(ItemStack stack) {
		own = true; // our own animation, which the server-pop-up check must let through
		try {
			Minecraft.getInstance().gameRenderer.displayItemActivation(stack);
		} finally {
			own = false;
		}
		long now = Util.getMillis();
		if (poppedAt < 0) poppedAt = now;
		popUntil = now + POPUP_MS;
	}

	/** The Preview button: the next rarity's flash and pop-up, the last being an RNGESUS drop's rainbow. */
	private void preview() {
		previewed = (previewed + 1) % PREVIEWS.size();
		String[] p = PREVIEWS.get(previewed);
		boolean big = previewed == PREVIEWS.size() - 1;
		int colour = colour(p[1]);
		flash(colour, big);
		landing.cancel(); // a drop still on its way would take the preview's animation over
		popUp(big ? "RNGESUS DROP" : p[0].toUpperCase(Locale.ROOT) + " DROP", big ? "A core, a scroll" : "A " + p[0] + " drop", colour, big,
				new ItemStack(big ? net.minecraft.world.item.Items.NETHER_STAR : net.minecraft.world.item.Items.DIAMOND));
		Toast.show("§7Preview: §f" + p[0] + (big ? " §7(rainbow)" : ""), colour);
	}

	/**
	 * The game is about to play the totem animation with {@code stack}; true to stop it. One the server starts with
	 * anything but a plain totem is its drop pop-up: Keep lets it play (and takes our words down, so they don't sit
	 * over its own), Replace stops it and shows ours. A real totem of undying, and our own animation, always play.
	 */
	public static boolean itemPopUp(ItemStack stack) {
		DropAlerts d = INSTANCE;
		if (d == null || d.own || !d.enabled() || !d.popup.on()) return false;
		if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) && stack.getComponentsPatch().isEmpty()) return false;
		return d.server();
	}

	/**
	 * A title, subtitle or action bar line is about to show; true to stop it. One in the server's drop fonts
	 * (dragsim:rare_drop, dragsim:rare_drop_banner) is its pop-up's words, kept or stopped as the totem is.
	 */
	public static boolean textPopUp(Component text) {
		DropAlerts d = INSTANCE;
		if (d == null || text == null || !d.enabled() || !d.popup.on() || !serverFont(text)) return false;
		return d.server();
	}

	/** Whether any part of {@code text} is drawn in one of the server's drop fonts. */
	static boolean serverFont(Component text) {
		return text.visit((style, part) -> style.getFont() instanceof FontDescription.Resource r && isDropFont(r.id().toString())
				? Optional.of(true) : Optional.empty(), Style.EMPTY).orElse(false);
	}

	/** Whether a font id ("dragsim:rare_drop") is one the server's drop pop-up is written in. */
	static boolean isDropFont(String id) {
		return id.equals("dragsim:rare_drop") || id.equals("dragsim:rare_drop_banner");
	}

	/** The server's pop-up is showing: notes when, and says whether to stop it. */
	private boolean server() {
		long now = Util.getMillis();
		serverAt = now;
		if (serverPopUp.get() == ServerPopUp.KEEP) {
			// Theirs is up: our words come down rather than sit over it, and our animation won't follow it.
			poppedAt = -1;
			landing.cancel();
			return false;
		}
		// Replace: the drop's line came first and ours wasn't shown for it, so it shows now in the server's place.
		if (!lastPopped && lastPopUp != null && now - lastDropAt < SERVER_MS) {
			lastPopped = true;
			lastPopUp.run();
		}
		return true;
	}

	/** Draws the flash and the pop-up's words over a screen, for the preview to show through the menu. */
	public static void drawOver(GuiGraphicsExtractor g) {
		if (INSTANCE == null || INSTANCE.previewed < 0) return;
		INSTANCE.drawFlash(g);
		INSTANCE.drawPopup(g);
	}

	/** The flash fading over the whole screen, or cycling the rainbow for a big drop. */
	private void drawFlash(GuiGraphicsExtractor g) {
		if (flashedAt < 0) return;
		long now = Util.getMillis();
		long length = rainbow ? RAINBOW_MS : FLASH_MS;
		float t = (now - flashedAt) / (float) length;
		if (t >= 1f || !enabled()) {
			flashedAt = -1;
			return;
		}
		// A plain flash fades from the start; the rainbow holds, then fades over its last quarter.
		float a = rainbow ? 0.3f * Math.min(1f, (1f - t) * 4f) : 0.35f * (1f - t);
		int colour = rainbow ? rainbow(now) : flashColour;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), Draw.alpha(colour, a));
	}

	/** The pink the server writes its "✦ CRAZY RARE DROP ✦" in, and the near-black outline round its letters. */
	private static final int LABEL = 0xFFFF55FF, OUTLINE = 0xFF1A1A1A;

	/**
	 * The pop-up's words above the item the totem animation holds in the middle of the screen, in the server's
	 * style: the tier in bold pink at one and a half times the size, the name under it a quarter larger than
	 * chat, in the drop's rarity colour, with any count in grey.
	 */
	private void drawPopup(GuiGraphicsExtractor g) {
		if (poppedAt < 0) return;
		long now = Util.getMillis();
		if (now > popUntil || !enabled()) {
			poppedAt = -1;
			return;
		}
		float in = Anim.easeOut((now - poppedAt) / 200f);
		float out = Math.min(1f, (popUntil - now) / 400f);
		int colour = popRainbow ? rainbow(now) : popColour;
		var font = Minecraft.getInstance().font;
		int cx = g.guiWidth() / 2, top = g.guiHeight() / 2 - 70 + Math.round((1f - in) * 6);
		Draw.opacity = Math.min(in, out);
		try {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx, top);
			// The tier as the server sets it: bold italic, half as big again, a two-pixel drop shadow under an outline.
			pose.scale(1.5f, 1.5f);
			Draw.outlinedCentered(g, font, "§l§o" + popTier, 0, 0, LABEL, OUTLINE, 2);
			pose.popMatrix();
			pose.pushMatrix();
			pose.translate(cx, top + 20);
			pose.scale(1.25f, 1.25f);
			Draw.outlinedCentered(g, font, popName, 0, 0, colour, OUTLINE, 1);
			pose.popMatrix();
		} finally {
			Draw.opacity = 1f;
		}
	}
}
