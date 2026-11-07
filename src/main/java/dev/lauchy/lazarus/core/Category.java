package dev.lauchy.lazarus.core;

/**
 * The menu's sidebar sections, in display order: the normal ones, then the Lazarus+ section (Lazarus+ and Capes),
 * then the Staff section, which only Dragsim staff see.
 */
public enum Category {
	END("The End"),
	SLAYERS("Slayers"),
	DUNGEONS("Dungeons"),
	INVENTORY("Inventory"),
	CHAT("Chat"),
	GENERAL("General"),
	DISCORD("Discord"),
	PLUS("Lazarus+"),
	CAPES("Capes"),
	STAFF("Staff");

	public final String title;

	Category(String title) {
		this.title = title;
	}

	/** Whether this category sits in the Lazarus+ section at the bottom of the sidebar. */
	public boolean plusSection() {
		return this == PLUS || this == CAPES;
	}

	/** Whether this category is the Staff section, listed only for Dragsim staff. */
	public boolean staffSection() {
		return this == STAFF;
	}
}
