package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.StreamUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Legacy group data format, which stored very limited data per train.
 * Includes static methods to read this legacy format.
 */
public class OfflineGroupFileFormatLegacy {

    public static void writeAllWorlds(DataOutputStream stream, List<OfflineGroupWorld> worlds) throws IOException {
        // Write it in legacy format
        stream.writeInt(worlds.size());
        for (OfflineGroupWorld world : worlds) {
            StreamUtil.writeUUID(stream, world.getWorld().getUniqueId());
            stream.writeInt(world.totalGroupCount());
            for (OfflineGroup wg : world) {
                writeGroup(stream, wg);
            }
        }
    }

    public static List<OfflineGroupWorld> readAllWorlds(DataInputStream stream) throws IOException {
        final int worldCount = stream.readInt();
        final List<OfflineGroupWorld> worlds = new ArrayList<>(worldCount);
        for (int worldIdx = 0; worldIdx < worldCount; worldIdx++) {
            worlds.add(readWorld(stream));
        }
        return Collections.unmodifiableList(worlds);
    }

    public static OfflineGroupWorld readWorld(DataInputStream stream) throws IOException {
        final UUID worldUUID = StreamUtil.readUUID(stream);
        final int groupCount = stream.readInt();
        return readWorld(stream, worldUUID, groupCount);
    }

    public static OfflineGroupWorld readWorld(DataInputStream stream, UUID worldUUID, int groupCount) throws IOException {
        OfflineWorld world = OfflineWorld.of(worldUUID);

        // Read all the groups contained
        List<OfflineGroup> groups = new ArrayList<>(groupCount);
        for (int groupIdx = 0; groupIdx < groupCount; groupIdx++) {
            groups.add(readGroup(stream, world));
        }

        // Done with world
        return OfflineGroupWorld.snapshot(world, groups);
    }

    /**
     * Writes the information of a single OfflineGroup
     *
     * @param stream Stream to write to
     * @param group OfflineGroup
     * @throws IOException
     */
    public static void writeGroup(DataOutputStream stream, OfflineGroup group) throws IOException {
        stream.writeInt(group.members.length);
        for (OfflineMember member : group.members) {
            writeMember(stream, member);
        }
        stream.writeUTF(group.name);
    }

    /**
     * Writes the information of a single OfflineMember
     *
     * @param stream Stream to write to
     * @param member OfflineMember
     * @throws IOException
     */
    public static void writeMember(DataOutputStream stream, OfflineMember member) throws IOException {
        stream.writeLong(member.entityUID.getMostSignificantBits());
        stream.writeLong(member.entityUID.getLeastSignificantBits());
        stream.writeDouble(member.motX);
        stream.writeDouble(member.motZ);
        stream.writeInt(member.cx);
        stream.writeInt(member.cz);
    }

    public static OfflineGroup readGroup(DataInputStream stream, OfflineWorld world) throws IOException {
        LegacyOfflineMemberData[] members = new LegacyOfflineMemberData[stream.readInt()];
        for (int i = 0; i < members.length; i++) {
            members[i] = LegacyOfflineMemberData.read(stream);
        }
        String name = stream.readUTF();

        return new OfflineGroup(
                name,
                world,
                Collections.emptyList(),
                Arrays.asList(members),
                (offlineGroup, legacyMember) -> legacyMember.toOfflineMember(offlineGroup));
    }

    private static class LegacyOfflineMemberData {
        public final UUID entityUID;
        public final int cx, cz;
        public final double motX, motZ;

        public static LegacyOfflineMemberData read(DataInputStream stream) throws IOException {
            return new LegacyOfflineMemberData(stream);
        }

        private LegacyOfflineMemberData(DataInputStream stream) throws IOException {
            entityUID = new UUID(stream.readLong(), stream.readLong());
            motX = stream.readDouble();
            motZ = stream.readDouble();
            cx = stream.readInt();
            cz = stream.readInt();
        }

        public OfflineMember toOfflineMember(OfflineGroup offlineGroup) {
            return new OfflineMember(offlineGroup,
                    entityUID, cx, cz, motX, 0.0, motZ,
                    Collections.emptyList(),
                    Collections.emptyList());
        }
    }
}
