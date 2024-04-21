package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Modern group data format, which stored a flexible amount of data using the OfflineDataBlock format
 */
public class OfflineGroupFileFormatModern {

    static {
        // Forward-initialize all classes that this class uses when reading and writing
        // This avoids a thread deadlock due to the class loader trying to call getPlugin()
        bootstrap(DataInputStream.class, DataOutputStream.class, Data.class,
                OfflineWorld.class, StreamUtil.class, OfflineDataBlock.class,
                IOException.class, ArrayList.class, Collections.class, List.class, UUID.class);
    }

    private static void bootstrap(Class<?>... classNames) {
        for (Class<?> clazz : classNames) {
            CommonUtil.loadClass(clazz);
        }
    }

    public static void writeAll(DataOutputStream stream, Data data) throws IOException {
        // Write data that, if a legacy format reader would read it, would read nothing
        // But to the modern reader, it is a header for the modern format
        stream.writeInt(1);
        StreamUtil.writeUUID(stream, new UUID(0L, 0L));
        stream.writeInt(0);

        // Write the modern format data. First create a data block, then write it to the stream
        for (OfflineGroupWorld world : data.worlds) {
            writeWorldGroups(data.root, world);
        }
        data.root.writeTo(stream);
    }

    public static Data readAll(DataInputStream stream) throws IOException {
        // The legacy format would never write group data for a world that has no groups
        // It's also extremely unlikely for a world to have UUID 0.
        // Perform these reads first, and if they meet this condition, it is the
        // modern format.
        // If not, it is the legacy format.
        final int worldCount = stream.readInt();
        if (worldCount == 0) {
            return new Data(Collections.emptyList()); // Legacy format, no data
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
            return new Data(Collections.unmodifiableList(worlds));
        }

        // This is for sure the modern group data format
        // Read all worlds
        OfflineDataBlock root = OfflineDataBlock.read(stream);
        List<OfflineDataBlock> worldDataList = (root == null) ? Collections.emptyList() : root.findChildren("world");
        if (worldDataList.isEmpty()) {
            return new Data(Collections.emptyList(), root);
        }
        List<OfflineGroupWorld> worlds = new ArrayList<>(worldDataList.size());
        for (OfflineDataBlock worldData : worldDataList) {
            worlds.add(readWorldGroups(worldData));
        }
        return new Data(Collections.unmodifiableList(worlds), root);
    }

    public static void writeWorldGroups(OfflineDataBlock root, OfflineGroupWorld world) throws IOException {
        // World UUID is saved in the data block
        OfflineDataBlock worldData = root.addChild("world", s -> {
            StreamUtil.writeUUID(s, world.getWorld().getUniqueId());
        });

        // Save all groups
        for (OfflineGroup group : world.getGroups()) {
            writeGroup(worldData, group);
        }
    }

    public static OfflineGroupWorld readWorldGroups(OfflineDataBlock worldGroupData) throws IOException {
        // World UUID is stored in the data block
        final OfflineWorld world;
        try (DataInputStream stream = worldGroupData.readData()) {
            world = OfflineWorld.of(StreamUtil.readUUID(stream));
        }

        // Find all groups
        List<OfflineDataBlock> groupListData = worldGroupData.findChildren("group");

        List<OfflineGroup> groups = new ArrayList<>(groupListData.size());
        for (OfflineDataBlock groupData : groupListData) {
            OfflineGroup group = readGroup(groupData, world);
            if (group != null) {
                groups.add(group);
            }
        }

        return OfflineGroupWorld.snapshot(world, groups);
    }

    public static void writeGroup(OfflineDataBlock root, OfflineGroup group) throws IOException {
        // Name is stored in the data block
        OfflineDataBlock groupData = root.addChild("group", s -> {
            s.writeUTF(group.name);
        });
        groupData.children.addAll(group.actions);
        groupData.children.addAll(group.skippedSigns);

        // Save all members
        for (OfflineMember member : group.members) {
            writeMember(groupData, member);
        }
    }

    public static OfflineGroup readGroup(OfflineDataBlock groupData, OfflineWorld world) throws IOException {
        // Name is stored in the data block
        final String name;
        try (DataInputStream stream = groupData.readData()) {
            name = stream.readUTF();
        }

        // Find all members
        List<OfflineDataBlock> members = groupData.findChildren("member");
        if (members.isEmpty()) {
            return null; // Invalid
        }

        return new OfflineGroup(name, world,
                groupData.findChildren("action"),
                groupData.findChildren("skipped-sign"),
                members, OfflineGroupFileFormatModern::readMember);
    }

    public static void writeMember(OfflineDataBlock root, OfflineMember member) throws IOException {
        // Standard data is written in the data tag itself
        OfflineDataBlock memberData = root.addChild("member", s -> {
            StreamUtil.writeUUID(s, member.entityUID);
            s.writeInt(member.cx);
            s.writeInt(member.cz);
            s.writeDouble(member.motX);
            s.writeDouble(member.motY);
            s.writeDouble(member.motZ);
        });

        // Extra metadata
        memberData.children.addAll(member.actions);
        memberData.children.addAll(member.activeSigns);
        memberData.children.addAll(member.skippedSigns);
    }

    private static OfflineMember readMember(OfflineGroup group, OfflineDataBlock memberData) throws IOException {
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

    public static final class Data {
        public final List<OfflineGroupWorld> worlds;
        public final OfflineDataBlock root; // Additional data can be included here

        public Data(List<OfflineGroupWorld> worlds) {
            this.worlds = worlds;
            this.root = OfflineDataBlock.create("root");
        }

        public Data(List<OfflineGroupWorld> worlds, OfflineDataBlock root) {
            this.worlds = worlds;
            this.root = root;
        }
    }
}
