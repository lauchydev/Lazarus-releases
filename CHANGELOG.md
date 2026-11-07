# Changelog

What changed for players in the newest version of Lazarus. Only that version is listed: each release replaces it.

## 1.40.0

### Added
- Staff desk (staff): one screen for moderating whoever is online, replacing the online roster and the staff panel, drawn like a web app (Inter and JetBrains Mono, cards, line icons) at a size that fits the window. Everyone online on the left, the flagged first; the selected player in the middle across Overview (a card for each reason they're flagged), Records (history, warnings, alts) and Account (lookup, the anticheat's numbers, dupe scan, captcha status, audit log); every staff command the server lists down the right, greyed with why when it can't be used. Cards and records open a detail panel (a priority score's last readings, a punishment's full reason, the audit log's transactions by kind, the accounts a player is linked to with the banned one marked); bans and mutes open a dialog that picks the duration for the player's next offence under the chosen rule. Opens from `/lazarus online`, its key, a name clicked in chat or `/lazarus staff <player>`, offline players included. Drawn in a few hundred pieces rather than thousands, so it takes little off the frame rate.
- Anticheat Lines (staff): the anticheat's `[FLAG REVIEW]` and `DragSim AC »` lines leave chat; priority scores and setbacks become flags on the desk, shared with every staff member, and the client a player joined with shows in their dossier.

### Changed
- Lazarus is open to everyone with the mod: there's no whitelist, and nothing needs unlocking. `/lazarus discord` still links your Discord account for its roles, but the mod works the same whether you link it or not.
- Lazarus no longer reads anything private from your Minecraft session. It used to sign its calls to the Lazarus server with your Mojang chat key; it now makes a key of its own the first time it runs (`config/lazarus/install-key`), and the server ties your account to it. Moving to a new computer, or losing that file, needs an admin to reset your key; until then badges, capes, Lazarus+ and staff sharing are off there.
- Lazarus 1.39 and older can no longer reach the Lazarus server: update to this version by hand.
- The license covers every player who uses Lazarus, rather than only players who linked their Discord account.
- The price database's indexers are `/lazarus index firesale` and `/lazarus index recipes` (each with `stop`), instead of `/lazarus firesale index` and `/lazarus recipes index`.

### Removed
- `/lazarus rarity` and `/lazarus slot debug`, debugging commands with no use in play.
