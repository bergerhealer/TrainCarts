package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import org.bukkit.World;

import java.util.function.BiFunction;

/**
 * A collection of Minecraft features enabled on a World relevant for attachments to
 * adjust their behavior.
 */
public class AttachmentWorldFeatures {
    /**
     * The new experimental Minecart improvements are enabled. In this case, minecart entities
     * must be synchronized using
     * {@link com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundMoveMinecartPacketHandle}
     * instead of the normal entity teleport / synchronization methods.
     */
    public final boolean MINECART_IMPROVEMENTS;

    /**
     * Gets the enabled features of a World
     *
     * @param world
     * @return features
     */
    public static AttachmentWorldFeatures of(World world) {
        return new AttachmentWorldFeatures(world);
    }

    private AttachmentWorldFeatures(World world) {
        this.MINECART_IMPROVEMENTS = hasFeatureMethod.apply(world, "minecraft:minecart_improvements");
    }

    // For old BKCL compatibility, can be removed once 1.21.4+ is a hard dependency
    private static final BiFunction<World, String, Boolean> hasFeatureMethod = Common.hasCapability("Common:WorldUtil:HasFeatureFlag") ?
            WorldUtil::hasFeatureFlag : (w, n) -> false;

    /**
     * Keeps track of the features of a world, with repeated calls. Assumes that
     * {@link #get(World)} will be called very often with the same world.
     */
    public static final class Tracker {
        private World world = null;
        private AttachmentWorldFeatures last = null;

        public AttachmentWorldFeatures get(World world) {
            if (this.world == world) {
                return last;
            } else {
                this.world = world;
                return this.last = of(world);
            }
        }
    }
}
