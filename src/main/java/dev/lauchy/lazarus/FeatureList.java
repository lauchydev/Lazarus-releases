package dev.lauchy.lazarus;

import dev.lauchy.lazarus.core.Features;
import dev.lauchy.lazarus.feature.chat.ChatCleanup;
import dev.lauchy.lazarus.feature.chat.CopyChat;
import dev.lauchy.lazarus.feature.chat.MessageSound;
import dev.lauchy.lazarus.feature.chat.NameMention;
import dev.lauchy.lazarus.feature.discord.RichPresence;
import dev.lauchy.lazarus.feature.dungeon.BloodRoom;
import dev.lauchy.lazarus.feature.dungeon.DungeonMap;
import dev.lauchy.lazarus.feature.dungeon.HigherOrLower;
import dev.lauchy.lazarus.feature.dungeon.IcePath;
import dev.lauchy.lazarus.feature.dungeon.KeyAlerts;
import dev.lauchy.lazarus.feature.dungeon.Quiz;
import dev.lauchy.lazarus.feature.dungeon.SimonSays;
import dev.lauchy.lazarus.feature.dungeon.Terminals;
import dev.lauchy.lazarus.feature.dungeon.ThreeWeirdos;
import dev.lauchy.lazarus.feature.dungeon.TicTacToe;
import dev.lauchy.lazarus.feature.cosmetic.Capes;
import dev.lauchy.lazarus.feature.cosmetic.TabBadges;
import dev.lauchy.lazarus.feature.end.BossBeacon;
import dev.lauchy.lazarus.feature.end.BossDrops;
import dev.lauchy.lazarus.feature.end.CacheTracker;
import dev.lauchy.lazarus.feature.end.DragonTimer;
import dev.lauchy.lazarus.feature.end.EyeGuard;
import dev.lauchy.lazarus.feature.end.GoldenDragonMath;
import dev.lauchy.lazarus.feature.end.GolemLoot;
import dev.lauchy.lazarus.feature.end.GoopBait;
import dev.lauchy.lazarus.feature.end.GoopTracker;
import dev.lauchy.lazarus.feature.end.MiningTracker;
import dev.lauchy.lazarus.feature.end.GolemStatus;
import dev.lauchy.lazarus.feature.end.ZealotTracker;
import dev.lauchy.lazarus.feature.general.AutoSprint;
import dev.lauchy.lazarus.feature.general.CommandAliases;
import dev.lauchy.lazarus.feature.general.CommandBinds;
import dev.lauchy.lazarus.feature.general.FireSaleIndex;
import dev.lauchy.lazarus.feature.general.UpdateNotice;
import dev.lauchy.lazarus.feature.dungeon.BonemerangChain;
import dev.lauchy.lazarus.feature.general.Cooldowns;
import dev.lauchy.lazarus.feature.general.DamageNumbers;
import dev.lauchy.lazarus.feature.general.Etherwarp;
import dev.lauchy.lazarus.feature.general.ExplosionHider;
import dev.lauchy.lazarus.feature.general.FrozenScythe;
import dev.lauchy.lazarus.feature.general.Fullbright;
import dev.lauchy.lazarus.feature.general.NameGradients;
import dev.lauchy.lazarus.feature.general.NoFrontView;
import dev.lauchy.lazarus.feature.general.PetBinds;
import dev.lauchy.lazarus.feature.general.PriceDatabase;
import dev.lauchy.lazarus.feature.general.RiftEffects;
import dev.lauchy.lazarus.feature.general.ScreenshotCopy;
import dev.lauchy.lazarus.feature.general.ServerStats;
import dev.lauchy.lazarus.feature.inventory.ArmorGuard;
import dev.lauchy.lazarus.feature.inventory.HotSlots;
import dev.lauchy.lazarus.feature.inventory.InventoryPreview;
import dev.lauchy.lazarus.feature.inventory.MissingAccessories;
import dev.lauchy.lazarus.feature.inventory.RarityLines;
import dev.lauchy.lazarus.feature.inventory.ReadableLore;
import dev.lauchy.lazarus.feature.inventory.Recipes;
import dev.lauchy.lazarus.feature.inventory.SlotLock;
import dev.lauchy.lazarus.feature.inventory.StorageOverview;
import dev.lauchy.lazarus.feature.inventory.StoragePreview;
import dev.lauchy.lazarus.feature.profile.ProfileViewer;
import dev.lauchy.lazarus.feature.slayer.FadedSouls;
import dev.lauchy.lazarus.feature.slayer.RevenantTiles;
import dev.lauchy.lazarus.feature.slayer.SlayerTracker;
import dev.lauchy.lazarus.feature.slayer.SpawnMarker;
import dev.lauchy.lazarus.feature.slayer.VoidgloomHelper;
import dev.lauchy.lazarus.feature.staff.AltFlag;
import dev.lauchy.lazarus.feature.staff.CaptchaKey;
import dev.lauchy.lazarus.feature.staff.ChatPunish;
import dev.lauchy.lazarus.feature.staff.ChatSpy;
import dev.lauchy.lazarus.feature.staff.LookHistory;
import dev.lauchy.lazarus.feature.staff.NickReveal;
import dev.lauchy.lazarus.feature.staff.RankColours;
import dev.lauchy.lazarus.feature.staff.Reports;
import dev.lauchy.lazarus.feature.staff.RollDebug;
import dev.lauchy.lazarus.feature.staff.ServerHop;
import dev.lauchy.lazarus.feature.staff.StaffChat;
import dev.lauchy.lazarus.feature.staff.StaffChatKeep;
import dev.lauchy.lazarus.feature.staff.StaffTools;
import dev.lauchy.lazarus.feature.staff.WatchList;

