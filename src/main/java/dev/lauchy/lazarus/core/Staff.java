package dev.lauchy.lazarus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Whether the signed-in player is Dragsim staff, by their rank on dragsim.co (Helper and above). The rank is
 * asked for at most every ten minutes; until the first answer, or when the site can't be reached, the player
 * isn't staff, and is asked about again next time. The Staff section of the menu and the staff tools go by this.
 */
public final class Staff {
	/** The dragsim.co ranks that count as staff, as {@link #rankKey} spells them. */
	public static final Set<String> RANKS = Set.of("HELPER", "MOD", "MODERATOR", "ADMIN", "MANAGER", "OWNER");
	/** How long an answer is trusted before the site is asked again. */
	private static final long FRESH_MS = 10 * 60 * 1000;
	/** How long to wait before asking again after the site couldn't answer, so a tick-by-tick caller never floods it. */
	private static final long RETRY_MS = 60 * 1000;

	/** Whose rank the answer is for, so a switch of account asks again. */
	private static String rankFor;
	private static boolean staff;
	private static long askedAt;
	private static boolean asking;

	private Staff() {
	}

	/** The last answer, asking the site again in the background when it's stale; false until the first answer. */
	public static boolean is() {
		if (stale() && !asking) check(x -> { });
		return fresh() && staff;
	}

	/** Answers on the game thread with the player's staff standing, asking the site first when the answer is stale. */
	public static void check(Consumer<Boolean> then) {
		Minecraft mc = Minecraft.getInstance();
		if (fresh()) {
			then.accept(staff);
			return;
		}
		String me = mc.getUser().getName();
		asking = true;
		Http.json("/api/player/" + Http.encode(me) + "?mode=main").whenComplete((json, error) -> mc.execute(() -> {
			asking = false;
			if (error != null || json == null || !json.isJsonObject()) {
				// Unknown: not staff for now, and asked again in a minute.
				rankFor = me;
				staff = false;
				askedAt = Util.getMillis() - FRESH_MS + RETRY_MS;
				then.accept(false);
				return;
			}
			JsonElement p = json.getAsJsonObject().get("player");
			JsonObject player = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
			String rank = player.has("rank") && player.get("rank").isJsonPrimitive() ? player.get("rank").getAsString() : "";
			rankFor = me;
			staff = RANKS.contains(rankKey(rank));
			askedAt = Util.getMillis();
			then.accept(staff);
		}));
	}

	/** A rank as the site writes it ("§6[MVP_PLUS]", "Mod") reduced to one spelling: "MVP+", "MOD". */
	public static String rankKey(String rank) {
		return rank.replaceAll("§.", "").replaceAll("[\\[\\]]", "").trim().toUpperCase(Locale.ROOT).replace("_PLUS", "+").replace(" PLUS", "+");
	}

	private static boolean fresh() {
		return rankFor != null && rankFor.equals(Minecraft.getInstance().getUser().getName()) && Util.getMillis() - askedAt < FRESH_MS;
	}

	private static boolean stale() {
		return !fresh();
	}
}
