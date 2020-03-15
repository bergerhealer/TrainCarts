package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.controller.DefaultEntityController;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.internal.legacy.MaterialsByName;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelStore;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentLight;
import com.bergerkiller.bukkit.tc.attachments.control.GlowColorTeamProvider;
import com.bergerkiller.bukkit.tc.attachments.control.SeatAttachmentMap;
import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.cache.RailPieceCache;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.controller.*;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.portals.TCPortalManager;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSignManager;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bergerkiller.mountiplex.conversion.Conversion;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class TrainCarts extends PluginBase {
    public static TrainCarts plugin;
    private Task fixGroupTickTask;
    private Task signtask;
    private Task autosaveTask;
    private Task cacheCleanupTask;
    private Task mutexZoneUpdateTask;
    private TCPacketListener packetListener;
    private TCInteractionPacketListener interactionPacketListener;
    private FileConfiguration config;
    private AttachmentModelStore attachmentModels;
    private SpawnSignManager spawnSignManager;
    private SavedTrainPropertiesStore savedTrainsStore;
    private SeatAttachmentMap seatAttachmentMap;
    private RedstoneTracker redstoneTracker;
    private GlowColorTeamProvider glowColorTeamProvider;
    private PathProvider pathProvider;
    private Economy econ = null;

    /**
     * Gets a helper class for assigning (fake) entities to teams to change their glowing effect
     * color.
     * 
     * @return glow color team provider
     */
    public GlowColorTeamProvider getGlowColorTeamProvider() {
        return this.glowColorTeamProvider;
    }

    /**
     * Gets a mapping of passenger entity Ids to the cart attachment seat they are occupying,
     * if any.
     * 
     * @return seat attachment map
     */
    public SeatAttachmentMap getSeatAttachmentMap() {
        return this.seatAttachmentMap;
    }

    /**
     * Gets the program component responsible for automatically spawning trains from spawn signs periodically.
     * 
     * @return spawn sign manager
     */
    public SpawnSignManager getSpawnSignManager() {
        return this.spawnSignManager;
    }

    /**
     * Gets access to the place where attachment models are stored, loaded and saved
     * 
     * @return attachment model store
     */
    public AttachmentModelStore getAttachmentModels() {
        return this.attachmentModels;
    }

    /**
     * Gets access to a manager for saved trains
     * 
     * @return saved trains store
     */
    public SavedTrainPropertiesStore getSavedTrains() {
        return this.savedTrainsStore;
    }

    /**
     * Gets the path provider, which is responsible for finding the route to destinations
     * 
     * @return path provider
     */
    public PathProvider getPathProvider() {
        return this.pathProvider;
    }

    /**
     * Gets the Economy manager
     *
     * @return
     */
    public Economy getEconomy() {
        return econ;
    }

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
        Economy econ = TrainCarts.plugin.econ;
        if (econ != null) {
            return econ.format(value);
        }
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

    /**
     * Setup the Vault service
     *
     * @return boolean  whether Vault was registered successfully
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
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
        config.save();

        // Refresh
        this.autosaveTask.stop().start(TCConfig.autoSaveInterval, TCConfig.autoSaveInterval);
    }

    public void loadSavedTrains() {
        this.savedTrainsStore = new SavedTrainPropertiesStore(getDataFolder() + File.separator + "SavedTrainProperties.yml");
        this.savedTrainsStore.loadModules(getDataFolder() + File.separator + "savedTrainModules");
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
            case "LightAPI":
                log(Level.INFO, "LightAPI detected, the Light attachment is now available");
                if (enabled)
                    AttachmentTypeRegistry.instance().register(CartAttachmentLight.TYPE);
                else
                    AttachmentTypeRegistry.instance().unregister(CartAttachmentLight.TYPE);
                break;
        }
    }

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    public void enable() {
        plugin = this;

        // Do this first
        Conversion.registerConverters(MinecartMemberStore.class);

        // And this
        CartAttachment.registerDefaultAttachments();
        {
            // BEFORE we load configurations!
            Plugin lightAPI = Bukkit.getPluginManager().getPlugin("LightAPI");
            if (lightAPI != null && lightAPI.isEnabled()) {
                updateDependency(lightAPI, lightAPI.getName(), true);
            }
        }

        // Routinely saves TrainCarts changed state information to disk (autosave=true)
        // Configured by loadConfig() so instantiate it here
        autosaveTask = new AutosaveTask(this);

        //Load configuration
        loadConfig();

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : MaterialsByName.getAllMaterials()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, TCConfig.maxMinecartStackSize);
                }
            }
        }

        //Initialize entity glow color provider
        this.glowColorTeamProvider = new GlowColorTeamProvider(this);
        this.glowColorTeamProvider.enable();

        //Initialize seat attachment map
        this.seatAttachmentMap = new SeatAttachmentMap();
        this.register((PacketListener) this.seatAttachmentMap, SeatAttachmentMap.LISTENED_TYPES);

        //Load attachment models
        attachmentModels = new AttachmentModelStore(getDataFolder() + File.separator + "models.yml");
        attachmentModels.load();

        //Setup Economy (Vault)
        setupEconomy();

        //init statements
        Statement.init();

        //Init signs
        SignAction.init();

        //Load properties
        TrainProperties.load();

        //Load tickets
        TicketStore.load();

        //Load saved trains
        loadSavedTrains();

        //Load groups
        OfflineGroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

        //Convert Minecarts
        MinecartMemberStore.convertAllAutomatically();

        // Start the path finding task
        this.pathProvider = new PathProvider(this);
        this.pathProvider.enable(getDataFolder() + File.separator + "destinations.dat");

        //Load arrival times
        ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");

        //Load detector regions
        DetectorRegion.init(getDataFolder() + File.separator + "detectorregions.dat");

        //Load detector sign locations
        SignActionDetector.INSTANCE.init(getDataFolder() + File.separator + "detectorsigns.dat");

        //Load spawner signs
        spawnSignManager = new SpawnSignManager(this);
        spawnSignManager.load(getDataFolder() + File.separator + "spawnsigns.dat");
        spawnSignManager.init();

        //Restore carts where possible
        TrainCarts.plugin.log(Level.INFO, "Restoring trains and loading nearby chunks...");
        OfflineGroupManager.refresh();

        //Activate all detector regions with trains that are on it
        DetectorRegion.detectAllMinecarts();

        // Hackish fix the chunk persistence failing
        fixGroupTickTask = new TrainUpdateTask(this).start(1, 1);

        // Cleans up unused cached rail types over time to avoid memory leaks
        cacheCleanupTask = new CacheCleanupTask(this).start(1, 1);

        // Refreshes mutex signs with trains on it to release state again
        mutexZoneUpdateTask = new MutexZoneUpdateTask(this).start(1, 1);

        //Properly dispose of partly-referenced carts
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                for (World world : WorldUtil.getWorlds()) {
                    OfflineGroupManager.removeBuggedMinecarts(world);
                }
            }
        });

        // Register listeners and commands
        this.register(packetListener = new TCPacketListener(), TCPacketListener.LISTENED_TYPES);
        this.register(interactionPacketListener = new TCInteractionPacketListener(), TCInteractionPacketListener.TYPES);
        this.register(TCListener.class);
        this.register(this.redstoneTracker = new RedstoneTracker(this));
        this.register("train", "cart");

        // Destroy all trains after initializing if specified
        if (TCConfig.destroyAllOnShutdown) {
            getLogger().info("[DestroyOnShutdown] Destroyed " + OfflineGroupManager.destroyAll() + " trains or minecarts");
        }
    }

    /**
     * Saves all traincarts related information to file
     */
    public void save(boolean autosave) {
        //Save properties
        TrainProperties.save(autosave);

        //Save saved trains
        this.savedTrainsStore.save(autosave);

        //Save Train tickets
        TicketStore.save(autosave);

        //Save destinations
        pathProvider.save(autosave, getDataFolder() + File.separator + "destinations.dat");

        //Save arrival times
        if (!autosave) {
            ArrivalSigns.save(getDataFolder() + File.separator + "arrivaltimes.txt");
        }

        //Save spawn sign locations
        spawnSignManager.save(autosave, getDataFolder() + File.separator + "spawnsigns.dat");

        //Save detector sign locations
        SignActionDetector.INSTANCE.save(autosave, getDataFolder() + File.separator + "detectorsigns.dat");

        //Save detector regions
        DetectorRegion.save(autosave, getDataFolder() + File.separator + "detectorregions.dat");

        //Save attachment models
        attachmentModels.save(autosave);

        // Save train information
        if (!autosave) {
            OfflineGroupManager.save(getDataFolder() + File.separator + "trains.groupdata");
        }
    }

    public void disable() {
        //Destroy all trains after initializing if specified
        if (TCConfig.destroyAllOnShutdown) {
            getLogger().info("[DestroyOnShutdown] Destroyed " + OfflineGroupManager.destroyAll() + " trains or minecarts");
        }

        //Unregister listeners
        this.unregister(packetListener);
        this.unregister(interactionPacketListener);
        packetListener = null;
        interactionPacketListener = null;

        //Stop tasks
        Task.stop(signtask);
        Task.stop(fixGroupTickTask);
        Task.stop(autosaveTask);
        Task.stop(cacheCleanupTask);
        Task.stop(mutexZoneUpdateTask);

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : MaterialsByName.getAllMaterials()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    Util.setItemMaxSize(material, 1);
                }
            }
        }

        //this corrects minecart positions before saving
        MinecartGroupStore.doPostMoveLogic();

        //go by all minecart member entities on the server and eject those that have players inside
        //this makes sure that the default save function doesn't overwrite it
        for (World world : WorldUtil.getWorlds()) {
            for (Chunk chunk : WorldUtil.getChunks(world)) {
                for (org.bukkit.entity.Entity entity : ChunkUtil.getEntities(chunk)) {
                    if (entity instanceof Minecart) {
                        CommonEntity<?> commonEntity = CommonEntity.get(entity);
                        if (commonEntity.hasPlayerPassenger()) {
                            MinecartMember<?> member = commonEntity.getController(MinecartMember.class);
                            if (member != null && !member.isPlayerTakable()) {
                                commonEntity.eject();
                            }
                        }
                    }
                }
            }
        }

        //store all forced chunk instances of all groups currently in use
        //this delays unloading the chunks until after all trains have unloaded / controllers disabled
        List<ForcedChunk> allForcedChunks = new ArrayList<ForcedChunk>();
        try {
            //unload all groups, add their forced chunks to it for safekeeping first
            for (MinecartGroup mg : MinecartGroup.getGroups().cloneAsIterable()) {
                mg.getChunkArea().getForcedChunks(allForcedChunks);
                mg.unload();
            }

            //double-check all entities on all worlds, to see no unlinked groups exist. Unload those too.
            //replace all MinecartMember controllers with default controllers too
            for (World world : WorldUtil.getWorlds()) {
                for (Chunk chunk : WorldUtil.getChunks(world)) {
                    for (org.bukkit.entity.Entity entity : ChunkUtil.getEntities(chunk)) {
                        // Double-check for groups
                        MinecartGroup group = MinecartGroup.get(entity);
                        if (group != null) {
                            group.unload();
                        }

                        // Replace MinecartMember with default controller on shutdown
                        // This prevents Traincarts classes staying around after disabling
                        if (entity instanceof Minecart) {
                            CommonEntity<?> commonEntity = CommonEntity.get(entity);
                            if (commonEntity.getController(MinecartMember.class) != null) {
                                commonEntity.setController(new DefaultEntityController());
                            }
                        }
                    }
                }
            }
        } finally {
            // This will potentially unload the chunks
            for (ForcedChunk forcedChunk : allForcedChunks) {
                forcedChunk.close();
            }
            allForcedChunks.clear();
        }

        //save all data to disk (autosave=false)
        save(false);

        //Disable spawn manager
        spawnSignManager.deinit();

        // Disable path provider before de-initializing path nodes / sign actions
        if (this.pathProvider != null) {
            this.pathProvider.disable();
            this.pathProvider = null;
        }

        // Deinit classes
        ArrivalSigns.deinit();
        SignActionSpawn.deinit();
        Statement.deinit();
        SignAction.deinit();
        ItemAnimation.deinit();
        OfflineGroupManager.deinit();

        RailPieceCache.reset();
        RailSignCache.reset();
        RailMemberCache.reset();

        this.glowColorTeamProvider.disable();
        this.glowColorTeamProvider = null;

        this.redstoneTracker.disable();
        this.redstoneTracker = null;

        AttachmentTypeRegistry.instance().unregisterAll();
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

    private static class CacheCleanupTask extends Task {

        public CacheCleanupTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            RailPieceCache.update();
            RailSignCache.cleanup();
        }
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
            // Refresh whether or not trains are allowed to tick
            if (++ctr >= TCConfig.tickUpdateDivider) {
                ctr = 0;
                TCConfig.tickUpdateNow++;
            }
            if (TCConfig.tickUpdateNow > 0) {
                TCConfig.tickUpdateNow--;
                TCConfig.tickUpdateEnabled = true;
            } else {
                TCConfig.tickUpdateEnabled = false;
            }

            // For all Minecart that were not ticked, tick them ourselves
            MinecartGroupStore.doFixedTick();
        }
    }

    private static class MutexZoneUpdateTask extends Task {

        public MutexZoneUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            MutexZoneCache.refreshAll();
        }
    }
}
