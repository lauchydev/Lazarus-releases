package dev.lauchy.lazarus.feature.general;

import java.util.List;

/**
 * Everything {@link Valuer} prices from, so the rules are tested on their own: the game's live
 * records ({@link PriceDatabase}), or a snapshot (the shared test cases the worker runs too).
 * Every id is already as the market knows it ({@link #alias}).
 */
public interface Known {
	/** The time to judge evidence's age by, in ms. */
	long now();

	/** The id the market knows an item by: a learned item by its bare id, a pet by its name ("ender_dragon_pet_legendary"). */
	String alias(String id);

	/** What an NPC pays for one: the catalogue's price, else what items in game said, else a pet's by type and rarity; -1 when none. */
	long npc(String id);

	/** Every Lazarus user's history reads of an item, weighed by age ({@link Fresh}); {@link Fresh#NONE} when none. */
	Fresh prices(String id);

	/**
	 * This computer's own records of an item from the market feed: the bazaar over the last day and
	 * recent sales ({@link PriceMath}), each -1 when none; the worker has no records of its own.
	 */
	default Local local(String id) {
		return Local.NONE;
	}

	/** This computer's own records of an item: the bazaar's value and recent sales' value, each -1 when none. */
	record Local(long bazaar, long sales) {
		/** No records. */
		public static final Local NONE = new Local(-1, -1);
	}

	/** The owner's set price for an item, -1 when none. */
	long manual(String id);

	/** Whether the owner's set price wins over the market too. */
	boolean forced(String id);

	/** Every trade price of an item: what it went for, when, and the pair of traders ("" when an older mod didn't say). */
	List<Traded> trades(String id);

	/** One trade of an item: its price, when (ms), and its pair of traders. */
	record Traded(long price, long at, String pair) {
	}

	/** What the fire sale sold an item for, in coins, -1 when nothing (or nobody bought one). */
	long fireSale(String id);

	/** Every recipe known for an item, the shipped one first. */
	List<ItemValue.Recipe> recipes(String id);

	/** The stones that apply a reforge ("renowned"), empty when none does. */
	List<String> stones(String reforge);

	/** What dragsim.co's networth says one clean item sells for (sales or the bazaar), -1 when it doesn't. */
	long siteBase(String id);

	/** What dragsim.co reckons one clean item costs to craft, -1 when it didn't reckon. */
	long siteCraft(String id);

	/** Buy It Now listings of an item still up: each one's price, {@link ItemValue#variant}, and seller. */
	List<Listing> listings(String id);

	/** One listing still up: its price for one, which copy of the item it is, and who's selling. */
	record Listing(long price, String variant, String seller) {
	}

	/** The shares of each upgrade's own price the market pays. */
	ItemValue.Model shares();
}
