package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.controller.DefaultEntityController;
import com.bergerkiller.bukkit.common.conversion.type.HandleConversion;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.internal.legacy.MaterialsByName;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.metrics.Metrics;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.softdependency.SoftServiceDependency;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.neznamytabnametaghider.TabNameTagHider;
import com.bergerkiller.bukkit.neznamytabnametaghider.TabNameTagHiderDependency;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.common.softdependency.SoftDependency;
import com.bergerkiller.bukkit.sl.API.events.SignVariablesDetectEvent;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModelStore;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentLight;
import com.bergerkiller.bukkit.tc.attachments.control.GlowColorTeamProvider;
import com.bergerkiller.bukkit.tc.attachments.control.SeatAttachmentMap;
import com.bergerkiller.bukkit.tc.attachments.control.TeamProvider;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.schematic.WorldEditSchematicLoader;
import com.bergerkiller.bukkit.tc.attachments.ui.models.ResourcePackModelListing;
import com.bergerkiller.bukkit.tc.chest.TrainChestListener;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerRegistry;
import com.bergerkiller.bukkit.tc.commands.selector.TCSelectorHandlerRegistry;
import com.bergerkiller.bukkit.tc.controller.*;
import com.bergerkiller.bukkit.tc.controller.global.EffectLoopPlayerController;
import com.bergerkiller.bukkit.tc.controller.global.SignController;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayerStore;
import com.bergerkiller.bukkit.tc.controller.global.TrainUpdateController;
import com.bergerkiller.bukkit.tc.controller.player.TrainCartsAttachmentViewerMap;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerClientSynchronizer;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.locator.TrainLocator;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.pathfinding.RouteManager;
import com.bergerkiller.bukkit.tc.portals.TCPortalManager;
import com.bergerkiller.bukkit.tc.portals.plugins.MultiversePortalsProvider;
import com.bergerkiller.bukkit.tc.portals.plugins.MyWorldsPortalsProvider;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.registry.TCPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.category.PaperPlayerViewDistanceProperty;
import com.bergerkiller.bukkit.tc.properties.standard.category.PaperTrackingRangeProperty;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.TrackedSignLookup;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSignManager;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroup;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bergerkiller.bukkit.tc.utils.BlockPhysicsEventDataAccessor;
import com.bergerkiller.generated.net.minecraft.world.item.ItemHandle;
import com.bergerkiller.mountiplex.conversion.Conversion;

