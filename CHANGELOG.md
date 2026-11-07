# Changelog

What changed for players in the newest version of Lazarus. Only that version is listed: each release replaces it.

## 1.41.0

### Added
- Drop Alerts: Never Alert For, a list of items that never get a sound, flash, toast or pop-up, whatever they're worth or however they're announced. Alert for every Epic drop but Summoning Eye, say. Each item is its whole name, in any case.
- Staff desk (staff): a flagged player's card now says what the anticheat keeps catching them on over the last hour, check by check: "Click rate ×12 · CPS up to 20", then the other checks. It's read from the player's own page on the flag dashboard when you select them (again after a few minutes), and what staff chat hears since is added in. Its detail panel breaks each check down: how often, the range of every measure, the servers, what it added to the score and the newest evidence. The list shows the same checks under each flagged name.
- Staff desk (staff): a filter beside the search, to show only players flagged for the reasons you tick (REPORT, ALT, CAPTCHA, a dashboard flag, a detection, a score, setbacks, the watch list) or of the ranks you tick (only Default and VIP, say). The Flagged, All and Watched counts follow it.
- Staff Status (staff): a small HUD panel saying whether you're vanished, the nick you go by, your chat channel, which staff are online (their rank, whether they're vanished, the nick they go by, and for admins, every server's staff from `/glist all` with the server they're on), how many online players are flagged and how many reports are open. Move it in the HUD editor; each line can be turned off.

### Changed
- Pet Binds: each bind is a key and the slot of the pet it summons, counted across the pages of /pet, so J can summon the pet in slot 4 without binds for slots 1 to 3. Binds you'd already set keep the slot they had.
- Wardrobe Binds: each bind is a key and the wardrobe slot it puts on, so J can put on slot 4. Binds you'd already set keep the slot they had.
- Anticheat Lines (staff): the anticheat's review-only lines ("[DragSim AC] Steve Click rate: CPS=14.5 (review only)") leave chat too. Each becomes a detection on the player's card and a chip in the list ("CLICK RATE ×3"), shared with every staff member for an hour.

### Fixed
- Drop Alerts' pop-up now shows the item on the totem animation for pets, such as a Scatha Pet, and for drops that reach your inventory a moment after their chat line, such as a slayer boss's Shard of the Shredded or Flesh Fang.
- Wardrobe Binds: a bind waits until the wardrobe lets you swap, a second after it opens, instead of being told to wait. If the server still says to wait, the bind swaps again as soon as it can.
- Command, Pet and Wardrobe Binds: starting the game no longer adds another empty bind each time.
- Staff tools (staff): no longer switch off for a moment every ten minutes while your rank is checked again, which let an anticheat line through to chat now and then.
- Pet and Wardrobe Binds: the menu closes after the pet comes out, even when the server opens it again a moment later, and a pet key and a wardrobe key pressed together no longer get in each other's way.
