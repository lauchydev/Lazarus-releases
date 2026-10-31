# Changelog

All notable changes to Lazarus. Newest first.

## 1.31.0

### Added
- Hovering the networth in the profile viewer (`/l <player>`) lists the items it couldn't price under "Unpriced", most first, with how many of each (the first 15, then a count of the rest).

### Changed
- Missing Accessories also shows beside the crafting menu (`/craft`), recipe card included, so a recipe can be followed while crafting it. Accessories seen in any Accessory Bag page this session count as owned there too.

### Fixed
- Every Baby Skin and Shimmer Skin had one price. Each dragon armour set has its own (Strong, Protector, Wise…), but they all share a name, so anything that knew one only by its name (the fire sale history, a menu item without the server's id) took the same id for all of them. Skins that share a name are now told apart by their lore ("This skin can be applied to Strong Dragon Helmet"). No other skins share a name. The fire sale history needs indexing again (`/lazarus firesale index`) for each skin to get its own price.

## 1.30.1

### Fixed
- Missing Accessories showed no tooltip on hover: the panel asked for it after the window had drawn, which is too late. It draws with the window's contents now, so hovering an accessory shows its name, recipe and counts.
- Clicking an accessory in Missing Accessories did nothing. It opens a recipe card beside the panel: the 3x3 recipe with each part's icon and count, on a light slot when you have enough of it and a dark one when you don't (hover a part for how many you have), and the accessory itself. Click it again to close it. Clicks on the panel no longer reach the game, which would drop whatever the cursor held.

## 1.30.0

### Added
- Missing Accessories (Inventory, on by default): a panel right of the Accessory Bag (`/acc`) lists every accessory you don't have yet, leaving out the tiers below one you do (with a Rose Artifact, only the Rose Relic is missing). Each sits on a light slot when your inventory, bag, ender chest and the storage pages you've opened hold everything its recipe takes, and on a dark one when they don't. Hovering one lists its recipe with how many of each part you have. What you own comes from the bag, everything you carry or store, and your profile on dragsim.co; families are linked by their recipes and by their Talisman, Ring, Artifact and Relic names. Scroll the panel when the list is long.
- `/lazarus price set` can be given to players other than the owner (the worker's PRICERS list, starting with one account). They may set a price but not force or clear one, nor change a price the owner forced.

### Changed
- `/lazarus price set <price>` and `/lazarus price force <price>` with no item price the item in your hand.

### Fixed
- Golden Dragon Math never answered: the dragon speaks as "[BOSS] Golden Dragon: …", and the colon made it look like a player's chat line, which the solver skips.

## 1.29.0

### Added
- `/lazarus price gems <coins>` (owner only) sets what 1,000 gems are worth for every Lazarus user, e.g. `/lazarus price gems 2.3b`. Fire sale prices follow it within ten minutes, no re-indexing. `/lazarus price gems` on its own shows the rate. The worker keeps it as a new owner-only shared kind, `settings` (redeploys on merge).

### Changed
- Frozen Scythe Effects also hides the `dust` particles and mutes the bat take-off sound the scythe plays, found with `/lazarus effects`. Its lists start over from the new defaults (they were saved under new names), so ids hidden by hand in 1.28.0 need hiding again.
- Fire sale prices count a gem as 2.3M coins (1,000 gems = 2.3B), down from 3M, until the owner sets another rate. Fire sale prices are kept in gems now, so ones already indexed or shared change with the rate without re-indexing.

### Fixed
- A Furfsky skin applied to an item ("furfsky_hyperion_skin", shown as Furfsky Hyperion Skin) had no price, while the fire sale sold the same skin as "Hyperion Skin", which Lazarus learns under a different id. The two names now share a price: whichever has none takes the other's (Hyperion, Scylla, Valkyrie, Terminator, Aspect of the Void, Frozen and Glacial Scythe skins).

## 1.28.0

### Added
- Frozen Scythe Effects (General, on by default): hides the Frozen Scythe's ice trail and mutes its sound, from anyone's scythe. The particles and sounds it hides are lists in its settings. `/lazarus effects` names every particle and sound playing near you for 5 seconds, each with a Hide button that adds it to the list, so the exact ones can be picked out by swinging the scythe once.

### Changed
- A craftable item is valued at no less than what its recipe's priced parts cost ("recipe parts"). A Riftwalker Helmet showed its 20M NPC price beside 338M of Seraph Plating and Null Atoms. Settled market prices still win below that floor, since they show there are more of the item than people want: a set price, the bazaar, 3 or more sales, your own recorded sales, or trades. One or two sales, a listing or an estimate don't.

### Fixed
- A Buy It Now listing caps an item's clean price at what it's listed for, as asked. Taking a listed item's upgrades off undershot whenever the seller underpriced: a 90M Riftwalker Helmet with 10 books read as a 9M clean one, so the helmet fell to its NPC price (20M) instead of 90M.
- Items missing from dragsim.co's catalogue, which Lazarus learns by name, weren't found by their real id. An applied Ultimate Hyperion Skin showed as "Skin ultimate_hyperion_skin: no price yet", and `/lazarus price set|force` couldn't price it because the server refused the "~" id. They're found and priced by their bare id now, including fire sale prices already indexed under the old id (the skin's 105,000-gem sale, 315B).

## 1.27.3

### Added
- `/lazarus price force <price> <item>` (owner only): a set price that wins over sales and the bazaar too, for items whose sales can't be trusted (End Stone Rose's 100M from what look like coin transfers). `/lazarus price set` still only fills in until an item sells; `clear` removes either.

### Fixed
- The Lazarus server dropped the buyer count from shared fire sale reads, so a sale nobody bought from was only dropped on the computer that indexed it. It's kept and shared now (worker redeploys on merge).

## 1.27.2

### Changed
- A price set with `/lazarus price set` now only fills in until the item has a real price: a bazaar price, sales, or trades between Lazarus users win over it as soon as they exist, so a set price never hides the market. It still beats listings, recipes and the fire sale.

## 1.27.1

### Fixed
- /l pets were valued without their skin or held item (a Pink Sheep Skin and Lucky Clover were left out). Each pet now carries its own skin, held item and market id, so its tooltip adds them; pets that borrowed a look from one seen in game no longer carry that other pet's data.
- A fire sale nobody bought from no longer prices anything: none are in circulation. The indexer reads the buyers off each sale, and re-indexing drops those prices for everyone.
- Fire sale history entries read as 1 gem (a Terminator, a Dragon Claw, Necron's and Warden helmets) no longer price anything; the Terminator was 3M "fire sale". They're dropped from saved and shared prices too, and the indexer logs their lore.
- Something craftable is valued at its recipe's parts before any fire sale price. The Atomsplit Katana was 300B from a 100,000-gem fire sale no one bought, which also made every Voidfang Dagger 300B.
- Price reads shared by older versions of the mod no longer replace this computer's fuller read, and a lone sale in them counts as one (the Atomsplit Katana's 1.25B sale was being lost).
- Value rows no longer name a part's kind twice ("Dye Dye of the Pure Black" is now "Dye of the Pure Black").
- Tooltip text too dark to read, like "Dyed: Dye of the Pure Black" in the dye's own black, is lifted to a readable shade of its colour (Readable Lore, Inventory, on by default). /l does the same for its dye lines.

## 1.27.0

### Added
- Chat Spy Panel (Staff): the "[Spy DM] A → B: message" lines chat spy shows staff go on their own HUD panel, like the Staff Chat Panel, and out of the main chat (switch "Hide from chat" off to keep them in both). Opening the chat box shows the last ten even after they've faded, and the wheel over the panel scrolls back through the last hundred. Lines shown, how long they stay, lines with chat open and panel width are in its settings; move it in the HUD editor.
- Slot Lock has an Opacity slider for how strongly locked and linked slots are marked.

### Fixed
- Slot Lock's red marks drew over item tooltips. They're drawn with the slots now, like the rarity glow, so a tooltip covers them.
- The Golem Status spawn alert pinged twice for one Protector. The sidebar still says "Rising" for a moment after the spawn line, which re-armed the alert for the "Alive" read that followed. It re-arms only once the Protector is down or resting now, and never twice within a minute.

## 1.26.1

### Fixed
- Fire Sale Index counted the blank filler slots on the history's last page as sales with "no gem price" (12 of them on a 5-page history). They're skipped now, so the finish line only counts real sales.
- A Warden Helmet showed "Estimated value: 1.38M · dragsim.co" under a recipe costing 17.76B. The site's networth had priced it by a gem-to-coin guess (1 gem at its own 1.38M rate) and Lazarus took that as the site's price. Values the site reaches that way, or by its NPC floor, are ignored now, so such an item is valued by its fire sale price or its recipe instead; the site's prices from real sales, bazaar trades and listings are still used. The site's own recipe costings ranked above a sale or two, a listing and a fire sale price; they now come last, after Lazarus's own recipe, for items whose recipe Lazarus doesn't know ("dragsim.co recipe" in the tooltip).

- `/l` showed every pet with its plain look even when it wears a skin (sStepBr0's Neon Red Ender Dragon, Doge Wolf and the rest), because a pet of that type remembered from your own pet menu replaced the built head, skin and all. A pet with a skin now keeps the skin's head.

- An upgrade the market hasn't been fitted for (a dye, skin, pet skin, pet item, rune, scroll, enrichment or drill part) counted at 85% of its price; it now counts at its full price until sales say otherwise (Lauchlan's rule). Fitted kinds (books, recomb, reforge, stars) are unchanged.

## 1.26.0

### Added
- Discord Rich Presence, in a new Discord category in the menu. Your Discord profile shows that you're playing Lazarus, with two lines you write yourself: placeholders `{location}`, `{purse}`, `{held}` and `{name}` fill in where you are, your purse from the sidebar, the item in your hand and your name. A line whose placeholder has nothing to show (an empty hand, no purse on the sidebar) is left off rather than shown half empty. Switches add the time played on Dragsim, your Minecraft head and a button to the Lazarus Discord, and "Only on Dragsim" keeps it off other servers. It talks to the Discord app on your computer (Windows, macOS and Linux, Snap and Flatpak included), never holds up the game, and picks up again by itself when Discord is opened.

## 1.25.0

### Added
- Mining Profit (The End): a readout of what you mine and what it's worth. It counts End Stone, Obsidian, Crying Obsidian and Amethyst from your bag in raw blocks, so the Personal Compactor turning 160 Obsidian into an Enchanted Obsidian (or 80 Rough Amethyst into a Flawed) is never counted twice or lost, and compacting material you brought with you isn't counted as mined. Each material shows in Enchanted (or raw, or Refined; Flawed and Fine for Amethyst) with its value, then drops you got while mining (Void Fragments, Void Cores), total profit, profit per hour and time mined. The clock runs only while you swing a pickaxe or drill at a block, plus ten seconds to reach the next one, so menus and trips to the Forge don't dilute the rate, and nothing is counted with a menu open. Material is valued at market prices, or at NPC prices in the settings. It shows while a mining tool is in hand in the End; `/lazarus mining` lists all-time totals, `/lazarus mining session` this session's, and `/lazarus mining reset` starts the session over.

## 1.24.0

### Changed
- Invites have no limit: every Lazarus user can make as many invite codes as they like, a new one each time. The command is now `/lazarus invite` (it was `/lazarus generate`). Besides the code (click to copy), it shows how to invite someone: invite them to the Lazarus Discord (click to copy the link), give them the code, and have them type `/lazarus redeem <code>` in game. Cancelling a code with `/lazarus invites` still stops it working.

## 1.23.0

### Added
- Pet Binds (General): a key for each of the first nine pet slots. Pressing Pet 3's key runs `/pet`, clicks the third pet in the menu (turning the page if it's further on) and closes the menu. A bind can include Ctrl, Alt or Shift: hold them while picking the key, e.g. Alt + 3, and that combo no longer also picks a hotbar slot. Pets count in the menu's order, so slot 1 is the first pet it shows. The pet already out is left as it is rather than put away, and a slot with no pet says so. No keys are set to start with.
- Every Lazarus bind (Pet Binds, Command Binds, the staff talk key, feature toggle keys) can be a mouse button as well as a key: click the middle button or a side button while the bind is listening. Left and right stay the menu's own.

## 1.22.0

### Added
- Fire Sale Index: `/lazarus firesale index` opens `/firesale`, clicks through to the Fire Sale History and reads every page of it. Each past sale says what the item cost in gems, and a gem is worth three million coins (1,000 gems = 3B), so skins, dyes, runes and pet skins that never reach the auction house get a price: the item itself, and any item or pet wearing it. Fire sale prices are shared through the Lazarus server, rank after a listing and before a recipe, and show as "fire sale" in the tooltip. `/lazarus firesale stop` stops a run; closing the menu does too.

## 1.21.3

### Fixed
- Stay in Staff Chat didn't put you back in staff chat after moving between servers (Den to hub, hub to the Rift, Den #1 to Den #2). It looked for a server reply that mentions "staff chat", but Dragsim says "You are now in the STAFF channel", so it never knew you'd joined unless you typed exactly `/sc`; and it only noticed a move when the sidebar named a different kind of server, so Den to Den went unseen. It now reads the channel from that line, however you switched, and counts any new world as arriving: another server, another Den or logging back in. If a server turns out to have kept you in staff chat and the switch takes you out, it's sent once more.

### Changed
- Staff Chat Panel works like the main chat: opening the chat box shows the last staff lines (10 by default, "Lines with chat open" in its settings) even after they've faded from the panel, and the wheel over it scrolls further back.

## 1.21.2

### Fixed
- Rift Effect Remover still let the Nausea wobble show in the Rift's last minute. It took the effect off at the end of each tick, but the server's packet putting Nausea on lands before the tick, so the camera spin for that tick and the frames in between were drawn with it on. The packets for Darkness and Nausea are now dropped as they arrive, so neither effect is put on you at all; the end-of-tick sweep stays for any already on.

## 1.21.1

### Fixed
- The game crashed when opening a chest with Lazarus and Endsight both installed, and on start under Lunar Client, since 1.17.0. Storage Overview hides the chest behind its cards by replacing the call that draws the chest's panel. Endsight replaces that same call, and Lunar rewrites how chest windows draw, so Lazarus's hook had nothing to attach to and the game stopped. That hook is now optional: where it can't attach, the overview draws over the chest instead.

## 1.21.0

### Added
- `/lazarus price set <price> <item>` (owner only) sets what a clean item is worth for every Lazarus user, for items nothing else prices well: skins, dyes and pet items that never sell. The price takes "400m", "1.5b", "250k" or plain coins, and the item its name or id. A set price comes before every market price, shows as "set price" in the tooltip, and counts wherever the item does: as an upgrade on another item, in networth and in the loot trackers. `/lazarus price clear <item>` puts the item back on its market price. Prices are kept by the Lazarus server and reach everyone within ten minutes.

## 1.20.1

### Fixed
- An upgrade nothing prices yet was left out of an item's value breakdown, so a Renowned Sorrow Helmet with a Baby Skin, or armour dyed with Dye of the Pure Black, looked as if the skin or dye hadn't been counted. They were read; neither has ever sold, been listed or (yet) been traded, and they have no NPC price, so nothing prices them. They now show in the breakdown by name with "no price yet", and add nothing until a sale, listing or trade prices them. Dyes, skins, enrichments, runes and drill parts are named from the catalogue rather than left blank.
- A pet's held item wasn't counted in its value: an Ender Dragon with Gold Claws was valued as a bare Ender Dragon. The held item now counts as an upgrade (named, at its share of its own price, or "no price yet"), and a pet sale is read clean of it, so pets sold with items no longer inflate the clean pet's price. A pet's skin was already counted.

## 1.20.0

### Added
- Price Database learns from trades. When a trade goes through, the server sums it up in chat ("Trade successful with Name!", then a "+" line for each thing you got and a "-" line for each thing you gave, coins included), and Lazarus reads what each item went for: one kind of item for coins is the coins over its count, and in any other trade the one item with no price yet is worth the other side less the rest of its own. Each price is read clean of the item's upgrades (as last seen in your inventory), left out if it's under what an NPC pays (a gift, not a price), and shared through the Lazarus server, so every Lazarus user learns from every Lazarus user's trades. Both traders' mods report a trade under the same key, built from who traded what, so it counts once; trader names never leave your game. An item's trade price, the middle of its trades in the last 30 days, ranks after three or more auction sales and before everything else, which is how skins get a price. "Learn from trades" in the feature's settings turns it off.

### Fixed
- The Lazarus server kept only the bazaar and auction-median figures of the prices mods share, dropping the clean price, one or two sales' price and the cheapest listing that 1.19.0 added, so everyone fell back to the median of all sales until they read an item's history themselves. The server now keeps them. It deploys with this merge.

## 1.19.2

### Fixed
- An item with fewer than three sales was priced by its recipe before its one or two sales or its listings. Forge recipes learned by `/lazarus recipes index` priced the Wither Shield Scroll at its parts, under the 25M an NPC pays, while it had sold for 14B and was listed at 9B. Sales and listings now come before the recipe, which is used only when an item has no market at all, and one or two sales are capped by the cheapest listing.
- The value breakdown named every Wither blade scroll "Scrolls"; each now says which scroll it is.
- Dragon Stars were valued at 13% of the clean item's price each, measured on cheaper items, so five stars on a 27.7B Primordial Hyperion came to 17.85B. Stars are now valued by the Ritual Residue they take (the nth star takes n, 15 for all five) at the share of its price the market pays for them, fitted again over the same sales: 41%. Hot Potato Books (54%), Recombobulators (86%) and reforges (7%) are unchanged; Golden Hot Potato Books came out at 113%.

## 1.19.0

### Added
- Recipes: `/lazarus recipes index` browses the whole `/recipes` menu for you. It turns each page, opens every item whose lore says "Click to view recipe", waits for the recipe page to load, learns it and goes back, at a person's pace. A toast shows each page, and the chat says how many recipes were opened and how many were new. `/lazarus recipes stop`, or closing the menu, stops it. Recipes are shared through the Lazarus server as before, so one person's index fills everyone's book.

### Changed
- Item values take their upgrades into account. dragsim.co's price for an item is the median of all its sales, clean or upgraded alike, and its networth adds every upgrade at full price with nothing for stars or a reforge. Every recorded sale carries the item with its upgrades, so the share of each upgrade's own price the market actually pays was fitted over all 2,467 recorded sales (142 sales of 28 items with upgrades): Hot Potato Books 54%, Golden Hot Potato Books 91%, Recombobulator 86%, a reforge stone 7%, and each Dragon Star adds 13% of the clean item's price. An item's value is now its clean price plus those shares. Tested on 56 upgraded sales, leaving the sale out of the fit, the typical miss went from 38% (the site's median) to 20%, and the average miss from 82% to 34%.
- Upgrades that sold too rarely to fit (Fuming books, dyes, helmet and pet skins, Art of War, scrolls, enrichments, runes, drill parts, coatings) are counted at 85% of their own price; Fuming books follow the Hot Potato Book share. `tools/item-values/fit_item_values.py` fits the shares again as sales come in.
- Each sale is read as the clean item it would have been before taking the median, so an item's clean price isn't pushed up by upgraded sales.
- Almost every item has an estimated value now. When an item has no bazaar price and fewer than three sales, it is priced, in order, by this computer's own record of sales, what dragsim.co's networth said, its recipe (157 recipes shipped, plus every recipe learned in game, priced part by part up to four levels deep), one or two sales, then its cheapest listing; never below what an NPC pays. Pets in profiles are matched to their market names.
- The value tooltip says where the price came from, and on an upgraded item shows the clean price and what the upgrades add; hold Shift for each upgrade.
- Profile Viewer: the networth is worked out by Lazarus from dragsim.co's list of every item the profile holds, with each item's upgrades valued as above. The 40 biggest items have their own sale history fetched, and the figure updates as each arrives. Hovering it lists the biggest items and what dragsim.co says.
- Voidgloom Helper: the beams timer is now a 3-pixel red bar just under the readout, over a dark track, in place of the faint yellow hairline along its bottom edge.

## 1.18.0

### Added
- Copy Screenshots (General, on by default): each screenshot you take is put on your clipboard as an image, ready to paste into Discord, with a toast once it's there. Minecraft can't hold images on the clipboard itself, so the system's own tool does it: PowerShell on Windows, osascript on macOS, wl-copy or xclip on Linux. It watches the screenshots folder for new pictures rather than hooking the game's screenshot code, so it can't clash with other clients.
- Goop Bait Timer: drinking a Goop Bait ("The bait sinks in.") starts a readout counting down the time it says it lasts, with a bar running down under it. When it wears off you get a toast, a sound and a chat line; the sound and chat line each have their own switch. Drinking another starts the count again.
- Voidgloom Helper has a beams timer: when Broken Heart Radiation starts, a hairline under the boss readout runs down the 8 seconds the beams last, as the toasts' bar runs down theirs. It ends early if the boss's tag stops showing Radiation, and resets on a new spawn, kill or failed quest. Shown in the HUD editor's sample too; "Beams timer" in the feature's settings turns it off.

## 1.17.5

### Changed
- Every loot tracker (Boss Drops, Golem Loot, Goop, Crypt Caches, Slayers, Zealots) lists its drops by total value, highest first, rather than by count. Drops with no price come last, most common first. The HUD's "+N more" now hides the least valuable.

### Fixed
- The Goop Tracker booked any server line that arrived during The Disturbed's drop list as a drop, such as "OOPS! You don't have enough mana!" (shown as "4x OOPS!…" with a "?" price). A drop list line now has to read like a name and be a known item, or carry a count. The reward line's stats ("+5 ☘ Mining Fortune") were booked as drops too; only items on it count now. Lines and stats already booked are removed from your Goop totals when the game starts.

## 1.17.4

### Fixed
- Storage pages went blank in the overview after you moved an item out of or into them, until opened again. On leaving a page the server empties its slots a moment before the window switches, and 1.17.3 took that as the page being empty. A page whose slots all empty in one tick, which only the server can do, is now remembered as it was.

## 1.17.3

### Changed
- Chat messages: the brand's violet is now only on the word "Lazarus" (and "Lazarus+"). Headings, details and buttons are the game's dark purple, a boss or item in a heading keeps its own colour, and every green in a message is the game's green rather than the brand's.
- Storage Overview: remembered pages show the Rarity Lines mark under their items, as the open page does.

### Fixed
- Storage pages you had emptied kept showing their old contents. The mod waited for the game's "contents sent" mark before trusting a page, and Dragsim's storage pages never carry one, so an empty page was taken as not loaded yet. A page now counts as loaded once it shows anything or has been open a moment.
- Storage Overview: moving an item to another page waited for that same mark, so the move gave up after six seconds and left the item in your inventory. It now goes ahead as soon as the page has loaded, each window on the way gets twelve seconds, and if a page still doesn't open the notice says where the item is.

## 1.17.2

### Changed
- Storage Overview draws its pages a quarter bigger than the game's own slots; "Size" in its settings goes from 100% to 200%.
- Storage Overview: clicking anywhere on another page's card opens that page, every time. A click arriving while an earlier one was still waiting was dropped; it now starts over (only an item already on its way finishes first). A page that doesn't open within six seconds says so. With nothing on the cursor, a click on another page's items only opens the page, rather than also picking that item up.
- Storage Overview: an item on its way to another page stays on your cursor the whole time. The server still has to set it down in your inventory while the page switches (no mod can keep an item on the cursor across windows), but that slot is hidden and the item is drawn at the cursor until the page opens and it's picked back up.

## 1.17.1

### Changed
- Storage Overview shows at most three rows of three pages; scroll or use the arrows for the rest.

### Fixed
- Storage Overview: clicking an item on the page you're in, or in your inventory, did nothing. The window treated every click away from where the chest normally sits as a click outside it; it now counts the whole overview as inside.

## 1.17.0

### Added
- Storage Overview (Inventory, on by default): /storage shows your ender chest and every storage page at once, as they were the last time you opened them, with a search box that lights up matching items and dims the rest. A page you haven't opened yet says "Not visited", a locked one says "Locked", and hovering a page tells you when it was last seen. "Vanilla view" switches to the server's own menu and "Overview" switches back.
- The page that's open (and the ender chest) is the highlighted, live card, with your inventory beside it, so you move items in and out as usual. Click another page and it opens right there, the overview staying put; if you're holding an item it is still on your cursor when the page opens, ready to place anywhere. Clicking one of that page's slots does the click there too: pick up, drop into that exact slot, right click or shift-click. Each jump takes a moment while the server opens the page, and its card says "Opening" meanwhile. "Move by clicking" turns carrying items between pages off.

### Changed
- Remembered storage pages are kept per account (`storage/<uuid>.nbt`), so an alt no longer sees your main's pages. Pages remembered before this version carry over to the first account that opens storage.

## 1.16.0

### Added
- An item index on the Lazarus server: every dragsim.co item by id with the name, rarity and NPC price real items carry, which the site's catalogue sometimes lacks or names differently (every fragment is "Dragon Fragments" there; "Golden Fragment" in game). It starts from 955 items gathered from dragsim.co's catalogue, market and players' profiles (`tools/item-index/index_items.py`), and every Lazarus user's mod adds what it sees: items in any open menu (the /slayer boss drops menu included, scroll its pages once), items you carry, the market and profiles you open. A menu item with no id of its own but a rarity line is kept by its name. The mod fetches the index every three hours and keeps it in `cache/items.json`; what it saw beyond it stays in `cache/items-seen.json` until the server has it. This replaces `cache/npc-prices.json`.
- Skip Front View (General, off by default): F5 cycles first person → third person from behind → first person, leaving out the front-facing camera. Suggested by a player.
- Shared data: what one Lazarus user's mod learns is shared through the Lazarus server with everyone (`/shared/<kind>`): the price each item's dragsim.co history gives (so a value shows before your mod has read the history itself), recipes from the recipe menu, Quiz answers and ruled-out options, the blood room's wait (the countdown uses everyone's last hundred) and Revenant spawn tiles. Your own copies are still kept for when the server can't be reached. Only whitelisted players' findings are taken.

### Fixed
- Plural drops ("16 Gelatinous Membranes" on the Goop reward line) are matched to their item, so they're priced and coloured.
- `/drops` and other tracker lists in chat showed Epic and Mythic items in Lazarus violet; they now use the game's rarity colours like the HUD (Epic &5, Mythic &d).

## 1.15.0

### Added
- A "Drop rates" switch on Boss Drops, Golem Loot, Goop, Crypt Caches and Slayers hides the "(1 in 12)" / "(2.5 per kill)" note after each drop. On by default.

### Changed
- Prices come from dragsim.co's own history of each item (`/api/market/item/<id>`, every auction sale and bazaar quote the site has recorded over the auction window), fetched when an item is first priced and again every half hour, two at a time. Lazarus's own records, which only start when it is installed, are the fallback. The same rules apply: a bazaar ask with no bid is ignored, an auction value needs three sales. Warden Catalyst, for one, is ≈175M from 94 sales.
- NPC prices the dragsim.co catalogue leaves out (263 of its 921 items, Golden Eye among them) are read from the items themselves: their `item_data.npc_sell_price` in your inventory, on the market and in profiles you open. They're kept in `cache/npc-prices.json`.
- Loot trackers (Boss Drops, Golem Loot, Goop, Crypt Caches, Slayers, Zealots) value drops at the estimated price, which uses NPC, bazaar and auction history, instead of the NPC price alone, so a Golden Eye no longer shows "?" and Warden Catalyst isn't valued at its 10M NPC price. The "(NPC)" note is gone from Value and Profit.
- A drop's colour is the one the server wrote its name in on the drop line, which is its rarity; the catalogue's rarity is the fallback. That settles items that share one name in the catalogue, such as each tier of Combat XP Boost. "Bosses since last drop" rows and rare-drop toasts use the same colours.
- Profile viewer pets use dragsim.co's names ("Ender Dragon", not "Enderdragon") and levels, worked out from XP as the site does: most pets sit at 100, and a Golden Dragon climbs from 101 to 200. Items keep the name and colours the server gives them ("[Lvl 1] Goop" with the level grey), and an item whose name is its id takes the catalogue's name.
- The profile viewer's inventory, ender chest, accessories and pets grids are drawn smaller (at most 1.5x).
- Profile viewer tooltips follow dragsim.co's item cards. An item lists its own abilities when it carries them (a Hyperion's scrolls), else the catalogue's defaults, rather than every ability the catalogue knows for it; a full set bonus shows the pieces worn ("Old Blood (4/4)"). Upgrades are listed under the stats (Hot Potato Books, Final Synchronizers, The Art of War, reforge, enrichment), and a reforge's bonus text (Sparkling, Gilded…) follows the abilities. Pets show their category, the stats they give at their level and their abilities' lore at that level and rarity.
- Lazarus's own purples are dark purple (§5) like the game's: the Goop title, the Voidgloom readout and its default highlight, the slayer spawn timer, staff chat, the roll readout and History on Look warnings. The brand violet stays.

