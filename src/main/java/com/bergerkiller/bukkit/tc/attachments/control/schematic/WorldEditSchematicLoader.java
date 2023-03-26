package com.bergerkiller.bukkit.tc.attachments.control.schematic;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads and decodes a WorldEdit schematic on a dedicated thread, asynchronously.
 * Caches loaded schematics in memory so that they can be loaded more efficiently
 * in succession. While loading, multiple recipients can receive intermediate
 * blocks being loaded in. For large schematics this allows for a smooth gradual
 * loading of the model instead of a sudden pop-in.<br>
 * <br>
 * Loading can be aborted, and if all recipients have aborted, the loading
 * aborted as well. It's important that readers {@link SchematicReader#abort() abort}
 * even after successfully loading the full schematic, as this signals that the
 * loader can release the schematic from the memory cache.
 */
public class WorldEditSchematicLoader {
    private static final long SCHEMATIC_EXPIRE_TIME_MS = (30*60*1000); // After 30 mins
    private static final long SCHEMATIC_EXPIRE_TASK_INTERVAL = (20*60); // Every minute
    private final TrainCarts plugin;
    private final Path tcSchematicsPath;
    private final Object lock = new Object();
    private final Map<Path, Schematic> loadedSchematicsByFile = new HashMap<>();
    private final Map<String, Schematic> loadedSchematics = new HashMap<>();
    private final Map<String, List<SchematicReader>> pendingSchematics = new LinkedHashMap<>();
    private volatile boolean isShuttingDown = false;
    private volatile LoaderThread loaderThread = null;
    private volatile Task unloaderTask = null;

    public WorldEditSchematicLoader(TrainCarts plugin) {
        this.plugin = plugin;
        this.tcSchematicsPath = plugin.getDataFile("schematics").toPath().toAbsolutePath();

        // If the WorldEdit plugin wasn't even loaded (before us), don't allow anyone
        // to even being reading schematics
        this.isShuttingDown = (Bukkit.getPluginManager().getPlugin("WorldEdit") == null);
    }

    /**
     * Gets whether this loader is enabled at all. Indicates whether the schematic attachment
     * should even be listed as an option.
     *
     * @return True if enabled
     */
    public boolean isEnabled() {
        return !isShuttingDown;
    }

    /**
     * Enables the worldedit schematic loader. Does not enable if WorldEdit isn't installed,
     * or loading of schematics is disabled in TrainCarts configuration.
     */
    public void enable() {
        // If disabled in the config, or WorldEdit isn't loaded/enabled, do not enable at all
        if (!TCConfig.allowSchematicAttachment || !CommonCapabilities.HAS_DISPLAY_ENTITY) {
            isShuttingDown = true;
            unloadAllCurrentSchematics();
            return;
        }
        Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null || !worldEdit.isEnabled()) {
            isShuttingDown = true;
            unloadAllCurrentSchematics();
            return;
        }

        // Create 'schematics' folder on startup
        try {
            Files.createDirectories(tcSchematicsPath);
        } catch (Throwable t) { /* whatever */ }

        // Start the schematic loader thread
        isShuttingDown = false;
        if (loaderThread == null) {
            loaderThread = new LoaderThread();
            loaderThread.start();
        }

