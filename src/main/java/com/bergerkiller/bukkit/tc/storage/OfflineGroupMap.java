package com.bergerkiller.bukkit.tc.storage;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import org.bukkit.Chunk;

import java.util.*;

/**
 * Maps all the Offline Groups to chunk coordinates, allowing faster chunk access for restoring trains
 */
public class OfflineGroupMap implements Iterable<OfflineGroup> {
    private Set<OfflineGroup> groups = new HashSet<>();
    private LongHashMap<HashSet<OfflineGroup>> groupmap = new LongHashMap<>();

    @Override
    public Iterator<OfflineGroup> iterator() {
        return this.groups.iterator();
    }

    public int size() {
        return this.groups.size();
    }

    public boolean isEmpty() {
        return this.groups.isEmpty();
    }

    public void add(OfflineGroup group) {
        this.groups.add(group);
        for (long chunk : group.chunks) {
            if (!group.loadedChunks.contains(chunk)) {
                getOrCreateChunk(chunk).add(group);
            }
        }
    }

    public void remove(OfflineGroup group) {
        this.groups.remove(group);
        for (long chunk : group.chunks) {
            Set<OfflineGroup> groups = getOrCreateChunk(chunk);
            if (groups != null) {
                groups.remove(group);
                if (groups.isEmpty()) {
                    this.groupmap.remove(chunk);
                }
            }
        }
    }

    public boolean removeCart(UUID memberUUID) {
        for (OfflineGroup group : groups) {
            for (OfflineMember member : group.members) {
                if (member.entityUID.equals(memberUUID)) {
                    // Undo previous registration
                    remove(group);
                    // Remove this member from the group
                    {
                        ArrayList<OfflineMember> members = new ArrayList<>();
                        for (OfflineMember m : group.members) {
                            if (!m.entityUID.equals(memberUUID)) {
                                members.add(m);
                            }
                        }
                        group.members = members.toArray(new OfflineMember[0]);
                    }
                    group.genChunks();
                    if (group.members.length > 0) {
                        add(group);
                    }
                    // Finished
                    return true;
                }
            }
        }
        return false;
    }

    public final OfflineGroup remove(String groupName) {
        for (OfflineGroup group : groups) {
            if (group.name.equals(groupName)) {
                remove(group);
                return group;
            }
        }
        return null;
    }

    public Set<OfflineGroup> removeFromChunk(Chunk chunk) {
        return removeFromChunk(chunk.getX(), chunk.getZ());
    }

    public Set<OfflineGroup> removeFromChunk(int x, int z) {
        return removeFromChunk(MathUtil.longHashToLong(x, z));
    }

    public Set<OfflineGroup> removeFromChunk(long chunk) {
        Set<OfflineGroup> rval = this.groupmap.remove(chunk);
        if (rval != null) {
            for (OfflineGroup group : rval) {
                group.loadedChunks.add(chunk);
            }
        }
        return rval;
    }

    public Set<OfflineGroup> getFromChunk(Chunk chunk) {
        return this.getFromChunk(chunk.getX(), chunk.getZ());
    }

    public Set<OfflineGroup> getFromChunk(int x, int z) {
        return this.getFromChunk(MathUtil.longHashToLong(x, z));
    }

    public Set<OfflineGroup> getFromChunk(long chunk) {
        return this.groupmap.get(chunk);
    }

    public Set<OfflineGroup> getOrCreateChunk(long chunk) {
        HashSet<OfflineGroup> rval = this.groupmap.get(chunk);
        if (rval == null) {
            rval = new HashSet<>(1);
            this.groupmap.put(chunk, rval);
        }
        return rval;
    }

    public Set<OfflineGroup> values() {
        return this.groups;
    }

}
