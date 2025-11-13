package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Caches the shulkers that have been generated so far for this player.
 * Avoids rapidly using up lots of entity ids as the player walks around,
 * as potentially a lot of shulkers get spawned.
 */
final class ShulkerCache {
    private static final int ID_CHUNK_SIZE = 16;
    private Shulker[] cache = new Shulker[ID_CHUNK_SIZE];
    private int pos = -1;
    private final List<Shulker> shulkersToDestroy = new ArrayList<>();

    /**
     * Retrieves a shulker from the cache, or creates a new one if needed.
     *
     * @return Shulker ready to be used
     */
    public Shulker spawn() {
        Shulker[] cache = this.cache;
        int pos = this.pos--;
        if (pos < 0) {
            // Generate 16 more
            for (int i = 0; i < ID_CHUNK_SIZE; i++) {
                cache[i] = new Shulker();
            }
            this.cache = cache;
            this.pos += ID_CHUNK_SIZE;
            pos += ID_CHUNK_SIZE;
        }
        Shulker shulker = cache[pos];
        shulker.pendingSpawn = true;
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
        if (!shulker.pendingDestroy) {
            shulker.pendingDestroy = true;
            shulkersToDestroy.add(shulker);
        }
        cache[pos] = shulker;
    }

    /**
     * Sends destroy packets for all shulkers that have been sent to the cache using
     * {@link #destroy(Shulker)} since previous time this was called.
     *
     * @param viewer AttachmentViewer
     */
    public void sendDestroyPackets(AttachmentViewer viewer) {
        if (shulkersToDestroy.isEmpty()) {
            return;
        }

        try {
            if (PacketPlayOutEntityDestroyHandle.canDestroyMultiple()) {
                int[] ids = new int[shulkersToDestroy.size() * 2];
                int idx = 0;
                for (Shulker shulker : shulkersToDestroy) {
                    shulker.pendingDestroy = false;
                    ids[idx++] = shulker.entityId;
                    ids[idx++] = shulker.mountEntityId;
                }
                viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(ids));
            } else {
                for (Shulker shulker : shulkersToDestroy) {
                    shulker.pendingDestroy = false;
                    viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(shulker.entityId));
                    viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(shulker.mountEntityId));
                }
            }
        } finally {
            shulkersToDestroy.clear();
        }
    }
}
