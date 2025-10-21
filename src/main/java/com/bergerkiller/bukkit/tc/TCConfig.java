package com.bergerkiller.bukkit.tc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.Hastebin;
import com.bergerkiller.bukkit.common.StringReplaceBundle;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.utils.ConfiguredWorldSet;

/**
 * Stores all the settings specified in the TrainCarts config.yml.
 * These were previously in the main TrainCarts plugin
 */
public class TCConfig {
    public static final StringReplaceBundle messageShortcuts = new StringReplaceBundle();
    public static final StringReplaceBundle statementShortcuts = new StringReplaceBundle();

    // Some updates break things people did in the past
    // This allows people to restore older logic
    public static boolean legacySpeedLimiting;

    public static boolean destroyAllOnShutdown;
    public static double maxVelocity;
    public static double maxEjectDistance;
    public static double maxEnterDistance;
    public static double cartDistanceGapMax;
    public static double cartDistanceGap;
    public static double cartDistanceForcer;
    public static double cartDistanceForcerConstant;
    public static double worldBorderKillDistance;
    public static boolean breakCombinedCarts;
    public static double poweredCartBoost;
    public static double poweredRailBoost;
    public static double pushAwayForce;
    public static double launchForce;
    public static boolean collisionIgnoreGlobalOwners;
    public static boolean collisionIgnoreOwners;
    public static boolean useCoalFromStorageCart;
    public static boolean setOwnerOnPlacement;
    public static boolean keepChunksLoadedOnlyWhenMoving;
    public static int maxKeepChunksLoadedRadius;
    public static int maxDetectorLength;
    public static int maxMutexSize;
    public static boolean debugMutexGlow;
    public static int maxMinecartStackSize;
    public static int defaultTransferRadius;
    public static int maxTransferRadius;
    public static double maxTrainEditdistance;
    public static boolean optimizeInteraction;
    public static boolean allowPlayerCollisionFromBehind;
    public static boolean showTransferAnimations;
    public static boolean craftingRequireWorkbench;
    public static boolean slowDownEmptyCarts;
    public static boolean enableSeatThirdPersonView;
    public static double slowDownMultiplierSlow;
    public static double slowDownMultiplierNormal;
    public static boolean refillAtStations;
    public static boolean instantCreativeDestroy;
    public static boolean allowRailEditing;
    public static double manualMovementFactor;
    public static boolean allMinecartsAreTrainCarts;
    public static boolean allowUpsideDownRails;
    public static boolean allowNetherTeleport;
    public static int cacheVerificationTicks;
    public static int cacheExpireTicks;
    public static boolean enableCeilingBlockCollision = true; // whether to allow blocks above the minecart to collide
    public static int collisionReEnterDelay = 100; // Delay before letting mobs/player enter again
    public static boolean optimizeBlockActivation;
    public static boolean activatorEjectEnabled = true;
    public static boolean railTrackerDebugEnabled = false;
    public static boolean wheelTrackerDebugEnabled = false;
    public static boolean animationsUseTickTime = false;
    public static boolean claimNewSavedTrains = true;
    public static boolean claimNewSavedModels = true;
    public static boolean onlyPoweredSwitchersDoPathFinding = true;
    public static boolean onlyEmptySwitchersDoPathFinding = false;
    public static boolean onlyRegisteredSignsHandleRedstone = true;
    public static boolean enableSneakingInAttachmentEditor = false;
    public static boolean playHissWhenStopAtStation = true;
    public static boolean playHissWhenDestroyedBySign = true;
    public static boolean playHissWhenLinked = true;
    public static boolean playHissWhenCartRemoved = true;
    public static boolean rerouteOnStartup = false;
    public static boolean switcherResetCountersOnFirstCart = true;
    public static boolean logMutexConflicts = false;
    public static boolean logSyncChunkLoads = false;
    public static boolean logTrainSplitting = false;
    public static String launchFunctionType = "bezier";
    public static boolean parseOldSigns;
    public static boolean allowParenthesesFormat = true;
    public static boolean upsideDownSupportedByAll = false;
    public static boolean trainsCheckSignFacing = true;
    public static double unloadRunawayTrainDistance = 160.0;
    public static int autoSaveInterval = 30 * 20; // autosave every 30 seconds
    public static int attachmentTransformParallelism = -1;
    public static boolean allowExternalTicketImagePaths = false; // Whether images outside of the images subdirectory are allowed
    public static boolean allowSchematicAttachment = true;
    public static int maxCommandSelectorValues = 128;
    public static int maxConcurrentEffectLoops = 20;
    public static double spawnSignCooldown = -1.0;
    public static double itemPickupRadius = 2.0;
    public static int maxCartsPerWorld = -1;
    public static int maxCartsPerTrain = -1;
    public static boolean maxCartsPerWorldCountUnloaded = false;
    public static boolean modelSearchCompactFolders = true;
    public static String currencyFormat;
    public static Set<Material> allowedBlockBreakTypes = new HashSet<>();
    public static ConfiguredWorldSet enabledWorlds = new ConfiguredWorldSet();
    public static ConfiguredWorldSet disabledWorlds = new ConfiguredWorldSet();
    public static boolean enableVanillaActionSigns = true;
    public static Map<String, ItemParser[]> parsers = new HashMap<>();
    public static MapResourcePack resourcePack = MapResourcePack.SERVER;
    public static Map<String, Animation> defaultAnimations = new HashMap<>();
    public static Hastebin hastebin = null;

