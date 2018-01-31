package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles how a minecart travels on top of a rail
 */
public abstract class RailLogic {
    protected final boolean alongZ, alongX, alongY, curved;
    private final BlockFace horizontalDir;
    protected RailPath railPath;

    public RailLogic(BlockFace horizontalDirection) {
        this.horizontalDir = horizontalDirection;
        this.alongX = FaceUtil.isAlongX(horizontalDirection);
        this.alongZ = FaceUtil.isAlongZ(horizontalDirection);
        this.alongY = FaceUtil.isAlongY(horizontalDirection);
        this.curved = !alongZ && !alongY && !alongX;
        this.railPath = RailPath.EMPTY;
    }

    /**
     * Gets the horizontal direction of the rails this logic is for
     *
     * @return horizontal rail direction
     */
    public BlockFace getDirection() {
        return this.horizontalDir;
    }

    /**
     * Checks if this type of Rail Logic is for sloped tracks
     *
     * @return True if sloped, False if not
     */
    public boolean isSloped() {
        return false;
    }

    /**
     * Gets whether vertical movement is performed by this rail logic
     *
     * @return True if vertical movement is performed, False if not
     */
    public boolean hasVerticalMovement() {
        return false;
    }

    /**
     * Gets whether the rail logic type makes use of upside-down rail physics.
     * When this return True, passenger damage from blocks above the Minecart are ignored.
     * 
     * @return True if upside-down
     */
    public boolean isUpsideDown() {
        return false;
    }

    /**
     * Gets the vertical motion factor caused by gravity.
     * When gravity should be disabled for particular rail logic, it should be done here.
     *
     * @return gravity multiplier
     */
    public double getGravityMultiplier(MinecartMember<?> member) {
        return MinecartMember.GRAVITY_MULTIPLIER_RAILED;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + this.getDirection();
    }

    /**
     * Maintains equal spacing between Minecarts, updates for a single Minecart prior to moving
     * 
     * @param member to update
     * @param velocity that is currently set
     * @param factor that needs applying to ensure correct spacing
     */
    public void onSpacingUpdate(MinecartMember<?> member, Vector velocity, Vector factor) {
        double motLen = velocity.length();
        if (motLen > 0.0) {
            double f = TCConfig.cartDistanceForcer * factor.dot(velocity);
            f = MathUtil.clamp(f, -1.0, 1.0); // Don't go too overboard
            f += 1.0; // preserve self velocity
            velocity.multiply(f);
        }
        if (TCConfig.cartDistanceForcerConstant > 0.0) {
            velocity.add(factor.clone().multiply(TCConfig.cartDistanceForcerConstant));
        }
    }

    /**
     * Gets the minecart forward velocity on this type of Rail Logic
     *
     * @param member to get the velocity of
     * @return Forwards velocity of the minecart
     */
    public double getForwardVelocity(MinecartMember<?> member) {
        // Find segment of path we are at, and use motDot to get the velocity along it
        RailPath.Segment segment = this.getPath().findSegment(member.getEntity().loc.vector(), member.getBlock());
        if (segment != null) {
            RailPath.Position pos = new RailPath.Position();
            pos.setMotion(member.getDirection());
            segment.calcDirection(pos);
            return pos.motDot(member.getEntity().vel.vector());
        }

        // Fallback
        final CommonEntity<?> e = member.getEntity();
        final BlockFace direction = member.getDirection();
        double vel = 0.0;
        vel += e.vel.getX() * FaceUtil.cos(direction);
        vel += e.vel.getY() * direction.getModY();
        vel += e.vel.getZ() * FaceUtil.sin(direction);
        return vel;
    }

    /**
     * Sets the minecart forward velocity to go to a given direction on this type of Rail Logic
     *
     * @param member to set the velocity for
     * @param force  to set to, negative to reverse
     */
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        // Find segment of path we are at, and set a forward velocity along this segment
        RailPath.Segment segment = this.getPath().findSegment(member.getEntity().loc.vector(), member.getBlock());
        if (segment != null) {
            RailPath.Position pos = new RailPath.Position();
            pos.setMotion(member.getDirection());
            segment.calcDirection(pos);
            member.getEntity().vel.set(pos.motX * force, pos.motY * force, pos.motZ * force);
            return;
        }

        // Fallback
        final CommonEntity<?> e = member.getEntity();
        if (force == 0.0) {
            e.vel.setZero();
        } else if (!this.hasVerticalMovement() || !member.isMovingVerticalOnly()) {
            e.vel.setX(force * FaceUtil.cos(member.getDirection()));
            e.vel.setY(0.0);
            e.vel.setZ(force * FaceUtil.sin(member.getDirection()));
        } else {
            e.vel.set(0.0, force * member.getDirection().getModY(), 0.0);
        }
    }

    /**
     * Obtains the direction to which a Minecart is moving on this type of Rail Logic.
     * DEPRECATED: I'm going to move this to RailPath eventually.
     * This is only here to handle special if-this-then-down cases.
     *
     * @param endDirection block side the minecart is moving to
     * @return the BlockFace direction
     */
    @Deprecated
    public abstract BlockFace getMovementDirection(BlockFace endDirection);

    /**
     * Obtains a path consisting of connected points along which Minecarts move using this rail logic.
     * The point coordinates are relative to the coordinates of the rails block.
     * This allows the rails path to be cached, since they are unlikely to change.
     * 
     * @return rails path
     */
    public RailPath getPath() {
        return this.railPath;
    }

    /**
     * Gets the position of the Minecart when snapped to the rails. The input
     * position vector is adjusted, with the result written into the same vector.
     * 
     * @param position input and result output
     * @param railPos of the rails using this logic
     */
    public abstract void getFixedPosition(Vector position, IntVector3 railPos);

    /**
     * Is called right after all physics updates have completed, and the final orientation of the Minecart
     * entity can be adjusted. Before this is called, the orientation is already calculated from the rail path.
     * For most rail logic there is no further need to calculate rotation.<br>
     * <br>
     * The {@link MinecartMember#setOrientation(orientation) setOrientation(o)} method can be used to set
     * the orientation of the Minecart in this function. By default this is the only thing that happens,
     * which means calling super.onUpdateOrientation() with the desired orientation is enough.
     * 
     * @param member to update
     * @param orientation that is set based on rail path information
     */
    public void onUpdateOrientation(MinecartMember<?> member, Quaternion orientation) {
        member.setOrientation(orientation);
    }

    /**
     * Is called right before the minecart will perform the movement updates<br>
     * This event is called before the onPostMove event<br><br>
     * <p/>
     * Velocity changes and positional fixes that influence the final movement should occur here
     *
     * @param member to update
     */
    public void onPreMove(MinecartMember<?> member) {
        // Adjust the velocity vector to be oriented along the rail path slope
        if (!this.getPath().isEmpty()) {
            CommonMinecart<?> entity = member.getEntity();
            double vel = entity.vel.length();
            RailPath.Position pos = new RailPath.Position();
            pos.posX = entity.loc.getX();
            pos.posY = entity.loc.getY();
            pos.posZ = entity.loc.getZ();
            pos.setMotion(member.getDirection());
            this.getPath().move(pos, member.getBlock(), 0.0);
            entity.vel.set(vel * pos.motX, vel * pos.motY, vel * pos.motZ);
        }
    }

    /**
     * Is called after the minecart performed the movement updates<br>
     * This event is called after the onPreMove event<br><br>
     * <p/>
     * Final positioning updates and velocity changes for the next tick should occur here
     *
     * @param member that moved
     */
    public void onPostMove(MinecartMember<?> member) {
        
    }
}
