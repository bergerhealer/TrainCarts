package com.bergerkiller.bukkit.tc.offline.sign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.ModuleLogger;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorldMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler.DataMigrationDecoder;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Stores metadata tied to signs that persists even when chunks
 * or worlds signs are in unload. Data is persistently stored
 * to disk in the background.
 */
public class OfflineSignStore {
    private final TrainCarts plugin;
    private final ModuleLogger logger;
    private final OfflineWorldMap<OfflineSignWorldStore> byWorld = new OfflineWorldMap<>();
    private final Map<String, MetadataHandlerEntry<?>> handlers = new HashMap<>();
    private final Map<Class<?>, MetadataHandlerEntry<?>> handlersByMetadataType = new HashMap<>();
    private final Map<String, List<OfflineMetadataEntry<Object>>> pendingByMetadataType = new HashMap<>();
    private final ImplicitlySharedSet<OfflineMetadataEntry<?>> allEntries = new ImplicitlySharedSet<>(new LinkedHashSet<>());
    private final OfflineSignLegacyImporter legacyImporter;
    private final BackgroundWriter writer;
    private final OfflineSignStoreListener listener;

    public OfflineSignStore(TrainCarts plugin) {
        this.plugin = plugin;
        this.logger = new ModuleLogger(plugin, "OfflineSignStore");
        this.legacyImporter = new OfflineSignLegacyImporter(this, plugin);
        this.writer = new BackgroundWriter(plugin.getDataFile("SignMetadata.dat"));
        this.listener = new OfflineSignStoreListener(this);
    }

    /**
     * Gets the TrainCarts plugin instance this OfflineSignStore belongs to
     *
     * @return TrainCarts plugin
     */
    public TrainCarts getPlugin() {
        return this.plugin;
    }

    /**
     * Loads the metadata save file
     */
    public void load() {
        clearAllEntries();
        writer.load();
    }

    /**
     * Enables the store for first-time use. Loads the save file and starts the background
     * writer.
     */
    public void enable() {
        legacyImporter.enable();
        writer.start();
        plugin.register(listener);

        // Process all the worlds/chunks that are already loaded right now
        for (World world : Bukkit.getWorlds()) {
            this.loadSignsOnWorld(world);
        }
    }

    /**
     * Disables the store, saves any pending data and shuts down the writer.
     */
    public void disable() {
        // For all handlers still registered, print a warning. As that can be bad.
        // Handlers should unregister itself while their respective plugin disables
        // to prevent ClassLoadErrors during metadata encoding.
        for (MetadataHandlerEntry<?> entry : handlers.values()) {
            Plugin plugin = CommonUtil.getPluginByClass(entry.metadataType);
            String pluginNamePart = (plugin == null) ? "" : " [Plugin " + plugin.getName() + "] ";
            logger.log(Level.WARNING, "[Developer] " + pluginNamePart +
                    "Sign metadata handler for " + entry.metadataTypeName +
                    " is still registered! Please call unregisterHandler() in onDisable() to fix this warning!");
        }

        // As a followup, de-register all handlers still registered.
        // This calls onUnloaded() and such. Something the handlers do expect.
        for (OfflineMetadataEntry<?> entry : allEntries.cloneAsIterable()) {
            if (!entry.clearHandler()) {
                removeEntry(entry);
            }
        }

        // Clear all handlers
        handlers.clear();
        handlersByMetadataType.clear();

        // Performs a final save of any entries that changed still
        writer.stop();
    }

    /**
     * Registers a new sign metadata handler. After registering, the handler
     * {@link OfflineSignMetadataHandler#onAdded(OfflineSign, Object)} is
     * called for every offline sign that is currently known to this store.
     *
     * @param <T> Metadata type
     * @param <H> Handler type
     * @param metadataType Metadata class type
     * @param handler Handler for Metadata class type
     * @return The input Handler
     */
    public <H extends OfflineSignMetadataHandler<T>, T> H registerHandler(Class<T> metadataType, H handler) {
        MetadataHandlerEntry<T> newHandlerEntry = new MetadataHandlerEntry<T>(metadataType, handler);
        {
            MetadataHandlerEntry<?> existing = handlers.put(newHandlerEntry.metadataTypeName, newHandlerEntry);
            if (existing != null) {
                handlers.put(newHandlerEntry.metadataTypeName, existing);
                if (existing.handler == handler) {
                    return handler; // Ignore, already registered
                }
                throw new IllegalStateException("A handler for " + existing.metadataTypeName +
                        " is already registered: " + existing.handler.getClass().getName());
            }
        }
        handlersByMetadataType.clear();
        handlers.values().forEach(h -> handlersByMetadataType.put(h.metadataType, h));

        // Initialize all metadata entries that still require this handler
        List<OfflineMetadataEntry<T>> entries = CommonUtil.unsafeCast(pendingByMetadataType.remove(
                newHandlerEntry.metadataTypeName));
        if (entries != null) {
            for (OfflineMetadataEntry<T> entry : entries) {
                if (!entry.isRemoved()) {
                    initHandler(entry, newHandlerEntry);
                }
            }
        }

        return handler;
    }

    /**
     * Un-registers a previously registered handler tied to the specified metadata type.
     * Same behavior as {@link #unregisterHandler(OfflineSignMetadataHandler)} but
     * avoids having to keep track of the handler instance.<br>
     * <br>
     * The previously registered handler is notified of all previous entries that existed
     * with onUnloaded().
     *
     * @param metadataType Metadata class type
     */
    public void unregisterHandler(Class<?> metadataType) {
        MetadataHandlerEntry<?> handlerEntry = handlersByMetadataType.get(metadataType);
        if (handlerEntry == null) {
            throw new IllegalArgumentException("Handler for type " + metadataType + " is not registered");
        } else {
            unregisterHandlerEntry(handlerEntry);
        }
    }

