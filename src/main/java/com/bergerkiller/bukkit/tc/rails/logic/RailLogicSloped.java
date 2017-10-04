package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

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
    public void onRotationUpdate(MinecartMember<?> member) {
        //Update yaw and pitch based on motion
        CommonMinecart<?> entity = member.getEntity();
        final float oldyaw = entity.loc.getYaw();
        float newyaw = oldyaw;
        float newpitch = entity.loc.getPitch();

        // Update yaw
        newyaw = FaceUtil.faceToYaw(this.getDirection());
        newpitch = this.isUpsideDown() ? 135.0f : -45.0f;
        member.setRotationWrap(newyaw, newpitch, true);
    }

    @Override
    public boolean isSloped() {
        return true;
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();

        // Retrieve the Y-position of the minecart before the movement update
        RailLogic logic = member.getRailTracker().getLastLogic();
        IntVector3 lastRailPos = new IntVector3(member.getRailTracker().getLastBlock());
        double startY = logic.getFixedPosition(entity, entity.last, lastRailPos).getY();

        // Correct the Y-coordinate for the newly moved position
        // This also makes sure we don't clip through the floor moving down a slope
        Vector pos = getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), member.getBlockPos());
        entity.setPosition(pos.getX(), pos.getY(), pos.getZ());

        // Apply velocity factors from going up/down the slope
        if (member.getGroup().getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
            final double motLength = entity.vel.xz.length();
            if (motLength > 0) {
                entity.vel.xz.multiply((startY - pos.getY()) * 0.05 / motLength + 1.0);
            }
        }
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        Vector pos = super.getFixedPosition(entity, x, y, z, railPos);

        double stage = 0.0; // stage on the minecart track, where 0.0 is exactly in the middle
        if (alongZ) {
            stage = step * (pos.getZ() - (double) railPos.midZ());
        } else if (alongX) {
            stage = step * (pos.getX() - (double) railPos.midX());
        }

        double dy = (stage + 0.5);
        if (dy < 0.0) dy = 0.0;

        pos.setY(pos.getY() + dy);

        if (this.isUpsideDown()) {
            pos.setY(pos.getY() - 0.35);
        }

        return pos;
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();

        MinecartGroup group = member.getGroup();
        // Velocity modifier for sloped tracks
        if (group.getProperties().isSlowingDown(SlowdownMode.GRAVITY) && !member.isMovementControlled()) {
            entity.vel.xz.subtract(this.getDirection(), MinecartMember.SLOPE_VELOCITY_MULTIPLIER);
        }

        entity.vel.xz.add(this.getDirection(), entity.vel.getY());
        entity.vel.y.setZero();

        if (checkSlopeBlockCollisions()) {
            // Stop movement if colliding with a block at the slope
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

        // Perform remaining positioning updates
        super.onPreMove(member);
    }

    protected boolean checkSlopeBlockCollisions() {
        return true;
    }
}
