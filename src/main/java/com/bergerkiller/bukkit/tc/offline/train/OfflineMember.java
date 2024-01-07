package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunch;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Contains the information to get and restore a Minecart
 */
public final class OfflineMember {
    public final OfflineGroup group;
    public final UUID entityUID;
    public final int cx, cz;
    public final double motX, motY, motZ;

    OfflineMember(OfflineGroup group, UUID entityUID, int cx, int cz, double motX, double motY, double motZ) {
        this.group = group;
        this.entityUID = entityUID;
        this.cx = cx;
        this.cz = cz;
        this.motX = motX;
        this.motY = motY;
        this.motZ = motZ;
    }

    public OfflineMember(OfflineGroup offlineGroup, MinecartMember<?> instance) {
        this.group = offlineGroup;

        CommonEntity<?> entity = instance.getEntity();
        this.entityUID = entity.getUniqueId();
        this.cx = entity.loc.x.chunk();
        this.cz = entity.loc.z.chunk();

        MinecartGroup group = instance.getGroup();
        if (group.getActions().getCurrentAction() instanceof MemberActionLaunch) {
            // Simulate as if the launch has completed
            // TODO: Remove this
            double velMagn = ((MemberActionLaunch) group.getActions().getCurrentAction()).getTargetVelocity();
            Vector vel = entity.getVelocity();
            double ls = vel.lengthSquared();
            if (ls < 1e-20) {
                this.motX = velMagn;
                this.motY = 0.0;
                this.motZ = 0.0;
            } else {
                vel = vel.multiply(MathUtil.getNormalizationFactorLS(ls) * velMagn);
                this.motX = vel.getX();
                this.motY = vel.getY();
                this.motZ = vel.getZ();
            }
        } else {
            // Use current cart velocity
            this.motX = entity.vel.getX();
            this.motY = entity.vel.getY();
            this.motZ = entity.vel.getZ();
        }
    }

    public boolean isMoving() {
        return Math.abs(motX) >= CommonEntity.MIN_MOVE_SPEED || Math.abs(motZ) >= CommonEntity.MIN_MOVE_SPEED;
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
        mm.getEntity().setVelocity(new Vector(motX, motY, motZ));
        return mm;
    }
}