    public static void load(TrainCarts traincarts, FileConfiguration config) {
        config.setHeader("This is the configuration file of TrainCarts");
        config.addHeader("In here you can tweak TrainCarts to what you want");
        config.addHeader("For more information, you can visit the following websites:");
        config.addHeader("https://wiki.traincarts.net/p/TrainCarts");
        config.addHeader("https://www.spigotmc.org/resources/traincarts.39592/");

        config.setHeader("resourcePack", "\nPath or online download URL to the resource pack to use");
        config.addHeader("resourcePack", "Using 'server' makes it use server.properties (default)");
        config.addHeader("resourcePack", "Using 'default' or empty makes it use no resource pack at all");
        resourcePack = new MapResourcePack(config.get("resourcePack", "server"));
        resourcePack.load();

        config.setHeader("modelSearchCompactFolders", "\nWhether to automatically unpack folders in the /train model search dialog");
        config.addHeader("modelSearchCompactFolders", "If true, will show models in place of folders if there are few inside");
        config.addHeader("modelSearchCompactFolders", "If false, will always show all folders, even if there's only one model inside");
        modelSearchCompactFolders = config.get("modelSearchCompactFolders", true);

        // Legacy old 'normal' node, which used to exist because there was also one for 'angled'
        // Remove it and move the configuration properties to where they are now
        if (config.contains("normal")) {
            if (!config.contains("normal.cartDistanceGap") && config.contains("normal.cartDistance")) {
                config.set("normal.cartDistanceGap", config.get("normal.cartDistance", 1.5) - 1.0);
            }
            if (config.contains("normal.cartDistanceGap")) {
                config.set("linkProperties.cartDistanceGap", config.get("normal.cartDistanceGap"));
            }
            if (config.contains("normal.cartDistanceForcer")) {
                config.set("linkProperties.cartDistanceForcer", config.get("normal.cartDistanceForcer"));
            }
            if (config.contains("normal.cartDistanceForcerConstant")) {
                config.set("linkProperties.cartDistanceForcerConstant", config.get("normal.cartDistanceForcerConstant"));
            }
            config.remove("normal");
        }
        if (config.contains("maxCartDistance")) {
            config.remove("maxCartDistance");
        }

        config.setHeader("linkProperties", "\nProperties of the link between the carts of a train");
        config.setHeader("linkProperties.cartDistanceGap", "The default distance between two carts in a train");
        config.addHeader("linkProperties.cartDistanceGap", "This can be overridden by configuring the 'coupler length' in the physical menu of the attachment editor");
        config.setHeader("linkProperties.cartDistanceGapMax", "The distance between two carts above which they lose linkage and disconnect");
        config.setHeader("linkProperties.cartDistanceForcer", "Factor at which the gap is maintained, based on train velocity");
        config.setHeader("linkProperties.cartDistanceForcerConstant", "Factor at which the gap is maintained, always active");
        config.addHeader("linkProperties.cartDistanceForcerConstant", "When set to nonzero, will cause carts to move at standstill");

        cartDistanceGap = config.get("linkProperties.cartDistanceGap", 0.5);
        cartDistanceGapMax = config.get("linkProperties.cartDistanceGapMax", cartDistanceGap + 2.0);
        cartDistanceForcer = config.get("linkProperties.cartDistanceForcer", 0.1);
        cartDistanceForcerConstant = config.get("linkProperties.cartDistanceForcerConstant", 0.0);

        config.setHeader("worldBorderKillDistance", "\nWhat distance beyond the world border carts get destroyed automatically");
        config.addHeader("worldBorderKillDistance", "This covers a set world border, as well as falling into the void, or");
        config.addHeader("worldBorderKillDistance", "flying beyond the height limit of the World");
        worldBorderKillDistance = config.get("worldBorderKillDistance", 64.0);

        config.setHeader("breakCombinedCarts", "\nWhether or not the combined carts (powered/storage minecarts) break up into two items");
        breakCombinedCarts = config.get("breakCombinedCarts", false);

        config.setHeader("poweredCartBoost", "\nA performance boost to give to powered minecarts (0 = normal speed)");
        poweredCartBoost = config.get("poweredCartBoost", 0.1);

        config.setHeader("poweredRailBoost", "\nThe boosting factor of powered rails (default = 0.06)");
        poweredRailBoost = config.get("poweredRailBoost", 0.06);

        config.setHeader("maxVelocity", "\nThe maximum velocity (blocks/tick) a minecart can possibly have set");
        maxVelocity = config.get("maxVelocity", 5.0);

        config.setHeader("slowDownMultiplier", "\nThe multiplier used to slow down minecarts");
        config.addHeader("slowDownMultiplier", "Normal is the default, slow is when the minecart is meant to slow down.");
        slowDownMultiplierNormal = config.get("slowDownMultiplier.normal", 0.997);
        slowDownMultiplierSlow = config.get("slowDownMultiplier.slow", 0.96);


        config.setHeader("maxEnterDistance", "\nThe maximum allowed enter radius for enter signs");
        maxEnterDistance = config.get("maxEnterDistance", 50.0);
        
        config.setHeader("maxEjectDistance", "\nThe maximum allowed ejection distance for eject signs");
        maxEjectDistance = config.get("maxEjectDistance", 10.0);

        config.setHeader("maxTrainEditdistance", "\nMaximum distance from which /train edit can select trains");
        maxTrainEditdistance = config.get("maxTrainEditdistance", 64.0);

        config.setHeader("launchForce", "\nThe amount of velocity stations give when launching trains");
        launchForce = config.get("launchForce", 10.0);

        config.setHeader("allowPlayerCollisionFromBehind", "\nAllows players to push carts with PUSH/KILL/etc. collision modes");
        config.addHeader("allowPlayerCollisionFromBehind", "when pushing from behind. When true, they can make a standing-still");
        config.addHeader("allowPlayerCollisionFromBehind", "cart move by pushing it, even when collision is disallowed otherwise.");
        config.addHeader("allowPlayerCollisionFromBehind", "When the cart hits a player head-on, then the logic IS executed like normal");
        allowPlayerCollisionFromBehind = config.get("allowPlayerCollisionFromBehind", false);

        config.setHeader("destroyAllOnShutdown", "\nDestroys all existing minecarts on startup and shutdown of the plugin");
        destroyAllOnShutdown = config.get("destroyAllOnShutdown", false);

        // Deprecation backwards compatibility
        if (config.contains("pushAway")) {
            config.set("collision.ignoreOwners", config.get("pushAway.ignoreOwners", true));
            config.set("collision.ignoreGlobalOwners", config.get("pushAway.ignoreGlobalOwners", false));
            config.set("collision.pushAwayForce", config.get("pushAway.force", 0.2));
            config.remove("pushAway");
        }

        config.setHeader("collision", "\nSettings used when carts collide with entities");
        config.setHeader("collision.ignoreOwners", "If train owners are ignored");
        config.setHeader("collision.ignoreGlobalOwners", "If global train owners are ignored");
        config.setHeader("collision.pushAwayForce", "The amount of force at which minecarts push away others");
        collisionIgnoreOwners = config.get("collision.ignoreOwners", false);
        collisionIgnoreGlobalOwners = config.get("collision.ignoreGlobalOwners", false);
        pushAwayForce = config.get("collision.pushAwayForce", 0.2);

        config.setHeader("allMinecartsAreTrainCarts", "\nWhether or not all minecarts spawned on the server turn into TrainCarts' Minecarts");
        config.addHeader("allMinecartsAreTrainCarts", "Note that the TrainCart placement permission is then no longer active");
        allMinecartsAreTrainCarts = config.get("allMinecartsAreTrainCarts", false);

        config.setHeader("useCoalFromStorageCart", "\nWhether or not powered minecarts obtain their coal from attached storage minecarts");
        useCoalFromStorageCart = config.get("useCoalFromStorageCart", false);

        config.setHeader("setOwnerOnPlacement", "\nWhether or not the player that places a minecart is set owner");
        setOwnerOnPlacement = config.get("setOwnerOnPlacement", true);

        config.setHeader("launchFunction", "\nWhat style of launching to use in stations and launcher sign systems by default. Possible values:\n" +
                "- 'linear': gradually switches from one motion speed to another at a linear rate\n" +
                "- 'bezier': uses a bezier curve (ease in-out), resulting in slower changes in motion at start/end of launch");
        launchFunctionType = config.get("launchFunction", "bezier");

        config.setHeader("keepChunksLoadedOnlyWhenMoving", "\nWhether or not chunks are only kept loaded when the train is moving");
        config.addHeader("keepChunksLoadedOnlyWhenMoving", "They also keep chunks loaded while the train is waiting on a station");
        keepChunksLoadedOnlyWhenMoving = config.get("keepChunksLoadedOnlyWhenMoving", false);

        config.setHeader("maxKeepChunksLoadedRadius", "\nMaximum radius that can be set for the keep chunks loaded property of a train");
        config.addHeader("maxKeepChunksLoadedRadius", "The default is radius is 2, which loads a 5x5 chunk area. Avoid abuse, don't make it too big.");
        maxKeepChunksLoadedRadius = config.get("maxKeepChunksLoadedRadius", 7);

        config.setHeader("enableCeilingBlockCollision", "\nWhether to enable or cancel collisions with blocks above minecarts");
        config.addHeader("enableCeilingBlockCollision", "Some constructions depend on these block collisions to block minecarts");
        config.addHeader("enableCeilingBlockCollision", "If these collisions are unwanted, they can be turned off here");
        enableCeilingBlockCollision = config.get("enableCeilingBlockCollision", true);

        config.setHeader("animationsUseTickTime", "\nSets whether attachment animations use tick time or wall clock time");
        config.addHeader("animationsUseTickTime", "When false, wall clock time is used, and server lag will not slow down/speed up animations");
        config.addHeader("animationsUseTickTime", "When true, tick time is used, and server lag will cause speed changes. Animations do stay in sync with physics");
        animationsUseTickTime = config.get("animationsUseTickTime", false);

        config.setHeader("enableSneakingInAttachmentEditor", "\nSets whether the player can move around by holding shift in the attachment editor");
        enableSneakingInAttachmentEditor = config.get("enableSneakingInAttachmentEditor", !CommonCapabilities.PLAYER_OFF_HAND);

        config.setHeader("autoSaveInterval", "\nSets the interval at which all the properties of all trains on the server are saved to disk");
        config.addHeader("autoSaveInterval", "This saving may have a negative performance impact, as seen in the AutoSaveTask in timings");
        config.addHeader("autoSaveInterval", "If you do not worry about preserving this information, you can raise this interval");
        config.addHeader("autoSaveInterval", "A manual save can be performed using /train saveall");
        autoSaveInterval = config.get("autoSaveInterval", 30 * 20);

        config.setHeader("claimNewSavedTrains", "\nSets whether players automatically claim new saved trains that they save");
        config.addHeader("claimNewSavedTrains", "Once claimed, other players cannot overwrite the saved train, effectively protecting it");
        config.addHeader("claimNewSavedTrains", "Setting this to false will have new trains exist in public domain, and anyone can modify it");
        config.addHeader("claimNewSavedTrains", "The original owner can put the train in public domain by disclaiming it (/savedtrain [name] disclaim)");
        config.addHeader("claimNewSavedTrains", "Players (moderators) with the " + Permission.COMMAND_SAVEDTRAIN_GLOBAL.getName() + " permission are exempt");
        claimNewSavedTrains = config.get("claimNewSavedTrains", true);

        config.setHeader("claimNewSavedModels", "\nSets whether players automatically claim new saved attachment models that they save");
        config.addHeader("claimNewSavedModels", "Once claimed, other players cannot overwrite the saved model, effectively protecting it");
        config.addHeader("claimNewSavedModels", "Setting this to false will have new models exist in public domain, and anyone can modify it");
        config.addHeader("claimNewSavedModels", "The original owner can put the model in public domain by disclaiming it (/train model config [name] disclaim)");
        config.addHeader("claimNewSavedModels", "Players (moderators) with the " + Permission.COMMAND_SAVEDTRAIN_GLOBAL.getName() + " permission are exempt");
        claimNewSavedModels = config.get("claimNewSavedModels", true);

        config.setHeader("hastebinServer", "\nThe hastebin server which is used to upload saved trains");
        config.addHeader("hastebinServer", "This will be used when using the /savedtrain [name] paste command");
        hastebin = new Hastebin(traincarts, config.get("hastebinServer", config.get("hastebinServer", "https://paste.traincarts.net")));

        config.setHeader("enableSeatThirdPersonView", "\nEnable or disable seeing yourself in third-person when upside-down or vertical");
        config.addHeader("enableSeatThirdPersonView", "Turning this off will cause players to clip through the cart potentially");
        config.addHeader("enableSeatThirdPersonView", "This option is only active when FPV (First person view) is set to DYNAMIC");
        config.addHeader("enableSeatThirdPersonView", "In that case, such seats will behave as if FPV is set to DEFAULT");
        enableSeatThirdPersonView = config.get("enableSeatThirdPersonView", false);

        config.setHeader("maxDetectorLength", "\nThe maximum length a detector region (between two detectors) can be");
        maxDetectorLength = config.get("maxDetectorLength", 2000);

        config.setHeader("maxMutexSize", "\nThe maximum size a dimension of a mutex zone is allowed to have");
        config.addHeader("maxMutexSize", "Too large mutexes could result in an out-of-memory situation");
        maxMutexSize = config.get("maxMutexSize", 2000);

        config.setHeader("debugMutexGlow", "\nWhether to show a glowing see-through effect when showing mutexes with /train debug mutex");
        debugMutexGlow = config.get("debugMutexGlow", true);

        config.setHeader("maxMinecartStackSize", "\nThe maximum amount of minecart items that can be stacked in one item");
        maxMinecartStackSize = config.get("maxMinecartStackSize", 64);

        config.setHeader("maxTransferRadius", "\nThe maximum radius chest/furnace sign systems look for the needed blocks");
        maxTransferRadius = config.get("maxTransferRadius", 5);

        config.setHeader("defaultTransferRadius", "\nThe default radius chest/furnace sign systems look for the needed blocks");
        defaultTransferRadius = MathUtil.clamp(config.get("defaultTransferRadius", 2), 1, maxTransferRadius);

        config.setHeader("slowDownEmptyCarts", "\nWhether or not empty minecarts slow down faster than occupied minecarts");
        slowDownEmptyCarts = config.get("slowDownEmptyCarts", false);

        config.setHeader("refillAtStations", "\nWhether furnace minecarts get fuel when launching from stations");
        refillAtStations = config.get("refillAtStations", true);

        config.setHeader("optimizeInteraction", "\nWhether destroying or entering minecarts is made easier to do");
        config.addHeader("optimizeInteraction", "When optimized, block / air clicks are intercepted and handled as clicks with minecarts instead");
        optimizeInteraction = config.get("optimizeInteraction", true);

        config.setHeader("optimizeBlockActivation", "\nWhether block activation during movement is skipped when the rail type does not support it");
        config.addHeader("optimizeBlockActivation", "When optimized, pressure plates near (but not on) the track are not activated");
        config.addHeader("optimizeBlockActivation", "Rails that are activated, like detector rails, will function just fine");
        config.addHeader("optimizeBlockActivation", "This optimization helps improve performance of train movement physics, potentially improving tps");
        optimizeBlockActivation = config.get("optimizeBlockActivation", true);

        config.setHeader("instantCreativeDestroy", "\nWhen set to true, players will be able to break minecarts with a single slap\n" +
                "\nNo item drops are spawned for minecarts destroyed this way. Minecart contents ARE dropped." +
                "\nThey can still select minecarts by crouching and then slapping the minecart\n" +
                "\nWhen set to false, players will never instantly destroy minecarts and they will have to break it as if in survival.");
        instantCreativeDestroy = config.get("instantCreativeDestroy", true);

        // Allow vertical pitch doesn't work anymore
        if (config.contains("allowVerticalPitch")) {
            config.remove("allowVerticalPitch");
        }

        config.setHeader("allowUpsideDownRails", "\nWhether upside-down rail functionality is enabled on the server");
        config.addHeader("allowUpsideDownRails", "When disabled, minecarts can not be rotated upside-down");
        config.addHeader("allowUpsideDownRails", "With this enabled, rails will not break when they have a solid block above them");
        config.addHeader("allowUpsideDownRails", "This does mean that vanilla duper mechanics that rely on breaking powered rails might not work");
        allowUpsideDownRails = config.get("allowUpsideDownRails", true);

        config.setHeader("allowSchematicAttachment", "\nWhether to enable WorldEdit schematics to be loaded in as a SCHEMATIC attachment");
        config.addHeader("allowSchematicAttachment", "This can be quite laggy, so only enable it if you can trust people with it");
        config.addHeader("allowSchematicAttachment", "Only works on Minecraft server/clients 1.19.4 and later");
        allowSchematicAttachment = config.get("allowSchematicAttachment", true);

        config.setHeader("itemPickupRadius", "The radius with which minecarts with chest pick up items, when item pickup is enabled for the train");
        itemPickupRadius = config.get("itemPickupRadius", 2.0);

        config.setHeader("legacySpeedLimiting", "\nBefore TrainCarts v1.12.2-v1 speed limiting was done on each individual axis");
        config.addHeader("legacySpeedLimiting", "This had a big impact on air physics, because it never made a good ellipse fall");
        config.addHeader("legacySpeedLimiting", "This was changed to preserving the motion vector direction when doing speed limiting");
        config.addHeader("legacySpeedLimiting", "Slight changes may have occurred in curves, slopes and through-air physics");
        config.addHeader("legacySpeedLimiting", "To restore the old limiting system, set this option to True");
        legacySpeedLimiting = config.get("legacySpeedLimiting", false);

        config.setHeader("allowRailEditing", "\nWhether players (with build permissions) can edit existing rails by right-clicking on them");
        allowRailEditing = config.get("allowRailEditing", true);

        // Manual movement speed (slapping the cart) changes to using movement controls, so remove this old configuration option
        if (config.contains("manualMovementSpeed")) {
            config.remove("manualMovementSpeed");
        }
        config.setHeader("manualMovementFactor", "\nVelocity factor to apply when a player tries to move a cart using movement controls");
        manualMovementFactor = config.get("manualMovementFactor", 0.1);

        config.setHeader("currencyFormat", "\nThe currency Ticket signs will display in messages, %value% represents the displayed value");
        currencyFormat = config.get("currencyFormat", "%value% Dollars");

        config.setHeader("allowNetherTeleport", "\nWhether trains can be teleported to the nether (or back) when rails are laid close to the portals");
        allowNetherTeleport = config.get("allowNetherTeleport", true);

        config.setHeader("collisionReEnterDelay", "\nThe delay (in ticks) between ejecting and re-entering by collision (e.g. mobs auto-entering carts)");
        collisionReEnterDelay = config.get("collisionReEnterDelay", collisionReEnterDelay);

        config.setHeader("logMutexConflicts", "\nLogs a message to server log when two trains are inside a mutex zone, when they shouldn't be");
        logMutexConflicts = config.get("logMutexConflicts", false);

        config.setHeader("logTrainSplitting", "\nLogs detailed messages whenever a train splits apart because of issues finding a path between carts");
        logTrainSplitting = config.get("logTrainSplitting", false);

        config.setHeader("logSyncChunkLoads", "\nLogs when TrainCarts sync-loads another chunk while not expected to, like while handling a chunk load");
        config.addHeader("logSyncChunkLoads", "This is mostly for developers to diagnose performance issues. Sync chunk loads can, potentially, hurt server TPS");
        logSyncChunkLoads = config.get("logSyncChunkLoads", false);

        // Cache settings
        {
            config.setHeader("cache", "\nConfigures the in-memory rail information cache of TrainCarts");
            ConfigurationNode cacheConfig = config.getNode("cache");
            cacheConfig.setHeader("verificationTicks", "For how many ticks the cached information is accessed without verification");
            cacheConfig.addHeader("verificationTicks", "With 0 ticks, the information is verified every tick, reading world block data doing so");
            cacheConfig.addHeader("verificationTicks", "Higher values will verify less often, possibly improving performance, but this");
            cacheConfig.addHeader("verificationTicks", "may cause stale information to remain in the cache. This may cause 'ghost rails'.");
            cacheVerificationTicks = cacheConfig.get("verificationTicks", 0);
            cacheConfig.setHeader("expireTicks", "After this number of ticks beyond the verification ticks the cached information is deleted");
            cacheConfig.addHeader("expireTicks", "Higher values can reduce lookups but this comes at the cost of higher memory consumption");
            cacheExpireTicks = cacheConfig.get("expireTicks", 20);
        }

        config.setHeader("allowedBlockBreakTypes", "\nThe block materials that can be broken using minecarts");
        config.addHeader("allowedBlockBreakTypes", "Players with the admin block break permission can use any type");
        config.addHeader("allowedBlockBreakTypes", "Others have to use one from this list");
        allowedBlockBreakTypes.clear();
        if (config.contains("allowedBlockBreakTypes")) {
            for (String value : config.getList("allowedBlockBreakTypes", String.class)) {
                Material type = ParseUtil.parseMaterial(value, null);
                if (type != null) allowedBlockBreakTypes.add(type);
            }
        } else {
            allowedBlockBreakTypes.add(getMaterial("LEGACY_CROPS"));
            allowedBlockBreakTypes.add(getMaterial("LEGACY_LOG"));
        }

        config.setHeader("activatorEjectEnabled", "Whether powered activator rails eject players inside Minecarts (Vanilla)");
        config.addHeader("activatorEjectEnabled", "If activator rails are used for decoration purposes, this should be disabled");
        activatorEjectEnabled = config.get("activatorEjectEnabled", true);

        config.setHeader("enableVanillaActionSigns", "\nWhether action signs (https://wiki.traincarts.net/p/TrainCarts/Signs) using Vanilla Minecraft signs are enabled");
        config.addHeader("enableVanillaActionSigns", "When disabled, other signs (TC-Coasters signs) will still function. Only affects vanilla block signs.");
        enableVanillaActionSigns = config.get("enableVanillaActionSigns", true);

        config.setHeader("enabledWorlds", "\nA list of world names where TrainCarts should be enabled");
        config.addHeader("enabledWorlds", "If this list is empty, all worlds are enabled except those listed in disabledWorlds");
        config.addHeader("enabledWorlds", "World names are not case-sensitive");
        enabledWorlds.clear();
        if (!config.contains("enabledWorlds")) {
            config.set("enabledWorlds", Collections.emptyList());
        }
        for (String world : config.getList("enabledWorlds", String.class)) {
            enabledWorlds.add(world);
        }

        config.setHeader("disabledWorlds", "\nA list of world names where TrainCarts should be disabled");
        config.addHeader("disabledWorlds", "Overridden by enabledWorlds");
        config.addHeader("disabledWorlds", "World names are not case-sensitive");
        disabledWorlds.clear();
        if (!config.contains("disabledWorlds")) {
            config.set("disabledWorlds", Arrays.asList("DefaultWorld1", "DefaultWorld2"));
        }
        for (String world : config.getList("disabledWorlds", String.class)) {
            disabledWorlds.add(world);
        }

        //set it again
        List<String> types = config.getList("allowedBlockBreakTypes", String.class);
        types.clear();
        for (Material mat : allowedBlockBreakTypes) {
            types.add(mat.toString());
        }

        config.setHeader("showTransferAnimations", "\nWhether or not to show item animations when transferring items");
        showTransferAnimations = config.get("showTransferAnimations", true);

        config.setHeader("craftingRequireWorkbench", "\nWhether a crafting table must exist when crafting items inside a storage minecart\n" +
                "When this is set to false and no crafting table is nearby, no item transfer animations are shown");
        craftingRequireWorkbench = config.get("craftingRequireWorkbench", true);

        config.setHeader("triggerTimerDateFormat", "\nTime format used by trigger signs to display arrival times on signs");
        config.addHeader("triggerTimerDateFormat", "Formatting: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html");
        ArrivalSigns.setTimeDurationFormat(config.get("triggerTimerDateFormat", "HH:mm:ss"));

        //message shortcuts
        config.setHeader("messageShortcuts", "\nSeveral shortcuts you can use on announce signs (text is replaced)");
        if (!config.contains("messageShortcuts")) {
            config.set("messageShortcuts.welcome", "&eWelcome to &f");
        }
        messageShortcuts.clear().load(config.getNode("messageShortcuts"));

        //statement shortcuts
        config.setHeader("statementShortcuts", "\nSeveral shortcuts you can use on switcher and detector signs (text is replaced)");
        if (!config.contains("statementShortcuts")) {
            config.set("statementShortcuts.diamond", "i@diamond");
        }
        statementShortcuts.clear().load(config.getNode("statementShortcuts"));

        //parser shortcuts
        config.setHeader("itemShortcuts", "\nSeveral shortcuts you can use on signs to set the items");
        ConfigurationNode itemshort = config.getNode("itemShortcuts");

        /*
         * Signs created before Minecraft 1.8 with (for example) "[train]" will have the brackets stripped.
         * TrainCarts will not recognize these causing trains to ignore all "old" signs in the world.
         * Here is a flag to recognize "train" and "!train" etc. on the top line of signs and re-add in
         * the missing [].
         */
        config.setHeader("parseOldSigns", "\nParse signs made in Minecraft 1.7 and before without re-creating");
        if (config.contains("parseOldSigns")) {
            parseOldSigns = config.get("parseOldSigns", false);
        }

        config.setHeader("parseParentheses", "\nEnables parsing signs with (train), as well as [train]" +
                                             "\nThis makes it easier to write signs with a Mac keyboard layout");
        allowParenthesesFormat = config.get("parseParentheses", true);

        // Legacy configuration option migration
        if (config.contains("onlyPoweredEmptySwitchersDoPathfinding")) {
            if (config.get("onlyPoweredEmptySwitchersDoPathfinding", false)) {
                config.set("onlyPoweredSwitchersDoPathFinding", true);
                config.set("onlyEmptySwitchersDoPathFinding", true);
            }
            config.remove("onlyPoweredEmptySwitchersDoPathfinding");
        }

        config.setHeader("onlyPoweredSwitchersDoPathFinding", "\nSets whether switcher signs must be redstone-powered to switch track using pathfinding logic");
        config.addHeader("onlyPoweredSwitchersDoPathFinding", "If true, then signs must be redstone-powered or use [+train] to do pathfinding");
        config.addHeader("onlyPoweredSwitchersDoPathFinding", "If false, then signs will also switch track using pathfinding when not powered");
        config.addHeader("onlyPoweredSwitchersDoPathFinding", "Default is true, which allows for [-train] switcher signs to properly detect trains");
        onlyPoweredSwitchersDoPathFinding = config.get("onlyPoweredSwitchersDoPathFinding", true);

        config.setHeader("onlyEmptySwitchersDoPathFinding", "\nSets whether switchers must have the last two lines on the sign empty, for it to switch");
        config.addHeader("onlyEmptySwitchersDoPathFinding", "track using pathfinding logic");
        config.addHeader("onlyEmptySwitchersDoPathFinding", "If true, then statements on switcher signs will disable the pathfinding functionality");
        config.addHeader("onlyEmptySwitchersDoPathFinding", "If false, then statements on switcher signs complement the pathfinding functionality");
        config.addHeader("onlyEmptySwitchersDoPathFinding", "Default is false, which allows pathfinding to be a fallback case");
        onlyEmptySwitchersDoPathFinding = config.get("onlyEmptySwitchersDoPathFinding", false);

        config.setHeader("onlyRegisteredSignsHandleRedstone", "\nWhether only SignAction-registered signs receive redstone change events");
        config.addHeader("onlyRegisteredSignsHandleRedstone", "All TrainCarts signs are registered this way, so this should only be set to false");
        config.addHeader("onlyRegisteredSignsHandleRedstone", "if there are (legacy) plugins that rely solely on the SignActionEvent to operate");
        config.addHeader("onlyRegisteredSignsHandleRedstone", "Keeping this true will significantly improve performance, as non-tc (decorative) signs");
        config.addHeader("onlyRegisteredSignsHandleRedstone", "are exempt from tracking");
        onlyRegisteredSignsHandleRedstone = config.get("onlyRegisteredSignsHandleRedstone", true);

        config.setHeader("rerouteOnStartup", "\nWhen enabled, re-calculates all path finding routes on plugin startup");
        rerouteOnStartup = config.get("rerouteOnStartup", false);

        config.setHeader("pathFindingMaxProcessingPerTick", "\nSets the maximum amount of time (in milliseconds) to spend, per tick,");
        config.addHeader("pathFindingMaxProcessingPerTick", "calculating train routing information. (/train reroute, reroute debug stick)");
        config.addHeader("pathFindingMaxProcessingPerTick", "Raising this can make computations go faster at the cost of server TPS");
        traincarts.getPathProvider().setMaxProcessingPerTick(
                config.get("pathFindingMaxProcessingPerTick", PathProvider.DEFAULT_MAX_PROCESSING_PER_TICK));

        config.setHeader("switcherResetCountersOnFirstCart", "\nFor [cart] signs that use counter statements, specifies whether");
        config.addHeader("switcherResetCountersOnFirstCart", "counters reset on the first cart of the train");
        switcherResetCountersOnFirstCart = config.get("switcherResetCountersOnFirstCart", true);

        config.setHeader("trainsCheckSignFacing", "\nWhether trains only activate signs that they can 'see'. This means the text-side of the sign faces");
        config.addHeader("trainsCheckSignFacing", "the train, or either side does to trigger in either direction. When disabled, trains will instead always");
        config.addHeader("trainsCheckSignFacing", "activate signs. In both cases the behavior can be controlled with a :direction rule on the first line of the sign");
        trainsCheckSignFacing = config.get("trainsCheckSignFacing", true);

        parsers.clear();

        // ================= Defaults ===============
        if (!itemshort.contains("fuel")) {
            itemshort.set("fuel", MaterialUtil.ISFUEL.toString());
        }
        if (!itemshort.contains("heatable")) {
            itemshort.set("heatable", MaterialUtil.ISHEATABLE.toString());
        }
        if (!itemshort.contains("armor")) {
            itemshort.set("armor", MaterialUtil.ISARMOR.toString());
        }
        if (!itemshort.contains("sword")) {
            itemshort.set("sword", MaterialUtil.ISSWORD.toString());
        }
        if (!itemshort.contains("boots")) {
            itemshort.set("boots", MaterialUtil.ISBOOTS.toString());
        }
        if (!itemshort.contains("leggings")) {
            itemshort.set("leggings", MaterialUtil.ISLEGGINGS.toString());
        }
        if (!itemshort.contains("chestplate")) {
            itemshort.set("chestplate", MaterialUtil.ISCHESTPLATE.toString());
        }
        if (!itemshort.contains("helmet")) {
            itemshort.set("helmet", MaterialUtil.ISHELMET.toString());
        }
        // ===========================================

        // Default animations that can be applied to the root node
        defaultAnimations.clear();
        config.setHeader("defaultAnimations", "\nDefault attachment animations that can be applied to the base of all trains");
        if (!config.isNode("defaultAnimations")) {
            Animation[] defaults = {
                    new Animation("rotate",
                            "t=0.25 yaw=0.0",
                            "t=0.25 yaw=90.0",
                            "t=0.25 yaw=180.0",
                            "t=0.25 yaw=270.0",
                            "t=0.0 yaw=360.0"),
                    new Animation("roll",
                            "t=0.25 roll=0.0",
                            "t=0.25 roll=90.0",
                            "t=0.25 roll=180.0",
                            "t=0.25 roll=270.0",
                            "t=0.0 roll=360.0"),
                    new Animation("pitch",
                            "t=0.25 pitch=0.0",
                            "t=0.25 pitch=90.0",
                            "t=0.25 pitch=180.0",
                            "t=0.25 pitch=270.0",
                            "t=0.0 pitch=360.0")
            };

            ConfigurationNode defaultAnimationsNode = config.getNode("defaultAnimations");
            for (Animation anim : defaults) {
                anim.saveToParentConfig(defaultAnimationsNode);
            }
        }
        for (ConfigurationNode animationNode : config.getNode("defaultAnimations").getNodes()) {
            Animation defaultAnimation = Animation.loadFromConfig(animationNode);
            defaultAnimations.put(defaultAnimation.getOptions().getName(), defaultAnimation);
        }

        for (Map.Entry<String, String> entry : itemshort.getValues(String.class).entrySet()) {
            putParsers(entry.getKey(), Util.getParsers(entry.getValue()));
        }

        // Whether images can be loaded outside of the /images subdirectory
        config.setHeader("allowExternalTicketImagePaths", "\nWhether ticket background images can be loaded outside of");
        config.addHeader("allowExternalTicketImagePaths", "the 'plugins/Train_Carts/images' subdirectory. Enabling this may");
        config.addHeader("allowExternalTicketImagePaths", "allow players to view private server data!");
        allowExternalTicketImagePaths = config.get("allowExternalTicketImagePaths", false);

        // Migrate 'playSoundAtStation' to sounds sub-section
        if (config.contains("playSoundAtStation")) {
            config.set("sounds.hissWhenStopAtStation", config.get("playSoundAtStation", true));
            config.remove("playSoundAtStation");
        }

        // Sound configuration options
        config.setHeader("sounds", "\nConfigures the different sound effects used in traincarts globally");
        ConfigurationNode soundsConfig = config.getNode("sounds");
        soundsConfig.setHeader("hissWhenStopAtStation", "Enable/disable hiss sound played when trains stop at stations");
        playHissWhenStopAtStation = soundsConfig.get("hissWhenStopAtStation", true);
        soundsConfig.setHeader("hissWhenDestroyedBySign", "Enable/disable hiss sound played when carts are destroyed by a destroy sign");
        playHissWhenDestroyedBySign = soundsConfig.get("hissWhenDestroyedBySign", true);
        soundsConfig.setHeader("playHissWhenCartRemoved", "Enable/disable hiss sound played when a cart is removed from a train (destroyed/unlinked)");
        playHissWhenCartRemoved = soundsConfig.get("playHissWhenCartRemoved", true);
        soundsConfig.setHeader("hissWhenLinked", "Enable/disable hiss sound played when two carts connect together");
        playHissWhenLinked = soundsConfig.get("hissWhenLinked", true);

        // Whether only solid blocks support upside-down rails, or any type of block with collision
        config.setHeader("upsideDownSupportedByAll", "\nWhether any block supporting things underneath can hold upside-down rails");
        config.addHeader("upsideDownSupportedByAll", "If true, blocks like glass and barrier blocks can hold upside-down rails");
        config.addHeader("upsideDownSupportedByAll", "If false, only fully-solid blocks can hold them");
        upsideDownSupportedByAll = config.get("upsideDownSupportedByAll", true);

        // How many threads are used to update attachment positions
        config.setHeader("attachmentTransformParallelism", "\nHow many threads are used to update attachment positions");
        config.addHeader("attachmentTransformParallelism", "Multi-core CPU servers can benefit from higher parallelism");
        config.addHeader("attachmentTransformParallelism", "If only a single core is available, using 1 is recommended to avoid overhead");
        config.addHeader("attachmentTransformParallelism", "The default, 'auto' or -1, will detect the number of CPU cores and use that");
        if (!config.contains("attachmentTransformParallelism")) {
            config.set("attachmentTransformParallelism", "auto");
            attachmentTransformParallelism = -1;
        } else if ("auto".equals(config.get("attachmentTransformParallelism"))) {
            attachmentTransformParallelism = -1;
        } else {
            attachmentTransformParallelism = config.get("attachmentTransformParallelism", -1);
        }

        config.setHeader("unloadRunawayTrainDistance", "\nWhen trains that keep chunks loaded around them derail, they can end up");
        config.addHeader("unloadRunawayTrainDistance", "flying off into nowhere. This results in thousands of chunks being generated,");
        config.addHeader("unloadRunawayTrainDistance", "with no clear indication other than server performance tanking. To combat this,");
        config.addHeader("unloadRunawayTrainDistance", "carts that derail and move this distance threshold away while derailed, automatically");
        config.addHeader("unloadRunawayTrainDistance", "unload (by setting keep chunks loaded to false again). A warning with details is logged.");
        unloadRunawayTrainDistance = config.get("unloadRunawayTrainDistance", 160.0);

        config.setHeader("maxCommandSelectorValues", "\nMaximum number of expanded values resulting from the @train and @ptrain selectors players can use");
        config.addHeader("maxCommandSelectorValues", "If more than this amount is expanded then an error is sent and no commands are executed");
        config.addHeader("maxCommandSelectorValues", "This limit avoids players being able to freeze the server or crash players with large expressions");
        config.addHeader("maxCommandSelectorValues", "Players need permission 'train.command.selector.use' to use selectors at all");
        config.addHeader("maxCommandSelectorValues", "Players with permission 'train.command.selector.unlimited' are not subjected to this limit");
        maxCommandSelectorValues = config.get("maxCommandSelectorValues", 128);

        config.setHeader("maxConcurrentEffectLoops", "\nMaximum number of times a single Effect Loop can play concurrently");
        config.addHeader("maxConcurrentEffectLoops", "This limit prevents the server running out of resources if some infinite cycle occurs");
        config.addHeader("maxConcurrentEffectLoops", "This is not a GLOBAL limit, but one that applies per 'unique' configurable effect loop");
        config.addHeader("maxConcurrentEffectLoops", "If set to -1 there will be no limit, but this could be dangerous");
        {
            int max = config.get("maxConcurrentEffectLoops", 20);
            if (max < 0) {
                max = Integer.MAX_VALUE;
            } else if (max == 0) {
                max = 1;
            }
            maxConcurrentEffectLoops = max;
        }

        config.setHeader("spawnSignCooldown", "\nCooldown in seconds between spawn sign spawns");
        config.addHeader("spawnSignCooldown", "Spawns are skipped/cancelled when spawning faster than this");
        config.addHeader("spawnSignCooldown", "A value of -1 disables this cooldown (default)");
        spawnSignCooldown = config.get("spawnSignCooldown", -1.0);

        {
            ConfigurationNode cartLimits = config.getNode("cartLimits");
            cartLimits.setHeader("\nLimits of the amount of carts on the server");

            if (config.contains("maxCartsPerWorld")) {
                cartLimits.set("maxCartsPerWorld", config.get("maxCartsPerWorld"));
                config.remove("maxCartsPerWorld");
            }

            cartLimits.setHeader("maxCartsPerWorld", "Maximum number of TrainCarts minecarts allowed per world");
            cartLimits.addHeader("maxCartsPerWorld", "If there are more than this number, no more minecarts can be placed/spawned");
            cartLimits.addHeader("maxCartsPerWorld", "A value of -1 disables this limit (default)");
            maxCartsPerWorld = cartLimits.get("maxCartsPerWorld", -1);

            cartLimits.setHeader("countUnloaded", "\nWhether to include unloaded trains/carts in the maxCartsPerWorld limit");
            maxCartsPerWorldCountUnloaded = cartLimits.get("countUnloaded", false);

            cartLimits.setHeader("maxCartsPerTrain", "Maximum number of carts that can be joined together in a train");
            cartLimits.addHeader("maxCartsPerTrain", "Linking does not happen when it would exceed this limit,");
            cartLimits.addHeader("maxCartsPerTrain", "and trains longer than this cannot be spawned");
            cartLimits.addHeader("maxCartsPerTrain", "A value of -1 disables this limit, allowing any length (default)");
            maxCartsPerTrain = cartLimits.get("maxCartsPerTrain", -1);
        }
    }

    public static void putParsers(String key, ItemParser[] parsersArr) {
        if (LogicUtil.nullOrEmpty(parsersArr)) {
            parsers.remove(key.toLowerCase(Locale.ENGLISH));
        } else {
            parsers.put(key.toLowerCase(Locale.ENGLISH), parsersArr);
        }
    }
}
