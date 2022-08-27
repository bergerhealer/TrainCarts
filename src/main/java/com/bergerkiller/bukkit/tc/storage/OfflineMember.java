package com.bergerkiller.bukkit.tc.storage;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

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

    public Minecart findEntity(World world, boolean markChunkDirty) {
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

        // Not found
        return null;
    }

    public MinecartMember<?> create(TrainCarts plugin, World world) {
        Minecart entity = findEntity(world, false);
        if (entity == null || entity.isDead()) {
            return null;
        }
        MinecartMember<?> mm = MinecartMemberStore.convert(plugin, entity);
        if (mm == null) {
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