package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.StringReplaceBundle;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.controller.*;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.LaunchFunction;
import com.bergerkiller.mountiplex.conversion.Conversion;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class TrainCarts extends PluginBase {
    public static final StringReplaceBundle messageShortcuts = new StringReplaceBundle();
    public static final StringReplaceBundle statementShortcuts = new StringReplaceBundle();
    /*
     * Settings
     */
    public static double maxVelocity;
    public static double maxEjectDistance;
    public static double cartDistance;
    public static double turnedCartDistance;
    public static double cartDistanceForcer;
    public static double turnedCartDistanceForcer;
    public static double nearCartDistanceFactor;
    public static double maxCartDistance;
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
    public static boolean showTransferAnimations;
    public static boolean slowDownEmptyCarts;
    public static double slowDownMultiplierSlow;
    public static double slowDownMultiplierNormal;
    public static boolean refillAtStations;
    public static boolean instantCreativeDestroy;
    public static boolean allowRailEditing;
    public static double manualMovementSpeed;
    public static boolean allMinecartsAreTrainCarts;
    public static boolean useNetworkSynchronizer;
    public static boolean allowVerticalPitch;
    public static boolean enableCeilingBlockCollision = true; // whether to allow blocks above the minecart to collide
    public static int collisionReEnterDelay = 100; // Delay before letting mobs/player enter again
    public static boolean EssentialsEnabled = false;
    public static boolean SignLinkEnabled = false;
    public static boolean MinecartManiaEnabled = false;
    public static boolean MyWorldsEnabled = false;
    public static String launchFunctionType = "bezier";
    public static boolean parseOldSigns;
    public static boolean allowParenthesesFormat = true;
    public static int tickUpdateDivider = 1; // allows slowing down of minecart physics globally (debugging!)
    public static int tickUpdateNow = 0; // forces update ticks
    public static TrainCarts plugin;
    private static String currencyFormat;
    private static Task fixGroupTickTask;
    private Set<Material> allowedBlockBreakTypes = new HashSet<>();
    private Set<String> disabledWorlds = new HashSet<>();
    private Task signtask;
    private TCPacketListener packetListener;
    private FileConfiguration config;
    private Map<String, ItemParser[]> parsers = new HashMap<>();

    public static boolean canBreak(Material type) {
        return plugin.allowedBlockBreakTypes.contains(type);
    }

    /**
     * Gets the Currency text to display a currency value
     *
     * @param value to display
     * @return currency text
     */
    public static String getCurrencyText(double value) {
        return currencyFormat.replace("%value%", Double.toString(value));
    }

    /**
     * Converts generic text to a formatted message based on style codes and message shortcuts
     *
     * @param text to convert
     * @return message
     */
    public static String getMessage(String text) {
        return StringUtil.ampToColor(messageShortcuts.replace(text));
    }

    /**
     * Sends a message to a player, keeping player-specific text variables in mind
     *
     * @param player to send the message to
     * @param text   to send
     */
    public static void sendMessage(Player player, String text) {
        if (TrainCarts.SignLinkEnabled) {
            int startindex, endindex;
            while ((startindex = text.indexOf('%')) != -1 && (endindex = text.indexOf('%', startindex + 1)) != -1) {
                String varname = text.substring(startindex + 1, endindex);
                String value = varname.isEmpty() ? "%" : Variables.get(varname).get(player.getName());
                text = text.substring(0, startindex) + value + text.substring(endindex + 1);
            }
        }
        player.sendMessage(text);
    }

    public static boolean isWorldDisabled(BlockEvent event) {
        return isWorldDisabled(event.getBlock().getWorld());
    }

    public static boolean isWorldDisabled(Block worldContainer) {
        return isWorldDisabled(worldContainer.getWorld());
    }

    public static boolean isWorldDisabled(World world) {
        return isWorldDisabled(world.getName());
    }

    public static boolean isWorldDisabled(String worldname) {
        return plugin.disabledWorlds.contains(worldname.toLowerCase());
    }

    public static boolean handlePlayerVehicleChange(Player player, Entity newVehicle) {
        try {
            MinecartMember<?> newMinecart = MinecartMemberStore.getFromEntity(newVehicle);
            if (newMinecart != null) {
                CartPropertiesStore.setEditing(player, newMinecart.getProperties());
            }
            // Allow exiting the current minecart
            MinecartMember<?> entered = MinecartMemberStore.getFromEntity(player.getVehicle());
            if (entered != null && !entered.getProperties().getPlayersExit()) {
                return false;
            }

            // Allow entering the new minecart
            if (newMinecart != null && !newMinecart.getProperties().getPlayersEnter()) {
                return false;
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
        }
        return true;
    }

    /**
     * Writes the latest changes in message shortcuts to file
     */
    public void saveShortcuts() {
        messageShortcuts.save(config.getNode("messageShortcuts"));
        config.save();
    }

    /**
     * Obtains all Item parsers associated with a certain key and amount.
     * If none was found in the TrainCarts item mapping, it is parsed.
     *
     * @param key    to get
     * @param amount to multiply the result with. Use 1 to ignore.
     * @return An array of associated item parsers
     */
    public ItemParser[] getParsers(String key, int amount) {
        ItemParser[] rval = parsers.get(key.toLowerCase(Locale.ENGLISH));

        if (rval == null) {
            return new ItemParser[]{ItemParser.parse(key, amount == -1 ? null : Integer.toString(amount))};
        }
        // Clone to avoid altering the values in the map
        rval = LogicUtil.cloneArray(rval);
        if (amount == -1) {
            // Set to any amount
            for (int i = 0; i < rval.length; i++) {
                rval[i] = rval[i].setAmount(-1);
            }
        } else if (amount > 1) {
            // Multiply by amount (ignore 1)
            for (int i = 0; i < rval.length; i++) {
                rval[i] = rval[i].multiplyAmount(amount);
            }
        }
        return rval;
    }

    public void putParsers(String key, ItemParser[] parsers) {
        if (LogicUtil.nullOrEmpty(parsers)) {
            this.parsers.remove(key.toLowerCase(Locale.ENGLISH));
        } else {
            this.parsers.put(key.toLowerCase(Locale.ENGLISH), parsers);
        }
    }

    public void loadConfig() {
        config = new FileConfiguration(this);
        config.load();
        config.setHeader("This is the configuration file of TrainCarts");
        config.addHeader("In here you can tweak TrainCarts to what you want");
        config.addHeader("For more information, you can visit the following websites:");
        config.addHeader("http://www.minecraftwiki.net/wiki/Bukkit/TrainCarts");
        config.addHeader("http://forums.bukkit.org/threads/traincarts.29491/");

        config.setHeader("normal", "\nSettings for normally-aligned (straight) carts");
        config.setHeader("normal.cartDistance", "The distance between two carts in a train");
        config.setHeader("normal.cartDistanceForcer", "The factor at which this distance is kept");
        cartDistance = config.get("normal.cartDistance", 1.5);
        cartDistanceForcer = config.get("normal.cartDistanceForcer", 0.1);
        config.setHeader("normal", "\nThe distance between two normally-aligned (straight) carts in a train");

        config.setHeader("turned", "\nSettings for turned (in curve) carts");
        config.setHeader("turned.cartDistance", "The distance between two carts in a train");
        config.setHeader("turned.cartDistanceForcer", "The factor at which this distance is kept");
        turnedCartDistance = config.get("turned.cartDistance", 1.6);
        turnedCartDistanceForcer = config.get("turned.cartDistanceForcer", 0.2);

        config.setHeader("nearCartDistanceFactor", "\nThe 'keep distance' factor to apply when carts are too close to each other");
        nearCartDistanceFactor = config.get("nearCartDistanceFactor", 1.2);

        config.setHeader("maxCartDistance", "\nThe maximum allowed cart distance, after this distance the carts break apart");
        maxCartDistance = config.get("maxCartDistance", 4.0);

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

        config.setHeader("maxEjectDistance", "\nThe maximum allowed ejection distance for eject signs");
        maxEjectDistance = config.get("maxEjectDistance", 10.0);

        config.setHeader("launchForce", "\nThe amount of velocity stations give when launching trains");
        launchForce = config.get("launchForce", 10.0);


        // Deprecation backwards compatibility
        if (config.contains("pushAway")) {
            config.set("collision.ignoreOwners", config.get("pushAway.ignoreOwners", true));
            config.set("collision.ignoreGlobalOwners", config.get("pushAway.ignoreGlobalOwners", false));
            config.set("collision.pushAwayForce", config.get("pushAway.force", 0.2));
            config.remove("pushAway");
        }

        config.setHeader("pushAway", "\nSettings used when carts push away/aside others (if enabled)");
        config.setHeader("pushAway.ignoreOwners", "If train owners are ignored");
        config.setHeader("pushAway.ignoreGlobalOwners", "If global train owners are ignored");

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

        config.setHeader("launchFunction", "\nWhat style of launching to use in stations and launcher sign systems. Possible values:\n" +
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

        config.setHeader("instantCreativeDestroy", "\nWhether minecarts are instantly destroyed by creative players");
        config.addHeader("instantCreativeDestroy", "Note that manual minecart movement is not possible for creative players with this enabled");
        instantCreativeDestroy = config.get("instantCreativeDestroy", false);

        config.setHeader("allowVerticalPitch", "\nWhether minecarts are allowed to have a 90-degree pitch angle when going up vertical rails");
        config.addHeader("allowVerticalPitch", "When disabled, minecarts will keep a 0-degree pitch angle instead");
        allowVerticalPitch = config.get("allowVerticalPitch", true);

        config.setHeader("allowRailEditing", "\nWhether players (with build permissions) can edit existing rails by right-clicking on them");
        allowRailEditing = config.get("allowRailEditing", true);

        config.setHeader("manualMovementSpeed", "\nWhat velocity to set when a player tries to manually move a train (by damaging it)");
        manualMovementSpeed = config.get("manualMovementSpeed", 12.0);

        config.setHeader("currencyFormat", "\nThe currency Ticket signs will display in messages, %value% represents the displayed value");
        currencyFormat = config.get("currencyFormat", "%value% Dollars");

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
            allowedBlockBreakTypes.add(Material.CROPS);
            allowedBlockBreakTypes.add(Material.LOG);
        }

        config.setHeader("disabledWorlds", "\nA list of world names where TrainCarts should be disabled");
        config.addHeader("disabledWorlds", "World names are not case-sensitive");
        this.disabledWorlds.clear();
        if (!config.contains("disabledWorlds")) {
            config.set("disabledWorlds", Arrays.asList("DefaultWorld1", "DefaultWorld2"));
        }
        for (String world : config.getList("disabledWorlds", String.class)) {
            this.disabledWorlds.add(world.toLowerCase());
        }

        //set it again
        List<String> types = config.getList("allowedBlockBreakTypes", String.class);
        types.clear();
        for (Material mat : allowedBlockBreakTypes) {
            types.add(mat.toString());
        }

        config.setHeader("showTransferAnimations", "\nWhether or not to show item animations when transferring items");
        showTransferAnimations = config.get("showTransferAnimations", true);

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

        for (Map.Entry<String, String> entry : itemshort.getValues(String.class).entrySet()) {
            putParsers(entry.getKey(), Util.getParsers(entry.getValue()));
            itemshort.setRead(entry.getKey());
        }

        config.trim();
        config.save();
    }

    @Override
    public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
        switch (pluginName) {
            case "SignLink":
                Task.stop(signtask);
                if (SignLinkEnabled = enabled) {
                    log(Level.INFO, "SignLink detected, support for arrival signs added!");
                    signtask = new Task(this) {
                        public void run() {
                            ArrivalSigns.updateAll();
                        }
                    };
                    signtask.start(0, 10);
                } else {
                    signtask = null;
                }
                break;
            case "My_Worlds":
                if (MyWorldsEnabled = enabled) {
                    log(Level.INFO, "MyWorlds detected, support for portal sign train teleportation added!");
                }
                break;
            case "Essentials":
                EssentialsEnabled = enabled;
                break;
        }
    }

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    public void enable() {
        plugin = this;

        //registering
        this.register(packetListener = new TCPacketListener(), PacketType.IN_STEER_VEHICLE);
        this.register(TCListener.class);
        this.register(RedstoneTracker.class);
        this.register("train", "cart");
        Conversion.registerConverters(MinecartMemberStore.class);

        //Load configuration
        loadConfig();

        //update max item stack
        if (maxMinecartStackSize != 1) {
            for (Material material : Material.values()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, maxMinecartStackSize);
                }
            }
        }

        //init statements
        Statement.init();

        //Init signs
        SignAction.init();

        //Load properties
        TrainProperties.load();

        //Load groups
        OfflineGroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

        //Convert Minecarts
        MinecartMemberStore.convertAll();

        //Load destinations
        PathNode.init(getDataFolder() + File.separator + "destinations.dat");

        //Load arrival times
        ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");

        //Load detector regions
        DetectorRegion.init(getDataFolder() + File.separator + "detectorregions.dat");

        //Load detector sign locations
        SignActionDetector.INSTANCE.init(getDataFolder() + File.separator + "detectorsigns.dat");

        //Load spawn sign locations
        SignActionSpawn.init(getDataFolder() + File.separator + "spawnsigns.dat");

        //Restore carts where possible
        TrainCarts.plugin.log(Level.INFO, "Restoring trains and loading nearby chunks...");
        OfflineGroupManager.refresh();

        // Start the path finding task
        PathProvider.init();

        // Hackish fix the chunk persistence failing
        fixGroupTickTask = new TrainUpdateTask(this).start(1, 1);

        //Properly dispose of partly-referenced carts
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                for (World world : WorldUtil.getWorlds()) {
                    OfflineGroupManager.removeBuggedMinecarts(world);
                }
            }
        });
    }

    /**
     * Saves all traincarts related information to file
     */
    public void save() {
        //Save properties
        TrainProperties.save();

        //Save destinations
        PathNode.save(getDataFolder() + File.separator + "destinations.dat");

        //Save arrival times
        ArrivalSigns.save(getDataFolder() + File.separator + "arrivaltimes.txt");

        //Save spawn sign locations
        SignActionSpawn.save(getDataFolder() + File.separator + "spawnsigns.dat");

        //Save detector sign locations
        SignActionDetector.INSTANCE.save(getDataFolder() + File.separator + "detectorsigns.dat");

        //Save detector regions
        DetectorRegion.save(getDataFolder() + File.separator + "detectorregions.dat");

        // Save train information
        OfflineGroupManager.save(getDataFolder() + File.separator + "trains.groupdata");
    }

    public void disable() {
        //Unregister listeners
        this.unregister(packetListener);
        packetListener = null;

        //Stop tasks
        Task.stop(signtask);
        Task.stop(fixGroupTickTask);

        //update max item stack
        if (maxMinecartStackSize != 1) {
            for (Material material : Material.values()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, 1);
                }
            }
        }

        //undo replacements for correct native saving
        for (MinecartGroup mg : MinecartGroup.getGroups()) {
            mg.unload();
        }

        //entities left behind?
        for (World world : WorldUtil.getWorlds()) {
            for (org.bukkit.entity.Entity entity : WorldUtil.getEntities(world)) {
                MinecartGroup group = MinecartGroup.get(entity);
                if (group != null) {
                    group.unload();
                }
            }
        }

        save();

        // Deinit classes
        PathNode.deinit();
        ArrivalSigns.deinit();
        SignActionSpawn.deinit();
        Statement.deinit();
        SignAction.deinit();
        ItemAnimation.deinit();
        OfflineGroupManager.deinit();
        PathProvider.deinit();
    }

    public boolean command(CommandSender sender, String cmd, String[] args) {
        return Commands.execute(sender, cmd, args);
    }

    @Override
    public void localization() {
        this.loadLocales(Localization.class);
    }

    @Override
    public void permissions() {
        this.loadPermissions(Permission.class);
    }

    private static class TrainUpdateTask extends Task {
        int ctr = 0;

        public TrainUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        public void run() {
            if (++ctr >= tickUpdateDivider) {
                ctr = 0;
                tickUpdateNow++;
            }
            if (tickUpdateNow > 0) {
                tickUpdateNow--;
                MinecartGroupStore.doFixedTick(tickUpdateDivider != 1);
            }
        }
    }
}
