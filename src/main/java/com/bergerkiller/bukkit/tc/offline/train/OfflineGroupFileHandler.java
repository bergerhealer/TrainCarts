package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Handles the loading and (periodic) saving of the offline group mapping.
 */
class OfflineGroupFileHandler {
    private final OfflineGroupManager manager;
    private CompletableFuture<Void> currentSaveOperation = CompletableFuture.completedFuture(null);

    public OfflineGroupFileHandler(OfflineGroupManager manager) {
        this.manager = manager;
    }

    public void load() {

    }

    public void save(boolean autosave) {
        // Wait for previous auto-save to complete
        if (!currentSaveOperation.isDone()) {
            if (autosave) {
                return; // Skip saving this time, previous auto-save is still going on for some reason
            } else if (!waitForSaveCompletion()) {
                return; // Auto-save got stuck
            }
        }

        // On the main thread, collect all OfflineGroups and OfflineMembers of these groups
        // at this current time for all worlds. This is information that won't change
        // asynchronously


        // If not auto-saving, wait for saving to complete
        if (!autosave) {
            waitForSaveCompletion();
        }
    }

    private boolean waitForSaveCompletion() {
        try {
            currentSaveOperation.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            manager.getTrainCarts().log(Level.SEVERE, "Failed to save group data on plugin shutdown: save timed out");
            return false;
        } catch (Throwable t) { /* already logged */ }

        return true;
    }

    public static OfflineGroup readLegacyGroup(DataInputStream stream, OfflineWorld world) throws IOException {
        LegacyOfflineMemberData[] members = new LegacyOfflineMemberData[stream.readInt()];
        for (int i = 0; i < members.length; i++) {
            members[i] = LegacyOfflineMemberData.read(stream);
        }
        String name = stream.readUTF();

        return new OfflineGroup(name, world, Arrays.asList(members),
                (offlineGroup, legacyMember) -> legacyMember.toOfflineMember(offlineGroup));
    }

    public static class LegacyOfflineMemberData {
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
                    entityUID, cx, cz, motX, motZ);
        }
    }
}
