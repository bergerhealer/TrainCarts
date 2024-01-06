package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Contains the information to get and restore a Minecart
 */
public class OfflineMember {
    public double motX, motZ;
    public UUID entityUID;
    public int cx, cz;
    public final OfflineGroup group;

    private OfflineMember(OfflineGroup group) {
        this.group = group;
    }

    public OfflineMember(OfflineGroup group, MinecartMember<?> instance) {
        this(group);
        CommonEntity<?> entity = instance.getEntity();
        this.motX = entity.vel.getX();
        this.motZ = entity.vel.getZ();
        this.entityUID = entity.getUniqueId();
        this.cx = entity.loc.x.chunk();
        this.cz = entity.loc.z.chunk();
    }

    public static OfflineMember readFrom(OfflineGroup group, DataInputStream stream) throws IOException {
        OfflineMember wm = new OfflineMember(group);
        wm.entityUID = new UUID(stream.readLong(), stream.readLong());
        wm.motX = stream.readDouble();
        wm.motZ = stream.readDouble();
        wm.cx = stream.readInt();
        wm.cz = stream.readInt();
        return wm;
    }

    public boolean isMoving() {
        return Math.abs(motX) >= CommonEntity.MIN_MOVE_SPEED || Math.abs(motZ) >= CommonEntity.MIN_MOVE_SPEED;
    }

    public void setVelocity(double velocity) {
        Vector vel = new Vector(this.motX, 0.0, this.motZ);
        double ls = vel.lengthSquared();
        if (ls < 1e-20) {
            this.motX = velocity;
            this.motZ = 0.0;
        } else {
            vel = vel.multiply(MathUtil.getNormalizationFactorLS(ls) * velocity);
            this.motX = vel.getX();
            this.motZ = vel.getZ();
        }
    }

    public Minecart findEntity(Chunk chunk, boolean markChunkDirty) {
        for (Entity e : WorldUtil.getEntities(chunk)) {
            if (e instanceof Minecart && e.getUniqueId().equals(this.entityUID)) {
                if (markChunkDirty) {
                    Util.markChunkDirty(chunk);
                }
                return (Minecart) e;
            }
        }
        return null;
    }

    public Minecart findEntity(TrainCarts plugin, World world, boolean markChunkDirty) {
        // first try to find it in the chunk
        Minecart e = findEntity(world.getChunkAt(this.cx, this.cz), markChunkDirty);
        if (e != null) {
            return e;
        }

        // Try neighbouring chunks
        final int radius = 2;
        for (int cx = this.cx - radius; cx <= this.cx + radius; cx++) {
            for (int cz = this.cz - radius; cz <= this.cz + radius; cz++) {
                if (cx == this.cx && cz == this.cz) {
                    continue; // Already checked.
                }

                e = findEntity(world.getChunkAt(cx, cz), markChunkDirty);
                if (e != null) {
                    return e;
                }
            }
        }

        // Complete fallback just to make sure the entity is truly gone. It might be in an entirely different chunk,
        // but loaded nevertheless. It's worth trying to load it.
        Entity byUUID = EntityUtil.getEntity(world, this.entityUID);
        if (byUUID instanceof Minecart) {
            if (markChunkDirty) {
                Chunk chunk = EntityHandle.fromBukkit(byUUID).getCurrentChunk();
                if (chunk != null) {
                    Util.markChunkDirty(chunk);
                }
            }

            // This is a bit extraordinary, so log that we could not find the entity in any nearby chunks...
            Location loc = byUUID.getLocation();
            plugin.log(Level.WARNING, cartInfo() + " was not found in Chunk Entities, " +
                    "yet was found in World at " +
                    "[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]");

            return (Minecart) byUUID;
        }

        // Not found
        return null;
    }

    private String cartInfo() {
        return "Cart [" + this.entityUID + "] of train '" + group.name + "' " +
                "at chunk [" + this.cx + ", " + this.cz + "]";
    }

    public MinecartMember<?> create(TrainCarts plugin, World world) {
        Minecart entity = findEntity(plugin, world, false);
        if (entity == null || entity.isDead()) {
            return null;
        }
        MinecartMember<?> mm = MinecartMemberStore.convert(plugin, entity);
        if (mm == null) {
            plugin.log(Level.WARNING, cartInfo() + "Controller creation failed!");
            return null;
        }

        // Restore velocity
        mm.getEntity().vel.xz.set(this.motX, this.motZ);
        return mm;
    }

    public void writeTo(DataOutputStream stream) throws IOException {
        stream.writeLong(entityUID.getMostSignificantBits());
        stream.writeLong(entityUID.getLeastSignificantBits());
        stream.writeDouble(motX);
        stream.writeDouble(motZ);
        stream.writeInt(cx);
        stream.writeInt(cz);
    }
}