    /**
     * Un-registers a previously registered handler.<br>
     * <br>
     * The previously registered handler is notified of all previous entries that existed
     * with onUnloaded().
     *
     * @param handler Previously registered handler
     */
    public void unregisterHandler(OfflineSignMetadataHandler<?> handler) {
        // Try to match this handler up with metadata handled by it, if any
        List<MetadataHandlerEntry<?>> handlerEntries = handlers.values().stream()
            .filter(e -> e.handler == handler)
            .collect(Collectors.toList());

        // Verify
        if (handlerEntries.isEmpty()) {
            throw new IllegalArgumentException("Handler is not registered");
        }

        // Un-register them all
        handlerEntries.forEach(e -> unregisterHandlerEntry(e));
    }

    private void unregisterHandlerEntry(MetadataHandlerEntry<?> handlerEntry) {
        // Remove handler and recalculate
        handlers.remove(handlerEntry.metadataTypeName);
        handlersByMetadataType.clear();
        handlers.values().forEach(h -> handlersByMetadataType.put(h.metadataType, h));

        // For all entries that use this handler, park to pendingByMetadataType
        List<OfflineMetadataEntry<Object>> handlerEntries = byWorld.values().stream()
                .flatMap(v -> v.values().stream())
                .filter(e -> e.handlerEntry == handlerEntry)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!handlerEntries.isEmpty()) {
            handlerEntries.forEach(e -> {
                if (!e.clearHandler()) {
                    removeEntry(e);
                }
            });
            pendingByMetadataType.put(handlerEntry.metadataTypeName, handlerEntries);
        }
    }

    /**
     * Returns the current metadata stored for a sign, similar to {@link #get(Sign, Class)},
     * but if no metadata is currently stored computes a new metadata value to store. If the factory
     * returns null, then no metadata will be stored.<br>
     * <br>
     * Unlike the Block version of this same function, it is less error-prone when the sign
     * is not yet fully initialized (lines not known).
     *
     * @param <T> Type of metadata
     * @param signBlock Sign block, must be of a sign
     * @param metadataType Class type of metadata to check for
     * @param factory Factory method called to create new metadata if none exists yet
     * @return Computed metadata, null if the factory method returns null
     */
    public <T> T computeIfAbsent(Sign sign, Class<T> metadataType, Function<OfflineSign, ? extends T> factory) {
        return computeIfAbsentImpl(OfflineWorld.of(sign.getWorld()),
                                   new IntVector3(sign.getX(), sign.getY(), sign.getZ()),
                                   () -> sign,
                                   metadataType,
                                   factory);
    }

    /**
     * Returns the current metadata stored for a sign block, similar to {@link #get(Block, Class)},
     * but if no metadata is currently stored computes a new metadata value to store. If the factory
     * returns null, then no metadata will be stored.
     *
     * @param <T> Type of metadata
     * @param signBlock Sign block, must be of a sign
     * @param metadataType Class type of metadata to check for
     * @param factory Factory method called to create new metadata if none exists yet
     * @return Computed metadata, null if the factory method returns null
     */
    public <T> T computeIfAbsent(Block signBlock, Class<T> metadataType, Function<OfflineSign, ? extends T> factory) {
        return computeIfAbsentImpl(OfflineWorld.of(signBlock.getWorld()),
                                   new IntVector3(signBlock),
                                   signFromBlockSupplier(signBlock),
                                   metadataType,
                                   factory);
    }

    @SuppressWarnings("unchecked")
    private <T> T computeIfAbsentImpl(OfflineWorld world, IntVector3 position, Supplier<Sign> signGetter,
            Class<T> metadataType, Function<OfflineSign, ? extends T> factory
    ) {
        MetadataHandlerEntry<T> handlerEntry = findHandlerByType(metadataType);

        // Find an existing sign metadata entry and return it if found
        OfflineSign sign = null;
        OfflineSignWorldStore atWorld = forWorld(world);
        List<OfflineMetadataEntry<Object>> entries = atWorld.at(position);
        for (OfflineMetadataEntry<Object> entry : entries) {
            sign = entry.sign;
            if (entry.handlerEntry == handlerEntry) {
                return (T) entry.metadata;
            }
        }

        // Instantiate a new sign - error if this is not actually a sign!
        if (sign == null) {
            sign = new OfflineSign(world.getBlockAt(position), signGetter.get().getLines());
        }

        // Ask factory for new metadata and store it if not null
        T metadata = factory.apply(sign);
        if (metadata != null) {
            OfflineMetadataEntry<T> newEntry = new OfflineMetadataEntry<T>(sign, handlerEntry, metadata);
            entries.add((OfflineMetadataEntry<Object>) newEntry);
            atWorld.atChunk(position.toChunkCoordinates()).add((OfflineMetadataEntry<Object>) newEntry);
            onEntryAdded(newEntry);
        }
        return metadata;
    }

    /**
     * Updates the metadata for a sign block that already has metadata stored
     * in this store. Does nothing and returns null if no previous metadata is
     * stored, otherwise returns the previously stored metadata (non-null).<br>
     * <br>
     * This uniquely allows for updating the metadata without having to know
     * the current sign details, and can be used to update signs that aren't
     * currently loaded.
     *
     * @param <T> Type of metadata
     * @param signBlock OfflineBlock (world + coordinates) where the sign is at
     * @param metadata New Metadata to store
     * @return Previous metadata that was stored, or null if none was stored
     *         and no metadata was updated
     */
    @SuppressWarnings("unchecked")
    public <T> T putIfPresent(OfflineBlock signBlock, T metadata) {
        OfflineSignWorldStore atWorld = forWorld(signBlock.getWorld());
        MetadataHandlerEntry<T> handlerEntry = findHandler(metadata);

        // Find an existing sign metadata entry and update that if found
        List<OfflineMetadataEntry<Object>> entries = atWorld.at(signBlock.getPosition());
        for (OfflineMetadataEntry<Object> entry : entries) {
            if (entry.handlerEntry == handlerEntry) {
                T oldValue = (T) entry.metadata;
                entry.setMetadata(metadata);
                return oldValue;
            }
        }

        // Not found
        return null;
    }

    /**
     * Stores new or updated metadata for a sign. The initial sign
     * lines are read from the sign specified. This is less error-prone
     * than the Block method, whose sign may not yet be fully initialized.
     *
     * @param <T> Type of metadata
     * @param sign Sign to store the metadata for
     * @param metadata Metadata to store
     * @return Previous metadata that was stored, or null if none was stored
     */
    public <T> T put(Sign sign, T metadata) {
        return putImpl(OfflineWorld.of(sign.getWorld()),
                       new IntVector3(sign.getX(), sign.getY(), sign.getZ()),
                       () -> sign,
                       metadata);
    }

    /**
     * Stores new or updated metadata for a sign block
     *
     * @param <T> Type of metadata
     * @param signBlock Sign block, must be of a sign
     * @param metadata Metadata to store
     * @return Previous metadata that was stored, or null if none was stored
     */
    public <T> T put(Block signBlock, T metadata) {
        return putImpl(OfflineWorld.of(signBlock.getWorld()),
                       new IntVector3(signBlock),
                       signFromBlockSupplier(signBlock),
                       metadata);
    }

    @SuppressWarnings("unchecked")
    private <T> T putImpl(OfflineWorld world, IntVector3 position, Supplier<Sign> signGetter, T metadata) {
        OfflineSignWorldStore atWorld = forWorld(world);
        MetadataHandlerEntry<T> handlerEntry = findHandler(metadata);

        // Find an existing sign metadata entry and update that if found
        OfflineSign offlineSign = null;
        List<OfflineMetadataEntry<Object>> entries = atWorld.at(position);
        for (OfflineMetadataEntry<Object> entry : entries) {
            offlineSign = entry.sign;
            if (entry.handlerEntry == handlerEntry) {
                T oldValue = (T) entry.metadata;
                entry.setMetadata(metadata);
                return oldValue;
            }
        }

        // Instantiate a new sign - error if this is not actually a sign!
        if (offlineSign == null) {
            offlineSign = new OfflineSign(world.getBlockAt(position), signGetter.get().getLines());
        }

        // Create new
        OfflineMetadataEntry<T> newEntry = new OfflineMetadataEntry<T>(offlineSign, handlerEntry, metadata);
        entries.add((OfflineMetadataEntry<Object>) newEntry);
        atWorld.atChunk(position.toChunkCoordinates()).add((OfflineMetadataEntry<Object>) newEntry);
        onEntryAdded(newEntry);
        return null;
    }

    /**
     * Gets a read-only view of all the sign-bound metadata entries stored on the server
     * for a given metadata type
     *
     * @param <T> Metadata type
     * @param metadataType Metadata Class type
     * @return Collection of metadata entries of this Metadata type
     */
    public <T> Collection<Entry<T>> getAllEntries(Class<T> metadataType) {
        MetadataHandlerEntry<T> handler = tryFindHandlerByType(metadataType);
        if (handler == null) {
            // Not (yet) registered
            return Collections.emptyList();
        } else if (handler.metadataType == metadataType) {
            // Can safely return all possible metadata values that use this handler
            return Collections.unmodifiableCollection(handler.entries);
        } else {
            // Is a superclass - will need to filter result by this metadata type
            // This is slow, if it becomes a problem we'll need to come up with something
            // more performant.
            ArrayList<Entry<T>> entriesFiltered = new ArrayList<>(handler.entries.size());
            for (Entry<T> entry : handler.entries) {
                if (metadataType.isInstance(entry.getMetadata())) {
                    entriesFiltered.add(entry);
                }
            }
            return entriesFiltered;
        }
    }

    /**
     * Gets sign metadata stored for a sign at the given block
     *
     * @param <T> Type of metadata
     * @param signBlock Block the sign is at
     * @param metadataType Class type of metadata to check for
     * @return metadata stored of this type, or null if none/incompatible is stored
     */
    public <T> T get(Block signBlock, Class<T> metadataType) {
        return get(OfflineWorld.of(signBlock.getWorld()), new IntVector3(signBlock), metadataType);
    }

    /**
     * Gets sign metadata stored for a sign
     *
     * @param <T> Type of metadata
     * @param sign Sign whose metadata to get
     * @param metadataType Class type of metadata to check for
     * @return metadata stored of this type, or null if none/incompatible is stored
     */
    public <T> T get(Sign sign, Class<T> metadataType) {
        return get(OfflineWorld.of(sign.getWorld()),
                new IntVector3(sign.getX(), sign.getY(), sign.getZ()),
                metadataType);
    }

    /**
     * Gets sign metadata stored for a sign at the given block
     *
     * @param <T> Type of metadata
     * @param signBlock OfflineBlock the sign is at
     * @param metadataType Class type of metadata to check for
     * @return metadata stored of this type, or null if none/incompatible is stored
     */
    public <T> T get(OfflineBlock signBlock, Class<T> metadataType) {
        return get(signBlock.getWorld(), signBlock.getPosition(), metadataType);
    }

    /**
     * Gets sign metadata stored for a sign on a world at the given coordinates
     *
     * @param <T> Type of metadata
     * @param world World, can be loaded or not loaded
     * @param position Coordinates of the sign on the world
     * @param metadataType Class type of metadata to check for
     * @return metadata stored of this type, or null if none/incompatible is stored
     */
    @SuppressWarnings("unchecked")
    public <T> T get(OfflineWorld world, IntVector3 position, Class<T> metadataType) {
        for (OfflineMetadataEntry<?> entry : forWorld(world).at(position)) {
            Object metadata = entry.getMetadata();
            if (metadataType.isInstance(metadata)) {
                return (T) metadata;
            }
        }
        return null;
    }

    /**
     * Removes all metadata types stored for a sign at the given block.
     * If metadata is stored and a handler is installed, the handler will be
     * notified of the removal.
     *
     * @param signBlock Block where the sign is at
     */
    public void removeAll(Block signBlock) {
        removeAll(OfflineBlock.of(signBlock));
    }

    /**
     * Removes all metadata types stored for a sign at the given block.
     * If metadata is stored and a handler is installed, the handler will be
     * notified of the removal.
     *
     * @param signBlock OfflineBlock where the sign is at
     */
    public void removeAll(OfflineBlock signBlock) {
        OfflineSignWorldStore atWorld = forWorld(signBlock.getWorld());
        boolean hasMoreMetadata;
        do {
            // Generally only one metadata value is stored, but it could be multiple
            // The handler onRemoved callback could mutate the store, so we must not take
            // any chances and create a new iterator for every entry that remains.
            Iterator<OfflineMetadataEntry<Object>> iter = atWorld.at(signBlock.getPosition()).iterator();
            if (!iter.hasNext()) {
                return;
            }

            // Remove it
            OfflineMetadataEntry<Object> entry = iter.next();
            hasMoreMetadata = iter.hasNext();
            iter.remove();
            atWorld.atChunk(signBlock.getPosition().toChunkCoordinates()).remove(entry);
            onEntryRemoved(entry);
        } while (hasMoreMetadata);
    }

    /**
     * Gets and removes metadata stored for a sign at the given block.
     * If metadata is stored here and a handler is installed, will notify
     * the handler of removal.
     *
     * @param <T> Type of metadata
     * @param signBlock Block the sign is at
     * @param metadataType Class type of metadata to check for
     * @return Found metadata that was removed, or null if none was stored
     */
    public <T> T remove(Block signBlock, Class<T> metadataType) {
        return remove(OfflineBlock.of(signBlock), metadataType);
    }

    /**
     * Gets and removes metadata stored for a sign on a world at the given coordinates.
     * The world the sign is at does not have to be loaded.
     * If metadata is stored here and a handler is installed, will notify
     * the handler of removal.<br>
     * <br>
     * If the handler's {@link OfflineSignMetadataHandler#isUnloadedWorldsIgnored()} returns
     * true, and the world is not loaded, the handler will not be notified of this removal.
     *
     * @param <T> Type of metadata
     * @param signBlock OfflineBlock (OfflineWorld + coordinates) where the sign is at
     * @param metadataType Class type of metadata to check for
     * @return Found metadata that was removed, or null if none was stored
     */
    @SuppressWarnings("unchecked")
    public <T> T remove(OfflineBlock signBlock, Class<T> metadataType) {
        OfflineSignWorldStore atWorld = forWorld(signBlock.getWorld());
        Iterator<OfflineMetadataEntry<Object>> iter = atWorld.at(signBlock.getPosition()).iterator();
        while (iter.hasNext()) {
            OfflineMetadataEntry<Object> entry = iter.next();
            Object metadata = entry.getMetadata();
            if (metadataType.isInstance(metadata)) {
                iter.remove();
                atWorld.atChunk(signBlock.getPosition().toChunkCoordinates()).remove(entry);
                onEntryRemoved(entry);
                return (T) metadata;
            }
        }
        return null;
    }

    private void removeEntry(OfflineMetadataEntry<?> entryToRemove) {
        OfflineSignWorldStore atWorld = forWorld(entryToRemove.sign.getWorld());
        Iterator<OfflineMetadataEntry<Object>> iter = atWorld.at(entryToRemove.sign.getPosition()).iterator();
        while (iter.hasNext()) {
            OfflineMetadataEntry<Object> entry = iter.next();
            if (entry == entryToRemove) {
                iter.remove();
                atWorld.atChunk(entry.sign.getPosition().toChunkCoordinates()).remove(entry);
                onEntryRemoved(entry);
                break;
            }
        }
    }

    private static Supplier<Sign> signFromBlockSupplier(final Block block) {
        return () -> {
            Sign bukkitSign = BlockUtil.getSign(block);
            if (bukkitSign == null) {
                throw new IllegalArgumentException(String.format(
                        "Block on world %s at [x=%d y=%d z=%d] is not a sign",
                        block.getWorld().getName(),
                        block.getX(), block.getY(), block.getZ()));
            }
            return bukkitSign;
        };
    }

    private OfflineSignWorldStore forWorld(OfflineWorld world) {
        return byWorld.computeIfAbsent(world, OfflineSignWorldStore::new);
    }

    private OfflineSignWorldStore forWorld(World world) {
        return byWorld.computeIfAbsent(world, OfflineSignWorldStore::new);
    }

    private void clearAllEntries() {
        byWorld.clear();
        allEntries.clear();
        pendingByMetadataType.clear();
    }

    private void loadEntry(String metadataTypeName, OfflineMetadataEntry<Object> newEntry) {
        // Register entry
        OfflineSignWorldStore forWorld = this.forWorld(newEntry.sign.getWorld());
        forWorld.at(newEntry.sign.getPosition()).add(newEntry);
        forWorld.atChunk(newEntry.sign.getPosition().toChunkCoordinates()).add(newEntry);
        // this.onEntryAdded(newEntry); // Called changed(), which we don't want
        allEntries.add(CommonUtil.unsafeCast(newEntry));

        // Try to load the entry using an already-registered handler
        MetadataHandlerEntry<?> handler = handlers.get(metadataTypeName);
        if (handler != null) {
            initHandler(newEntry, CommonUtil.unsafeCast(handler));
        } else {
            // Not yet registered - add to the pending list
            List<OfflineMetadataEntry<Object>> pending = this.pendingByMetadataType.computeIfAbsent(
                    metadataTypeName, k -> new ArrayList<>());
            pending.add(newEntry);
        }
    }

    private <T> void initHandler(OfflineMetadataEntry<T> entry, MetadataHandlerEntry<T> handler) {
        if (!entry.setHandler(handler)) {
            // Something went wrong decoding - remove this entry again
            OfflineSignWorldStore atWorld = forWorld(entry.sign.getWorld());
            Iterator<OfflineMetadataEntry<Object>> iter = atWorld.at(entry.sign.getPosition()).iterator();
            while (iter.hasNext()) {
                if (iter.next() == entry) {
                    iter.remove();
                    atWorld.atChunk(entry.sign.getPosition().toChunkCoordinates()).remove(entry);
                    break;
                }
            }
            onEntryRemoved(entry);
        }
    }

    protected void unloadSignsOnWorld(World world) {
        // Iterate a copy to avoid concurrent modification exceptions
        for (OfflineMetadataEntry<Object> entry : new ArrayList<>(forWorld(world).values())) {
            if (entry.handlerEntry != null && entry.handlerEntry.handler.isUnloadedWorldsIgnored()) {
                if (!entry.unload()) {
                    // Failed to encode metadata - remove it
                    removeEntry(entry);
                }
            }
        }
    }

    protected void loadSignsOnWorld(World world) {
        // Iterate a copy to avoid concurrent modification exceptions
        for (OfflineMetadataEntry<Object> entry : new ArrayList<>(forWorld(world).values())) {
            if (!entry.addedToHandler && entry.handlerEntry != null) {
                if (entry.decodeMetadata()) {
                    entry.callOnLoaded();
                } else {
                    removeEntry(entry);
                }
            }
        }

        // This probably does nothing - there are no chunks loaded during world init
        // Will verify signs in spawn chunks of already-loaded worlds
        for (Chunk chunk : world.getLoadedChunks()) {
            verifySignsInChunk(chunk);
        }
    }

    protected void verifySignsInChunk(Chunk chunk) {
        OfflineSignWorldStore atWorld = forWorld(OfflineWorld.of(chunk.getWorld()));
        Iterator<OfflineMetadataEntry<Object>> entriesAtChunk = atWorld.atChunk(new IntVector2(chunk)).iterator();
        if (!entriesAtChunk.hasNext()) {
            return;
        }

        // Collect all Signs that are inside this chunk
        // The Collection might be slow to iterate multiple times through,
        // so this is an efficient way to do it.
        Map<IntVector3, Sign> signsByBlock;
        try {
            Collection<BlockState> blockStates = WorldUtil.getBlockStates(chunk);
            signsByBlock = new HashMap<IntVector3, Sign>(blockStates.size());
            for (BlockState state : blockStates) {
                if (state instanceof Sign) {
                    Sign s = (Sign) state;
                    signsByBlock.put(new IntVector3(s.getX(), s.getY(), s.getZ()), s);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, String.format(
                    "Failed to read BlockStates in chunk {world=%s, x=%d, z=%d}, verify failed",
                    chunk.getWorld().getName(), chunk.getX(), chunk.getZ()), t);
            return;
        }

        // Go by all entries in the chunk and try to find the matching sign
        // Then, verify the sign is still the same. If not, remove it.
        do {
            OfflineMetadataEntry<Object> entry = entriesAtChunk.next();
            Sign sign = signsByBlock.get(entry.sign.getPosition());
            if (sign == null || !entry.sign.verify(sign)) {
                entriesAtChunk.remove();
                atWorld.at(entry.sign.getPosition()).remove(entry);
                onEntryRemoved(entry);
            }
        } while (entriesAtChunk.hasNext());
    }

    private void onEntryAdded(OfflineMetadataEntry<?> entry) {
        allEntries.add(CommonUtil.unsafeCast(entry));
        entry.handlerEntry.entries.add(CommonUtil.unsafeCast(entry));
        writer.changed();
        entry.callOnAdded();
    }

    private void onEntryRemoved(OfflineMetadataEntry<?> entry) {
        if (allEntries.remove(entry)) {
            writer.changed();
        }
        entry.removed = true;
        entry.callOnRemoved();
    }

    private <T> MetadataHandlerEntry<T> findHandler(T metadataValue) {
        if (metadataValue == null) {
            throw new IllegalArgumentException("Metadata value type is null");
        } else {
            return findHandlerByType(metadataValue.getClass());
        }
    }

    private <T> MetadataHandlerEntry<T> findHandlerByType(Class<?> metadataType) {
        MetadataHandlerEntry<T> handler = tryFindHandlerByType(metadataType);
        if (handler != null) {
            return handler;
        }

        throw new IllegalArgumentException("No handler is registered for metadata type " + metadataType);
    }

    private <T> MetadataHandlerEntry<T> tryFindHandlerByType(Class<?> metadataType) {
        MetadataHandlerEntry<?> handler = handlersByMetadataType.get(metadataType);
        if (handler != null) {
            return CommonUtil.unsafeCast(handler);
        }

        // See if there is a super-type or interface that there is a handler for
        // This allows people to use inherited types for data values
        for (Class<?> superType : ReflectionUtil.getAllClassesAndInterfaces(metadataType).collect(Collectors.toList())) {
            if ((handler = handlersByMetadataType.get(superType)) != null) {
                handlersByMetadataType.put(metadataType, handler); // Remember for next time
                return CommonUtil.unsafeCast(handler);
            }
        }

        return null;
    }

    private static final class OfflineSignWorldStore {
        @SuppressWarnings("unused")
        private final OfflineWorld world;
        private final ListMultimap<IntVector3, OfflineMetadataEntry<Object>> byBlockCoordinates;
        private final ListMultimap<IntVector2, OfflineMetadataEntry<Object>> byChunkCoordinates;

        public OfflineSignWorldStore(World world) {
            this(OfflineWorld.of(world));
        }

        public OfflineSignWorldStore(OfflineWorld world) {
            this.world = world;
            this.byBlockCoordinates = ArrayListMultimap.create(1000, 1);
            this.byChunkCoordinates = ArrayListMultimap.create(500, 1);
        }

        public Collection<OfflineMetadataEntry<Object>> values() {
            return byBlockCoordinates.values();
        }

        public List<OfflineMetadataEntry<Object>> at(IntVector3 coordinate) {
            return byBlockCoordinates.get(coordinate);
        }

        public List<OfflineMetadataEntry<Object>> atChunk(IntVector2 chunkCoordinates) {
            return byChunkCoordinates.get(chunkCoordinates);
        }
    }

    /**
     * A single loaded (added) metadata entry stored in the OfflineSignStore.
     *
     * @param <T>
     */
    public static interface Entry<T> {

        /**
         * Gets the Sign details the metadata is tied to.
         *
         * @return sign details at the time of registering metadata
         */
        OfflineSign getSign();

        /**
         * Gets whether this entry is (by now) removed from the store
         *
         * @return True if removed
         */
        boolean isRemoved();

        /**
         * Gets the current metadata value
         *
         * @return metadata value
         */
        T getMetadata();

        /**
         * Sets a new metadata value. If the value differs, the
         * onUpdated callback is called on the handler
         *
         * @param metadata New metadata to set
         */
        void setMetadata(T metadata);

        /**
         * If not already removed, removes this metadata entry
         * entirely. The onRemoved callback is called on the handler.
         */
        void remove();
    }

    private final class OfflineMetadataEntry<T> implements Entry<T> {
        public final OfflineSign sign;
        private MetadataHandlerEntry<T> handlerEntry;
        private byte[] encodedData;
        private T metadata;
        private boolean removed;
        private boolean addedToHandler;

        public OfflineMetadataEntry(OfflineSign sign, MetadataHandlerEntry<T> handlerEntry, T metadata) {
            this.sign = sign;
            this.handlerEntry = handlerEntry;
            this.encodedData = null;
            this.metadata = metadata;
            this.removed = false;
            this.addedToHandler = false;
        }

        public OfflineMetadataEntry(OfflineSign sign, byte[] encodedData) {
            this.sign = sign;
            this.handlerEntry = null;
            this.encodedData = encodedData;
            this.metadata = null;
            this.removed = false;
            this.addedToHandler = false;
        }

        @Override
        public OfflineSign getSign() {
            return this.sign;
        }

        @Override
        public T getMetadata() {
            return this.metadata;
        }

        @Override
        public boolean isRemoved() {
            return this.removed;
        }

        public boolean clearHandler() {
            if (this.handlerEntry == null) {
                return true;
            }

            // Before un-registering, make sure the data is encoded
            if (encodeMetadata() == null) {
                return false;
            }
            this.handlerEntry.entries.remove(this);

            this.callOnUnloaded();
            this.handlerEntry = null;
            return true;
        }

        /**
         * Sets the handler to use for this metadata entry. If not already decoded,
         * decodes the metadata and calls onAdded on the handler.
         *
         * @param handlerEntry
         * @return True if decoding of the metadata was successful, or False if a
         *         problem occurred and the metadata needs to be removed.
         */
        public boolean setHandler(MetadataHandlerEntry<T> handlerEntry) {
            if (this.handlerEntry == handlerEntry) {
                return true;
            } else if (this.handlerEntry != null) {
                logger.log(Level.SEVERE, "Attempted to register handler " +
                        handlerEntry.handler.getClass().getName() + " for sign " + sign +
                        " but another handler was already registered");
                logger.log(Level.SEVERE, "Handler currently registered: " +
                        this.handlerEntry.handler.getClass().getName());
                return false;
            } else if (this.encodedData == null) {
                logger.log(Level.SEVERE, "Attempted to decode metadata for sign " +
                        sign + " but no encoded data is available to decode");
                return false;
            }

            // Link with handler
            this.handlerEntry = handlerEntry;
            this.handlerEntry.entries.add(this);

            // If world is not loaded, do not call onAdded and do not decode until the world is loaded
            if (!this.sign.getWorld().isLoaded() && handlerEntry.handler.isUnloadedWorldsIgnored()) {
                return true;
            }

            // Decode metadata and if successful, call onAdded
            if (!decodeMetadata()) {
                return false;
            }
            callOnLoaded();
            return true;
        }

        /**
         * Encodes the current metadata if saving is needed, then notifies
         * the handler of removal and clears the metadata.
         *
         * @return True if unloading was successful, False if an error occurred encoding
         *         the old metadata and this entry should be removed.
         */
        public boolean unload() {
            if (encodeMetadata() == null) {
                return false;
            }
            this.callOnUnloaded();
            this.metadata = null;
            return true;
        }

        public boolean decodeMetadata() {
            try {
                try (ByteArrayInputStream b_stream = new ByteArrayInputStream(this.encodedData);
                     InflaterInputStream d_stream = new InflaterInputStream(b_stream);
                     DataInputStream stream = new DataInputStream(d_stream))
                {
                    // Skip over the default encoded sign data and such - we don't need that right now
                    OfflineSign.readFrom(stream); // OfflineSign
                    stream.readUTF(); // Handler metadata type

                    // Check data version, perform migration if needed
                    int metadataVersion = readVariableLengthInt(stream);
                    if (metadataVersion == this.handlerEntry.handler.getMetadataVersion()) {
                        // Decode metadata itself
                        this.metadata = this.handlerEntry.handler.onDecode(stream, this.sign);
                        if (this.metadata == null) {
                            throw new IllegalStateException("Decoded metadata is null");
                        }
                    } else {
                        // Check if this metadata could be decoded with a migration decoder
                        DataMigrationDecoder<T> decoder;
                        try {
                            decoder = this.handlerEntry.handler.getMigrationDecoder(this.sign, metadataVersion);
                            if (decoder == null) {
                                throw new UnsupportedOperationException("Not supported");
                            }
                        } catch (UnsupportedOperationException ex) {
                            logger.log(Level.WARNING, "Failed to decode metadata for sign " + sign +
                                    ": Unsupported data version (type=" + this.handlerEntry.metadataTypeName + ")");
                            return false;
                        }

                        // Decode metadata using migration decoder
                        this.metadata = decoder.onDecode(stream, this.sign, metadataVersion);
                        if (this.metadata == null) {
                            throw new IllegalStateException("Failed to migrate metadata: decoded metadata is null");
                        }
                    }
                }
                return true;
            } catch (OfflineSignMetadataHandler.InvalidMetadataException ex) {
                // No logging but fail the decoding, which will delete the metadata silently
                return false;
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Failed to decode metadata for sign " + sign, t);
                return false;
            }
        }

        public byte[] encodeMetadata() {
            byte[] encodedData = this.encodedData;
            if (encodedData == null) {
                synchronized (this) {
                    encodedData = this.encodedData;
                    if (encodedData == null) {
                        try (ByteArrayOutputStream b_stream = new ByteArrayOutputStream()) {
                            try (DeflaterOutputStream d_stream = new DeflaterOutputStream(b_stream);
                                 DataOutputStream stream = new DataOutputStream(d_stream)
                            ) {
                                // Prefix with the sign information itself - is required for later decoding
                                OfflineSign.writeTo(stream, this.sign);
                                stream.writeUTF(this.handlerEntry.metadataTypeName);

                                // Encode data version using variable length encoding
                                writeVariableLengthInt(stream, this.handlerEntry.handler.getMetadataVersion());

                                // Encode the metadata itself
                                this.handlerEntry.handler.onEncode(stream, this.sign, this.metadata);
                            }

                            this.encodedData = encodedData = b_stream.toByteArray();
                        } catch (OfflineSignMetadataHandler.InvalidMetadataException ex) {
                            // No logging but clean up the sign metadata
                            return null;
                        } catch (Throwable t) {
                            logger.log(Level.SEVERE, "Failed to encode metadata for sign " + sign, t);
                            return null;
                        }
                    }
                }
            }
            return encodedData;
        }

        public void callOnAdded() {
            if (!this.addedToHandler && this.handlerEntry != null) {
                try {
                    this.handlerEntry.handler.onAdded(OfflineSignStore.this, this.sign, this.metadata);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to handle onAdded for sign " + sign, t);
                }
                this.addedToHandler = true;
            }
        }

        public void callOnRemoved() {
            if (this.addedToHandler && this.handlerEntry != null && this.metadata != null) {
                try {
                    this.handlerEntry.handler.onRemoved(OfflineSignStore.this, this.sign, this.metadata);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to handle onRemoved for sign " + sign, t);
                }
                this.addedToHandler = false;
            }
        }

        public void callOnLoaded() {
            if (!this.addedToHandler && this.handlerEntry != null) {
                try {
                    this.handlerEntry.handler.onLoaded(OfflineSignStore.this, this.sign, this.metadata);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to handle onLoaded for sign " + sign, t);
                }
                this.addedToHandler = true;
            }
        }

        public void callOnUnloaded() {
            if (this.addedToHandler && this.handlerEntry != null && this.metadata != null) {
                try {
                    this.handlerEntry.handler.onUnloaded(OfflineSignStore.this, this.sign, this.metadata);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to handle onUnloaded for sign " + sign, t);
                }
                this.addedToHandler = false;
            }
        }

        @Override
        public void setMetadata(T metadata) {
            if (metadata == null) {
                throw new IllegalArgumentException("New metadata is null");
            }
            if (metadata.equals(this.metadata)) {
                return;
            }

            T oldMetadata = this.metadata;

            synchronized (this) {
                this.metadata = metadata;
                this.encodedData = null;
            }

            if (this.handlerEntry != null) {
                try {
                    this.handlerEntry.handler.onUpdated(OfflineSignStore.this, this.sign, oldMetadata, metadata);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to handle onUpdated for sign " + sign, t);
                }
            }

            OfflineSignStore.this.writer.changed();
        }

        @Override
        public void remove() {
            removeEntry(this);
        }
    }

    private class BackgroundWriter {
        private Thread thread;
        private final Object lock = new Object();
        private final File saveFile;
        private volatile boolean savingNeeded = false;
        private volatile boolean shuttingDown = false;

        public BackgroundWriter(File saveFile) {
            this.saveFile = saveFile;
        }

        public void changed() {
            synchronized (lock) {
                savingNeeded = true;
                lock.notifyAll();
            }
        }

        public void start() {
            this.shuttingDown = false;
            if (this.thread == null) {
                this.thread = new Thread(this::runWorker, "TrainCarts:SignMetadataWriterThread");
                this.thread.setDaemon(true);
                this.thread.start();
            }
        }

        public void stop() {
            synchronized (lock) {
                this.shuttingDown = true;
                this.lock.notifyAll();
            }

            if (this.thread != null) {
                try {
                    this.thread.join(10000);
                    if (this.thread.isAlive()) {
                        logger.log(Level.WARNING, "Saving sign metadata is taking longer than 10s");
                        this.thread.join();
                    }
                } catch (InterruptedException e) { /* ignore */ }
                this.thread = null;
            }
        }

        private void runWorker() {
            final long MIN_SAVE_INTERVAL = 5000; // 5s
            long lastSaveTS = System.currentTimeMillis() - MIN_SAVE_INTERVAL;
            do {
                boolean doSave = false;
                synchronized (lock) {
                    try {
                        // Wait until a change occurs that must be saved, or shutting down
                        while(!savingNeeded && !shuttingDown)
                            lock.wait();

                        // Avoid writing too often, ignore when shutting down
                        while (!shuttingDown) {
                            long remaining = (lastSaveTS + MIN_SAVE_INTERVAL) - System.currentTimeMillis();
                            if (remaining > 0)
                                lock.wait(remaining);
                            else
                                break;
                        }
                    } catch (InterruptedException e) { /* ignore */ }

                    // Track whether saving (and not just shutting down) and reset state
                    doSave = savingNeeded;
                    savingNeeded = false;
                }

                // Perform the saving if needed
                if (doSave) {
                    lastSaveTS = System.currentTimeMillis();
                    save();
                }
            } while (!shuttingDown);
        }

        public void load() {
            if (this.saveFile.exists()) {
                try {
                    try (FileInputStream f_stream = new FileInputStream(this.saveFile);
                         DataInputStream stream = new DataInputStream(f_stream))
                    {
                        int versionCode = readVariableLengthInt(stream);
                        if (versionCode == 1) {
                            while (stream.available() > 0) {
                                // Read metadata bytes
                                byte[] encodedData = new byte[readVariableLengthInt(stream)];
                                stream.readFully(encodedData);

                                // Decode just the sign metadata bit
                                OfflineSign sign;
                                String metadataTypeName;
                                try (ByteArrayInputStream m_b_stream = new ByteArrayInputStream(encodedData);
                                     InflaterInputStream m_d_stream = new InflaterInputStream(m_b_stream);
                                     DataInputStream m_stream = new DataInputStream(m_d_stream))
                                {
                                    sign = OfflineSign.readFrom(m_stream);
                                    metadataTypeName = m_stream.readUTF();
                                }

                                // Import it into the store
                                OfflineMetadataEntry<Object> newEntry = new OfflineMetadataEntry<Object>(sign, encodedData);
                                loadEntry(metadataTypeName, newEntry);
                            }
                        } else {
                            logger.log(Level.SEVERE, "Failed to read sign metadata: unsupported version");
                        }
                    }
                } catch (EOFException ex) {
                    logger.log(Level.SEVERE, "Reached unexpected end-of-file while reading sign metadata (corrupted file?)");
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Failed to read sign metadata", ex);
                }
            }
        }

        public void save() {
            final List<OfflineMetadataEntry<?>> encodeFailures = new ArrayList<>();
            File tmpFile = new File(this.saveFile.getParentFile(), this.saveFile.getName() +
                    "." + System.currentTimeMillis() + ".tmp");

            // Write fully to the tmp file first
            boolean saveSuccessful = false;
            try {
                try (FileOutputStream f_stream = new FileOutputStream(tmpFile);
                     DataOutputStream stream = new DataOutputStream(f_stream))
                {
                    writeVariableLengthInt(stream, 1);
                    for (OfflineMetadataEntry<?> entry : allEntries.cloneAsIterable()) {
                        byte[] encodedData = entry.encodeMetadata();
                        if (encodedData != null) {
                            writeVariableLengthInt(stream, encodedData.length);
                            stream.write(encodedData);
                        } else {
                            encodeFailures.add(entry);
                        }
                    }
                }
                saveSuccessful = true;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to write sign metadata", ex);
            }

            // Swap the tmp and actual save file atomically
            if (saveSuccessful) {
                try {
                    atomicMove(tmpFile, saveFile);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to finalize writing sign metadata", t);
                }
            }

            // If there were entries that couldn't be encoded, clean those up on the main thread later
            if (!encodeFailures.isEmpty()) {
                CommonUtil.getPluginExecutor(plugin).execute(() -> {
                    encodeFailures.forEach(OfflineSignStore.this::removeEntry);
                });
            }
        }
    }

    private static class MetadataHandlerEntry<T> {
        public final Class<T> metadataType;
        public final String metadataTypeName;
        public final OfflineSignMetadataHandler<T> handler;
        public final Set<OfflineMetadataEntry<T>> entries;

        public MetadataHandlerEntry(Class<T> metadataType, OfflineSignMetadataHandler<T> handler) {
            this.metadataType = metadataType;
            this.metadataTypeName = metadataType.getName();
            this.handler = handler;
            this.entries = new LinkedHashSet<OfflineMetadataEntry<T>>();
        }
    }

    private static void atomicMove(File fromFile, File toFile) throws Throwable {
        // First try a newer Java's Files.move as this allows for an atomic move with overwrite
        // If this doesn't work, only then do we try our custom non-atomic methods
        try {
            Files.move(fromFile.toPath(), toFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException unsupportedIgnored) {
            // Efficient move using this method is not supported, use a fallback
        }

        // Check file actually even exists
        if (!fromFile.exists()) {
            throw new IOException("File " + fromFile + " does not exist");
        }

        // More dangerous: delete target file, then move the temp file to it
        // This operation is not atomic and could fail
        if (toFile.delete() && fromFile.renameTo(toFile)) {
            return;
        }

        // Even more risky: copy the data by using file streams
        // This could result in partial data in the destination file :(
        if (StreamUtil.tryCopyFile(fromFile, toFile)) {
            fromFile.delete();
            return;
        }

        // Failed :(
        throw new IOException("Atomic move from " + fromFile + " to " + toFile + " failed");
    }

    private static int readVariableLengthInt(InputStream stream) throws IOException {
        // Read bytes as 7-bit chunks and keep reading/or-ing while the 8th bit is set
        int value = 0;
        int b;
        do {
            b = stream.read();
            if (b == -1) {
                throw new EOFException("Unexpected end of stream");
            }
            value <<= 7;
            value |= (b & 0x7F);
        } while ((b & 0x80) != 0);

        return value;
    }

    private static void writeVariableLengthInt(OutputStream stream, int value) throws IOException {
        // Get the number of 7-bit chunks to encode the number with some bit magic
        int numExtraBits = ((Integer.SIZE - Integer.numberOfLeadingZeros(value)) / 7) * 7;
        while (numExtraBits > 0) {
            stream.write(0x80 | ((value >> numExtraBits) & 0x7F));
            numExtraBits -= 7;
        }
        stream.write(value & 0x7F);
    }
}
