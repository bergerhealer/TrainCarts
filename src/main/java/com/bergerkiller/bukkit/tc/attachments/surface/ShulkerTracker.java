package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Caches the shulkers that have been generated so far for this player.
 * Avoids rapidly using up lots of entity ids as the player walks around,
 * as potentially a lot of shulkers get spawned.
 */
final class ShulkerTracker {
    private static final int ID_CHUNK_SIZE = 16;
    private Shulker[] cache = new Shulker[ID_CHUNK_SIZE];
    private int pos = -1;
    final List<Shulker> shulkersToDestroy = new ArrayList<>();
    final List<Shulker> shulkersToSpawn = new ArrayList<>();
    final List<Shulker> shulkersToMove = new ArrayList<>();

    /**
     * Retrieves a shulker from the cache, or creates a new one if needed.
     *
     * @param pushDirection The direction to push players if they happen to be
     *                      inside the bbox of this shulker during spawning.
     * @return Shulker ready to be used
     */
    public Shulker spawn(BlockFace pushDirection) {
        Shulker[] cache = this.cache;
        int pos = this.pos--;
        if (pos < 0) {
            // Generate 16 more
            for (int i = 0; i < ID_CHUNK_SIZE; i++) {
                cache[i] = new Shulker(this);
            }
            this.cache = cache;
            this.pos += ID_CHUNK_SIZE;
            pos += ID_CHUNK_SIZE;
        }
        Shulker shulker = cache[pos];
        shulker.pushDirection = pushDirection;
        shulker.scheduleSpawn();
        return shulker;
    }

    /**
     * Stores a shulker back into this cache after it is no longer needed
     *
     * @param shulker Shulker to store
     */
    public void destroy(Shulker shulker) {
        Shulker[] cache = this.cache;
        int pos = ++this.pos;
        if (pos >= cache.length) {
            this.cache = cache = Arrays.copyOf(cache, cache.length + ID_CHUNK_SIZE);
        }

        shulker.scheduleDestroy();
        cache[pos] = shulker;
    }

    /**
     * Performs a full update sync for this shulker tracker.
     *
     * @param viewer AttachmentViewer
     * @param pusher PlayerPusher of the viewer. The player is pushed away for all newly
     *               spawned shulkers.
     */
    public void update(AttachmentViewer viewer, PlayerPusher pusher) {
        try {
            // Clean up any stale shulker states and reset their flag
            // This guarantees the lists only contain shulkers that actually need those updates
            shulkersToDestroy.removeIf(Shulker::clearDestroy);
            shulkersToSpawn.removeIf(Shulker::clearSpawn);
            shulkersToMove.removeIf(Shulker::clearMove);

            // First destroy all shulkers that have been destroyed
            sendDestroyPackets(viewer, shulkersToDestroy);

            // For all shulkers to spawn, send them to the player pusher to displace the player
            // This prevents players from clipping through newly spawned shulkers
            // We repeat this in a loop in case both an upwards and sideways adjustment is needed.
            // The player pusher has logic to avoid infinite loops (only allows pushing into one direction)
            if (!shulkersToSpawn.isEmpty()) {
                pusher.reset();

                boolean wasPushed;
                do {
                    wasPushed = false;
                    for (Shulker shulker : shulkersToSpawn) {
                        wasPushed |= pusher.shulkerSpawned(shulker);
                    }
                } while (wasPushed);

                pusher.sendPush();
            }

            // Now actually spawn the shulkers
            for (Shulker shulker : shulkersToSpawn) {
                shulker.spawn(viewer);
            }

            // And also move shulkers
            for (Shulker shulker : shulkersToMove) {
                shulker.syncPosition(viewer);
            }

        } finally {
            // Reset lists
            shulkersToDestroy.clear();
            shulkersToSpawn.clear();
            shulkersToMove.clear();
        }
    }

    /**
     * Sends destroy packets for all shulkers in the List
     *
     * @param viewer AttachmentViewer
     * @param shulkers Shulkers to destroy
     */
    private static void sendDestroyPackets(AttachmentViewer viewer, List<Shulker> shulkers) {
        if (shulkers.isEmpty()) {
            return;
        }

        if (PacketPlayOutEntityDestroyHandle.canDestroyMultiple()) {
            int[] ids = new int[shulkers.size() * 2];
            int idx = 0;
            for (Shulker shulker : shulkers) {
                ids[idx++] = shulker.entityId;
                ids[idx++] = shulker.mountEntityId;
            }
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(ids));
        } else {
            for (Shulker shulker : shulkers) {
                viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(shulker.entityId));
                viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(shulker.mountEntityId));
            }
        }
    }
}