import me.m56738.smoothcoasters.api.SmoothCoastersAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class TrainCarts extends PluginBase {
    public static TrainCarts plugin;
    private Task autosaveTask;
    private Task cacheCleanupTask;
    private Task mutexZoneUpdateTask;
    private final List<ChunkPreloadTask> chunkPreloadTasks = new ArrayList<>();
    private TCPropertyRegistry propertyRegistry;
    private TCListener listener;
    private TCPacketListener packetListener;
    private TCSuppressSeatTeleportPacketListener suppressSeatTeleportPacketListener;
    private TCInteractionPacketListener interactionPacketListener;
    private FileConfiguration config;
    private final SpawnSignManager spawnSignManager = new SpawnSignManager(this);
    private SavedAttachmentModelStore savedAttachmentModels;
    private SavedTrainPropertiesStore savedTrainsStore;
    private SeatAttachmentMap seatAttachmentMap;
    private TeamProvider teamProvider;
    private PathProvider pathProvider;
    private RouteManager routeManager;
    private TrainLocator trainLocator;
    private TrainUpdateController trainUpdateController = new TrainUpdateController(this);
    private final TCSelectorHandlerRegistry selectorHandlerRegistry = new TCSelectorHandlerRegistry(this);
    private final OfflineGroupManager offlineGroupManager = new OfflineGroupManager(this);
    private final OfflineSignStore offlineSignStore = new OfflineSignStore(this);
    private final ActionRegistry actionRegistry = new ActionRegistry(this);
    private final TrackedSignLookup trackedSignLookup = new TrackedSignLookup(this);
    private final SignController signController = new SignController(this);
    private final TrainCartsAttachmentViewerMap attachmentViewerMap = new TrainCartsAttachmentViewerMap(this);
    private ResourcePackModelListing modelListing = new ResourcePackModelListing(); // Uninitialized
    private final WorldEditSchematicLoader worldEditSchematicLoader = new WorldEditSchematicLoader(this);
    private final TrainCartsPlayerStore playerStore = new TrainCartsPlayerStore(this);
    private final EffectLoopPlayerController effectLoopPlayerController = new EffectLoopPlayerController(this);
    private final PlayerClientSynchronizer.Provider playerClientSynchronizerProvider = PlayerClientSynchronizer.Provider.create(this);
    private final PlayerPacketListener.Provider playerPacketListenerProvider = PlayerPacketListener.Provider.create(this);
    private SmoothCoastersAPI smoothCoastersAPI;
    private Commands commands;

    /* ===================================
     * Optional dependencies of TrainCarts
     * =================================== */

    private final SoftDependency<TabNameTagHider> tabNameTagHider = new TabNameTagHiderDependency(this) {
        @Override
        protected void onEnable() {
            getLogger().info("Neznamy TAB plugin detected! Seats with nametag hidden will also hide TAB nametags.");
        }
    };
    private final SoftDependency<Plugin> signLink = new SoftDependency<Plugin>(this, "SignLink") {
        private Task signtask;
        private Listener variableSuppressionListener = null;

        @Override
        protected Plugin initialize(Plugin plugin) {
            return plugin;
        }

        @Override
        protected void onEnable() {
            log(Level.INFO, "SignLink detected, support for arrival signs added!");
            Task.stop(signtask);
            signtask = new Task(TrainCarts.this) {
                public void run() {
                    ArrivalSigns.updateAll();
                }
            };
            signtask.start(0, 10);

            // If SignLink supports it, add a variable detection event handler that suppresses detection
            // inside TrainCarts ([train]/[cart]) signs. This is primarily to make the spawn sign % chance
            // logic behave properly.
            boolean hasEvent = false;
            try {
                Class.forName("com.bergerkiller.bukkit.sl.API.events.SignVariablesDetectEvent");
                hasEvent = true;
            } catch (Throwable t) {}
            if (hasEvent) {
                variableSuppressionListener = createVariableSuppressionListener();
                TrainCarts.this.register(variableSuppressionListener);
            }
        }

        private Listener createVariableSuppressionListener() {
            return new Listener() {
                @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
                public void onSignVariablesDetected(SignVariablesDetectEvent event) {
                    if (SignActionHeader.parse(event.getLine(0)).isValid()) {
                        event.setCancelled(true);
                    }
                }
            };
        }

        @Override
        protected void onDisable() {
            Task.stop(signtask);
            signtask = null;
            if (variableSuppressionListener != null) {
                CommonUtil.unregisterListener(variableSuppressionListener);
                variableSuppressionListener = null;
            }
        }
    };
    private final SoftDependency<Plugin> lightAPI = SoftDependency.build(this, "LightAPI")
            .withInitializer(p -> p)
            .whenEnable(p -> {
                log(Level.INFO, "LightAPI detected, the Light attachment is now available");
                AttachmentTypeRegistry.instance().register(CartAttachmentLight.TYPE);
            })
            .whenDisable(p -> AttachmentTypeRegistry.instance().unregister(CartAttachmentLight.TYPE))
            .create();
    private final SoftDependency<MyWorldsPortalsProvider> myWorldsPortalProvider = SoftDependency.build(this, "My_Worlds")
            .withInitializer(p -> new MyWorldsPortalsProvider(this, p))
            .whenEnable(s -> TCPortalManager.addPortalSupport(s.name(), s.get()))
            .whenDisable(s -> TCPortalManager.removePortalSupport(s.name()))
            .create();
    private final SoftDependency<MultiversePortalsProvider> multiversePortalProvider = SoftDependency.build(this, "Multiverse-Portals")
            .withInitializer(p -> new MultiversePortalsProvider(TrainCarts.this, p))
            .whenEnable(s -> TCPortalManager.addPortalSupport(s.name(), s.get()))
            .whenDisable(s -> TCPortalManager.removePortalSupport(s.name()))
            .create();
    private final SoftServiceDependency<Economy> vaultEconomy = new SoftServiceDependency<Economy>(this, "net.milkbowl.vault.economy.Economy") {
        @Override
        protected Economy initialize(Object service) throws Error, Exception {
            return Economy.class.cast(service);
        }

        @Override
        protected void onEnable() {
            log(Level.INFO, "Support for Economy plugin '" + getServicePlugin().getName() + "' enabled");
        }
    };

    /**
     * Gets the property registry which tracks all train and cart properties
     * that have been registered.
     * 
     * @return property registry
     */
    public IPropertyRegistry getPropertyRegistry() {
        return propertyRegistry;
    }

    /**
     * Gets a helper class for assigning (fake) entities to teams to change their glowing effect
     * color.
     * 
     * @return glow color team provider
     */
    public GlowColorTeamProvider getGlowColorTeamProvider() {
        return this.teamProvider.glowColors();
    }

    /**
     * Gets a helper class for assigning (fake) entities to teams to change their glowing effect
     * color or their collision behavior.
     *
     * @return team provider
     */
    public TeamProvider getTeamProvider() {
        return this.teamProvider;
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
    public SavedAttachmentModelStore getSavedAttachmentModels() {
        return this.savedAttachmentModels;
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
     * Gets the route manager, which stores the routes a train can go when set by name.
     * Each route consists of a list of destinations.
     * 
     * @return route manager
     */
    public RouteManager getRouteManager() {
        return this.routeManager;
    }

    /**
     * Gets the selector handler registry, which is used to replace selectors
     * in commands with the handler-provided replacements.<br>
     * <br>
     * For example, this replaces <code>@ptrain[train=train12]</code>
     * with the names of players in the train.
     *
     * @return selector handler registry
     */
    public SelectorHandlerRegistry getSelectorHandlerRegistry() {
        return this.selectorHandlerRegistry;
    }

    /**
     * Gets the train locator manager, which is used to display a line from
     * players to minecarts to locate them.
     *
     * @return train locator manager
     */
    public TrainLocator getTrainLocator() {
        return this.trainLocator;
    }

    /**
     * Gets the main controller responsible for updating trains, and
     * configuring how trains are updated.
     *
     * @return train update controller
     */
    public TrainUpdateController getTrainUpdateController() {
        return this.trainUpdateController;
    }

    /**
     * Gets the information about all train groups and member that have unloaded
     *
     * @return offline group manager
     */
    public OfflineGroupManager getOfflineGroups() {
        return this.offlineGroupManager;
    }

    /**
     * Gets the offline sign store, where metadata of signs can be stored
     * persistently
     *
     * @return offline sign metadata store
     */
    public OfflineSignStore getOfflineSigns() {
        return this.offlineSignStore;
    }

    /**
     * Gets the tracked sign lookup register. This is the bridge between a serialized
     * sign unique key and the live tracked sign it represents. Plugins can register
     * their custom sign implementations here, making metadata mapped ot them
     * persistent.
     *
     * @return Tracked Sign lookup
     */
    public TrackedSignLookup getTrackedSignLookup() {
        return trackedSignLookup;
    }

    /**
     * Gets the action registry. This registry contains all the {@link Action} types
     * that are persistent. Actions that survive a train reload are registered here.
     *
     * @return Action Registry
     */
    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    /**
     * Gets the sign controller, which tracks where loaded signs exist
     * in the world.
     *
     * @return sign controller
     */
    public SignController getSignController() {
        return this.signController;
    }

    /**
     * Gets the attachment viewer map, which stores special queues per player to send packets
     * asynchronously among other utilities. Primarily for internal use only.
     *
     * @return packet queue map
     */
    public TrainCartsAttachmentViewerMap getAttachmentViewers() {
        return this.attachmentViewerMap;
    }

    /**
     * Gets the AttachmentViewer of a Player, which can be used with the Attachments API.
     * Returned value is cached and reused for online players, cleaned up when they go
     * offline.
     *
     * @param player Player
     * @return AttachmentViewer
     */
    public AttachmentViewer getAttachmentViewer(Player player) {
        return this.attachmentViewerMap.getViewer(player);
    }

    /**
     * Gets a TAB plugin custom nametag hider. This is used when the TAB plugin is installed
     * to hide the armorstand-supported custom name while players are in a seat marked no-nametag.
     *
     * @param player
     * @return Tab name tag hider if supported, null if the plugin isn't active or name
     *         can't be hidden
     */
    public TabNameTagHider.TabPlayerNameTagHider getTabNameHider(Player player) {
        return tabNameTagHider.get().get(player);
    }

    /**
     * Gets the resource pack model list. This provides information about the current resource
     * pack loaded by TrainCarts, and has methods to display this to a Player.
     *
     * @return resource pack model listing
     */
    public ResourcePackModelListing getModelListing() {
        ResourcePackModelListing listing = this.modelListing;
        if (listing.loadedResourcePack() != TCConfig.resourcePack) {
            listing = new ResourcePackModelListing(this);
            listing.load(TCConfig.resourcePack);
            this.modelListing = listing;
        }
        return listing;
    }

    /**
     * Gets the WorldEdit schematic loader. This is a service that loads WorldEdit schematics
     * asynchronously, letting readers read the block data at their own leisure. Is not always
     * enabled.
     *
     * @return WorldEdit schematic loader
     */
    public WorldEditSchematicLoader getWorldEditSchematicLoader() {
        return worldEditSchematicLoader;
    }

    /**
     * Gets the TrainCarts player information store. This store tracks information about
     * players, such as the cart they are currently editing.
     *
     * @return TrainCarts player metadata/information store
     */
    public TrainCartsPlayerStore getPlayerStore() {
        return playerStore;
    }

    /**
     * Gets or creates the TrainCarts player information tracked for the Player with
     * the specified UUID
     *
     * @param playerUUID UUID of the Player
     * @return TrainCarts Player information
     */
    public TrainCartsPlayer getPlayer(UUID playerUUID) {
        return playerStore.get(playerUUID);
    }

    /**
     * Gets or creates the TrainCarts player information tracked for the Player specified.
     *
     * @param player Player
     * @return TrainCarts Player information
     */
    public TrainCartsPlayer getPlayer(Player player) {
        return playerStore.get(player);
    }

    /**
     * Gets the {@link EffectLoopPlayerController} that can play effect loops til completion
     *
     * @return EffectLoopPlayer
     */
    public EffectLoopPlayerController getEffectLoopPlayerController() {
        return effectLoopPlayerController;
    }

    /**
     * Creates a new EffectLoop player instance, with the number of concurrently playing
     * effect loops limited by TrainCarts configuration.
     *
     * @return EffectLoop Player
     * @see #createEffectLoopPlayer(int)
     */
    public EffectLoop.Player createEffectLoopPlayer() {
        return effectLoopPlayerController.createPlayer();
    }

    /**
     * Creates a new EffectLoop player instance with a certain limit of concurrently playing
     * effect loops.
     *
     * @param limit Maximum number of concurrently playing effect loops. The TrainCarts configured
     *              limit is also in effect.
     * @return EffectLoop Player
     * @see #createEffectLoopPlayer()
     */
    public EffectLoop.Player createEffectLoopPlayer(int limit) {
        return effectLoopPlayerController.createPlayer(limit);
    }

    /**
     * Gets the main provider for the PlayerClientSynchronizer API. This API is used to send
     * packets to players and fire callbacks when the client acknowledges those packets.
     *
     * @return PlayerClientSynchronizer Provider
     */
    public PlayerClientSynchronizer.Provider getPlayerClientSynchronizerProvider() {
        return playerClientSynchronizerProvider;
    }

    /**
     * Gets the PlayerPacketListener Provider, which is used to register temporary packet listeners
     * for specific players only.
     *
     * @return PlayerPacketListener Provider
     */
    public PlayerPacketListener.Provider getPlayerPacketListenerProvider() {
        return playerPacketListenerProvider;
    }

    /**
     * Gets the Economy manager
     *
     * @return
     */
    public Economy getEconomy() {
        return vaultEconomy.get();
    }

    public SmoothCoastersAPI getSmoothCoastersAPI() {
        return smoothCoastersAPI;
    }

    public boolean isSignLinkEnabled() {
        return signLink.isEnabled();
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
        Economy econ = TrainCarts.plugin.vaultEconomy.get();
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
        if (TrainCarts.plugin.isSignLinkEnabled()) {
            //TODO: SignLink 1.16.5-v1 supports far more functionality, such as escaping using %% and
            //      filtering out variable names with spaces in them. This code doesn't do that.
            //      Improvements could definitely be made.
            int startindex, endindex = 0;
            while ((startindex = text.indexOf('%', endindex)) != -1 && (endindex = text.indexOf('%', startindex + 1)) != -1) {
                String varname = text.substring(startindex + 1, endindex);
                String value = varname.isEmpty() ? "%" : Variables.get(varname).get(player.getName());
                text = text.substring(0, startindex) + value + text.substring(endindex + 1);

                // Search from beyond this point to avoid infinite loops if value contains %-characters
                endindex = startindex + value.length();
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
        if(!TCConfig.enabledWorlds.isEmpty())
            return !TCConfig.enabledWorlds.contains(world);

        return TCConfig.disabledWorlds.contains(world);
    }

    public static boolean isWorldDisabled(String worldname) {
        if(!TCConfig.enabledWorlds.isEmpty())
            return !TCConfig.enabledWorlds.contains(worldname);

        return TCConfig.disabledWorlds.contains(worldname);
    }

    public boolean handlePlayerVehicleChange(Player player, Entity newVehicle) {
        try {
            MinecartMember<?> newMinecart = MinecartMemberStore.getFromEntity(newVehicle);

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
            handle(t);
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
        rval = rval.clone();
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

    protected void preloadChunks(Map<OfflineGroup, List<ForcedChunk>> chunks) {
        // Load all chunks right now. This does not yet load the entities inside.
        chunks.values().stream().flatMap(list -> list.stream()).forEachOrdered(chunk -> {
            try {
                // Ensure that for all chunks to be loaded, the by-world lookup cache is prepared
                // This initializes stuff like detector regions earlier on, avoiding trouble
                RailLookup.forWorld(chunk.getWorld());

                // Load the chunk finally, which on older MC versions might trigger trains to initialize
                chunk.getChunk();
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to load chunk " + chunk.getWorld().getName()
                        + " [" + chunk.getX() + ", " + chunk.getZ() + "]", t);
            }
        });

        // Dispatch to the preloader which will keep these loaded for a little while,
        // until the entities in the chunks have also been loaded soon after.
        ChunkPreloadTask preloadTask = new ChunkPreloadTask(this, chunks);
        preloadTask.startPreloading();
        this.chunkPreloadTasks.add(preloadTask);
    }

    private void loadConfig(boolean isEnabling) {
        config = new FileConfiguration(this);
        config.load();
        TCConfig.load(this, config);
        config.save();

        // Refresh
        this.autosaveTask.stop().start(TCConfig.autoSaveInterval, TCConfig.autoSaveInterval);

        // Load this one right away
        this.modelListing = new ResourcePackModelListing(this);
        this.modelListing.load(TCConfig.resourcePack);

        if (!isEnabling) {
            this.signController.updateEnabled();
        }
    }

    public void loadConfig() {
        loadConfig(false);
    }

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    @Override
    public void onLoad() {
        // These things need to be initialized early on so that third-party plugins can register
        // their own types. They should do this registration in onLoad rather than onEnable so
        // that Traincarts can pick them up before initializing all the trains.

        // Commands/PropertyRegistry are required for registering properties picked up by trains
        // Actual registration of property commands is done during enable()
        commands = new Commands();
        propertyRegistry = new TCPropertyRegistry(this, commands.getHandler());
        propertyRegistry.registerAll(StandardProperties.class);

        // Paper player view distance
        if (Util.hasPaperViewDistanceSupport()) {
            try {
                propertyRegistry.register(PaperPlayerViewDistanceProperty.INSTANCE);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to register paper player view distance property", t);
            }
        }

        // Paper custom entity tracking range
        if (Util.hasPaperCustomTrackingRangeSupport()) {
            try {
                propertyRegistry.register(PaperTrackingRangeProperty.INSTANCE);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to register paper tracking range property", t);
            }
        }

        // Register TrainCarts default attachment types
        CartAttachment.registerDefaultAttachments();

        // Register TrainCarts default rail types
        RailType.values();

        // Load detector regions from file. These may be required for signs (such as detector signs)
        DetectorRegion.init(this);

        // Init signs
        SignAction.init();

        // Init offline sign metadata store from disk
        // Makes metadata available as early as possible
        this.offlineSignStore.load();

        // Initialize various offline-sign based signs that require info during enabling
        this.enableOfflineSignHandlers();

        //Initialize early so that others can register handlers
        //Loading is done in enable()
        this.pathProvider = new PathProvider(this);

        // We allow other plugins to register stuff during onLoad() as well
        plugin = this;
    }

    @Override
    public void enable() {
        // For good measure
        plugin = this;

        // Before doing anything, initialize the controller logic (slow!)
        CommonEntity.forceControllerInitialization();

        // Do this first
        Conversion.registerConverters(MinecartMemberStore.class);

        // Initialize commands (Cloud command framework)
        // Must make sure no TrainCarts state is instantly accessed while initializing
        commands.enable(this);

        // Core properties need to be there before defaults/cart/train properties are loaded
        // Will register commands that properties may define using annotations
        propertyRegistry.enable();

        // Selector registry, do this early in case a command block triggers during enabling
        selectorHandlerRegistry.enable();

        // Routinely saves TrainCarts changed state information to disk (autosave=true)
        // Configured by loadConfig() so instantiate it here
        autosaveTask = new AutosaveTask(this);

        // Start playing effect loops. Not that internally it only starts playing after a 1 tick delay.
        // This is so that asynchronous loops don't play while the server is still starting up...
        effectLoopPlayerController.enable();

        // Load configuration. Must occur before dependencies as some dependencies might be
        // disabled using TC's configuration.
        loadConfig(true);

        // Ensure dependencies are loaded in at this point, they must be available when
        // trains load in. There might be some dependencies we want to skip, in that case,
        // add detect() calls per field instead of using the helper detectAll().
        SoftDependency.detectAll(this);

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : MaterialsByName.getAllMaterials()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    ItemHandle.createHandle(HandleConversion.toItemHandle(material))
                            .setMaxStackSize(TCConfig.maxMinecartStackSize);
                }
            }
        }

        //WorldEdit schematic loader
        this.worldEditSchematicLoader.enable();

        //Automatically tracks the signs that are loaded
        this.signController.enable();

        //Automatically saves sign metadata to disk in the background
        //For worlds not already loaded, loads metadata where this is a condition
        this.offlineSignStore.enable();

        //Initialize entity glow color provider
        this.teamProvider = new TeamProvider(this);
        this.teamProvider.enable();

        //Initialize train locator manager
        this.trainLocator = new TrainLocator();
        this.trainLocator.enable(this);

        //Initialize route manager
        this.routeManager = new RouteManager(getDataFolder() + File.separator + "routes.yml");
        this.routeManager.load();

        //Initialize SmoothCoastersAPI
        this.smoothCoastersAPI = new SmoothCoastersAPI(this);

        //Initialize seat attachment map
        this.seatAttachmentMap = new SeatAttachmentMap();
        this.register((PacketListener) this.seatAttachmentMap, SeatAttachmentMap.LISTENED_TYPES);

        //init statements
        Statement.init();

        // Start the path finding task
        this.pathProvider.enable(getDataFolder() + File.separator + "destinations.dat");

        // Initialize train updater task
        // This initially uses a single-threaded updater because of a paper synchronization bug
        this.trainUpdateController.preEnable();

        //Load properties
        TrainProperties.load(this);

        //Load tickets
        TicketStore.load(this);

        //Load attachment models - used by MODEL attachments
        this.savedAttachmentModels = SavedAttachmentModelStore.create(this,
                "SavedModels.yml",
                "savedModelModules");

        //Load saved trains
        this.savedTrainsStore = SavedTrainPropertiesStore.create(this,
                "SavedTrainProperties.yml",
                "savedTrainModules");

        //Load groups
        offlineGroupManager.load();

        //Convert Minecarts
        MinecartMemberStore.convertAllAutomatically(this);

        //Load arrival times
        ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");

        // Cleans up unused cached rail types over time to avoid memory leaks
        cacheCleanupTask = new CacheCleanupTask(this).start(1, 1);

        // Clean up the cache right now, so that all rails are recalculated
        RailLookup.forceRecalculation();

        // Refreshes mutex signs with trains on it to release state again
        mutexZoneUpdateTask = new MutexZoneUpdateTask(this).start(1, 1);

        // Starts a task to track the auto-spawn timers
        this.spawnSignManager.enable();

        //Properly dispose of partly-referenced carts
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                for (World world : WorldUtil.getWorlds()) {
                    OfflineGroupManager.removeBuggedMinecarts(world);
                }
            }
        });

        // Register listeners
        this.register(packetListener = new TCPacketListener(this), TCPacketListener.LISTENED_TYPES);
        this.register(interactionPacketListener = new TCInteractionPacketListener(packetListener), TCInteractionPacketListener.TYPES);
        this.register(listener = new TCListener(this));
        this.register(new TCSeatChangeListener());
        this.register(new TrainChestListener(this));

        // Temporary per-player listener system
        this.playerClientSynchronizerProvider.enable();
        this.playerPacketListenerProvider.enable();

        // Only registered when needed...
        if (TCSuppressSeatTeleportPacketListener.SUPPRESS_POST_ENTER_PLAYER_POSITION_PACKET) {
            suppressSeatTeleportPacketListener = new TCSuppressSeatTeleportPacketListener(this);
            this.register((PacketListener) suppressSeatTeleportPacketListener, TCSuppressSeatTeleportPacketListener.LISTENED_TYPES);
            this.register((Listener) suppressSeatTeleportPacketListener);
        }

        //Restore carts where possible
        log(Level.INFO, "Restoring trains and loading nearby chunks...");
        {
            // Check chunks that are already loaded first
            offlineGroupManager.refresh();

            // Get all chunks to be kept loaded and load them right now
            preloadChunks(offlineGroupManager.getForceLoadedChunks());
        }

        //Activate all detector regions with trains that are on it
        DetectorRegion.detectAllMinecarts();

        // Paper player view distance logic handling
        if (Util.hasPaperViewDistanceSupport()) {
            try {
                PaperPlayerViewDistanceProperty.INSTANCE.enable(this);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to enable paper player view distance property", t);
                this.propertyRegistry.unregister(PaperPlayerViewDistanceProperty.INSTANCE);
            }
        }

        // Paper tracking range logic handling
        if (Util.hasPaperCustomTrackingRangeSupport()) {
            try {
                PaperTrackingRangeProperty.INSTANCE.enable(this);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to enable paper tracking range property", t);
                this.propertyRegistry.unregister(PaperTrackingRangeProperty.INSTANCE);
            }
        }

        // Destroy all trains after initializing if specified
        if (TCConfig.destroyAllOnShutdown) {
            offlineGroupManager.destroyAllAsync(false).thenAccept(count -> {
                getLogger().info("[DestroyOnShutdown] Destroyed " + count + " trains");
            });
        }

        // Register custom metrics
        if (hasMetrics()) {
            Metrics metrics = getMetrics();

            // smoothCoastersInstalled: Number of players with/without SmoothCoasters and the mod version
            metrics.addCustomChart(new Metrics.DrilldownPie("smoothCoastersInstalled", () -> {
                Map<String, Integer> versions = new HashMap<>();
                int disabled = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (smoothCoastersAPI.isEnabled(player)) {
                        String version = smoothCoastersAPI.getModVersion(player);
                        if (version == null) {
                            version = "unknown";
                        }
                        versions.merge(version, 1, Integer::sum);
                    } else {
                        disabled++;
                    }
                }

                Map<String, Map<String, Integer>> categories = new HashMap<>();
                categories.put("installed", versions);
                categories.put("not installed", Collections.singletonMap("not installed", disabled));
                return categories;
            }));
        }

        // Now all is setup right, start a (potential) multithreaded updater
        this.trainUpdateController.postEnable();
    }

    @Override
    public void disable() {
        //Destroy all LOADED trains after initializing if specified
        //We can't destroy unloaded trains - the asynchronous nature makes it impossible
        if (TCConfig.destroyAllOnShutdown) {
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    group.destroy();
                }
                getLogger().info("[DestroyOnShutdown] Destroyed " + groups.size() + " trains");
            }
        }

        // Disable Paper player view distance logic handling
        if (Util.hasPaperViewDistanceSupport()) {
            try {
                PaperPlayerViewDistanceProperty.INSTANCE.disable(this);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to disable paper player view distance property", t);
            }
        }

        // Disable Paper tracking range logic handling
        if (Util.hasPaperCustomTrackingRangeSupport()) {
            try {
                PaperTrackingRangeProperty.INSTANCE.disable(this);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to disable paper tracking range property", t);
            }
        }

        // Close any open dialogs
        try {
            ResourcePackModelListing.closeAllDialogs();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to shut down all open resource pack model dialogs");
        }

        //Unregister listeners
        this.unregister(packetListener);
        this.unregister(interactionPacketListener);
        smoothCoastersAPI.unregister();
        listener = null;
        packetListener = null;
        interactionPacketListener = null;
        smoothCoastersAPI = null;

        //Stop pending tasks for fake player list clearing
        FakePlayerSpawner.runAndClearCleanupTasks();

        //Stop tasks
        Task.stop(autosaveTask);
        Task.stop(cacheCleanupTask);
        Task.stop(mutexZoneUpdateTask);

        // Temporary per-player listener system
        this.playerClientSynchronizerProvider.disable();
        this.playerPacketListenerProvider.disable();

        //Stop preloading chunks (happens when quickly disabling after enabling)
        for (ChunkPreloadTask preloadTask : this.chunkPreloadTasks) {
            preloadTask.abortPreloading();
        }

        //stop updating
        trainUpdateController.disable();

        //update max item stack
        if (TCConfig.maxMinecartStackSize != 1) {
            for (Material material : MaterialsByName.getAllMaterials()) {
                if (MaterialUtil.ISMINECART.get(material)) {
                    ItemHandle.createHandle(HandleConversion.toItemHandle(material))
                            .setMaxStackSize(1);
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
                        // Ignore dead/removed entities
                        if (entity.isDead()) {
                            continue;
                        }

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
        save(SaveMode.SHUTDOWN);

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
        offlineGroupManager.deinit();
        RailLookup.clear();
        this.signController.disable();

        // Now plugin is mostly shut down, de-register all MinecartMember controllers from the server
        undoAllTCControllers();

        this.effectLoopPlayerController.disable();

        this.teamProvider.disable();
        this.teamProvider = null;

        this.trainLocator.disable();
        this.trainLocator = null;
 
        AttachmentTypeRegistry.instance().unregisterAll();

        // De-register any offline sign handlers
        this.disableOfflineSignHandlers();

        // Save offline sign metadata to disk (if needed) and stop writing in the background
        this.offlineSignStore.disable();

        //WorldEdit schematic loader can now also be shut down permanently
        this.worldEditSchematicLoader.disable();
    }

    @SuppressWarnings({"rawtypes", "deprecation", "unchecked"})
    private void undoAllTCControllers() {
        List<Entity> entities = new ArrayList<Entity>();
        for (World world : WorldUtil.getWorlds()) {
            for (Entity entity : WorldUtil.getEntities(world)) {
                CommonEntity ce = CommonEntity.get(entity);
                if (ce.getController(MinecartMember.class) != null ||
                    ce.getNetworkController() instanceof MinecartMemberNetwork
                ) {
                    entities.add(entity);
                }
            }
        }
        entities.forEach(CommonEntity::clearControllers);
    }

    /**
     * Saves all traincarts related information to file
     *
     * @param saveMode Mode of saving
     */
    public void save(SaveMode saveMode) {
        boolean autosave = saveMode.isAutoSave();

        //Save properties
        TrainProperties.save(autosave);

        //Save model attachments
        this.savedAttachmentModels.save(autosave);

        //Save saved trains
        this.savedTrainsStore.save(autosave);

        //Save Train tickets
        TicketStore.save(this, autosave);

        //Save destinations
        pathProvider.save(autosave, getDataFolder() + File.separator + "destinations.dat");

        //Save arrival times
        if (!autosave) {
            ArrivalSigns.save(getDataFolder() + File.separator + "arrivaltimes.txt");
        }

        //Save detector regions
        DetectorRegion.save(this, autosave);

        //Save routes
        routeManager.save(autosave);

        // Save train information
        offlineGroupManager.save(saveMode);
    }

    private void enableOfflineSignHandlers() {
        MutexZoneCache.init(this);
        this.spawnSignManager.load();
        SignActionDetector.INSTANCE.enable(this);
    }

    private void disableOfflineSignHandlers() {
        MutexZoneCache.deinit(this);
        this.spawnSignManager.disable();
        SignActionDetector.INSTANCE.disable(this);
    }

    public void setBlockDataWithoutBreaking(Block block, BlockData blockData) {
        if (Common.evaluateMCVersion(">=", "1.19")) {
            // Ugh...
            WorldUtil.setBlockDataFast(block, blockData);
            WorldUtil.queueBlockSend(block);
            applyBlockPhysics(block, blockData);
        } else {
            WorldUtil.setBlockData(block, blockData);
        }
    }

    public void applyBlockPhysics(Block block, BlockData blockData) {
        if (Common.evaluateMCVersion(">=", "1.19")) {
            // Broken cancellation on MC 1.19+. Must avoid firing actual block physics.
            listener.onBlockPhysics(BlockPhysicsEventDataAccessor.INSTANCE.createEvent(block, blockData));
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                listener.onBlockPhysics(BlockPhysicsEventDataAccessor.INSTANCE.createEvent(block.getRelative(face), blockData));
            }
        } else {
            // This is OK
            BlockUtil.applyPhysics(block, blockData.getType());
        }
    }

    @Override
    public boolean command(CommandSender sender, String cmd, String[] args) {
        return false; // Note: unused, no commands are registered in plugin.yml
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
            RailLookup.update();
        }
    }

    private static class AutosaveTask extends Task {

        public AutosaveTask(TrainCarts plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            ((TrainCarts) this.getPlugin()).save(SaveMode.AUTOSAVE);
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

    /**
     * Keeps chunks with trains in them loaded for a short time
     * to allow for the asynchronous entity loading to complete.
     */
    private static class ChunkPreloadTask extends Task {
        private final Map<OfflineGroup, List<ForcedChunk>> chunks;
        private final List<FinishedChunks> finished = new ArrayList<>();
        private int deadline;

        public ChunkPreloadTask(JavaPlugin plugin, Map<OfflineGroup, List<ForcedChunk>> chunks) {
            super(plugin);
            this.chunks = chunks;
        }

        /**
         * Starts tracking the groups that have been loaded, and when
         * loading fails.
         */
        public void startPreloading() {
            this.start(5, 5);
            this.deadline = CommonUtil.getServerTicks() + (10 * 60 * 20); // after 10 minutes, fail.
        }

        /**
         * Aborts further loading of chunks and stops this task entirely
         */
        public void abortPreloading() {
            this.stop();
            this.finished.forEach(FinishedChunks::close);
            this.finished.clear();
            this.chunks.values().forEach(chunks -> chunks.forEach(ForcedChunk::close));
            this.chunks.clear();
        }

        @Override
        public void run() {
            // If all done, stop the task
            if (this.finished.isEmpty() && this.chunks.isEmpty()) {
                ((TrainCarts) getPlugin()).chunkPreloadTasks.remove(this);
                this.stop();
                return;
            }

            int ticks = CommonUtil.getServerTicks();

            // Check if deadline is exceeded, and stop trying at that point
            if (!this.chunks.isEmpty() && ticks > this.deadline) {
                List<String> trainNames = this.chunks.keySet().stream().map(g -> g.name).collect(Collectors.toList());
                this.getPlugin().getLogger().log(Level.SEVERE, "Failed to restore " + trainNames.size() + " keep-chunks-loaded trains in time!");
                if (trainNames.size() < 10) {
                    this.getPlugin().getLogger().log(Level.SEVERE, "Trains: " + StringUtil.combineNames(trainNames));
                }
                this.abortPreloading();
                return;
            }

            // Cleanup finished chunks
            for (Iterator<FinishedChunks> iter = this.finished.iterator(); iter.hasNext();) {
                FinishedChunks chunks = iter.next();
                if (ticks > chunks.deadline) {
                    chunks.close();
                    iter.remove();
                }
            }

            // Check if other groups have been loaded
            for (Iterator<Map.Entry<OfflineGroup, List<ForcedChunk>>> iter = this.chunks.entrySet().iterator(); iter.hasNext();) {
                Map.Entry<OfflineGroup, List<ForcedChunk>> entry = iter.next();
                if (entry.getKey().isLoadedAsGroup()) {
                    this.finished.add(new FinishedChunks(entry.getValue()));
                    iter.remove();
                }
            }
        }

        private static class FinishedChunks implements AutoCloseable {
            public final List<ForcedChunk> chunks;
            public final int deadline;

            public FinishedChunks(List<ForcedChunk> chunks) {
                this.chunks = chunks;
                this.deadline = CommonUtil.getServerTicks() + 10; // 10 ticks
            }

            @Override
            public void close() {
                this.chunks.forEach(ForcedChunk::close);
                this.chunks.clear();
            }
        }
    }

    /**
     * Mode traincarts can save in
     */
    public enum SaveMode {
        /** Save that runs periodically in the background. Only saves deltas */
        AUTOSAVE,
        /** Forceful save-all requested using a command */
        COMMAND,
        /** Save that occurs on shutdown */
        SHUTDOWN;

        public boolean isAutoSave() {
            return this == AUTOSAVE;
        }
    }

    /**
     * Designates a Class to be making a TrainCarts plugin instance accessible
     */
    public interface Provider {

        /**
         * Gets the TrainCarts main plugin instance. From here all of TrainCarts' API
         * can be accessed
         *
         * @return TrainCarts plugin instance
         */
        TrainCarts getTrainCarts();
    }
}
