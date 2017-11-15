package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModels;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.controller.*;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.portals.TCPortalManager;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
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
    public static TrainCarts plugin;
    private static Task fixGroupTickTask;
    private Task signtask;
    private Task autosaveTask;
    private TCPacketListener packetListener;
    private FileConfiguration config;

    public static boolean canBreak(Material type) {
        return TCConfig.allowedBlockBreakTypes.contains(type);
    }

    /**
     * Gets the Currency text to display a currency value
     *
     * @param value to display
     * @return currency text
     */
    public static String getCurrencyText(double value) {
        return TCConfig.currencyFormat.replace("%value%", Double.toString(value));
    }

    /**
     * Converts generic text to a formatted message based on style codes and message shortcuts
     *
     * @param text to convert
     * @return message
     */
    public static String getMessage(String text) {
        return StringUtil.ampToColor(TCConfig.messageShortcuts.replace(text));
    }

    /**
     * Sends a message to a player, keeping player-specific text variables in mind
     *
     * @param player to send the message to
     * @param text   to send
     */
    public static void sendMessage(Player player, String text) {
        if (TCConfig.SignLinkEnabled) {
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
        return TCConfig.disabledWorlds.contains(worldname.toLowerCase());
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
        TCConfig.messageShortcuts.save(config.getNode("messageShortcuts"));
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
        ItemParser[] rval = TCConfig.parsers.get(key.toLowerCase(Locale.ENGLISH));

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
        TCConfig.putParsers(key, parsers);
    }

    public void loadConfig() {
        config = new FileConfiguration(this);
        config.load();
        TCConfig.load(config);
        config.trim();
        config.save();
    }

    @Override
    public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
        TCPortalManager.updateProviders(pluginName, plugin, enabled);
        switch (pluginName) {
            case "SignLink":
                Task.stop(signtask);
                if (TCConfig.SignLinkEnabled = enabled) {
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
            case "Essentials":
                TCConfig.EssentialsEnabled = enabled;
                break;
        }
    }

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    public void enable() {
        plugin = this;

        MapResourcePack.SERVER.load();

        //Load configuration
        loadConfig();

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : Material.values()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, TCConfig.maxMinecartStackSize);
                }
            }
        }

        //Load attachment models
        AttachmentModels.init(getDataFolder() + File.separator + "models.yml");

        //init statements
        Statement.init();

        //Init signs
        SignAction.init();

        //Load properties
        TrainProperties.load();

        //Load tickets
        TicketStore.load();

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

        //Activate all detector regions with trains that are on it
        DetectorRegion.detectAllMinecarts();

        // Hackish fix the chunk persistence failing
        fixGroupTickTask = new TrainUpdateTask(this).start(1, 1);

        // Routinely saves TrainCarts changed state information to disk (autosave=true)
        autosaveTask = new AutosaveTask(this).start(TCConfig.autoSaveInterval, TCConfig.autoSaveInterval);

        //Properly dispose of partly-referenced carts
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                for (World world : WorldUtil.getWorlds()) {
                    OfflineGroupManager.removeBuggedMinecarts(world);
                }
            }
        });

        // Register listeners and commands
        this.register(packetListener = new TCPacketListener(), PacketType.IN_STEER_VEHICLE, PacketType.IN_USE_ENTITY);
        this.register(TCListener.class);
        this.register(RedstoneTracker.class);
        this.register("train", "cart");
        Conversion.registerConverters(MinecartMemberStore.class);
    }

    /**
     * Saves all traincarts related information to file
     */
    public void save(boolean autosave) {
        //Save properties
        TrainProperties.save(autosave);

        //Save Train tickets
        TicketStore.save(autosave);

        //Save destinations
        PathNode.save(autosave, getDataFolder() + File.separator + "destinations.dat");

        //Save arrival times
        if (!autosave) {
            ArrivalSigns.save(getDataFolder() + File.separator + "arrivaltimes.txt");
        }

        //Save spawn sign locations
        SignActionSpawn.save(autosave, getDataFolder() + File.separator + "spawnsigns.dat");

        //Save detector sign locations
        SignActionDetector.INSTANCE.save(autosave, getDataFolder() + File.separator + "detectorsigns.dat");

        //Save detector regions
        DetectorRegion.save(autosave, getDataFolder() + File.separator + "detectorregions.dat");

        //Save attachment models
        AttachmentModels.save(autosave, getDataFolder() + File.separator + "models.yml");

        // Save train information
        if (!autosave) {
            OfflineGroupManager.save(getDataFolder() + File.separator + "trains.groupdata");
        }
    }

    public void disable() {
        //Unregister listeners
        this.unregister(packetListener);
        packetListener = null;

        //Stop tasks
        Task.stop(signtask);
        Task.stop(fixGroupTickTask);
        Task.stop(autosaveTask);

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : Material.values()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, 1);
                }
            }
        }

        //this corrects minecart positions before saving
        MinecartGroupStore.doPostMoveLogic();

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

        //save all data to disk (autosave=false)
        save(false);

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

    private static class AutosaveTask extends Task {

        public AutosaveTask(TrainCarts plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            ((TrainCarts) this.getPlugin()).save(true);
        }
    }

    private static class TrainUpdateTask extends Task {
        int ctr = 0;

        public TrainUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        public void run() {
            if (++ctr >= TCConfig.tickUpdateDivider) {
                ctr = 0;
                TCConfig.tickUpdateNow++;
            }
            if (TCConfig.tickUpdateNow > 0) {
                TCConfig.tickUpdateNow--;
                MinecartGroupStore.doFixedTick(TCConfig.tickUpdateDivider != 1);
            }
        }
    }
}