/** Every feature Lazarus ships, in menu order within each category. */
final class FeatureList {
	private FeatureList() {
	}

	static void registerAll() {
		Features.register(new ServerStats());
		Features.register(new Cooldowns());
		Features.register(new BonemerangChain());
		Features.register(new DamageNumbers());
		Features.register(new Fullbright());
		Features.register(new ExplosionHider());
		Features.register(new FrozenScythe());
		Features.register(new RiftEffects());
		Features.register(new Etherwarp());
		Features.register(new AutoSprint());
		Features.register(new NoFrontView());
		Features.register(new ScreenshotCopy());
		Features.register(new NameGradients());
		Features.register(new CommandBinds());
		Features.register(new PetBinds());
		Features.register(new CommandAliases());
		Features.register(new UpdateNotice());
		Features.register(new PriceDatabase());
		Features.register(new FireSaleIndex());
		Features.register(new ProfileViewer());
		Features.register(new Capes());
		Features.register(new TabBadges());

		// DragonTimer before EyeGuard and GolemStatus before BossBeacon: the later ones read the earlier.
		Features.register(new DragonTimer());
		Features.register(new EyeGuard());
		Features.register(new GolemStatus());
		Features.register(new BossBeacon());
		Features.register(new ZealotTracker());
		Features.register(new CacheTracker());
		Features.register(new BossDrops());
		Features.register(new GolemLoot());
		Features.register(new GoopTracker());
		Features.register(new GoopBait());
		Features.register(new MiningTracker());
		Features.register(new GoldenDragonMath());

		// SpawnMarker first: the tiles and the Voidgloom helper use its landing point.
		Features.register(new SpawnMarker());
		Features.register(new RevenantTiles());
		Features.register(new VoidgloomHelper());
		Features.register(new SlayerTracker());
		Features.register(new FadedSouls());

		Features.register(new DungeonMap());
		Features.register(new KeyAlerts());
		Features.register(new BloodRoom());
		Features.register(new ThreeWeirdos());
		Features.register(new HigherOrLower());
		Features.register(new TicTacToe());
		Features.register(new Quiz());
		Features.register(new IcePath());
		Features.register(new SimonSays());
		Features.register(new Terminals());

		Features.register(new StorageOverview());
		Features.register(new StoragePreview());
		Features.register(new MissingAccessories());
		Features.register(new InventoryPreview());
		Features.register(new RarityLines());
		Features.register(new ReadableLore());
		Features.register(new Recipes());
		Features.register(new SlotLock());
		Features.register(new HotSlots());
		Features.register(new ArmorGuard());

		Features.register(new ChatCleanup());
		Features.register(new CopyChat());
		Features.register(new NameMention());
		Features.register(new MessageSound());

		Features.register(new RichPresence());

		Features.register(new StaffTools());
		Features.register(new ChatPunish());
		Features.register(new RankColours());
		Features.register(new Reports());
		Features.register(new WatchList());
		Features.register(new LookHistory());
		Features.register(new AltFlag());
		Features.register(new NickReveal());
		Features.register(new StaffChat());
		Features.register(new ChatSpy());
		Features.register(new StaffChatKeep());
		Features.register(new RollDebug());
		Features.register(new CaptchaKey());
		Features.register(new ServerHop());
	}
}
