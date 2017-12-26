package com.bergerkiller.bukkit.tc.signactions.spawner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.utils.ChunkArea;

public class SpawnSign {
    private final BlockLocation location;
    private World world_last = null;
    private boolean active = true;
    private long interval = 0L;
    private long nextSpawnTime = System.currentTimeMillis();
    private double spawnForce = 0.0;
    private SpawnableGroup spawnableGroup = new SpawnableGroup();
    private LongHashSet chunks = new LongHashSet();

    // This is used to track and load chunks in preparation of a spawn
    private long[] chunks_array = null;
    private int chunks_last_idx = 0;

    public SpawnSign(BlockLocation location) {
        this.location = location;
        this.addChunk(MathUtil.toChunk(location.x), MathUtil.toChunk(location.z));
    }

    /**
     * Loads spawn sign information by parsing the information stored on a spawn sign
     * 
     * @param signEvent of the spawn sign to parse
     */
    public void load(SignActionEvent signEvent) {
        this.active = signEvent.isPowered();
        this.interval = getSpawnTime(signEvent);
        this.spawnForce = getSpawnForce(signEvent);
        this.spawnableGroup = SpawnableGroup.parse(signEvent.getLine(2) + signEvent.getLine(3));
    }

    /**
     * Gets the location of the spawn sign
     * 
     * @return spawn sign location
     */
    public BlockLocation getLocation() {
        return this.location;
    }

    /**
     * Gets whether this spawn sign periodically spawns
     * 
     * @return True if an interval was set
     */
    public boolean hasInterval() {
        return this.interval > 0L;
    }

    /**
     * Gets the interval to spawn at, measured in milliseconds.
     * A value of 0 indicates no interval is used.
     * 
     * @return spawn interval
     */
    public long getInterval() {
        return this.interval;
    }

    /**
     * Gets the duration in milliseconds until this spawn sign should spawn next.
     * Returns 0 when the spawn must occur right now.
     * Returns MAX_VALUE when this spawn sign is inactive.
     * 
     * @param currentTime from which to measure remaining time
     * @return remaining time in milliseconds
     */
    public long getRemaining(long currentTime) {
        if (!this.isActive() || !this.hasInterval()) {
            return Long.MAX_VALUE;
        }
        long time = (this.nextSpawnTime - currentTime);
        return time < 0 ? 0 : time;
    }

    /**
     * Gets the timestamp ( currentTimeMillis() ) for when the next spawn occurs.
     * Returns MAX_VALUE when this spawn sign is inactive.
     * 
     * @return timestamp of next spawn
     */
    public long getNextSpawnTime() {
        if (!this.isActive() || !this.hasInterval()) return Long.MAX_VALUE;
        return this.nextSpawnTime;
    }

    /**
     * Checks whether this spawn sign is powered by redstone / active
     * 
     * @return True if active
     */
    public boolean isActive() {
        return this.active;
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
        return this.spawnableGroup;
    }

    /**
     * Resets the spawn timer, causing this sign to spawn when the interval elapses.
     */
    public void resetSpawnTime() {
        this.nextSpawnTime = System.currentTimeMillis() + this.interval;
        TrainCarts.plugin.getSpawnSignManager().notifyChanged();
    }

    /**
     * Resets the spawn timer right after an interval spawn was performed
     */
    public void nextSpawnTime() {
        long currentTime = System.currentTimeMillis();
        this.nextSpawnTime += this.interval;
        if (this.getRemaining(currentTime) == 0) {
            this.nextSpawnTime = currentTime + this.interval;
        }
        TrainCarts.plugin.getSpawnSignManager().notifyChanged();
    }

    /**
     * Gets the (cached) world information of the sign
     * 
     * @return world
     */
    public World getWorld() {
        if (this.world_last == null) {
            this.world_last = this.location.getWorld();
        }
        return this.world_last;
    }

    /**
     * Queues additional chunks for loading asynchronously in preparation for a spawn
     * 
     * @param percent of chunks that should be loaded
     */
    public void loadChunksAsync(double percent) {
        if (this.chunks_array == null) {
            this.chunks_array = this.chunks.toArray();
        }
        if (this.chunks_last_idx >= this.chunks_array.length) {
            return;
        }
        if (getWorld() == null) {
            this.chunks_last_idx = this.chunks_array.length;
            return;
        }

        percent = MathUtil.clamp(percent, 0.0, 1.0);
        int endIdxExcl = ((int) ((double) this.chunks_array.length * percent));
        while (this.chunks_last_idx < endIdxExcl) {
            long chunkComp = this.chunks_array[this.chunks_last_idx++];
            int chunkX = MathUtil.longHashMsw(chunkComp);
            int chunkZ = MathUtil.longHashLsw(chunkComp);
            WorldUtil.getChunkAsync(this.world_last, chunkX, chunkZ, ChunkArea.DUMMY_RUNNABLE);
        }
        if (this.chunks_last_idx >= this.chunks_array.length) {
            this.world_last = null;
        }
    }

    /**
     * Called after the spawn sign goes back to a longer-term slumber
     */
    public void loadChunksAsyncReset() {
        this.chunks_last_idx = 0;
    }

