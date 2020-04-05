package com.bergerkiller.bukkit.tc;

import java.util.Arrays;
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
    public static boolean playSoundAtStation;
    public static int maxDetectorLength;
    public static int maxMinecartStackSize;
    public static int defaultTransferRadius;
    public static int maxTransferRadius;
    public static boolean optimizeInteraction;
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
    public static boolean useNetworkSynchronizer;
    public static boolean allowUpsideDownRails;
    public static boolean allowNetherTeleport;
    public static boolean enableCeilingBlockCollision = true; // whether to allow blocks above the minecart to collide
    public static int collisionReEnterDelay = 100; // Delay before letting mobs/player enter again
    public static boolean optimizeBlockActivation;
    public static boolean SignLinkEnabled = false;
    public static boolean MinecartManiaEnabled = false;
    public static boolean activatorEjectEnabled = true;
    public static boolean railTrackerDebugEnabled = false;
    public static boolean wheelTrackerDebugEnabled = false;
    public static boolean initRedstoneWithRadius = true;
    public static boolean animationsUseTickTime = false;
    public static boolean claimNewSavedTrains = true;
    public static boolean onlyPoweredEmptySwitchersDoPathfinding = false;
    public static boolean enableSneakingInAttachmentEditor = false;
    public static String launchFunctionType = "bezier";
    public static boolean parseOldSigns;
    public static boolean allowParenthesesFormat = true;
    public static int tickUpdateDivider = 1; // allows slowing down of minecart physics globally (debugging!)
    public static int tickUpdateNow = 0; // forces update ticks
    public static boolean tickUpdateEnabled = true; // whether train tick updates are enabled
    public static int autoSaveInterval = 30 * 20; // autosave every 30 seconds
    public static String currencyFormat;
    public static Set<Material> allowedBlockBreakTypes = new HashSet<>();
    public static Set<String> disabledWorlds = new HashSet<>();
    public static Map<String, ItemParser[]> parsers = new HashMap<>();
    public static MapResourcePack resourcePack = MapResourcePack.SERVER;
    public static Map<String, Animation> defaultAnimations = new HashMap<>();
    public static Hastebin hastebin = null;

    public static void load(FileConfiguration config) {
        config.setHeader("This is the configuration file of TrainCarts");
        config.addHeader("In here you can tweak TrainCarts to what you want");
        config.addHeader("For more information, you can visit the following websites:");
        config.addHeader("https://minecraft.gamepedia.com/Mods/TrainCarts");
        config.addHeader("https://www.spigotmc.org/resources/traincarts.39592/");

        config.setHeader("resourcePack", "\nPath or online download URL to the resource pack to use");
        config.addHeader("resourcePack", "Using 'server' makes it use server.properties (default)");
        config.addHeader("resourcePack", "Using 'default' or empty makes it use no resource pack at all");
        resourcePack = new MapResourcePack(config.get("resourcePack", "server"));
        resourcePack.load();

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
        config.setHeader("linkProperties.cartDistanceGap", "The distance between two carts in a train");
        config.setHeader("linkProperties.cartDistanceGapMax", "The distance between two carts above which they lose linkage and disconnect");
        config.setHeader("linkProperties.cartDistanceForcer", "Factor at which the gap is maintained, based on train velocity");
        config.setHeader("linkProperties.cartDistanceForcerConstant", "Factor at which the gap is maintained, always active");
        config.addHeader("linkProperties.cartDistanceForcerConstant", "When set to nonzero, will cause carts to move at standstill");

        cartDistanceGap = config.get("linkProperties.cartDistanceGap", 0.5);
        cartDistanceGapMax = config.get("linkProperties.cartDistanceGapMax", cartDistanceGap + 2.0);
        cartDistanceForcer = config.get("linkProperties.cartDistanceForcer", 0.1);
        cartDistanceForcerConstant = config.get("linkProperties.cartDistanceForcerConstant", 0.0);

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

        config.setHeader("launchForce", "\nThe amount of velocity stations give when launching trains");
        launchForce = config.get("launchForce", 10.0);

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

        config.setHeader("playSoundAtStation", "\nWhether or not a hissing sound is made when trains stop at a station");
        playSoundAtStation = config.get("playSoundAtStation", true);

        config.setHeader("launchFunction", "\nWhat style of launching to use in stations and launcher sign systems by default. Possible values:\n" +
                "- 'linear': gradually switches from one motion speed to another at a linear rate\n" +
                "- 'bezier': uses a bezier curve (ease in-out), resulting in slower changes in motion at start/end of launch");
        launchFunctionType = config.get("launchFunction", "bezier");

        config.setHeader("keepChunksLoadedOnlyWhenMoving", "\nWhether or not chunks are only kept loaded when the train is moving");
        keepChunksLoadedOnlyWhenMoving = config.get("keepChunksLoadedOnlyWhenMoving", false);

        config.setHeader("enableCeilingBlockCollision", "\nWhether to enable or cancel collisions with blocks above minecarts");
        config.addHeader("enableCeilingBlockCollision", "Some constructions depend on these block collisions to block minecarts");
        config.addHeader("enableCeilingBlockCollision", "If these collisions are unwanted, they can be turned off here");
        enableCeilingBlockCollision = config.get("enableCeilingBlockCollision", true);

        config.setHeader("useNetworkSynchronizer", "\nAdvanced: Whether trains use a different way of server->client synchronization");
        config.addHeader("useNetworkSynchronizer", "With this enabled, trains are expected to move smoother with less bumping");
        config.addHeader("useNetworkSynchronizer", "With this disabled, no smoothing is applied. Only disable it if it causes problems/incompatibility");
        useNetworkSynchronizer = config.get("useNetworkSynchronizer", true);

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

        config.setHeader("hastebinServer", "\nThe hastebin server which is used to upload saved trains");
        config.addHeader("hastebinServer", "This will be used when using the /savedtrain [name] paste command");
        hastebin = new Hastebin(TrainCarts.plugin, config.get("hastebinServer", config.get("hastebinServer", "https://paste.traincarts.net")));

        config.setHeader("enableSeatThirdPersonView", "\nEnable or disable seeing yourself in third-person on vertical rails");
        config.addHeader("enableSeatThirdPersonView", "Turning this off only causes this mode to activate when going upside-down");
        enableSeatThirdPersonView = config.get("enableSeatThirdPersonView", false);

        config.setHeader("maxDetectorLength", "\nThe maximum length a detector region (between two detectors) can be");
        maxDetectorLength = config.get("maxDetectorLength", 2000);

        config.setHeader("maxMinecartStackSize", "\nThe maximum amount of minecart items that can be stacked in one item");
        maxMinecartStackSize = config.get("maxMinecartStackSize", 64);

        config.setHeader("maxTransferRadius", "\nThe maximum radius chest/furnace sign systems look for the needed blocks");
        maxTransferRadius = config.get("maxTransferRadius", 5);

        config.setHeader("defaultTransferRadius", "\nThe default radius chest/furnace sign systems look for the needed blocks");
        defaultTransferRadius = MathUtil.clamp(config.get("defaultTransferRadius", 2), 1, maxTransferRadius);

        config.setHeader("slowDownEmptyCarts", "\nWhether or not empty minecarts slow down faster than occupied minecarts");
        slowDownEmptyCarts = config.get("slowDownEmptyCarts", false);

        config.setHeader("refillAtStations", "\nWhether storage minecarts get fuel when launching from stations");
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
        allowUpsideDownRails = config.get("allowUpsideDownRails", true);

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

        config.setHeader("disabledWorlds", "\nA list of world names where TrainCarts should be disabled");
        config.addHeader("disabledWorlds", "World names are not case-sensitive");
        disabledWorlds.clear();
        if (!config.contains("disabledWorlds")) {
            config.set("disabledWorlds", Arrays.asList("DefaultWorld1", "DefaultWorld2"));
        }
        for (String world : config.getList("disabledWorlds", String.class)) {
            disabledWorlds.add(world.toLowerCase());
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

        config.setHeader("initRedstoneWithRadius", "\nRequires all neighbouring chunks to be loaded too before");
        config.addHeader("initRedstoneWithRadius", "initializing the redstone state (powered or not) of signs in a chunk");
        config.addHeader("initRedstoneWithRadius", "True can prevent crashes and helps performance by avoiding recursive chunk loads");
        config.addHeader("initRedstoneWithRadius", "False will make sure signs initialize sooner and respond to redstone faster");
        initRedstoneWithRadius = config.get("initRedstoneWithRadius", true);

        config.setHeader("onlyPoweredEmptySwitchersDoPathfinding", "\nSets whether only powered switcher signs without statements on them");
        config.addHeader("onlyPoweredEmptySwitchersDoPathfinding", "actively switch tracks to lead trains towards their destination");
        config.addHeader("onlyPoweredEmptySwitchersDoPathfinding", "When true, only [+train] switcher signs do pathfinding logic");
        config.addHeader("onlyPoweredEmptySwitchersDoPathfinding", "When false, the original behavior is used, where any switcher sign handles path finding");
        config.addHeader("onlyPoweredEmptySwitchersDoPathfinding", "False will allow switching of trains overriding standard path finding");
        onlyPoweredEmptySwitchersDoPathfinding = config.get("onlyPoweredEmptySwitchersDoPathfinding", false);

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
            itemshort.set("leggins", MaterialUtil.ISLEGGINGS.toString());
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
            itemshort.setRead(entry.getKey());
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
