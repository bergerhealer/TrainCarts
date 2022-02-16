package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonView.HeadRotation;
import com.bergerkiller.generated.net.minecraft.server.level.EntityTrackerEntryStateHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Two VirtualEntity instances which are swapped around to work around the
 * camera glitch at pitch 180. Only spawns to a single viewer.
 */
class PitchSwappedEntity<E extends VirtualEntity> {
    private static final float MIN_PITCH = EntityTrackerEntryStateHandle.getRotationFromProtocol(-128); // Closest to -180
    private static final float MAX_PITCH = EntityTrackerEntryStateHandle.getRotationFromProtocol(127); // Closest to 180

    private final VehicleMountController vmc;
    private Consumer<E> beforeSwap = e -> {};
    private Runnable afterSwap = () -> {};
    /** The entity as it is currently displayed */
    public E entity;
    /** The entity right at the pitch-flip boundary */
    public E entityAlt;
    /** The entity 180-degrees yaw and pitch flipped, for when camera flips upside-down */
    public E entityAltFlip;
    private boolean spectating = false;

    private PitchSwappedEntity(VehicleMountController vmc, E entity, E entityAlt, E entityAltFlip) {
        this.vmc = vmc;
        this.entity = entity;
        this.entityAlt = entityAlt;
        this.entityAltFlip = entityAltFlip;
    }

    public int getEntityId() {
        return entity.getEntityId();
    }

    /**
     * Called before a swap occurs. The entity with which will be swapped is given to
     * the consumer.
     *
     * @param action
     */
    public void beforeSwap(Consumer<E> action) {
        beforeSwap = action;
    }

    /**
     * Called after a swap occurs and the newly swapped-out entity has its position updated
     *
     * @param action
     */
    public void afterSwap(Runnable action) {
        afterSwap = action;
    }

    public void spawn(Matrix4x4 eyeTransform, Vector motion) {
        HeadRotation headRot = HeadRotation.compute(eyeTransform);

        this.entity.updatePosition(eyeTransform, headRot.pyr);
        this.entity.syncPosition(true);

        this.entityAlt.updatePosition(eyeTransform, new Vector(
                computeAltPitch(headRot.pitch, MAX_PITCH),
                headRot.yaw,
                0.0));
        this.entityAlt.syncPosition(true);

        this.entityAltFlip.updatePosition(eyeTransform, headRot.flipVertical().pyr);
        this.entityAltFlip.syncPosition(true);

        this.entity.spawn(vmc.getPlayer(), motion);
        this.entity.forceSyncRotation();
        this.entityAlt.spawn(vmc.getPlayer(), motion);
        this.entityAlt.forceSyncRotation();
        this.entityAltFlip.spawn(vmc.getPlayer(), motion);
        this.entityAltFlip.forceSyncRotation();
    }