    /**
     * Checks whether this spawn sign is nearby a particular chunk
     * 
     * @param x - coordinate of the chunk to probe
     * @param z - coordinate of the chunk to probe
     * @return True if nearby
     */
    public boolean isNearChunk(int x, int z) {
        return this.chunks.contains(x, z);
    }

    private void addChunk(int x, int z) {
        this.chunks_array = null; // reset
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                this.chunks.add(x + dx, z + dz);
            }
        }
    }

    /**
     * Removes this spawn sign from the spawn sign manager
     */
    public void remove() {
        TrainCarts.plugin.getSpawnSignManager().remove(this);
    }

    /**
     * Attempts to spawn a train from this sign right this instant
     */
    public void spawn() {
        Block signBlock = this.location.getBlock();
        if (signBlock != null) {
            SignActionEvent event = new SignActionEvent(signBlock);
            if (isValid(event)) {
                this.load(event);
                this.spawn(event);
            } else {
                this.remove();
            }
        }
    }

    /**
     * Attempts to spawn a train from this sign, using the sign action information, right this instant
     * 
     * @param sign event info
     */
    public void spawn(SignActionEvent sign) {
        try (Timings t = TCTimings.SIGNACTION_SPAWN.start()) {
            // Ensure all chunks we may need are loaded (getChunk())
            {
                World world = sign.getWorld();
                LongIterator chunkIter = this.chunks.longIterator();
                while (chunkIter.hasNext()) {
                    long chunkComp = chunkIter.next();
                    world.getChunkAt(MathUtil.longHashMsw(chunkComp), MathUtil.longHashLsw(chunkComp));
                }
            }

            // Perform the spawn
            List<Location> locs = SignActionSpawn.spawn(this, sign);
            if (locs != null && !locs.isEmpty()) {
                // Refresh the chunk coordinates we keep loaded
                HashSet<IntVector2> coords = new HashSet<IntVector2>(this.chunks.size());
                this.chunks.clear();
                this.chunks_array = null;
                for (Location loc : locs) {
                    coords.add(new IntVector2(MathUtil.toChunk(loc.getX()), MathUtil.toChunk(loc.getZ())));
                }
                for (IntVector2 coord : coords) {
                    this.addChunk(coord.x, coord.z);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        str.append("pos=").append(this.location.toString());
        str.append(", interval=").append(this.getInterval());
        str.append(", remaining=").append(this.getRemaining(System.currentTimeMillis()));
        str.append(", spawnForce=").append(this.getSpawnForce());
        str.append(", spawnable=").append(this.spawnableGroup.toString());
        str.append("}");
        return str.toString();
    }

    /*
     * Serialization: rules.
     * interval = 0 -> no autospawn, anything else -> autospawn
     * remaining time of MAX_VALUE: sign is inactive (not powered)
     * other remaining time: read/write offset from current timestamp
     */

    public void write(DataOutputStream stream) throws IOException {
        this.location.getCoordinates().write(stream);
        stream.writeUTF(this.location.world);
        stream.writeLong(this.interval);

        if (this.isActive()) {
            long remaining = this.nextSpawnTime - System.currentTimeMillis();
            if (remaining < 0) {
                remaining = 0;
            }
            stream.writeLong(remaining);
        } else {
            stream.writeLong(Long.MAX_VALUE);
        }
    }

    public static SpawnSign read(DataInputStream stream) throws IOException {
        IntVector3 coord = IntVector3.read(stream);
        String world = stream.readUTF();
        SpawnSign sign = new SpawnSign(new BlockLocation(world, coord));
        sign.interval = stream.readLong();
        long remaining = stream.readLong();
        if (remaining == Long.MAX_VALUE) {
            sign.nextSpawnTime = System.currentTimeMillis();
            sign.active = false;
        } else {
            sign.nextSpawnTime = System.currentTimeMillis() + remaining;
            sign.active = true;
        }
        return sign;
    }

    /* ================================================================ */

    private static String[] getArgs(SignActionEvent event) {
        final String line = event.getLine(1).toLowerCase(Locale.ENGLISH);
        final int idx = line.indexOf(' ');
        if (idx == -1) {
            return StringUtil.EMPTY_ARRAY;
        }
        return line.substring(idx + 1).split(" ");
    }

    public static double getSpawnForce(SignActionEvent event) {
        String[] bits = getArgs(event);
        if (bits.length >= 2) {
            // Choose
            if (!bits[0].contains(":")) {
                return ParseUtil.parseDouble(bits[0], 0.0);
            } else {
                return ParseUtil.parseDouble(bits[1], 0.0);
            }
        } else if (bits.length >= 1 && !bits[0].contains(":")) {
            return ParseUtil.parseDouble(bits[0], 0.0);
        }
        return 0.0;
    }

    public static long getSpawnTime(SignActionEvent event) {
        String[] bits = getArgs(event);
        if (bits.length >= 2) {
            // Choose
            if (bits[1].contains(":")) {
                return ParseUtil.parseTime(bits[1]);
            } else {
                return ParseUtil.parseTime(bits[0]);
            }
        } else if (bits.length >= 1 && bits[0].contains(":")) {
            return ParseUtil.parseTime(bits[0]);
        }
        return 0;
    }

    public static boolean isValid(SignActionEvent event) {
        return event != null && event.getMode() != SignActionMode.NONE && event.isType("spawn");
    }

}