### Fixed
- The profile viewer's networth is now dragsim.co's own (`/api/networth`), the figure the site's profile page shows. The site prices upgrades, recipes and pets from market history, so Egg_man_421 reads ≈149B there against ≈34B from the local estimate. Hovering it lists the eight items worth most, with stacks of one item added together. If the site doesn't answer, the local estimate stays. A hidden inventory shows as Hidden.
- The profile's selected accessory power (Strong, Sighted…) was labelled "Magical power"; it's now "Accessory power".

## 1.14.0

### Changed
- Loot trackers show each drop in its in-game rarity colour, with what it's worth right-aligned on its row and the session's total value at the bottom; Profit, Per golem and Per boss line up the same way. Lists in chat put the value after a dot.
- "Kills since last drop" (Golem Loot) and "Bosses since last drop" (Slayers) are off by default, each with its own switch.
- Hovering the networth in the profile viewer lists the eight items worth most, with their count and value, so a wrong price shows where it comes from.
- The Zealots tracker's per-hour rates, Profit/h included, are off by default and come back with "Per-hour rates".

### Fixed
- The Roll readout showed "miss" on rolls that dropped (a Warden's Golem Upgrader, a Hot Potato Book): the server only writes HIT on fixed-odds items, so a roll that names its item now counts as landed and only "miss" counts as a miss.
- The Goop timer started when someone opened the rift ("has woken something under the altar"); it now starts when goop is squeezed into the altar.
- Voidgloom Helper's on-screen readout often stayed empty or stuck at full health. It now keeps your boss's body rather than one tag, reads every Seraph tag over it each tick and shows the lowest health (a T5 carries a second tag that stays full all fight), waits half a minute instead of 15 seconds for your boss to appear, and claims it outright when the server floats a "Spawned by: you" line with it, the way SkyblockAddons finds a slayer boss. The readout also says when beams are up.
- Networth could read in the trillions (≈136,215B on one profile). Price estimates now ignore a bazaar ask with no bid or one over 3x the bid (using the bid), and an auction price needs at least three sales in the window, since one or two can be a coin transfer such as a cheap item "sold" for billions. Below that the NPC price is used.
- Golem Loot stopped counting Wardens and Protectors (and their drops) whenever Boss Drops was switched off, which closing the Dragon Drops readout in the HUD editor does. Boss Drops now reads golem fights for Golem Loot even while it's off; only its own dragon counts stop.
- Golem fights and drops are written to the game log, and a boss box the tracker can't read is logged whole, so a Warden that isn't counted can be traced.

## 1.13.0

### Added
- Punishment ladder on the ban and mute card (staff panel and profile drawer): pick the rule (1.0 Cheating, 2.0 Chat Abuse…) and the reason fills in as "1.0 - Cheating Through Unfair Advantages", with the duration for a 1st, 2nd, 3rd… offence as chips. A rule with one duration (Duping, Advertising: perm) fills it too; "set" rules leave the time to you. Hovering a rule shows its full name and steps. The rules are two Staff Tools settings, "Ban rules" and "Mute rules", so times can change without a release.

### Changed
- Expanding a feature in the menu shows its whole description, wrapped, above its settings, instead of cutting it off.

## 1.12.0

### Added
- Stay in Staff Chat (Staff section): when you were in staff chat mode and change server, the toggle is sent again a moment after you arrive, so you come back in it. Whether you're in it is read from the toggle you send and from the server's reply, both editable (toggle command, "server says on/off" patterns), with a wait setting and a switch for logging back in.
- Staff Chat Panel plays a sound when another staff member talks in staff chat (pling, bell, chime or none; a setting adds your own lines too), and scrolls: with the chat box open, the wheel over the panel moves back through the last hundred staff lines, and only the panel (the main chat stays put); closing the chat box returns it to the newest.
- Message Sound (Chat section): a sound when a private message arrives (the server's "From Name:" line; "To Name:" lines optional, with a toast option). Chat spy lines never count.
- Command Binds holds the mod's own binds too, each with its command to change: Wardrobe (Alt + W), Look-up (Alt + E), History (Alt + H), Captcha (Alt + C) and Server hop (unbound). In a bind's command `%p` is the player you're looking at and `%me` is you. The look-up command left empty opens the staff panel for staff and the profile for everyone else. `/lazarus captcha <player>` sends a captcha by name.

### Fixed
- Clicking a name in chat for the staff panel failed on players whose rank or name is a gradient (one colour per letter): the name is now found across the pieces, so every DRAGON-ranked name is clickable too.
- The report card's heading is red and plain, and the alts card's heading plain, instead of bold violet.
- The tab list never showed a nicked player's real name or a watched player's ⚠: the badge took the name first and ended the rewrite, so the staff marks never ran. Both are now added in one pass.
- Players with Lazarus who aren't whitelisted had no grey mark in the tab list on Dragsim, whose tab cells are its own and are matched by name: a grey player's name now counts too, with a Lazarus user's name winning over anyone grey claiming it.

## 1.11.0

### Added
- Staff panel (Staff section, "Staff Panel from Chat"): clicking a player's name in chat opens a panel for them. The left side is a report: rank, whether they're online or nicked, watch note, open reports against them, their alts and their punishment history (the last two asked of the server quietly). The right side has Ban, Mute and Kick (duration and reason chips, a ban is held to confirm), Go to them, Bring here, Captcha, Watch and View profile, which opens the normal lookup. The server's reply to an action shows along the bottom. `/lazarus staff <player>` opens it for anyone; alts in the report open their own panel.
- Staff Chat Panel wraps long lines and has a width setting; the rank before each line takes its rank colour.

### Changed
- Chat buttons are underlined rather than bold, so they read as links; a player's name in a card is a Bone button that opens their profile, and anything that punishes is an orange button. The line bar beside multi-line messages is thinner. No ⚑ after chat lines any more: the name itself is the button, and the punish card it opened is gone.
- Rank Colours in Chat colours staff names on every server line (player lists such as `/glist`, joins, broadcasts), not only in what players say.

### Fixed
- One feature hiding a chat line stopped every feature after it from reading that line, so a quiet `/history` could leave the staff chat panel or a report inbox a line short.
- Quiet server asks (`/history`, `/alts`, captcha) only keep lines that look like the answer or name the player, so other server lines arriving in the same seconds are no longer hidden with it; a player talking is never swallowed.
- An alts answer that isn't the server's list (an error, say) is no longer read as a list of names.

## 1.10.1

### Fixed
- Nick Reveal asked the server for the nick list every second when nobody was nicked, filling chat with "No online players are nicked". That answer now counts as the list, is kept out of chat, and the list is asked for only every few minutes.

## 1.10.0

### Added
- Ten features for the Staff section, each with its own switch and shown to Dragsim staff only:
  - Punish from Chat: a small ⚑ after every player's chat line opens a card for them with ban and mute durations, kick, and profile, history and alts buttons; a duration leads to the reason chips, and a chip sends the command. `/lazarus punish <player>` opens the card for anyone.
  - Report Inbox: each report line the server sends becomes a card with the reporter, target and reason and Profile, Punish, Teleport and Done buttons; open reports are counted on the HUD, listed by `/lazarus reports`, and kept between sessions. The report line is matched by an editable pattern.
  - Watch List: `/lazarus watch add <player> [note]`; watched players carry a red ⚠ in the tab list, over their head and after their chat lines, you're told when they join or leave, and server lines naming them are flagged.
  - History on Look: Alt + H shows the punishment history of the player you're looking at as one card (the server's `/history` asked quietly): a count of each kind, then each punishment with its date, reason and whether it expired or was undone; remembered for ten minutes. Also `/lazarus history <player>`.
  - Alt Flag: opening a profile with the staff drawer asks the server's `/alts` quietly, and the answer comes back as a toast and a card whose names are profile buttons; also `/lazarus alts <player>`.
  - Nick Reveal: the server's nick list is asked for every few minutes, and nicked staff show "(RealName)" after their nick in the tab list, over their head and in chat; `/lazarus nicks` lists them.
  - Staff Chat Panel: the last few "Staff >" lines on their own HUD panel, optionally kept out of the main chat, and a key that opens the chat box with the staff chat command typed.
  - Roll Readout: the staff "[Debug]" roll lines as a HUD readout (mob, item, roll, HIT or miss, another player's name when it's theirs) and out of chat.
  - Captcha Key: Alt + C sends a captcha to the player you're looking at, the server's reply as a toast, and the pass or fail line minutes later as a green or red toast.
  - Rank Colours in Chat: a staff member's name said in chat takes their rank's colour (owner blue, admin red, manager dark red, mod green, helper aqua; editable) in bold, the way your own name is picked out. Ranks are learnt from the rank before their chat lines and from the dragsim.co lookup, one player online a second, remembered for a day.
  - Server Hop: `/lazarus hop` puts a row of server buttons in chat (hub, end, rift, dungeons, crypts, limbo, editable), each sending you there; `/lazarus hop <server>` goes straight away.

### Changed
- The profile's rank tag takes its staff colours from the same table as the chat colours, so the owner's tag is blue there too.
- A Staff feature only runs for Dragsim staff, whatever its switch says, so nobody else's game ever sends staff commands.
- When dragsim.co can't be reached, the staff check waits a minute before asking again instead of asking on every call.

## 1.9.0

### Added
- A Staff section at the end of the menu, after its own gap like Lazarus+, that only Dragsim staff (Helper and above, by your rank on dragsim.co) see. Search shows its features to staff only. It starts with Staff Tools: the profile drawer's ban, mute, kick, teleport, tunnel, send, captcha, history, alts and refund buttons, with the duration and reason chips.

### Changed
- The Profile Viewer's "Staff buttons" switch and its chips moved to Staff Tools; chips set before are back at their defaults.

## 1.8.0

### Added
- Auto Sprint: holds the sprint key for you while you walk forward. The game still decides whether you can sprint, and the key goes back to your own finger when you stop or switch it off. Off by default.
- Rift Effect Remover: takes off the Darkness the Rift puts on you under five minutes and the Nausea under one, on your own client before each frame, so neither the dimming nor the wobble is seen. Each effect has its own switch, and "Only in the Rift" can be turned off to remove them everywhere.

## 1.7.0

### Added
- Zealot Tracker keeps all-time totals beside the session, with a Period setting like the other trackers.
- Slayer Tracker counts the rewards the server gives with no chat line (Null Ovoid, Null Atom, Revenant Viscera, Foul Flesh) from what you carry before and after each kill; the list is a setting.

### Changed
- One loot reader for every tracker (Boss Drops, Golem Loot, Goop, Crypt Caches, Slayers, Zealots): each chat line is read once, a "BONUS!" line is a reward of its own, and the staff roll line that names the mob a tick before its drop is announced tells a zealot's eye from a slayer boss's or the Protector's. Players without staff see no roll lines; the trackers then fall back to timing, as before.
- The trackers share one tally (session and all time, drops, dry streaks) and one set of HUD rows, so each file holds only what is particular to its boss. Saved totals from earlier versions are read as they are.

### Fixed
- Dragon kills were never credited: the server writes "» You placed a Summoning Eye!" and the tracker looked for the line without the arrow. The loot summary the server sends you now also proves the kill, so a dragon counts even when the eye line was missed.
- A Summoning Eye or Golden Eye that a Riftborn Seraph dropped a moment before "SLAYER BOSS SLAIN" was counted as a zealot's. Drops now wait a moment for the kill line, and go to the slayer tracker when it comes.
- Goop fights read your share of the damage from the end box ("13.8%, Position #3"), not only from the separate "You did N%" line.
- A tracker's all-time file is saved on leaving a server as well as on quit and once a minute.
- Protector and Warden fights are read from the end box when it carries your damage share ("9.1%, Position #2").
- A dragon whose de-spawn line was missed is still credited from its loot summary a minute or more after the last kill.
- A staff roll that missed no longer names the mob for the next drop of that item, and a roll that landed is used once.
- A slayer boss's silent reward is not counted when nothing was seen of your inventory before the kill.
- The zealot and slayer trackers use one rule for which drops near a slayer kill are the boss's, so an eye is never counted by both.

## 1.6.0

### Changed
- Every message starts with "Lazarus ➜" (a heavy round-tipped arrow in Fog), with "Lazarus" in Violet and the Lazarus+ plus in Halo gold, as the brand guide sets out. Plain text is Bone rather than pure white, headings are bold Violet, and secondary notes are Fog.
- Every message is reworded in one voice: short and polished, one sentence that says what happened ("Your Lazarus+ plan has ended.") and one call to action on a button. Reassurance and explanation ("It doesn't renew by itself", "Your settings are kept") are gone. This covers the join and welcome messages, every Lazarus+ notice and /lazarus plus, updates, invites and Discord linking, and the feature messages and toasts. The server's Lazarus+ notices (ending soon, ended, a late refund) are now worded by the mod, so the words no longer depend on a worker deploy.
- /lazarus changelog groups its lines under New, Improved and Fixed, each "Feature  one sentence", instead of one run of mixed markers.
- Tracker listings in chat (/drops, /golem, /goop, /voidgloom, /revenant, /lazarus caches) keep the HUD's layout and colours: grey labels, green coins, an item's rarity colour, instead of plain white rows.
- "No kills recorded (session)" style notes read as sentences: "No Goop fights this session." and "No Goop fights recorded yet."
- /lazarus changelog has a bold heading and quieter bullets.
- Buttons in chat are bold underlined Violet text with no brackets, instead of blue text.
- The copy-chat toast shows a preview of what was copied again.
- Toasts rise into place with a fade instead of sliding in from the right.
- British spelling throughout: catalogue, colour.

## 1.5.1

### Changed
- Ability Cooldowns has an "Ignored items" list, never timed whatever their lore says. It starts with Golden Cannon, Golden Gun and Terminator.
- A Debug switch on Ability Cooldowns says in chat why each timer did or didn't start, for diagnosing an item.

### Fixed
- Blinding Sword (and any item with several abilities) could fail to start its timer: an ability section with no Cooldown line counted as "zero seconds" instead of letting the catalogue and the built-in fallback answer.
- Right-clicking during a cooldown no longer restarts the timer. The press waits half a second for the server's "on cooldown for Ns" refusal, which corrects the timer; only a press the server took restarts it.
- The server's "on cooldown for Ns" line was only read when it contained a colon, so it never corrected a timer.

## 1.5.0

Needs the new Lazarus server, which now deploys itself: merging to main deploys the worker once the `CLOUDFLARE_API_TOKEN` secret and the `CF_ACCOUNT_ID`, `CF_KV_ID` and `CF_D1_ID` variables are set on the repo (`tools/lazarus-server/README.md`, "Deploying"). This mod gets its badges, capes and live Lazarus+ from that server, so release the two together.

### Added
- Live updates: every Lazarus mod keeps one connection to the Lazarus server. Capes, the gold Lazarus+ badge and the Lazarus badge change for everyone within a couple of seconds, and appear only while that player is online with the mod.
- A grey Lazarus badge for players who have the mod but aren't whitelisted. They see everyone's badges and capes too.
- Your own Lazarus+ changes arrive straight away, for you only: a purchase, a gift, the owner's grant or revoke, a refund or a chargeback. No restart needed.
- The menu ends with its own Lazarus+ section, after a gap: Lazarus+ and Capes, open to everyone and locked without Lazarus+. Every Lazarus+ feature lives there, and search shows them only to members.
- A red BANNABLE badge on Bonemerang Chain, which now lives only under Lazarus+.

### Changed
- Every message starts with "Lazarus ➔", or "Lazarus+ ➔" with a gold plus for Lazarus+ messages, and a message over several lines gets one prefix and a purple bar. Green is success, red an error, orange a warning, white information and purple the details; clickable text is blue and says what it does when you hover over it.
- The welcome reads "Lazarus+ ➔ Welcome to Lazarus+! Thank you for your support."
- New join messages on every game start: a welcome with how to verify on the Lazarus Discord and how to become a member for players who aren't whitelisted, and a greeting with the menu key, your Lazarus+ status and a Discord prompt for Lazarus users.
- The Discord setup message walks through verifying in four steps.
- When the connection drops, the mod checks in every minute and connects again on its own, and notices a connection that has gone quiet.

### Fixed
- Badges and capes no longer stay on players who have left, and players don't vanish from the list while they're still online.
- A gift to every Lazarus user reaches everyone online at once, however many there are.
- A cape, whitelist or invite change reaches everyone even while the server's storage is catching up, and a newly invited player shows as a Lazarus user straight away.
- Only whitelisted players' names are trusted, so nobody can put a badge on someone else's name.

## 1.4.0

Needs the new Lazarus server and website: paste the new `worker.js` into Cloudflare and Deploy, deploy `tools/plus-site`, and give both Stripe restricted keys (test and live) the *Refunds: Write* permission.

### Added
- Buy Lazarus+ for anyone: on plus.lazaruss.co, type a Minecraft name and press Check. It says yes or no, shows the player's skin, and the pass goes to that account even if they change their name before paying. /lazarus upgrade opens the page with your own name already checked.
- A Lazarus+ category in the menu, only for members, listing every Lazarus+ feature. They're the same features as in their home categories, and search shows each once.
- Welcome to Lazarus+ shows in game whenever days arrive, from your own purchase, someone else's or an owner's gift, and at your next login if you were offline. Every Lazarus+ message shows once per account, across all your computers.
- Lazarus+ checks with the server every 10 minutes while you play (every 30 seconds for 30 minutes after /lazarus upgrade), so purchases, gifts, refunds and disputes show up without restarting.
- Owner commands: /lazarus plus remove, /lazarus plus lookup (end date, paused or blacklisted, and the full history), /lazarus plus unblacklist and /lazarus plus unblacklistemail. /lazarus plus giftall now takes a gift name, e.g. /lazarus plus giftall launch 7, and running it again only fills in players who missed it.

### Changed
- Reminders come at 7 days and 1 day left (was 3 days and 1 day), and when it ends, once per account. Coming back after it ended shows only the "ended" message. Buying or being gifted more time resets them.
- Lazarus+ works offline until your end date, as long as the mod reached the Lazarus server in the last 7 days and your computer's clock hasn't gone backwards. Otherwise Lazarus+ pauses with a message saying why and comes back on its own once the server answers.
- Lazarus+ features are locked for players without Lazarus+ (they were open to everyone until Lazarus+ went on sale).
- The /lazarus plus manage link is view-only and opens once: refreshing or closing the page needs a new link.
- Checkout asks you to agree that the pass starts straight away, and the Terms say purchases are final except where the law requires otherwise. Refunds are for faulty passes, double charges and gifts to the wrong account.
- A full refund takes 30 days off; a partial refund takes nothing.
- A payment that confirms more than 14 days after checkout is refunded automatically, and the player is told in game.
- A chargeback pauses the pass while it's open and blacklists the account and the buyer's email from buying or receiving Lazarus+. If the owner wins, the pass carries on as it was; if the buyer wins, 30 days come off.

### Fixed
- Purchases, refunds and gifts that land at the same moment, or are sent twice by Stripe, can no longer lose or double days.

## 1.3.0

### Added
- Name badges over players' heads: a Lazarus user's name tag shows the Lazarus mark in front, with the gold plus for Lazarus+, the same as the tab list. This also works on name tags the server draws itself, like Dragsim's. Only Lazarus users see the badges. The setting is now General > Name Badges (it was Tab List Badges) and covers both.

## 1.2.2

### Changed
- Lazarus+ now costs US$4.99 for 30 days (was US$8.99). The mod's Lazarus+ page and /lazarus plus, the website and the Terms say the new price, and Stripe charges it once the Lazarus server uses the new price ID.

## 1.2.1

### Changed
- Releases in Discord: the bot posts just the jar in #releases, without the release notes embed, and deletes its previous jar post so only the newest is there.

## 1.2.0

### Added
- Discord: verifying sets your server nickname to your Minecraft name, and the bot keeps it current when you change your name. Unlinking removes the nickname the bot gave. The bot needs the Manage Nicknames permission; it can't rename the server owner or anyone ranked above it.
- Every release's jar is posted in #mod in the Lazarus Discord by the bot, with the release notes and a link to the release. Needs the bot token as the DISCORD_BOT_TOKEN secret on the source repo.

## 1.1.2

### Fixed
- Discord: the slash commands are registered as soon as the Interactions Endpoint URL is saved, instead of waiting for the first cron run (a new cron trigger can take up to 30 minutes to start).

## 1.1.1

### Changed
- Discord: Unverified now means a linked Minecraft account that isn't a Lazarus user yet. Members who haven't linked get no Lazarus role, and /unlink takes the roles away rather than giving Unverified.

## 1.1.0

### Added
- The Lazarus Discord bot gives out roles: Verified for Lazarus users, Lazarus+ for members with a pass, Unverified for everyone else. To link, type /lazarus discord in game for a code, then press Verify in the Discord and enter it. Codes last 10 minutes and work once. Roles follow the account on their own: every 5 minutes, and straight away after a purchase, an invite being used or a whitelist change.
- Discord commands: /verify (same as the button) and /unlink for everyone; /setup (posts the Verify message), /sync and /lookup for staff with Manage Roles.
- A once-a-week reminder in the welcome message while no Discord account is linked. Inactive accounts can link too, so their role updates once they're invited.
- The bot runs inside the Lazarus server (tools/lazarus-server), so nothing else needs hosting. It stays off until it's set up as in that folder's README; this release is safe to install before then.

## 1.0.5

### Fixed
- Links from the game to the Lazarus+ website (/buy, /thanks, /manage) and the Terms, Refund Policy, Privacy and Contact pages opened as "page not found" when the site was hosted as a Cloudflare Worker. Each page is now its own file, so they load on any static host.
- The Lazarus server's code no longer shows a type warning in the Cloudflare editor.

## 1.0.4

### Added
- /lazarus plus giftall <days> (owner only): gives every Lazarus user that many days of Lazarus+, for a launch gift. Each gets a gift entry on their Manage page, and the days add to any pass they already have. Needs the Lazarus server redeployed with this version's worker.js.

## 1.0.3

### Changed
- Refunds are asked for by email to support@lazaruss.co. The Manage page's refund button and form are gone, as are the Lazarus server's refund request endpoint and /lazarus plus requests. The Refund Policy, Terms, Privacy Policy and Contact page say so.
- No personal details in the mod or on the website. The site names its owner only as "Lauchy", with no location or ABN. The jar no longer carries the old whitelist.json or the owner's account IDs: the Lazarus server now tells the mod whether you're the owner when it checks your account (/verify answers "admin": true), which shows the owner's commands.

### Removed
- The GitHub whitelist fallback (whitelist.json in Lazarus-releases and its copy in the jar). The Lazarus server has decided who may play since 0.5.1.

## 1.0.2

### Added
- The Lazarus+ website (tools/plus-site), for Cloudflare Pages at plus.lazaruss.co: the landing page, buy (with the Terms and Refund Policy agreement), thanks, and manage (time left, payments, refund requests), plus the Terms, Refund Policy, Privacy Policy and Contact pages. No logins, cookies or trackers.

### Fixed
- Tab list badges show on servers that draw their own tab list cells, like Dragsim. 1.0.1 only matched real player entries by account, so no badge appeared there. Badges now also match a Lazarus user's name inside a cell. The Lazarus server lists names with the badges and keeps each player's current name. A game test checks the badge is added and its font loads.

## 1.0.1

### Added
- Tab list badges: the Lazarus mark before every Lazarus user's name in the tab list, and the same mark with a gold plus for Lazarus+. On by default (General > Tab List Badges). The Lazarus server lists who (POST /badges, Lazarus users only).

### Changed
- The menu's Lazarus+ page and /lazarus plus list the same perks as the Discord banner: all 56 capes seen by every Lazarus user, exclusive features and cosmetics, new Plus features first, the gold plus in the tab list.

### Fixed
- Lazarus+ features stay open to everyone until the Lazarus server sells Lazarus+. 1.0.0 locked them as soon as it was installed, before anyone could buy it. The Lazarus+ page says it's coming soon, and /lazarus upgrade says it isn't on sale yet instead of "not found".

## 1.0.0

### Added
- Lazarus+, a 30-day pass for US$8.99, bought once on plus.lazaruss.co. It never renews and there is nothing to cancel.
- /lazarus upgrade opens the site, already signed in as your Minecraft account, and Lazarus welcomes you in game as soon as the payment goes through. Buying again while you have it adds 30 days (up to 60 days ahead).
- /lazarus plus shows how long you have left, with links to extend or manage it. /lazarus plus manage opens your payments and the refund request form.
- A Lazarus+ page at the top of the menu: time left with a bar, the buttons to buy, extend or manage, what's included, and the terms.
- A gold + beside Lazarus in the menu title while you have Lazarus+. Hover it for the time left.
- Reminders 3 days and 1 day before Lazarus+ ends, and when it does, since nothing renews on its own.
- Owner tools: /lazarus plus grant <player> <days>, revoke <player>, check <player> and requests (refund requests waiting).
- Lazarus+ server side: the Lazarus server sells the pass through Stripe. Only Stripe's confirmed payment adds days; refunds and chargebacks take them back. Records are kept in a database that can't lose a payment.

### Changed
- Capes are now Lazarus+. Everyone can still browse them and see members' capes; wearing one needs Lazarus+. Each cape has a small gold plus on its front so it reads as a Lazarus cape.
- Some features are now Lazarus+ (Bonemerang Chain for now). Public copy only says "exclusive features and cosmetics".
- Lazarus+ features show a gold + by their name. Without Lazarus+ their switch is faded, and clicking it opens the Lazarus+ page. Your settings are kept, so they come back as they were.

## 0.6.1

### Added
- /lazarus invites (owner only) lists every unused invite code, who made it and when, with a [cancel] button on each. /lazarus invites cancel <code> does the same. A cancelled code stops working, and its maker gets the invite back to make a new one.

## 0.6.0

### Added
- Invite only: Lazarus users type /lazarus generate for a one-time invite code (click it to copy) to give one person. Every player gets one invite; asking again before it's used shows the same code. The owner's accounts have no limit.
- New players see that Lazarus is invite only and their account is inactive, and activate it with /lazarus redeem <code>. A restart then loads Lazarus, and they get one invite of their own.
- /lazarus whitelist list shows who invited each player. Removing a player also cancels their unused invite code.

## 0.5.11

### Fixed
- /lazarus update installs the newest release even when you are several versions behind. It could pick the release one before the newest (all releases share a date), so updating went one version at a time.

## 0.5.10

### Changed
- Lazarus is now proprietary, all rights reserved (it was marked MIT). The jar carries the full terms (LICENSE_lazarus), its mod info says so, and a copyright notice is kept in the code itself and printed to the log at start: whitelisted players only; no copying, redistributing, decompiling, reverse engineering (AI tools included), modifying or bypassing access without permission.

## 0.5.9

### Changed
- The Lazarus server is reached at mod.lazaruss.co only; the second workers.dev address is gone from the mod.

## 0.5.8

### Fixed
- Menu: binding a key to a letter (say I) no longer also types it into the search, which jumped out of the page you were binding on.

## 0.5.7

### Added
- HUD editor: every readout has a small red × on its top-right corner. Clicking it switches that feature off (a toast says so); turn it back on in the menu.

## 0.5.6

### Changed
- `/l` staff panel only appears for staff: your rank on dragsim.co must be Helper, Mod, Admin, Manager or Owner (checked through the site's player API, at most every ten minutes). Others get the profile without it, and /nicklist isn't run for them.

## 0.5.5

### Changed
- Hot slots: shift-clicking an item with a hot slot now swaps it in even when that hotbar slot holds an item without a hot slot; that item takes the clicked one's place in your inventory (and stays there when shift-clicked, as it has no slot of its own). An item there with a different hot slot is still left alone.

## 0.5.4

### Added
- `/l` pets and equipment look as they do in game. Lazarus now remembers how pets and items appear in any menu you open (model, head skin, name and full lore, without the menu's "Click to…" hints), kept in config/lazarus/looks.nbt. A pet of the same type, rarity and level you've seen shows exactly as the game drew it, and an item you've seen yourself (same item UUID) shows as that very item. Otherwise the item takes the look of one like it you've seen, with its own name and stats from dragsim.co. Open your pets menu and equipment once to teach it.

### Fixed
- `/l` staff drawer: it scrolls with the mouse wheel (with a thin scrollbar) when its buttons don't all fit.
- `/l` staff actions on a nicked player: To them, Here and Tunnel use the name they go by on the server (read quietly from `/nicklist` when you run `/l`, else found in the tab list by their UUID), so they no longer fail with "can't find that player". Every other action still uses the real username.
- `/l <nick>` opens the real player's profile: `/l wurm` opens WurmFart's when /nicklist says WurmFart is nicked as wurm.

## 0.5.3

### Added
- `/l` staff drawer: a Server row with **Tunnel** (`/join <player>`, to go to their server), **To mine** (`/send <player> <server>`, sending them to the server you're on: end, rift, dungeons, hub or crypts, read from your sidebar) and **Limbo** (`/send <player> limbo`). To mine says so instead of sending when the sidebar doesn't show where you are.
- `/l` staff drawer: a **Refund** button (`/refund <player>`).
- `/l` staff actions show their outcome: a card along the bottom of the profile shows the command, waits up to 4 seconds for the server's reply and then shows it (green, or red when it reads like a failure, or "No reply from the server."). Copying a name or UUID shows there too. With the profile closed, the reply comes up as a toast.

### Fixed
- Hot slots: items that look identical (same name and lore, like bones) shared one binding, so `/lazarus slot <n>` on one bone moved every bone. A hot slot now belongs to the exact item you hold when the game gives it its own UUID; items without one still share. Old bindings keep working until you reassign them. `/lazarus slot debug` shows the item's UUID.

## 0.5.2

### Fixed
- 0.5.1 turned everyone away: Mojang refuses the Lazarus server's account checks because it runs on Cloudflare. Accounts are now proven with the Mojang player certificate the game already has (the one chat signing uses): Mojang signed it for your UUID, and the mod signs each request with it, so the server checks both signatures itself and never calls Mojang. `/lazarus whitelist` looks players up from your game for the same reason. Needs the updated worker.js redeployed.
- On joining, a short **Lazarus** notice: "Welcome <name>, your session has been validated successfully." when you're in, or "You are not whitelisted to use this mod. Contact Lauchy on Discord for more information." when not, each with the account's name and UUID beneath. Why a check failed goes to the log.

## 0.5.1

### Changed
- The Lazarus server is live at mod.lazaruss.co: the whitelist is now checked there through Mojang (whitelist.json is no longer used), and Lazarus users see each other's capes.
- The whitelist goes by UUID only, so a renamed account keeps its access and a name can't stand in for one; `/lazarus whitelist list` shows each player's current name. Add or remove by name or UUID. (Needs the updated worker.js redeployed.)

## 0.5.0

### Added
- Capes (new Capes section in the menu): wear any official cape: every Minecon cape, the Mojang staff capes, event capes and the test and unused ones (56 in all). Click a tile to put it on, and again (or Take off) to remove it. It's cosmetic: the server and players without Lazarus still see your real cape, while other Lazarus users see your pick (shared through the Lazarus server, refreshed about once a minute). You see it in third person, the inventory and your own `/l` card. Textures download once from Mojang's texture server (the wiki for the few never uploaded there) and are kept in config/lazarus/capes. Needs Cape switched on in Skin Customization. Elytra take the cape's design like a real one.
- Lazarus server (tools/lazarus-server): a small free Cloudflare Worker that holds the whitelist and everyone's capes. The mod proves your account through Mojang, the same check a server does when you join, so a faked UUID or name no longer gets in, and the list is private. If the server can't be reached, a pass it signed in the last 3 days still lets you start. Takes over from whitelist.json once its address is built in.
- `/lazarus whitelist list|add|remove <player>`: manage the whitelist in game. Only the owner's accounts may, and the server enforces it.
- Profile viewer: a Networth estimate heads the Purse section: purse and bank plus every item in the inventory, ender chest, storage, accessory bag and wardrobe at the price database's estimate (NPC price when the market hasn't been seen). Pets aren't priced yet. When under 80% of items have a price, it says how many did.

## 0.4.0

### Added
- Slayer Tracker (Slayers): Voidgloom and Revenant kills by tier (Riftborn and Atoned Horror count as T5), failed quests, every drop with how often it comes, its NPC value, and how many bosses since each rare drop, with a toast when one lands. Only your own quest prints "SLAYER BOSS SLAIN", so every kill counted is yours; drops landing within a few seconds of it are credited to it. A HUD card shows the boss you're on; `/voidgloom` and `/revenant` list everything (add `session` for this session, `reset` to clear it). The rare drops to watch are editable.

### Changed
- Profile viewer: item grids are drawn at about 80% of the size that would fill the panel (at most 2x) and centred, instead of filling it edge to edge.

### Fixed
- Voidgloom Helper finds your boss again, so the readout, boss box, tracer and Yang Glyph marks show. It used to claim the boss only within 3 seconds and 3 blocks of the spawn marker's particle spot, which was often missed. Now it takes the nearest Seraph within 10 blocks in the 15 seconds after your own "SLAYER BOSS SPAWNING". The T5 Riftborn Seraph's tag (no tier) and a single "Hit" left are read too.

## 0.3.2

### Fixed
- Key binds: holding Ctrl, Alt or Shift before pressing the key binds the combo (e.g. Alt + S). Pressing the modifier first used to bind the modifier on its own. A modifier pressed and released alone still binds by itself.

## 0.3.1

### Fixed
- Update now no longer prints the jar's full path in chat after downloading.

## 0.3.0

### Added
- Profile viewer item tooltips match the in-game lore: stat totals with their reforge, potato book, star and dungeon parts, the item's description, full set bonus and abilities, the applied skin or dye, and the rarity line (wrapped in scrambled letters when recombobulated, with DUNGEON and the item type).

### Changed
- Versions follow major.minor.hotfix and are bumped on purpose: a batch of features raises the minor version (hotfix back to 0), fixes raise the hotfix. Releases are named `Lazarus 0.3.0` and tagged `v0.3.0-<CI run>`; the run number only orders releases for the updater, so builds from before this still update, and chat shows the plain version.

### Fixed
- Protect Placed Eyes only guards End altar frames. It used to swallow a repeat right-click on any block in the End, so spamming a Hyperion while looking at the ground had its ability blocked.
- Rarity lines no longer show through item tooltips in inventories and chests: they're drawn with each slot now, so the tooltip covers them.

## 0.2

### Added
- Hot Slots: an item that has a hot slot now shows a "Hot slot: N" line in its tooltip (can be switched off).
- `/lazarus slot debug` prints the held item's custom-data tag and hot-slot key, to work out what distinguishes two visually identical items.
- Profile viewer: the Overview shows the player standing at an angle, NameMC style, in their armour and holding their first hotbar item. They turn to follow the mouse while it's over them. Their real skin shows when they're on the server; otherwise it's the default skin.
- Category switches: each category's page in the menu opens with a master switch that turns every feature in it off or on. Features keep their own switches, so turning a category back on restores them as they were. Switched-off categories read faint in the sidebar.
- The menu shows the Lazarus version above a Check updates button. It checks right away and, when a newer build is out, becomes Install update (the same as `/lazarus update`).
- Name Mention (Chat): when someone says your name, it's highlighted in gold in their message, a sound plays (Wither spawn by default; Dragon growl, Anvil, Pling or none) and a toast says who. Add nicknames under Other names. Your own messages don't count.
- Goop Tracker (End): the Rift's Goop boss. While it runs, a HUD shows its form (1 of 5) and the fight time. It also counts fights, paid fights, goops you placed, your average damage share and place, and loot with drop rates. `/goop` lists all-time totals, `/goop session` this session, `/goop reset` clears the session; saved in `config/lazarus/goop.json`.
- Hot Slots (Inventory): give an item its own hotbar slot. Hold it and run `/lazarus slot <1-9>`. Shift-clicking that item in your inventory then puts it in that slot when the slot is empty, or swaps it with the item there if both share that hot slot. Otherwise shift-click works as normal. `/lazarus slot` lists them and `/lazarus slot clear` removes the held item's. Items are matched by catalog id, else by name, and saved in `config/lazarus/hotslots.json`.
- Rarity Lines (Inventory): a thin line under each item in the hotbar and in open inventories, coloured by the rarity in its lore (common to divine, special in red). Hotbar and inventories can each be switched off.
- Golem Loot (End): Endstone Protector and Warden fights on their own HUD. It shows kills (and fights where you placed too low to be paid), average place and number of firsts, average damage, every drop with how often it comes, NPC value overall and per golem, and how many golems since each rare drop (Tier Boost Core, Golem pet, Warden Heart and Warden Core by default, editable). A toast says how many golems a rare took. `/golem` lists all-time totals, `/golem session` this session, `/golem reset` clears the session. It uses Boss Drops' judgement of which loot is yours, so Boss Drops must be on.
- Profile viewer redesign: a header with the player's head, rank and profile; icon tabs; an Overview of skills, catacombs, slayers, dungeon stats and purse as progress bars (XP to next level on hover); the inventory laid out with armour beside it and the hotbar below; numbered storage page chips. Storage is read from more shapes (a list of pages, or pages wrapped in `pages`, `backpacks`, `items` or `contents`), and the last profile opened is saved to `config/lazarus/cache/last-profile.json`.
- Staff tools in the profile viewer moved into a slide-out drawer grouped as Punish, Teleport, Check and Copy (name, UUID). Ban, mute and kick open a card with one-click duration and reason chips (editable in settings), a preview of the exact command, and hold-to-confirm for bans.
- Command Aliases: short commands for long ones. `/w` runs `/wardrobe` out of the box, with seven more free to set; anything typed after the alias is passed on.
- Favourites: star any feature in the menu (☆ beside its switch) to pin it under ★ Favourites at the top of the sidebar.
- Whitelist: Lazarus only starts for players listed in `whitelist.json` (by name or UUID). The live list sits in the public `lauchydev/Lazarus-releases` repo and is read at every start, so editing it there adds or removes people with no new build; the jar carries a fallback copy for when GitHub can't be reached. Nothing is cached on disk, and an empty or unreadable list lets nobody in. The account is read from the launch arguments, since Minecraft's user isn't set when mods start. Dev workspaces and CI are always allowed.
- Releases now go to the public `lauchydev/Lazarus-releases` repo, so this source repo can stay private and nobody can rebuild Lazarus without the whitelist. `/lazarus update`, the update notice and `tools/update-lazarus.ps1` all read from there. CI needs a `RELEASES_TOKEN` secret that can write releases to that repo.
- `/lazarus changelog` prints a short, plain-language list of what the current version adds, changes and fixes (from `src/main/resources/lazarus-changelog.txt`, kept alongside this file).
- Update Notice: Lazarus checks every 10 minutes (adjustable) for a newer published build and shows a toast and a chat link when one is out. It never downloads or changes files itself.
- `/lazarus update` downloads the newest published build (the latest `build-N` GitHub release) and swaps it in for the running jar when Minecraft closes, using libautoupdate. Builds are ordered by their run number, so it never downgrades. The Update Notice chat message has an "Update now" link that runs it.
- Each merge to main now publishes a GitHub release with the jar. `tools/update-lazarus.ps1` installs the newest release into the Prism `MC 26.1.2` mods folder and removes the old Lazarus jar. Set it as the instance's pre-launch command so every launch starts on the latest build.
- Bonemerang Chain (Dungeons, off by default): right-click a Bonemerang and every Bonemerang in that unbroken hotbar run is thrown in turn, leftmost first, spread over one cooldown (1.2s, adjustable) so the first is back as the last is thrown. Holding right-click keeps it going; letting go returns you to your slot.
- Profile viewer: staff buttons beside the profile for ban, mute, kick, teleport to, teleport here, captcha, history and alts. Ban, mute and kick ask for a duration and reason first (Tab or Space moves between the fields, Enter sends). They can be switched off under Profile Viewer.
- Profile viewer: Alt + E opens the profile of the player you're looking at, through walls and at any distance (the key can be changed under Profile Viewer). It no longer opens your inventory as well.
- Command Binds: binds can be combos with Ctrl, Alt or Shift (hold them while picking the key). Alt + W opens your wardrobe out of the box, and the eight free binds take combos too. Feature toggle keys can use combos as well.

### Changed
- Profile viewer: item grids (inventory, ender chest, storage, accessories, wardrobe, pets) scale up to fill the panel, up to 2.5x, instead of sitting small in the middle. A one-page container is only as tall as its filled rows.
- Profile viewer: the 3D player stands in a card of its own to the left of the profile, NameMC style, and stays there on every tab. On narrow screens it falls back to its old spot in the Overview.
- Profile viewer: the rank tag in the header uses the rank's in-game colours: VIP green with a gold +, MVP aqua with a red +, DRAGON pink, MOD dark green, and ADMIN, MANAGER and OWNER red. The chip is tinted to match. Ranks not on that list keep the old style.
- Profile viewer: pet tooltips read like in-game pets. They show rarity, the held item by its real name in its rarity colour (Iron Claws, not pet_item_iron_claws_rare), total XP, candies used, whether the pet is active, and a rarity line.
- Release notes on Lazarus-releases no longer start with GitHub's "Merge pull request #N from …" line; they open with the change's title.
- Published builds are now obfuscated with ProGuard (rename-only) and shipped under the normal release name, so the released jar is much harder to decompile. The entrypoint, mixins and enums are kept so everything still loads.
- Published builds are now compiled with no debug information (line numbers, variable and source-file names stripped), so the released jar is harder to decompile and read. Dev builds keep it.
- Rarity Lines: also drawn under items in the Inventory Preview HUD (its own switch, on by default).
- Golem Loot: the rare drop counters sit under a "Kills since last drop" heading instead of reading "2 ago".
- Rarity Lines look softer. The hard bar across the slot is now a faint glow of the rarity colour rising from the bottom, with a short accent centred on the edge whose ends fade out. The glow can be turned off.
- Inventory Preview and the storage preview draw thin lines between and around the slots, so they read like a real inventory. Inventory Preview can turn them off with Slot lines.
- Releases are named major.minor.change, e.g. `Lazarus 0.2.75` tagged `v0.2.75`. Major and minor come from `mod_version` in gradle.properties (now `0.2`); the change number is the CI run, so it always goes up. The jar's own version matches. The updater orders releases by the change number and still reads older `build-N` tags.
- Lazarus-releases keeps only the newest build and the one before it. CI deletes older releases and their tags after each publish.
- Update Notice checks for a new build every 2 minutes by default (was 10), and can go down to 1. That stays well inside GitHub's limit of 60 checks an hour.
- A quieter, more minimal look: softer panels with rounded corners, hairline dividers instead of filled blocks, and calmer colours across the menu, HUD, toasts, profile viewer and storage preview.
- Menu: fades and rises in when opened, scrolls smoothly, the category highlight slides between categories, rows and buttons glow in on hover, switches slide, sliders glide, and a feature's settings unfold instead of popping open. A slim scrollbar shows when the list is long.
- HUD readouts fade in when they appear and fade out where they stood when they end.
- Toasts slide in from the edge, show a thin line counting down their time, and fade away.
- HUD editor: fades in, outlines and name labels ease in under the cursor, and the centre guides only show while dragging.
- Profile viewer: fades in, the tab underline slides between tabs, and item slots light up on hover.
- Storage preview fades in; the Terminals slot to click next pulses gently.

### Fixed
- Profile viewer: the Wardrobe tab showed barriers. The site sends sets as {helmet, chestplate, leggings, boots}, and each set was read as one item. Sets now show as in game, one column per set from helmet down to boots. The equipped set, which the site leaves empty, is filled from the armour being worn and marked "Equipped set".
- Drops written like "RARE DROP! (Summoning Eye) (✯ 239%)" were read as "(Summoning Eye)", so Zealot Tracker never counted Summoning Eyes and dragon summaries could miss items. Notes come off the end first, then the brackets around the name. Unit tests cover the drop formats.
- The Endstone Protector took over the dragon drops HUD after a golem fight. That HUD (now "Dragon Drops") shows dragons only; golems stay on Golem Loot, and `/drops` still lists everything.
- Boss Drops: an eye placed in one lobby no longer makes the next lobby's dragon count as yours.
- Golem Status: the spawn alert fired up to three times (the chat line, the sidebar flicking between Rising and Alive, and a stale read after the kill). It now fires once per spawn and re-arms when the next rise begins. The "ENDSTONE PROTECTOR DOWN!" and "THE WARDEN DOWN!" boxes also mark it resting.
- Golem Loot: Warden rares (Warden Heart, Warden Core) never counted up, because only Protector kills advanced the "kills since last drop" counters. Warden rares now count Wardens and the rest count Protectors, and the toast says which.
- Goop Tracker: fights you weren't in (no placing, place, share or loot) no longer count, and a fight is booked when its loot window closes rather than at the next chat message.
- Area: leaving for somewhere the sidebar doesn't name no longer keeps the last place, so End or dungeon readouts stop showing in the wrong place.
- Boss Drops, Golem Loot, Cache Tracker and Goop Tracker save their all-time totals every minute, not only on quit, so a crash loses at most a minute.
- v0.2.148 crashed at startup (`VerifyError: Bad type on operand stack`): the ProGuard-obfuscated jar failed Java's bytecode check. Releases are the plain jar again, still without debug info; the obfuscated jar is only built as a CI artifact.
- `/lazarus update` on Lunar Client: the download worked but the jar was never swapped, since Lunar ends Minecraft without running the shutdown step that does the swap. Lazarus now remembers what it downloaded and, on the next start, says in chat if it didn't install, with links to the mods folder and the release so it can be replaced by hand. The log also says where updates are staged.
- Zealot Tracker missed kills on dragsim. A dead mob is removed instantly there, so the kill is caught when the zealot leaves the world rather than by watching it die. That path only counted a kill when the server put your name on the damage, so a melee, ability or arrow kill the server didn't attribute to you was dropped. It now credits those the same way the dying-mob path already did, so it no longer matters what you killed it with. A new Zealot Tracker "Debug" setting prints in chat how each kill is (or isn't) counted, to diagnose any that still slip through.
- Zealot Tracker missed ordinary zealots. Those are plain endermen whose name, if any, floats on a separate tag, but the tracker only counted endermen named Zealot on the mob itself. Any enderman in the End now counts, unless its tag says Seraph or Voidgloom. A zealot you hurt that vanishes near you is counted as killed, since dragsim removes dead mobs without a dying animation.
- Rarity Lines only marks items whose lore names a rarity (Common to Divine, Special). The name-colour and catalog fallbacks put lines on every item and have been removed. The lore check accepts dragsim's rarity line, such as "EPIC SWORD" with icons around it.
- Profile viewer: tabs with nothing in the profile's data lost their icon, so the open tab showed only its name, an empty box appeared on hover, and Wardrobe and Pets looked missing. Every tab now shows its icon, empty ones are dimmed, and empty tabs can't be opened. The tooltip still says why.
- Rarity Lines showed nothing on dragsim items. The rarity is now also taken from the item's catalog tier or the colour of its name when the lore has no rarity line. `/lazarus rarity` shows what was found for the held item.
- Release cleanup ordered releases by `createdAt`, which is the tagged commit's date and the same for every build, so it deleted the newest releases and kept build-60 and build-67. It now orders by publish time and never deletes the release it just published.
- Zealot Tracker counts kills made with any weapon. A zealot counts as yours when the server's damage events say you hurt it in the 5 seconds before it died, so bows like the Terminator, abilities and pets all count. Your projectiles are also followed in flight, as a fallback for damage the server doesn't credit to you.
- Update Notice: a new build found while you were on the title screen or in a menu was announced there, where the toast doesn't draw and there's no chat, and then never again. It now waits until you're in a world. Each check is logged, so a missing notice can be traced in latest.log.
- Profile viewer: staff buttons take less padding, so Captcha and History share a line and the whole drawer fits without the last group being cut off.
- Whitelist: when the launcher doesn't pass the account the usual way, Lazarus checks again with the signed-in Minecraft account once the game has started, so listed players aren't turned away. The "not enabled" message now names the account and UUID it saw, and the check is logged.
- Profile viewer: staff drawer buttons no longer overlap; a group wraps onto another line when its labels don't fit.
- Ability Cooldowns: an ability leaves the list as soon as it's ready, instead of showing "Ready" (the "Hide when ready" option is gone).
- Ability Cooldowns: a timer only starts for the ability you actually used. Each ability's cooldown is read from its own lore section (or catalog entry), and sneak abilities only count when you were sneaking, so a Hyperion right click no longer starts the shift-right-click cooldown.
- Profile viewer: items look like themselves. Leather armour shows its dye colour, heads and skinned helmets show their skin, and pets show their head (from the item, then dragsim.co's catalog).
- Ctrl and Alt combos no longer also trigger the vanilla key under them (e.g. Alt + E won't open your inventory).
- Profile viewer: every storage page now shows, one page each with its own number (they used to come out as barriers).
- Profile viewer: skills show their level (combat goes past 50 with Revenant and Voidgloom levels), each slayer shows its level and XP, and dungeons show the Catacombs level. Levels use dragsim.co's own XP curves from the item catalog. The overview scrolls when it is long.
- Price and recipe tooltips have a blank line between the item's rarity and the added lines.

## 0.1.0

### Fixed
- World highlights (boxes, beams, rings, paths) now actually draw: they are added each client tick, which the game test now checks with a screenshot.

### Added
- Profile Viewer: `/l <player>` (or just `/l` for yourself) opens their dragsim.co profile: coins, skills, slayers and dungeons, plus inventory, ender chest, storage, accessories, wardrobe and pets as item grids with tooltips.
- Dungeon Map: the live dungeon map on screen (movable, resizable), hidden in the boss room.
- Key Alerts: toast and sound when a Wither or Blood Key drops, with a box on it (not through walls).
- Blood Room: countdown from the blood door to the first mob, learned from your own runs, then the mob's name.
- Three Weirdos: solves the three statements (one liar), names the chest in chat and boxes it.
- Higher Or Lower: boxes the next blaze to kill in the order the room asks for.
- Tic Tac Toe: boxes the best move (never loses).
- Quiz: learns answers when you get them right (and rules out wrong ones), then names and boxes the answer.
- Ice Path: draws the shortest set of slides to get the silverfish to the chest.
- Simon Says: records the flashes and boxes the next button.
- Terminals: highlights what to click in every Floor VII terminal (order, panes, starts with, colours, rubix with click counts, melody timing).
- Slayer Spawn Marker: a ring and countdown where your boss is about to land, found from the spawn burst (which can be hidden).
- Revenant Spawn Tiles: learns the Crypts tiles your Revenants land on and outlines repeat tiles nearby (config/lazarus/revenant_tiles.json).
- Voidgloom Helper: shield hits or health and distance on screen, a box (and optional tracer) on your Seraph, the Yang Glyph beacon boxed, and a Broken Heart Radiation alert.
- Faded Souls: raised souls are drawn see-through, with their name tags optionally hidden.
- Boss Drops: kills and loot for each dragon kind, the Endstone Protector and the Warden, this session and all time, with drop rates and NPC value. Only fights that pay you count (your eye in the dragon, or top three on the Protector/Warden). `/drops`, `/drops session`, `/drops reset`.
- Recipes: learns each recipe the first time you open it in the server's recipe menu (config/lazarus/recipes.json). Hold Shift on an item to see what it takes, how much of each you carry and the parts cost; `/lazarus recipe <item>` prints it.
- Estimated item value: bazaar items use the last day's median mid-price, auction items the median of recent sales with outliers dropped, never below NPC value. Shown first in the price tooltip.
- Cache Tracker: Crypt Caches you open and every reward, for this session or all time, with NPC value. `/lazarus caches` lists everything; `/lazarus caches reset` clears the session.
- Zealot Tracker: kills (only ones you caused: hits, aimed abilities, Giant's Sword), kills since a Special Zealot, eyes and other zealot drops, and profit valued at NPC sell prices, with per-hour rates. `/lazarus zealots reset` starts a new session.
- Price Database: reads the dragsim.co market every few minutes and keeps 30 days of finished auction prices and bazaar bid/ask in config/lazarus/prices.json. Item tooltips show NPC, bazaar and auction-median prices; `/lazarus price <item>` prints them.
- Item catalog from dragsim.co (cached for offline use); ability cooldowns fall back to it when an item's lore has no Cooldown line.
- Dragon Timer: eye count, dragon fight time and egg respawn countdown.
- Eye Guard: stops a double click from pulling an eye you just placed back out of the altar.
- Golem Status: shows the Protector/Warden stage from the sidebar and chat, with a toast and sound when it spawns.
- Boss Beacon: a box and tall beam over the Protector or Warden, with an optional tracer.
- Golden Dragon Math: answers the Golden Dragon's maths question instantly, in chat or copied to the clipboard.
- Storage Preview: remembers each storage page and the ender chest as you open them, shows a page's contents when you hover it in /storage, and `/lazarus find <item>` lists where an item is and highlights those pages.
- Inventory Preview: a movable HUD grid of your inventory.
- Slot Lock: lock inventory slots against clicks, shift-clicks, number keys, drops and collecting (L over a slot); link an inventory slot to a hotbar slot for shift-click swaps.
- Armor Swap Guard: right-click no longer swaps armour on unless you sneak.
- Copy Chat: right-click a chat line (chat open) to copy the whole message, optionally with colour codes.
- Ability Spam Cleanup: cooldown refusals stack into one line with a count (or hide), any back-to-back repeated server line can stack too, and Goop boss chatter can be hidden.
- Command Binds: eight keys that each run a command or send a chat line.
- Animated Name Gradients: any gradient-coloured player name flows in chat and the tab list.
- Etherwarp Outline: boxes the block Etherwarp will land on while sneaking, with an optional red box for blocked targets.
- Explosion Particle Hider, with an option to keep small puffs.
- Fullbright with adjustable brightness and an option to cancel Darkness and boss dimming.
- Damage Numbers: compact (8.49M), crits only, or hidden, keeping the server's colour gradient.
- Ability Cooldowns: reads each item's own "Cooldown" lore so any ability works; toggles for Giant's Sword, Tuba, katanas, Blinding Sword (12s Final Spark), Riftwalker and other items.
- Ping / TPS readout: measured ping (Dragsim reports 0 in tab) and TPS from server time packets.
- Core: feature registry, saved settings (config/lazarus/settings.json), settings menu with search (Right Shift or /lazarus, /lz), HUD editor with snapping and scaling, per-feature toggle keys.
- CI builds the jar on every push and boots the game headless to check every hook.