    public void destroy() {
        if (spectating) {
            spectating = false;
            Util.stopSpectating(vmc, entity.getEntityId());
        }
        entity.destroy(vmc.getPlayer());
        entityAlt.destroy(vmc.getPlayer());
        entityAltFlip.destroy(vmc.getPlayer());
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
     * Can be called during {@link #beforeSwap(Consumer)} to make the new alt visible,
     * and the current entity invisible.
     */
    public void swapVisibility(E swapped) {
        entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
        entity.syncMetadata(); // do early
        swapped.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
        swapped.syncMetadata(); // do early
    }

    public void updatePosition(Matrix4x4 eyeTransform) {
        Vector position = eyeTransform.toVector();
        HeadRotation headRot = HeadRotation.compute(eyeTransform);
        HeadRotation headRotFlipped = headRot.flipVertical();

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(entity.getSyncPitch(), headRot.pitch)) {
            // Spectate other entity
            if (spectating) {
                Util.swapSpectating(vmc, entity.getEntityId(), entityAlt.getEntityId());
            }

            // Perform any needed logic first
            beforeSwap.accept(entityAlt);

            // Swap them out, continue working with alt
            {
                E tmp = entity;
                entity = entityAlt;
                entityAlt = tmp;
            }

            // Give the fake player full sync pitch
            // Sync this information right away, otherwise a second swap can happen next tick
            entity.updatePosition(position, headRot.pyr);
            entity.syncPosition(true);

            // Sync these right away
            entity.syncMetadata();
            entityAlt.syncMetadata();

            // Done
            afterSwap.run();
        } else if (isCameraFlipped(headRot, headRotFlipped)) {
            // Spectate other entity
            if (spectating) {
                Util.swapSpectating(vmc, entity.getEntityId(), entityAltFlip.getEntityId());
            }

            // Perform any needed logic first
            beforeSwap.accept(entityAltFlip);

            // Swap them out, continue working with alt flip
            {
                E tmp = entity;
                entity = entityAltFlip;
                entityAltFlip = tmp;
            }

            // Give the fake player full sync pitch
            // Sync this information right away, otherwise a second swap can happen next tick
            entity.updatePosition(position, headRot.pyr);
            entity.syncPosition(true);

            // Sync these right away
            entity.syncMetadata();
            entityAltFlip.syncMetadata();

            // Done
            afterSwap.run();
        } else {
            // Update like normal
            entity.updatePosition(position, headRot.pyr);
        }

        // Sync flipped info
        {
            boolean requiresRespawning = Util.isProtocolRotationGlitched(headRotFlipped.pitch, entityAltFlip.getLivePitch());
            entityAltFlip.updatePosition(position, headRotFlipped.pyr);
            if (requiresRespawning) {
                entityAltFlip.respawnForAll(new Vector());
                entityAltFlip.forceSyncRotation();
            }
        }

        // Calculate what new alt-pitch should be used. This swaps over at the 180-degree mark
        {
            float newAltPitch = computeAltPitch(headRot.pitch, entityAlt.getLivePitch());
            boolean requiresRespawning = Util.isProtocolRotationGlitched(newAltPitch, entityAlt.getLivePitch());

            // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
            entityAlt.updatePosition(position, new Vector(
                    newAltPitch, headRot.yaw, headRot.roll));

            if (requiresRespawning) {
                // We cannot safely rotate between these two - it requires a respawn to do this quickly
                entityAlt.respawnForAll(new Vector());
                entityAlt.forceSyncRotation();
            }
        }
    }

    private boolean isCameraFlipped(HeadRotation newRot, HeadRotation newRotFlipped) {
        // When yaw changes a lot suddenly, and the pitch of the flipped one is better than the non-flipped one,
        // then the player camera probably inverted suddenly.
        return MathUtil.getAngleDifference(entity.getLiveYaw(), newRot.yaw) > 90.0f &&
                MathUtil.getAngleDifference(entity.getLivePitch(),
                                            newRotFlipped.pitch) < MathUtil.getAngleDifference(entity.getLivePitch(),
                                                                                               entity.getLiveYaw());
    }

    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
        entityAlt.syncPosition(absolute);
        entityAltFlip.syncPosition(absolute);
    }

    /**
     * Can be overrided to perform logic prior to swapping the two entities
     */
    public void onBeforeSwap() {
    }

    public static <E extends VirtualEntity> PitchSwappedEntity<E> create(VehicleMountController vmc, E entity, E entityAlt, E entityAltFlip) {
        return new PitchSwappedEntity<E>(vmc, entity, entityAlt, entityAltFlip);
    }

    public static <E extends VirtualEntity> PitchSwappedEntity<E> create(VehicleMountController vmc, Supplier<E> entityFactory) {
        return new PitchSwappedEntity<E>(vmc, entityFactory.get(), entityFactory.get(), entityFactory.get());
    }

    static float computeAltPitch(float currPitch, float currAltPitch) {
        // Special care must be taken at 180 degrees. Floating point error is a pain!
        int protRot = EntityTrackerEntryStateHandle.getProtocolRotation(currPitch);
        if (protRot == -128) {
            return MAX_PITCH;
        } else if (protRot == 127) {
            return MIN_PITCH;
        }

        // Wrap between -180 and 180 degrees and check if exceeding 90 degrees pitch
        currPitch = MathUtil.wrapAngle(currPitch);
        if (currPitch > 90.0) {
            return MIN_PITCH;
        } else if (currPitch < -90.0) {
            return MAX_PITCH;
        } else {
            return currAltPitch;
        }
    }
}
