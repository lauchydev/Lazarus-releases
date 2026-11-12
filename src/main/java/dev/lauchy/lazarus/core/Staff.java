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
 * asked for again every ten minutes, and the last answer stands meanwhile, and when the site can't be reached;
 * until the first answer the player isn't staff, and is asked about again in a minute. The Staff section of the
 * menu and the staff tools go by this.
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
	/** The player's own rank as {@link #rankKey} spells it, "" until the site has said. */
	private static String rank = "";
	private static long askedAt;
	private static boolean asking;
	/** Whether the site has answered for {@link #rankFor}, rather than the answer being "not staff" for want of one. */
	private static boolean answered;

	private Staff() {
	}

	/**
	 * The last answer, asking the site again in the background when it's ten minutes old; false until the first.
	 * The last answer stands while it's asked again: going by "not staff" for the moment the site takes would let
	 * the staff tools off every ten minutes (an anticheat line through to chat).
	 */
	public static boolean is() {
		if (stale() && !asking) check(x -> { });
		return known() && staff;
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
				// No answer: the last one stands when there is one for this account, else not staff for now; asked again in a minute.
				if (!(answered && me.equals(rankFor))) {
					rankFor = me;
					staff = false;
					rank = "";
					answered = false;
				}
				askedAt = Util.getMillis() - FRESH_MS + RETRY_MS;
				then.accept(staff);
				return;
			}
			JsonElement p = json.getAsJsonObject().get("player");
			JsonObject player = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
			String rank = player.has("rank") && player.get("rank").isJsonPrimitive() ? player.get("rank").getAsString() : "";
			rankFor = me;
			Staff.rank = rankKey(rank);
			staff = RANKS.contains(Staff.rank);
			answered = true;
			askedAt = Util.getMillis();
			then.accept(staff);
		}));
	}

	/** The player's own rank ("ADMIN", "MOD"), as the site last said; "" before it has, or for another account. */
	public static String rank() {
		is();
		return known() ? rank : "";
	}

	/** Whether there's an answer for the account signed in now, however old. */
	private static boolean known() {
		return rankFor != null && rankFor.equals(Minecraft.getInstance().getUser().getName());
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
