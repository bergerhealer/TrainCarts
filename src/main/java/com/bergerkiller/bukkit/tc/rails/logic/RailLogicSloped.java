package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles minecart movement on sloped rails
 */
public class RailLogicSloped extends RailLogicHorizontal {
    private static final RailLogicSloped[] values = new RailLogicSloped[4];
    private static final RailLogicSloped[] values_upsideDown = new RailLogicSloped[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicSloped(FaceUtil.notchToFace(i << 1), false);
            values_upsideDown[i] = new RailLogicSloped(FaceUtil.notchToFace(i << 1), true);
        }
    }

    private final double step;

    protected RailLogicSloped(BlockFace direction) {
        this(direction, false);
    }

    protected RailLogicSloped(BlockFace direction, boolean upsideDown) {
        super(direction, upsideDown);
        if (direction == BlockFace.SOUTH || direction == BlockFace.EAST) {
            this.step = 1.0;
        } else {
            this.step = -1.0;
        }
    }

    /**
     * Gets the sloped rail logic for the the sloped track leading up on the direction specified
     *
     * @param direction    of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicSloped get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    /**
     * Gets the sloped rail logic for the the sloped track leading up on the direction specified
     *
     * @param direction    of the sloped rail
     * @param upsideDown   upside-down or not
     * @return Rail Logic
     */
    public static RailLogicSloped get(BlockFace direction, boolean upsideDown) {
        if (upsideDown) {
            return values_upsideDown[FaceUtil.faceToNotch(direction) >> 1];
        } else {
            return values[FaceUtil.faceToNotch(direction) >> 1];
        }
    }

    @Override
    public boolean isSloped() {
        return true;
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();

        // Correct the Y-coordinate for the newly moved position
        // This also makes sure we don't clip through the floor moving down a slope
        Vector pos = entity.loc.vector();
        getFixedPosition(pos, member.getBlockPos());
        entity.setPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        super.getFixedPosition(position, railPos);
        
        double stage = 0.0; // stage on the minecart track, where 0.0 is exactly in the middle
        if (alongZ) {
            stage = step * (position.getZ() - (double) railPos.midZ());
        } else if (alongX) {
            stage = step * (position.getX() - (double) railPos.midX());
        }

        double dy = (stage + 0.5);
        if (dy < 0.0) dy = 0.0;

        position.setY(position.getY() + dy);

        if (this.isUpsideDown()) {
            position.setY(position.getY() + Y_POS_OFFSET_UPSIDEDOWN_SLOPE);
        }
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        super.onPreMove(member);

        if (checkSlopeBlockCollisions()) {
            // Stop movement if colliding with a block at the slope
            final CommonMinecart<?> entity = member.getEntity();
            Block inside = member.getRailType().findMinecartPos(member.getBlock());
            double blockedDistance = Double.MAX_VALUE;
            Block heading = inside.getRelative(this.getDirection().getOppositeFace());
            if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
                if (MaterialUtil.SUFFOCATES.get(heading)) {
                    blockedDistance = entity.loc.xz.distance(heading) - 1.0;
                }
            } else if (member.isHeadingTo(this.getDirection())) {
                Block above = inside.getRelative(BlockFace.UP);
                if (MaterialUtil.SUFFOCATES.get(above)) {
                    blockedDistance = entity.loc.xz.distance(above);
                }
            }
            if (entity.vel.xz.length() > blockedDistance) {
                member.getGroup().setForwardForce(blockedDistance);
            }
        }
    }

    protected boolean checkSlopeBlockCollisions() {
        return true;
    }
}
