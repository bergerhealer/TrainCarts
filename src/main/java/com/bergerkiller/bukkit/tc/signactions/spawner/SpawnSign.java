package com.bergerkiller.bukkit.tc.signactions.spawner;

import java.util.Locale;
import java.util.UUID;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import org.bukkit.block.Sign;

public class SpawnSign {
    private final TrainCarts plugin;
    private final OfflineSignStore store;
    private final OfflineBlock location;
    private final boolean frontText;
    private SpawnSignManager.SpawnSignMetadata state;
    private int ticksUntilFreeing = 0;
    private double spawnForce = 0.0;
    private String spawnFormat;
    private LongHashMap<SignSpawnChunk> chunks = new LongHashMap<SignSpawnChunk>();
    private int num_chunks_loaded = 0;

    SpawnSign(TrainCarts plugin, OfflineSignStore store, OfflineSign sign, SpawnSignManager.SpawnSignMetadata metadata) {
        this.plugin = plugin;
        this.store = store;
        this.location = sign.getBlock();
        this.frontText = sign.isFrontText();
        this.updateState(sign, metadata);

        // Add the 5x5 area of chunks around the sign as the initial chunks to load
        int center_cx = MathUtil.toChunk(location.getX());
        int center_cz = MathUtil.toChunk(location.getZ());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = center_cx + dx;
                int cz = center_cz + dz;
                this.chunks.put(cx, cz, createSpawnChunk(cx, cz));
            }
        }
    }

    /**
     * Gets the TrainCarts plugin instance managing this spawn sign
     *
     * @return plugin instance
     */
    public TrainCarts getPlugin() {
        return this.plugin;
    }

    void updateState(OfflineSign sign, SpawnSignManager.SpawnSignMetadata metadata) {
        this.spawnForce = SpawnOptions.fromOfflineSign(sign).launchVelocity;
        this.spawnFormat = sign.getLine(2) + sign.getLine(3);
        this.state = metadata;
    }

    void updateUsingEvent(SignActionEvent event) {
        // Make sure stuff is up to date
        this.store.verifySign(event.getSign(), frontText, SpawnSignManager.SpawnSignMetadata.class);

        // Update active (redstone) state in case it changed without us knowing
        boolean active = event.isPowered();
        if (active != this.state.active) {
            this.store.putIfPresent(location, frontText, this.state.setActive(active));
        }
    }

    /**
     * Gets the location of the spawn sign
     * 
     * @return spawn sign location
     */
    public OfflineBlock getLocation() {
        return this.location;
    }

    /**
     * Gets whether this spawn sign was created on the front side of the sign (true),
     * or back side (false)
     *
     * @return True if this is the front sign side
     */
    public boolean isFrontText() {
        return this.frontText;
    }

    /**
     * Gets whether this spawn sign periodically spawns
     * 
     * @return True if an interval was set
     */
    public boolean hasInterval() {
        return this.state.intervalMillis > 0L;
    }

    /**
     * Gets the interval to spawn at, measured in milliseconds.
     * A value of 0 indicates no interval is used.
     * 
     * @return spawn interval
     */
    public long getInterval() {
        return this.state.intervalMillis;
    }

    /**
     * Gets the duration in milliseconds until this spawn sign should spawn next.
     * Returns 0 when the spawn must occur right now.
     * Returns MAX_VALUE when this spawn sign is inactive.
     *
     * @param previousTime Previous spawn 'wave'. Is used to detect skipped
     *                     spawn intervals during downtime.
     * @param currentTime Time from which to measure remaining time
     * @return remaining time in milliseconds
     */
    public long getRemaining(long previousTime, long currentTime) {
        if (!this.isActive() || !this.hasInterval()) {
            return Long.MAX_VALUE;
        }

        // Get next spawn time by comparing with the previousTime value
        long numIntervalsSkipped = (currentTime - state.autoSpawnStartTime) / state.intervalMillis;
        long nextSpawnTimestamp = state.autoSpawnStartTime + (numIntervalsSkipped * state.intervalMillis);
        if (nextSpawnTimestamp <= previousTime) {
            nextSpawnTimestamp += state.intervalMillis;
        }

        // If current time is beyond the next spawn timestamp, remaining is 0
        // Then the sign should spawn right now!
        if (currentTime >= nextSpawnTimestamp) {
            return 0;
        } else {
            return (nextSpawnTimestamp - currentTime);
        }
    }

    /**
     * Checks whether this spawn sign is powered by redstone / active
     * 
     * @return True if active
     */
    public boolean isActive() {
        return this.state.active;
    }

    /**
     * Gets the speed at which the train is launched after spawning
     * 
     * @return spawn force
     */
    public double getSpawnForce() {
        return this.spawnForce;
    }

    /**
     * Gets the spawnable group that is spawned by this spawn sign
     * 
     * @return spawnable group
     */
    public SpawnableGroup getSpawnableGroup() {
        return SpawnableGroup.parse(this.getPlugin(), this.spawnFormat);
    }

    /**
     * Resets the spawn timer, causing this sign to spawn when the interval elapses
     * after the current system time.
     */
    public void resetSpawnTime() {
        if (store != null) {
            store.putIfPresent(location, frontText, state.setAutoSpawnStart(
                    System.currentTimeMillis() + state.intervalMillis));
        }
    }

    /**
     * Gets the (cached) world information of the sign
     * 
     * @return world
     */
    public World getWorld() {
        return this.location.getLoadedWorld();
    }

    /**
     * Queues additional chunks for loading asynchronously in preparation for a spawn
     * 
     * @param percent of chunks that should be loaded
     */
    public void loadChunksAsync(double percent) {
        if (getWorld() == null) {
            this.num_chunks_loaded = this.chunks.size();
            return;
        }

        percent = MathUtil.clamp(percent, 0.0, 1.0);
        int num_chunks_loaded_goal = ((int) ((double) this.chunks.size() * percent));
        for (SignSpawnChunk chunk : this.chunks.getValues()) {
            if (this.num_chunks_loaded >= num_chunks_loaded_goal) {
                break;
            } else if (!chunk.chunk.isNone()) {
                chunk.loadAsync();
                this.num_chunks_loaded++;
            }
        }
    }

    /**
     * Called after the spawn sign goes back to a longer-term slumber
     */
    public void loadChunksAsyncReset() {
        for (SignSpawnChunk chunk : this.chunks.getValues()) {
            chunk.close();
        }
        this.num_chunks_loaded = 0;
    }

    /**
     * Calls {@link #loadChunksAsyncReset()} automatically when the chunks can be unloaded
     * a few ticks after spawning
     */
    public void loadChunksAsyncResetAuto() {
        if (this.ticksUntilFreeing > 0 && --this.ticksUntilFreeing == 0) {
            loadChunksAsyncReset();
        }
    }

    /**
     * Removes this spawn sign from the spawn sign manager
     */
    public void remove() {
        if (this.store != null) {
            this.store.remove(location, frontText, SpawnSignManager.SpawnSignMetadata.class);
        }
    }

    /**
     * Attempts to spawn a train from this sign right this instant.
     * This SpawnSign must be tracked in the Offline Sign Store to work properly.
     */
    public void spawn() {
        Block signBlock = this.location.getLoadedBlock();
        if (signBlock != null) {
            Sign bsign = BlockUtil.getSign(signBlock);
            if (bsign == null) {
                store.removeAll(signBlock);
                return; // removed
            }

            // Before proceeding, verify the sign's contents again. May have changed!
            if (store.verifySign(bsign, frontText, SpawnSignManager.SpawnSignMetadata.class) == null) {
                return; // removed
            }

            SignActionEvent event = new SignActionEvent(RailLookup.TrackedSign.forRealSign(bsign, frontText, null));
            if (isValid(event)) {
                this.updateUsingEvent(event);
                this.spawn(event);
            } else {
                this.remove();
            }
        } else {
            this.loadChunksAsyncReset();
        }
    }

    /**
     * Attempts to spawn a train from this sign, using the sign action information, right this instant
     * 
     * @param sign event info
     */
    public void spawn(SignActionEvent sign) {
        /* Timings: spawn  (Sign Action, Spawner) */
        {
            // Before proceeding, verify the sign's contents again. May have changed!
            // Store can be null when a SpawnSign is created that is not tracked in the OfflineStore
            if (store != null && store.verifySign(sign.getSign(), frontText, SpawnSignManager.SpawnSignMetadata.class) == null) {
                return; // removed
            }

            // Keep the area loaded for 2 more ticks, allowing the train to activate signs
            this.ticksUntilFreeing = 2;

            // Ensure all chunks we may need are loaded (getChunk())
            for (SignSpawnChunk chunk : this.chunks.getValues()) {
                chunk.loadSync();
            }

            // Perform the spawn
            SpawnableGroup.SpawnLocationList locs = SignActionSpawn.spawn(this, sign);
            if (locs != null && !locs.locations.isEmpty()) {
                // Compute a new mapping of all the chunks that must be loaded at these positions
                // The coordinates might change as a result of switchers / change on the sign
                LongHashMap<SignSpawnChunk> new_chunks = new LongHashMap<SignSpawnChunk>(this.chunks.size());
                for (SpawnableMember.SpawnLocation loc : locs.locations) {
                    int x = MathUtil.toChunk(loc.location.getX());
                    int z = MathUtil.toChunk(loc.location.getZ());
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int cx = x + dx;
                            int cz = z + dz;
                            long key = MathUtil.longHashToLong(cx, cz);
                            if (!new_chunks.contains(key)) {
                                SignSpawnChunk chunk = this.chunks.remove(key);
                                if (chunk == null) {
                                    chunk = createSpawnChunk(cx, cz);
                                    chunk.loadSync();
                                }
                                new_chunks.put(key, chunk);
                            }
                        }
                    }
                }

                // The chunks map now has chunks that are no longer important, free them
                for (SignSpawnChunk originalChunk : this.chunks.getValues()) {
                    originalChunk.close();
                }

                // And now assign the chunks
                this.chunks = new_chunks;
                this.num_chunks_loaded = this.chunks.size();
            }
        }
    }

    private SignSpawnChunk createSpawnChunk(int cx, int cz) {
        if (this.store == null) {
            return new SignSpawnChunkSync(this.location.getWorldUUID(), cx, cz);
        } else {
            return new SignSpawnChunk(this.location.getWorldUUID(), cx, cz);
        }
    }

    @Override
    public String toString() {
        long currentTime = System.currentTimeMillis();

        StringBuilder str = new StringBuilder();
        str.append("{");
        str.append("pos=").append(this.location.toString());
        str.append(", interval=").append(this.getInterval());
        str.append(", remaining=").append(this.getRemaining(currentTime, currentTime));
        str.append(", spawnForce=").append(this.getSpawnForce());
        str.append(", spawnable=").append(this.spawnFormat);
        str.append("}");
        return str.toString();
    }

    public static double getSpawnForce(SignActionEvent event) {
        return SpawnOptions.fromEvent(event).launchVelocity;
    }

    public static long getSpawnTime(SignActionEvent event) {
        return SpawnOptions.fromEvent(event).autoSpawnInterval;
    }

    public static boolean isValid(SignActionEvent event) {
        return event != null && event.getMode() != SignActionMode.NONE && event.isType("spawn");
    }

    public static class SpawnOptions {
        public final double launchVelocity;
        public final long autoSpawnInterval;

        private SpawnOptions(String secondSignLine) {
            String[] args;
            final String line = secondSignLine.toLowerCase(Locale.ENGLISH);
            final int idx = line.indexOf(' ');
            if (idx == -1) {
                args = StringUtil.EMPTY_ARRAY;
            } else {
                args = line.substring(idx + 1).split(" ");
            }

            this.launchVelocity = parseVelocity(args);
            this.autoSpawnInterval = getAutoSpawnInterval(args);
        }

        public static SpawnOptions fromEvent(SignActionEvent event) {
            return new SpawnOptions(event.getLine(1));
        }

        public static SpawnOptions fromOfflineSign(OfflineSign sign) {
            return new SpawnOptions(sign.getLine(1));
        }

        private static double parseVelocity(String[] args) {
            if (args.length >= 2) {
                // Choose
                if (!args[0].contains(":")) {
                    return Util.parseVelocity(args[0], 0.0);
                } else {
                    return Util.parseVelocity(args[1], 0.0);
                }
            } else if (args.length >= 1 && !args[0].contains(":")) {
                return Util.parseVelocity(args[0], 0.0);
            }
            return 0.0;
        }

        private static long getAutoSpawnInterval(String[] args) {
            if (args.length >= 2) {
                // Choose
                if (args[1].contains(":")) {
                    return ParseUtil.parseTime(args[1]);
                } else {
                    return ParseUtil.parseTime(args[0]);
                }
            } else if (args.length >= 1 && args[0].contains(":")) {
                return ParseUtil.parseTime(args[0]);
            }
            return 0;
        }
    }

    private static class SignSpawnChunk {
        private final ForcedChunk chunk;
        public final UUID worldUUID;
        public final int x;
        public final int z;

        public SignSpawnChunk(UUID worldUUID, int x, int z) {
            this.chunk = ForcedChunk.none();
            this.worldUUID = worldUUID;
            this.x = x;
            this.z = z;
        }

        public void loadSync() {
            World world = Bukkit.getWorld(this.worldUUID);
            if (world == null) {
                return;
            }
            if (this.chunk.isNone()) {
                this.chunk.move(ChunkUtil.forceChunkLoaded(world, x, z));
            }
            this.chunk.getChunk();
        }

        public void loadAsync() {
            if (this.chunk != null) {
                World world = Bukkit.getWorld(this.worldUUID);
                if (world != null) {
                    this.chunk.move(ChunkUtil.forceChunkLoaded(world, x, z));
                }
            }
        }

        public void close() {
            this.chunk.close();
        }
    }

    /**
     * Spawn chunk that only loads sync, and doesn't keep the area loaded
     * afterwards. Used for single-activation redstone-triggered signs.
     */
    private static class SignSpawnChunkSync extends SignSpawnChunk {

        public SignSpawnChunkSync(UUID worldUUID, int x, int z) {
            super(worldUUID, x, z);
        }

        @Override
        public void loadSync() {
            World world = Bukkit.getWorld(this.worldUUID);
            if (world != null) {
                world.getChunkAt(this.x, this.z);
            }
        }

        @Override
        public void loadAsync() {
        }
    }
}
