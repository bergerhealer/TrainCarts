package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import org.bukkit.Location;

/**
 * The material properties of a surface
 */
public interface CollisionSurfaceMaterial {
    boolean CAN_USE_SOUND_CATEGORY = Common.hasCapability("WorldUtil:PlaySoundCategory");
    CollisionSurfaceMaterial AIR = () -> BlockData.AIR;
    CollisionSurfaceMaterial DEFAULT = () -> BlockData.fromMaterial(MaterialUtil.getFirst("STONE", "LEGACY_STONE"));

    /**
     * Gets the block material the surface is made of. This is used for deciding on the
     * step sound the material makes when walking on it.
     *
     * @return BlockData material
     */
    BlockData getMaterial();

    /**
     * Plays a step sound effect of walking on this surface material somewhere in the world
     *
     * @param at Position to play it at
     */
    default void playStepSound(Location at) {
        BlockData material = getMaterial();
        if (material == BlockData.AIR) {
            return;
        }
        ResourceKey<SoundEffect> sound = material.getStepSound();
        if (sound == null) {
            return;
        }
        if (CAN_USE_SOUND_CATEGORY) {
            bkclPlayStepSound(at, sound, 0.15f, 1.0f);
        } else {
            WorldUtil.playSound(at, sound, 0.15f, 1.0f);
        }
    }

    // In a separate method to help avoid missing method runtime errors
    static void bkclPlayStepSound(Location at, ResourceKey<SoundEffect> sound, float volume, float pitch) {
        WorldUtil.playSound(at, sound, "block", volume, pitch);
    }
}
