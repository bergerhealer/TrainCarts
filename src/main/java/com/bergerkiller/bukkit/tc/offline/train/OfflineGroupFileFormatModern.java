package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Modern group data format, which stored a flexible amount of data using the DataBlock format
 */
public class OfflineGroupFileFormatModern {

    public static void writeAllWorlds(DataOutputStream stream, List<OfflineGroupWorld> worlds) throws IOException {
        // Write data that, if a legacy format reader would read it, would read nothing
        // But to the modern reader, it is a header for the modern format
        stream.writeInt(1);
        StreamUtil.writeUUID(stream, new UUID(0L, 0L));
        stream.writeInt(0);

        // Write the modern format data. First create a data block, then write it to the stream
        DataBlock root = DataBlock.create("root");
        for (OfflineGroupWorld world : worlds) {
            writeWorldGroups(root, world);
        }
        root.writeTo(stream);
    }

    public static List<OfflineGroupWorld> readAllWorlds(DataInputStream stream) throws IOException {
        // The legacy format would never write group data for a world that has no groups
        // It's also extremely unlikely for a world to have UUID 0.
        // Perform these reads first, and if they meet this condition, it is the
        // modern format.
        // If not, it is the legacy format.
        final int worldCount = stream.readInt();
        if (worldCount == 0) {
            return Collections.emptyList(); // Legacy format, no data
        }
        final UUID firstWorldUUID = StreamUtil.readUUID(stream);
        final int firstWorldGroupCount = stream.readInt();
        if (worldCount != 1 ||
            firstWorldUUID.getMostSignificantBits() != 0L ||
            firstWorldUUID.getLeastSignificantBits() != 0L ||
            firstWorldGroupCount != 0
        ) {
            // Reading of the legacy format
            final List<OfflineGroupWorld> worlds = new ArrayList<>(worldCount);
            worlds.add(OfflineGroupFileFormatLegacy.readWorld(stream, firstWorldUUID, firstWorldGroupCount));
            for (int worldIdx = 1; worldIdx < worldCount; worldIdx++) {
                worlds.add(OfflineGroupFileFormatLegacy.readWorld(stream));
            }
            return Collections.unmodifiableList(worlds);
        }

        // This is for sure the modern group data format
        // Read all worlds
        DataBlock root = DataBlock.read(stream);
        List<DataBlock> worldDataList = (root == null) ? Collections.emptyList() : root.findChildren("world");
        if (worldDataList.isEmpty()) {
            return Collections.emptyList();
        }
        List<OfflineGroupWorld> worlds = new ArrayList<>(worldDataList.size());
        for (DataBlock worldData : worldDataList) {
            worlds.add(readWorldGroups(worldData));
        }
        return Collections.unmodifiableList(worlds);
    }

    public static void writeWorldGroups(DataBlock root, OfflineGroupWorld world) throws IOException {
        // World UUID is saved in the data block
        DataBlock worldData = root.addChild("world", s -> {
            StreamUtil.writeUUID(s, world.getWorld().getUniqueId());
        });

        // Save all groups
        for (OfflineGroup group : world.getGroups()) {
            writeGroup(worldData, group);
        }
    }

    public static OfflineGroupWorld readWorldGroups(DataBlock worldGroupData) throws IOException {
        // World UUID is stored in the data block
        final OfflineWorld world;
        try (DataInputStream stream = worldGroupData.readData()) {
            world = OfflineWorld.of(StreamUtil.readUUID(stream));
        }

        // Find all groups
        List<DataBlock> groupListData = worldGroupData.findChildren("group");

        List<OfflineGroup> groups = new ArrayList<>(groupListData.size());
        for (DataBlock groupData : groupListData) {
            OfflineGroup group = readGroup(groupData, world);
            if (group != null) {
                groups.add(group);
            }
        }

        return OfflineGroupWorld.snapshot(world, groups);
    }

    public static void writeGroup(DataBlock root, OfflineGroup group) throws IOException {
        // Name is stored in the data block
        DataBlock groupData = root.addChild("group", s -> {
            s.writeUTF(group.name);
        });

        // Save all members
        for (OfflineMember member : group.members) {
            writeMember(groupData, member);
        }
    }

    public static OfflineGroup readGroup(DataBlock groupData, OfflineWorld world) throws IOException {
        // Name is stored in the data block
        final String name;
        try (DataInputStream stream = groupData.readData()) {
            name = stream.readUTF();
        }

        // Find all members
        List<DataBlock> members = groupData.findChildren("member");
        if (members.isEmpty()) {
            return null; // Invalid
        }

        return new OfflineGroup(name, world,
                groupData.findChildren("action"),
                groupData.findChildren("skipped-sign"),
                members, OfflineGroupFileFormatModern::readMember);
    }

    public static void writeMember(DataBlock root, OfflineMember member) throws IOException {
        // Standard data is written in the data tag itself
        DataBlock memberData = root.addChild("member", s -> {
            StreamUtil.writeUUID(s, member.entityUID);
            s.writeInt(member.cx);
            s.writeInt(member.cz);
            s.writeDouble(member.motX);
            s.writeDouble(member.motY);
            s.writeDouble(member.motZ);
        });
    }

    private static OfflineMember readMember(OfflineGroup group, DataBlock memberData) throws IOException {
        // Standard data is included in the data tag itself
        final UUID entityUID;
        final int cx, cz;
        final double motX, motY, motZ;
        try (DataInputStream stream = memberData.readData()) {
            entityUID = StreamUtil.readUUID(stream);
            cx = stream.readInt();
            cz = stream.readInt();
            motX = stream.readDouble();
            motY = stream.readDouble();
            motZ = stream.readDouble();
        }

        return new OfflineMember(group, entityUID, cx, cz, motX, motY, motZ,
                memberData.findChildren("action"),
                memberData.findChildren("sign"),
                memberData.findChildren("skipped-sign"));
    }
}
