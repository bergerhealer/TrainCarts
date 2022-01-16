package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Two VirtualEntity instances which are swapped around to work around the
 * camera glitch at pitch 180. Only spawns to a single viewer.
 */
class PitchSwappedEntity<E extends VirtualEntity> {
    private final VehicleMountController vmc;
    private Runnable beforeSwap = () -> {};
    public E entity;
    public E entityAlt;
    private boolean spectating = false;

    private PitchSwappedEntity(VehicleMountController vmc, E entity, E entityAlt) {
        this.vmc = vmc;
        this.entity = entity;
        this.entityAlt = entityAlt;
    }

    public int getEntityId() {
        return entity.getEntityId();
    }

    public void beforeSwap(Runnable runnable) {
        beforeSwap = runnable;
    }

    public void spawn(Matrix4x4 eyeTransform, Vector motion) {
        this.entity.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());
        this.entity.syncPosition(true);

        this.entityAlt.updatePosition(eyeTransform, new Vector(
                computeAltPitch(this.entity.getYawPitchRoll().getX(), 179.0f),
                this.entity.getYawPitchRoll().getY(),
                0.0));
        this.entityAlt.syncPosition(true);

        this.entity.spawn(vmc.getPlayer(), motion);
        this.entityAlt.spawn(vmc.getPlayer(), motion);
    }

    public void destroy() {
        spectating = false;
        Util.stopSpectating(vmc, entity.getEntityId());
        entity.destroy(vmc.getPlayer());
        entityAlt.destroy(vmc.getPlayer());
    }

    public void spectate() {
        Util.startSpectating(vmc, this.entity.getEntityId());
        spectating = true;
    }

    public void spectateFrom(int previousEntityId) {
        Util.swapSpectating(vmc, previousEntityId, this.entity.getEntityId());
        spectating = true;
    }

    /**
     * Can be called during {@link #beforeSwap(Runnable)} to make the new alt visible,
     * and the current entity invisible.
     */
    public void swapVisibility() {
        entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
        entity.syncMetadata(); // do early
        entityAlt.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
        entityAlt.syncMetadata(); // do early
    }

    public void updatePosition(Matrix4x4 eyeTransform) {
        entity.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(entity.getSyncPitch(), entity.getLivePitch())) {
            // Spectate other entity
            if (spectating) {
                Util.swapSpectating(vmc, entity.getEntityId(), entityAlt.getEntityId());
            }

            // Perform any needed logic first
            beforeSwap.run();

            // Swap them out, continue working with alt
            {
                E tmp = entity;
                entity = entityAlt;
                entityAlt = tmp;
            }

            // Give the fake player full sync pitch
            entity.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());

            // Sync these right away
            entity.syncMetadata();
            entityAlt.syncMetadata();
        }

        // Calculate what new alt-pitch should be used. This swaps over at the 180-degree mark
        {
            float newAltPitch = computeAltPitch(entity.getYawPitchRoll().getX(),
                                                entityAlt.getLivePitch());
            boolean isAltPitchDifferent = (newAltPitch != entityAlt.getLivePitch());

            // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
            entityAlt.updatePosition(eyeTransform, new Vector(
                    newAltPitch, entity.getYawPitchRoll().getY(), 0.0));

            if (isAltPitchDifferent) {
                // We cannot safely rotate between these two - it requires a respawn to do this quickly
                entityAlt.destroy(vmc.getPlayer());
                entityAlt.syncPosition(true);
                entityAlt.spawn(vmc.getPlayer(), new Vector());
            }
        }
    }

    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
        entityAlt.syncPosition(absolute);
    }

    /**
     * Can be overrided to perform logic prior to swapping the two entities
     */
    public void onBeforeSwap() {
    }

    public static <E extends VirtualEntity> PitchSwappedEntity<E> create(VehicleMountController vmc, E entity, E entityAlt) {
        return new PitchSwappedEntity<E>(vmc, entity, entityAlt);
    }

    public static <E extends VirtualEntity> PitchSwappedEntity<E> create(VehicleMountController vmc, Supplier<E> entityFactory) {
        return new PitchSwappedEntity<E>(vmc, entityFactory.get(), entityFactory.get());
    }

    static float computeAltPitch(double currPitch, float currAltPitch) {
        currPitch = MathUtil.wrapAngle(currPitch); // Wrap between -180 and 180 degrees

        if (currPitch > 90.0) {
            return 181.0f;
        } else if (currPitch < -90.0) {
            return 179.0f;
        } else {
            return currAltPitch;
        }
    }
}
