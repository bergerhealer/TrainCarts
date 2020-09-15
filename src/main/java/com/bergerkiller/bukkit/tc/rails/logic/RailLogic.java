package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles how a minecart travels on top of a rail
 */
public abstract class RailLogic {
    protected final boolean alongZ, alongX, alongY, curved;
    private final BlockFace horizontalDir;
    private RailPath railPath;

    public RailLogic(BlockFace horizontalDirection) {
        this.horizontalDir = horizontalDirection;
        this.alongX = FaceUtil.isAlongX(horizontalDirection);
        this.alongZ = FaceUtil.isAlongZ(horizontalDirection);
        this.alongY = FaceUtil.isAlongY(horizontalDirection);
        this.curved = !alongZ && !alongY && !alongX;
        this.railPath = null;
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
        final CommonEntity<?> e = member.getEntity();

        // Find segment of path we are at, and use motDot to get the velocity along it
        RailPath.Segment segment = this.getPath().findSegment(member.getEntity().loc.vector(), member.getBlock());
        double dot;
        if (segment != null) {
            RailPath.Position pos = new RailPath.Position();
            pos.setMotion(member.getDirection());
            segment.calcDirection(pos);
            dot = pos.motDot(member.getEntity().vel.vector());
        } else {
            // Fallback
            final BlockFace direction = member.getDirection();
            dot = e.vel.getX() * FaceUtil.cos(direction) +
                  e.vel.getY() * direction.getModY() +
                  e.vel.getZ() * FaceUtil.sin(direction);
        }

        return MathUtil.invert(e.vel.length(), dot < 0.0);
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
            pos.setMotion(member.getRailTracker().getMotionVector());
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
    public BlockFace getMovementDirection(BlockFace endDirection) {
        return endDirection;
    }

    public BlockFace getMovementDirection(Block railsBlock, Block positionBlock, BlockFace endDirection) {
        RailPath path = this.getPath();
        if (path.isEmpty()) {
            return endDirection;
        }
        RailPath.Position position = new RailPath.Position();
        position.setLocationMidOf(positionBlock);
        position.posX -= 0.5 * endDirection.getModX();
        position.posY -= 0.5 * endDirection.getModY();
        position.posZ -= 0.5 * endDirection.getModZ();
        position.setMotion(endDirection);
        path.snap(position, railsBlock);
        return Util.vecToFace(position.motX, position.motY, position.motZ, true);
    }

    /**
     * Callback which allows Rail Logic to alter the movement direction or general positioning
     * of the rail state during path tracking.
     * 
     * @param state
     */
    public void onPathAdjust(RailState state) {
    }

    /**
     * Obtains a path consisting of connected points along which Minecarts move using this rail logic.
     * The point coordinates are relative to the coordinates of the rails block.
     * This allows the rails path to be cached, since they are unlikely to change.
     * 
     * @return rails path
     */
    public RailPath getPath() {
        if (this.railPath == null) {
            this.railPath = this.createPath();
        }
        return this.railPath;
    }

    /**
     * This method is called once the first time {@link #getPath()} is invoked to generate
     * the appropriate rail path to use. To set the path to use for this rail logic,
     * override this method. If the path changes for the duration the rail logic is in use,
     * it is better to override and handle {@link #getPath()} instead.
     * 
     * @return path to use
     */
    protected RailPath createPath() {
        return RailPath.EMPTY;
    }

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
        member.snapToPath(getPath());
        
        // Adjust the velocity vector to be oriented along the rail path slope
        //System.out.println("VEL: " + member.getEntity().vel + "    DIR " + member.getDirection());
        if (!this.getPath().isEmpty()) {
            CommonMinecart<?> entity = member.getEntity();
            double vel = entity.vel.length();
            RailPath.Position pos = new RailPath.Position();
            pos.setLocation(entity.loc);
            pos.setMotion(entity.vel);
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
        member.snapToPath(getPath());
    }

    /**
     * Performs gravity physics for this rail logic. By default, aligns the effect of
     * gravity along the rail path direction.
     * 
     * @param member that has gravity updated
     * @param gravityFactorSquared Combination of the physics update factor and gravity property
     */
    public void onGravity(MinecartMember<?> member, double gravityFactorSquared) {
        CommonMinecart<?> e = member.getEntity();
        Block block = member.getRailTracker().getBlock();
        RailPath.Segment segment = getPath().findSegment(e.loc.vector(), block);
        if (segment == null) {
            // Not on any segment? Simply subtract GRAVITY_MULTIPLIER
            // This case should be handled by the rail logic implementations that lack paths directly
            e.vel.y.subtract(gravityFactorSquared * getGravityMultiplier(member));
        } else if (segment.dt_norm.y < -1e-6 || segment.dt_norm.y > 1e-6) {
            // On a non-level segment, gravity must be applied based on the slope the segment is at
            double f = gravityFactorSquared * getGravityMultiplier(member) * segment.dt_norm.y;
            e.vel.subtract(segment.dt_norm.x * f, segment.dt_norm.y * f, segment.dt_norm.z * f);
        }
    }
}
