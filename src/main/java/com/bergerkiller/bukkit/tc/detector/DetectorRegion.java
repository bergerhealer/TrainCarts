package com.bergerkiller.bukkit.tc.detector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class DetectorRegion {
    private static boolean hasChanges = false;
    private static HashMap<UUID, DetectorRegion> regionsById = new HashMap<>();
    private static BlockMap<List<DetectorRegion>> regions = new BlockMap<>();
    private final UUID id;
    private final String world;
    private final Set<IntVector3> coordinates;
    private final Set<MinecartMember<?>> members = new HashSet<>();
    private final List<DetectorListener> listeners = new ArrayList<>(1);

    private DetectorRegion(final UUID uniqueId, final String world, final Set<IntVector3> coordinates) {
        this.world = world;
        this.id = uniqueId;
        this.coordinates = coordinates;
        regionsById.put(this.id, this);
        hasChanges = true;
        for (IntVector3 coord : this.coordinates) {
            BlockLocation block_coord = new BlockLocation(world, coord);
            regions.compute(block_coord, (key, list) -> {
                list = (list == null) ? new ArrayList<>(1) : new ArrayList<>(list);
                list.add(this);
                return list;
            });
        }
    }

    /**
     * Detects all minecarts that are on this region and fires onEnter events.
     * This should be called after the listeners are set up.
     */
    public void detectMinecarts() {
        //load members
        World w = Bukkit.getServer().getWorld(this.world);
        if (w != null) {
            for (IntVector3 coord : this.coordinates) {
                for (MinecartMember<?> mm : RailMemberCache.findAll(BlockUtil.getBlock(w, coord))) {
                    mm.getSignTracker().addToDetectorRegion(this);
                }
            }
        }
    }

    /**
     * Gets all the regions occuping a particular rails block
     * 
     * @param railsBlock
     * @return list of detector regions, empty list if no regions exist
     */
    public static List<DetectorRegion> getRegions(Block at) {
        return LogicUtil.fixNull(regions.get(at), Collections.emptyList());
    }

    public static void detectAllMinecarts() {
        for (DetectorRegion region : regionsById.values()) {
            region.detectMinecarts();
        }
    }

    public static DetectorRegion create(Collection<Block> blocks) {
        if (blocks.isEmpty()) return null;
        World world = null;
        Set<IntVector3> coords = new HashSet<>(blocks.size());
        for (Block b : blocks) {
            if (world == null) {
                world = b.getWorld();
            } else if (world != b.getWorld()) {
                continue;
            }
            coords.add(new IntVector3(b));
        }
        return create(world, coords);
    }

    public static DetectorRegion create(World world, final Set<IntVector3> coordinates) {
        return create(world.getName(), coordinates);
    }

    public static DetectorRegion create(final String world, final Set<IntVector3> coordinates) {
        //first check if this region is not already defined
        for (IntVector3 coord : coordinates) {
            List<DetectorRegion> list = regions.get(world, coord);
            if (list != null) {
                for (DetectorRegion region : list) {
                    if (!region.coordinates.containsAll(coordinates)) continue;
                    if (!coordinates.containsAll(region.coordinates)) continue;
                    return region;
                }
            }
            break;
        }
        return new DetectorRegion(UUID.randomUUID(), world, coordinates);
    }

    public static DetectorRegion getRegion(UUID uniqueId) {
        return regionsById.get(uniqueId);
    }

    public static void init(String filename) {
        regionsById.clear();
        regions.clear();
        new DataReader(filename) {
            public void read(DataInputStream stream) throws IOException {
                int count = stream.readInt();
                int coordcount;
                for (; count > 0; --count) {
                    //get required info
                    UUID id = StreamUtil.readUUID(stream);
                    String world = stream.readUTF();
                    coordcount = stream.readInt();
                    Set<IntVector3> coords = new HashSet<>(coordcount);
                    for (; coordcount > 0; --coordcount) {
                        coords.add(IntVector3.read(stream));
                    }
                    //create
                    new DetectorRegion(id, world, coords);
                }
                if (regionsById.size() == 1) {
                    TrainCarts.plugin.log(Level.INFO, regionsById.size() + " detector rail region loaded covering " + regions.size() + " blocks");
                } else {
                    TrainCarts.plugin.log(Level.INFO, regionsById.size() + " detector rail regions loaded covering " + regions.size() + " blocks");
                }
            }
        }.read();
        hasChanges = false;
    }

    public static void save(boolean autosave, String filename) {
        if (autosave && !hasChanges) {
            return;
        }
        new DataWriter(filename) {
            public void write(DataOutputStream stream) throws IOException {
                stream.writeInt(regionsById.size());
                for (DetectorRegion region : regionsById.values()) {
                    StreamUtil.writeUUID(stream, region.id);
                    stream.writeUTF(region.world);
                    stream.writeInt(region.coordinates.size());
                    for (IntVector3 coord : region.coordinates) {
                        coord.write(stream);
                    }
                }
            }
        }.write();
        hasChanges = false;
    }

    public String getWorldName() {
        return this.world;
    }

    public Set<IntVector3> getCoordinates() {
        return this.coordinates;
    }

    private void cleanUnloadedMembers() {
        Iterator<MinecartMember<?>> iter = this.members.iterator();
        while (iter.hasNext()) {
            MinecartMember<?> mm = iter.next();
            if (mm.isUnloaded()) {
                iter.remove();

                // Attempt to retrieve position info
                Block pos = null;
                if (mm.getEntity() != null) {
                    pos = mm.getEntity().getLocation().getBlock();
                } else {
                    pos = mm.getRailTracker().getBlock();
                    if (pos == null) {
                        pos = mm.getRailTracker().getLastBlock();
                    }
                }
                String posStr = "Unknown";
                if (pos != null) {
                    posStr = "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
                }
                TrainCarts.plugin.getLogger().warning("[Detector] Purged unloaded Minecart at " + posStr);
            }
        }
    }

    public Set<MinecartMember<?>> getMembers() {
        return this.members;
    }

    public Set<MinecartGroup> getGroups() {
        Set<MinecartGroup> rval = new HashSet<>();
        this.cleanUnloadedMembers();
        for (MinecartMember<?> mm : this.members) {
            if (mm.getGroup() == null) continue;
            rval.add(mm.getGroup());
        }
        return rval;
    }

    public UUID getUniqueId() {
        return this.id;
    }

    public void register(DetectorListener listener) {
        this.listeners.add(listener);
        listener.onRegister(this);
        for (MinecartMember<?> mm : this.members) {
            listener.onEnter(mm);
        }
        for (MinecartGroup group : this.getGroups()) {
            listener.onEnter(group);
        }
    }

    public void unregister(DetectorListener listener) {
        this.listeners.remove(listener);
        for (MinecartMember<?> mm : this.members) {
            listener.onLeave(mm);
        }
        for (MinecartGroup group : this.getGroups()) {
            listener.onLeave(group);
        }
        listener.onUnregister(this);
    }

    public boolean isRegistered() {
        return !this.listeners.isEmpty();
    }

    private void onLeave(MinecartMember<?> mm) {
        for (DetectorListener listener : getListeners()) {
            listener.onLeave(mm);
        }
        if (mm.isUnloaded()) {
            return;
        }
        this.cleanUnloadedMembers();
        final MinecartGroup group = mm.getGroup();
        for (MinecartMember<?> ex : this.members) {
            if (ex != mm && ex.getGroup() == group) {
                return;
            }
        }
        for (DetectorListener listener : getListeners()) {
            listener.onLeave(group);
        }
    }

    private void onEnter(MinecartMember<?> mm) {
        for (DetectorListener listener : getListeners()) {
            listener.onEnter(mm);
        }
        if (mm.isUnloaded()) {
            return;
        }
        this.cleanUnloadedMembers();
        final MinecartGroup group = mm.getGroup();
        for (MinecartMember<?> ex : this.members) {
            if (ex != mm && ex.getGroup() == group) {
                return;
            }
        }
        for (DetectorListener listener : getListeners()) {
            listener.onEnter(group);
        }
    }

    public void unload(MinecartGroup group) {
        if (this.members.removeAll(group)) {
            for (DetectorListener listener : getListeners()) {
                listener.onUnload(group);
            }
        }
    }

    public void remove(MinecartMember<?> mm) {
        if (this.members.remove(mm)) {
            this.onLeave(mm);
        }
    }

    public boolean add(MinecartMember<?> mm) {
        if (this.members.add(mm)) {
            this.onEnter(mm);
            return true;
        } else {
            return false;
        }
    }

    private List<DetectorListener> getListeners() {
        return new ArrayList<DetectorListener>(this.listeners);
    }

    public void update(MinecartMember<?> member) {
        for (DetectorListener list : this.listeners) {
            list.onUpdate(member);
        }
    }

    public void update(MinecartGroup group) {
        for (DetectorListener list : this.listeners) {
            list.onUpdate(group);
        }
    }

    public void remove() {
        this.cleanUnloadedMembers();
        Iterator<MinecartMember<?>> iter = this.members.iterator();
        while (iter.hasNext()) {
            this.onLeave(iter.next());
            iter.remove();
        }
        regionsById.remove(this.id);
        hasChanges = true;
        for (IntVector3 coord : this.coordinates) {
            BlockLocation block_coord = new BlockLocation(this.world, coord);
            regions.computeIfPresent(block_coord, (key, list) -> {
                if (list.size() == 1 && list.get(0) == DetectorRegion.this) {
                    return null;
                } else {
                    list = new ArrayList<DetectorRegion>(list);
                    list.remove(DetectorRegion.this);
                    return list;
                }
            });
        }
    }
}
