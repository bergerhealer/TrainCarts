package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.PoweredTrackLogic;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.material.Rails;

public class RailTypePowered extends RailTypeRegular {
    public static final double START_BOOST = 0.02;
    private final boolean isPowered;

    protected RailTypePowered(boolean isPowered) {
        this.isPowered = isPowered;
    }

    public boolean isPowered() {
        return this.isPowered;
    }

    @Override
    public void onBlockPlaced(Block railsBlock) {
        super.onBlockPlaced(railsBlock);

        // Also apply physics on the blocks adjacent for power to spread correctly
        Rails rails = Util.getRailsRO(railsBlock);
        if (rails != null && isUpsideDown(railsBlock)) {
            BlockUtil.applyPhysics(railsBlock.getRelative(rails.getDirection()), Material.POWERED_RAIL);
            BlockUtil.applyPhysics(railsBlock.getRelative(rails.getDirection().getOppositeFace()), Material.POWERED_RAIL);
        }
    }

    @Override
    public void onBlockPhysics(BlockPhysicsEvent event) {
        super.onBlockPhysics(event);
        if (this.isUpsideDown(event.getBlock())) {
            PoweredTrackLogic logic = new PoweredTrackLogic(Material.POWERED_RAIL);
            logic.updateRedstone(event.getBlock());
        }
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        // Do not do anything if being controlled
        if (member.isMovementControlled()) {
            return;
        }

        CommonMinecart<?> entity = member.getEntity();
        if (!this.isPowered) {
            // Perform braking
            if (entity.vel.xz.lengthSquared() < 0.0009) {
                entity.vel.multiply(0.0);
            } else if (TCConfig.legacySpeedLimiting) {
                entity.vel.multiply(0.5);
            } else {
                // With the new speed limiting, the minecart won't move 0.4 (default) in a single tick
                // Instead this is 0.4 * halfrootoftwo (because of 45 degree incline)
                // For this reason, we must slow down less rapidly otherwise it won't match vanilla behavior
                // This is only important for sloped track
                Rails rails = Util.getRailsRO(member.getBlock());
                if (rails == null || !rails.isOnSlope()) {
                    entity.vel.multiply(0.5);
                } else {
                    entity.vel.multiply(1.0 - 0.5 * MathUtil.HALFROOTOFTWO);
                }
            }
        } else {
            // Perform launching
            double motLength = entity.vel.xz.length();
            if (motLength > 0.01) {
                // Simple motion boosting when already moving
                entity.vel.xz.add(entity.vel.xz, TCConfig.poweredRailBoost / motLength);
            } else {
                // Launch away from a suffocating block
                BlockFace dir = this.getDirection(member.getBlock());
                org.bukkit.block.Block block = member.getBlock();
                if (this.isUpsideDown(block)) {
                    block = block.getRelative(BlockFace.DOWN);
                }
                boolean pushFrom1 = BlockUtil.isSuffocating(block.getRelative(dir.getOppositeFace()));
                boolean pushFrom2 = BlockUtil.isSuffocating(block.getRelative(dir));

                // If pushing from both directions, block all movement
                if (pushFrom1 && pushFrom2) {
                    entity.vel.xz.setZero();
                } else if (pushFrom1 != pushFrom2) {
                    // Boosting to the open spot
                    final double boost = MathUtil.invert(START_BOOST, pushFrom2);
                    entity.vel.xz.set(boost * dir.getModX(), boost * dir.getModZ());
                }
            }
        }
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.isType(Material.POWERED_RAIL) && ((blockData.getRawData() & 0x8) == 0x8) == isPowered;
    }

}