        // Periodically remove schematics that aren't being used anymore
        unloaderTask = new Task(plugin) {
            List<Schematic> schematicsToUnload = new ArrayList<>();

            @Override
            public void run() {
                try {
                    long time = System.currentTimeMillis();
                    synchronized (lock) {
                        for (Schematic s : loadedSchematicsByFile.values()) {
                            if (s.canUnload(time)) {
                                schematicsToUnload.add(s);
                            }
                        }
                        for (Schematic s : schematicsToUnload) {
                            System.out.println("Unloading: " + s.schematicFilePath);
                            s.remove(true);
                        }
                    }
                } finally {
                    schematicsToUnload.clear();
                }
            }
        }.start(SCHEMATIC_EXPIRE_TASK_INTERVAL, SCHEMATIC_EXPIRE_TASK_INTERVAL); // Every minute
    }

    /**
     * Shuts down this schematic loader, if it was ever enabled. All previously loaded
     * schematics are purged.
     */
    public void disable() {
        isShuttingDown = true;
        if (loaderThread != null) {
            try {
                loaderThread.join(500);
            } catch (Throwable t1) {
                plugin.log(Level.WARNING, "Schematic loader is still busy. Waiting for 15s...");
                try {
                    loaderThread.join(15000);
                } catch (Throwable t2) {
                    plugin.log(Level.SEVERE, "Schematic loader is stuck! Resuming shutdown anyway...");
                }
            }
            loaderThread = null;
        }
        unloadAllCurrentSchematics();
        Task.stop(unloaderTask);
        unloaderTask = null;
    }

    private void unloadAllCurrentSchematics() {
        synchronized (lock) {
            pendingSchematics.values().forEach(l -> l.forEach(r -> r.state = ABORTED_STATE));
            pendingSchematics.clear();
            loadedSchematicsByFile.values().forEach(s -> {
                s.activeReaders.forEach(r -> r.state = ABORTED_STATE);
                s.activeReaders.clear();
            });
            loadedSchematicsByFile.clear();
            loadedSchematics.clear();
        }
    }

    /**
     * Starts loading and reading a schematic in the background. The returned
     * reader can be used to retrieve the asynchronously loaded blocks and
     * schematic information. Use {@link SchematicReader#abort()} to stop loading
     * new blocks or when the schematic is no longer being used.<br>
     * <br>
     * It's important to call {@link SchematicReader#abort()} when the schematic
     * is no longer used, as that will allow the loader to release the schematic
     * from memory cache after a short time of not being used. Not calling abort
     * will mean these schematics stay loaded in memory indefinitely!
     *
     * @param fileName Schematic file name to read
     * @return Reader for this schematic
     */
    public SchematicReader startReading(String fileName) {
        if (isShuttingDown || fileName.isEmpty()) {
            return new SchematicReader(fileName, ABORTED_STATE);
        }

        synchronized (lock) {
            // Find a schematic that is already (being) loaded
            Schematic loaded = loadedSchematics.get(fileName);
            if (loaded != null && !loaded.wasModifiedSinceLoading()) {
                return loaded.addReader(new SchematicReader(fileName, new ReaderStateBusy(loaded)));
            }

            // Request for loading next. Wake up the thread if it's waiting for tasks
            SchematicReader reader = new SchematicReader(fileName, WAITING_STATE);
            pendingSchematics.computeIfAbsent(fileName, n -> new ArrayList<>()).add(reader);
            lock.notifyAll();
            return reader;
        }
    }

    /**
     * Metadata information about the schematic itself
     */
    public class Schematic {
        public final IntVector3 dimensions;
        public final Path schematicFilePath;
        protected final FileTime lastModified;
        protected long lastModifiedLastChecked;
        protected boolean wasModified;

        protected final BlockData[] blockData;
        protected boolean error = false;
        protected boolean done = false;
        protected long lastAccessed;
        protected final Set<SchematicReader> activeReaders = new HashSet<>();
        protected final List<String> fileNames = new ArrayList<>();

        private Schematic(Path schematicFilePath, FileTime lastModified, IntVector3 dimensions) {
            int numOfBlocks = (dimensions.x * dimensions.y * dimensions.z);
            if (numOfBlocks > 1000000) {
                throw new IllegalArgumentException("Schematic is too big (>1 million blocks): " + dimensions);
            }

            this.dimensions = dimensions;
            this.schematicFilePath = schematicFilePath;
            this.lastModified = lastModified;
            this.wasModified = false;
            this.lastModifiedLastChecked = this.lastAccessed = System.currentTimeMillis();
            this.blockData = new BlockData[numOfBlocks];
        }

        public boolean isDone() {
            return done;
        }

        public boolean hasError() {
            return error;
        }

        /**
         * Periodically verifies that the schematic file loaded into this schematic has not
         * been modified. If it has been, it must be loaded again.
         *
         * @return True if not modified
         */
        protected boolean wasModifiedSinceLoading() {
            if (wasModified) {
                return true;
            }
            long timeNow = System.currentTimeMillis();
            if ((timeNow - lastModifiedLastChecked) > 1000) {
                try {
                    FileTime currTime = Files.getLastModifiedTime(schematicFilePath);
                    if (!currTime.equals(lastModified)) {
                        wasModified = true;
                        return true;
                    } else {
                        lastModifiedLastChecked = timeNow;
                    }
                } catch (Throwable t) {
                    wasModified = true;
                    return true;
                }
            }
            return false;
        }

        protected boolean canUnload(long currentTime) {
            // Unload after a while of nobody using it
            return activeReaders.isEmpty() && (currentTime - this.lastAccessed) > SCHEMATIC_EXPIRE_TIME_MS;
        }

        protected boolean hasActiveReaders() {
            return !activeReaders.isEmpty();
        }

        // Note: must be done under global lock!
        protected SchematicReader addReader(SchematicReader reader) {
            if (reader.state != ABORTED_STATE) {
                reader.state = new ReaderStateBusy(this);
                activeReaders.add(reader);
                lastAccessed = System.currentTimeMillis();
            }
            return reader;
        }

        // Note: must be done under global lock!
        protected void remove(boolean abortReaders) {
            for (String fileName : fileNames) {
                loadedSchematics.remove(fileName, this);
            }
            loadedSchematicsByFile.remove(schematicFilePath, this);
            fileNames.clear();
            if (abortReaders) {
                for (SchematicReader reader : activeReaders) {
                    reader.state = ABORTED_STATE;
                }
                activeReaders.clear();
            }
        }
    }

    /**
     * A single Block in the schematic
     */
    public static class SchematicBlock {
        public final Schematic schematic;
        public final int x, y, z;
        public final BlockData blockData;

        public SchematicBlock(Schematic schematic, int x, int y, int z, BlockData blockData) {
            this.schematic = schematic;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockData = blockData;
        }
    }

    /**
     * Reader for an asynchronously loaded schematic. Newly loaded schematic
     * blocks can be retrieved up until loading is {@link #isDone()} done.
     */
    public class SchematicReader {
        private final String fileName;
        protected volatile ReaderState state;

        private SchematicReader(String fileName, ReaderState state) {
            this.fileName = fileName;
            this.state = state;
        }

        /**
         * Gets the File Name this reader originally tried loading
         *
         * @return Schematic file name
         */
        public String fileName() {
            return fileName;
        }

        /**
         * Aborts the reading of this schematic. {@link #isDone()} will return true
         * and the back-end will stop attempting to load more blocks if this is
         * the last schematic reader.
         */
        public void abort() {
            synchronized (lock) {
                state.abort(this);
                state = ABORTED_STATE;
            }
        }

        /**
         * Gets whether reading the schematic has completed. No more blocks will
         * be read from now on. This also returns true is loading is aborted.
         *
         * @return True if done
         */
        public boolean isDone() {
            return state.isDone();
        }

        /**
         * Gets whether an error occurred trying to load the schematic. Recipient
         * can decide to do something when that happens. Also returns true
         * when loading is {@link #abort() aborted}.
         *
         * @return True if an error had occurred loading the schematic
         */
        public boolean hasError() {
            return state.hasError();
        }

        /**
         * Tries to read the next block of the schematic. If no next block is
         * available yet, or loading is {@link #isDone() done}, returns null.
         * The read block includes information about the parent schematic itself.
         *
         * @return Next block, or <i>null</i> if no next block is available
         */
        public SchematicBlock next() {
            return state.next();
        }
    }

    private static final ReaderState WAITING_STATE = new ReaderState() {
        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean hasError() {
            return false;
        }
    };

    private static final ReaderState ABORTED_STATE = new ReaderState() {
        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean hasError() {
            return true;
        }
    };

    private interface ReaderState {
        boolean isDone();
        boolean hasError();
        default void abort(SchematicReader reader) {}
        default SchematicBlock next() { return null; }
    }

    private class ReaderStateBusy extends BlockIterator implements ReaderState {
        public final Schematic schematic;
        public boolean error;

        public ReaderStateBusy(Schematic schematic) {
            super(schematic.dimensions);
            this.schematic = schematic;
            this.error = false;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean hasError() {
            return error;
        }

        @Override
        public void abort(SchematicReader reader) {
            schematic.activeReaders.remove(reader);
            schematic.lastAccessed = System.currentTimeMillis();
        }

        @Override
        public SchematicBlock next() {
            if (done) {
                return null;
            }

            // Return current block being read, and advance indices as we do
            BlockData data = schematic.blockData[index];
            if (data != null) {
                SchematicBlock block = new SchematicBlock(schematic, x, y, z, data);
                advance();
                return block;
            }

            // Refresh done/error state after finding no more block data to read
            done = schematic.isDone();
            error = schematic.hasError();
            return null;
        }
    }

    private static class BlockIterator {
        public final int x_max, y_max, z_max;
        public int x, y, z;
        public int index;
        public boolean done;

        public BlockIterator(IntVector3 dimensions) {
            this.x_max = dimensions.x;
            this.y_max = dimensions.y;
            this.z_max = dimensions.z;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.index = 0;
            this.done = (x_max == 0 || y_max == 0 || z_max == 0);
        }

        public void advance() {
            ++index;
            if (++x == x_max) {
                x = 0;
                if (++z == z_max) {
                    z = 0;
                    if (++y == y_max) {
                        // Done!
                        done = true;
                    }
                }
            }
        }
    }

    private class LoaderThread extends Thread {

        public LoaderThread() {
            this.setName("TrainCarts schematic loader thread");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                // Under lock wait for new schematics to load
                String inputFileName;
                Path schematicFilePath;
                List<SchematicReader> readers;
                synchronized (lock) {
                    // If shutting down (plugin disabling) abort all other pending tasks
                    if (isShuttingDown) {
                        pendingSchematics.values().forEach(readerList -> readerList.forEach(SchematicReader::abort));
                        pendingSchematics.clear();
                        return;
                    }
                    Iterator<Map.Entry<String, List<SchematicReader>>> iter = pendingSchematics.entrySet().iterator();
                    if (!iter.hasNext()) {
                        try {
                            lock.wait(10000);
                        } catch (InterruptedException e) { /* ignore */ }
                        continue;
                    }

                    // Start loading this. We might find that the filename corresponds with an already
                    // loaded schematic. (e.g. "test" -> "test.schem"/"test.schematic"). Checking for this
                    // require filesystem access so it shouldn't happen inside this synchronized lock.
                    // For that we reason we remove the entry (guaranteed order!) later.
                    Map.Entry<String, List<SchematicReader>> loadEntry = iter.next();
                    inputFileName = loadEntry.getKey();
                    try {
                        schematicFilePath = Paths.get(inputFileName);
                    } catch (InvalidPathException ex) {
                        schematicFilePath = null;
                    }
                    readers = loadEntry.getValue();
                }

                // We only allow the loading of files from these directories
                // We don't want people using this to maliciously load in system files...even if useless
                List<Path> searchPaths = Stream.of(WorldEdit.getInstance().getWorkingDirectoryPath("schematics").toAbsolutePath(),
                                                   tcSchematicsPath)
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());

                // Is a directory specified? If so, extract the directory where the file should be found
                if (schematicFilePath == null) {
                    // Serious problem with the file name
                    searchPaths = Collections.emptyList();
                } else if (schematicFilePath.getParent() != null) {
                    // Subdirectory is specified. Apply it to our search paths.
                    searchPaths = applyToSearchPaths(searchPaths, schematicFilePath.getParent());
                    schematicFilePath = schematicFilePath.getFileName();
                }

                // Search for this file in all of the search paths. Null if not found.
                schematicFilePath = findFile(searchPaths, schematicFilePath);

                // Attempt figuring out the last modified date. If this fails, then there's
                // some I/O error crap going on. Assume file is missing
                FileTime lastModifiedTime = null;
                if (schematicFilePath != null) {
                    try {
                        lastModifiedTime = Files.getLastModifiedTime(schematicFilePath);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "Failed to read last modified date of " + schematicFilePath, t);
                    }
                }

                if (schematicFilePath != null && lastModifiedTime != null) {
                    // If found, start loading
                    this.loadSchematic(inputFileName, schematicFilePath, lastModifiedTime, readers);
                } else {
                    // If not found, abort all readers and continue with the next task
                    synchronized (lock) {
                        pendingSchematics.remove(inputFileName);
                        readers.forEach(SchematicReader::abort);
                    }
                }
            }
        }

        private void loadSchematic(final String inputFileName, final Path schematicFilePath, final FileTime lastModifiedTime, final List<SchematicReader> readers) {
            // Early check: have we already loaded this? If so, forward and store the
            // loaded schematic for the same path as an alias
            // It can also be that the new modified time differs from when that schematic was last loaded. If so,
            // the old one must be removed first. Doesn't matter if readers lose linkage as the schematic
            // was already fully loaded.
            synchronized (lock) {
                final Schematic loadedSchematic = loadedSchematicsByFile.get(schematicFilePath);
                if (loadedSchematic != null) {
                    if (loadedSchematic.lastModified.equals(lastModifiedTime)) {
                        // Same file, let readers read from it
                        pendingSchematics.remove(inputFileName);
                        loadedSchematics.put(inputFileName, loadedSchematic);
                        readers.forEach(loadedSchematic::addReader);
                        return;
                    } else {
                        // Different file. Remove the previous schematic. Allow readers to keep reading.
                        loadedSchematic.remove(false);
                    }
                }
            }

            // Load the file ourselves
            Clipboard clipboard = null;
            Schematic loadedSchematic;
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFilePath.toFile());
                try (ClipboardReader reader = format.getReader(Files.newInputStream(schematicFilePath))) { // Create a reader for the file
                    clipboard = reader.read(); // Read the clipboard from the file
                }
                BlockVector3 dims = clipboard.getDimensions();
                loadedSchematic = new Schematic(schematicFilePath, lastModifiedTime, new IntVector3(dims.getX(), dims.getY(), dims.getZ()));
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load schematic " + schematicFilePath, t);
                clipboard = null;
                loadedSchematic = new Schematic(schematicFilePath, lastModifiedTime, IntVector3.ZERO);
                loadedSchematic.done = true;
                loadedSchematic.error = true;
            }

            // Now we've probed the file, clean up the load task / register the loaded schematic
            // Even if loading fails, then we know for next time not to load it again
            synchronized (lock) {
                loadedSchematic.fileNames.add(inputFileName);
                pendingSchematics.remove(inputFileName);
                loadedSchematics.put(inputFileName, loadedSchematic);
                loadedSchematicsByFile.put(schematicFilePath, loadedSchematic);
                readers.forEach(loadedSchematic::addReader);

                // If no readers remain, abort loading right away
                if (!loadedSchematic.hasActiveReaders()) {
                    loadedSchematic.remove(true);
                    return;
                }
            }

            // If loading the clipboard actually succeeded, now initialize all the blocks
            // as well. The reader gets these asynchronously
            if (clipboard != null) {
                BlockVector3 min = clipboard.getMinimumPoint();
                try {
                    BlockIterator iter = new BlockIterator(loadedSchematic.dimensions);
                    int checkReadersCounter = 0;
                    while (!iter.done) {
                        BlockData blockData = BlockData.fromBukkit(BukkitAdapter.adapt(
                                clipboard.getBlock(min.add(iter.x, iter.y, iter.z))));
                        loadedSchematic.blockData[iter.index] = blockData;
                        iter.advance();

                        // Every 100 blocks check whether there's still actually any readers that
                        // want this information, and whether this thread is requested to abort.
                        if (++checkReadersCounter == 100) {
                            synchronized (lock) {
                                if (isShuttingDown) {
                                    loadedSchematic.error = true;
                                    break;
                                } else if (!loadedSchematic.hasActiveReaders()) {
                                    // Unload it right away! Don't even keep it in the mappings.
                                    loadedSchematic.error = true;
                                    loadedSchematic.remove(true);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load schematic " + schematicFilePath, t);
                    loadedSchematic.error = true;
                }
                loadedSchematic.done = true;
            }
        }

        private Path findFile(final List<Path> searchPaths, final Path fileName) {
            if (searchPaths.isEmpty()) {
                return null;
            }

            // First pass: go by all search paths and identify the file exactly
            for (Path searchPath : searchPaths) {
                Path foundFile = searchPath.resolve(fileName);
                if (Files.isRegularFile(foundFile)) {
                    return foundFile;
                }
            }

            // Second pass: list the contents of all directories and try to match the file name
            String baseNameToFind = findBaseName(fileName);
            for (Path searchPath : searchPaths) {
                try {
                    Optional<Path> result = Files.list(searchPath)
                            .filter(p -> findBaseName(p).equals(baseNameToFind))
                            .filter(Files::isRegularFile)
                            .findFirst();
                    if (result.isPresent()) {
                        return result.get().toAbsolutePath();
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to list schematics in " + searchPath, t);
                }
            }
            return null;
        }

        private List<Path> applyToSearchPaths(final List<Path> searchPaths, final Path subDir) {
            if (subDir.isAbsolute()) {
                // Verify this sub-directory matches one of the search paths, and if so,
                // continue from there.
                boolean isAllowed = false;
                for (Path search : searchPaths) {
                    if (subDir.startsWith(search)) {
                        isAllowed = true;
                        break;
                    }
                }
                if (isAllowed && Files.isDirectory(subDir)) {
                    return Collections.singletonList(subDir);
                } else {
                    // Not allowed. Abort loading.
                    return Collections.emptyList();
                }
            } else {
                // Search all directories for this relative path and keep those that exist
                // Make sure that some cheeky fucker can't bypass our search paths using /../!
                return searchPaths.stream()
                        .map(s -> {
                            Path sub = s.resolve(subDir).toAbsolutePath();
                            return sub.startsWith(s) ? sub : null;
                        })
                        .filter(Objects::nonNull)
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());
            }
        }

        private String findBaseName(Path path) {
            String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
            if (name.endsWith(".schem")) {
                return name.substring(0, name.length() - 6);
            } else if (name.endsWith(".schematic")) {
                return name.substring(0, name.length() - 10);
            } else if (name.endsWith(".")) {
                return name.substring(0, name.length() - 1);
            } else {
                return name;
            }
        }
    }
}
