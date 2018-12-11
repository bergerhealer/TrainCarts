package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Represents a point in time of a Minecart or virtual point moving over rails.
 * All this information is required to properly handle rail logic.
 */
public class RailState {
    private RailPiece _railPiece;
    private final Vector _enterDirection;
    private final Vector _enterPosition;
    private MinecartMember<?> _member;
    private final RailPath.Position _position;

    public RailState() {
        this._railPiece = RailPiece.NONE;
        this._enterDirection = new Vector(Double.NaN, Double.NaN, Double.NaN);
        this._enterPosition = new Vector(Double.NaN, Double.NaN, Double.NaN);
        this._member = null;
        this._position = new RailPath.Position();
        this._position.relative = false;
    }

    /**
     * Returns the rail path position information, which stores the position,
     * orientation and movement direction. This returned {@link RailPath.Position} object
     * is mutable.
     * 
     * @return position
     */
    public RailPath.Position position() {
        return this._position;
    }

    /**
     * Sets the rail path position information. See {@link #position()}.
     * 
     * @param position
     */
    public void setPosition(RailPath.Position position) {
        position.copyTo(this._position);
    }

    /**
     * Gets the Block at the current {@link #position()}.
     * 
     * @return position block
     */
    public Block positionBlock() {
        if (this._position.relative) {
            Block railBlock = this.railBlock();
            if (railBlock == null) {
                throw new IllegalStateException("Rails Block must be set before positionBlock can be obtained");
            }
            return railBlock.getWorld().getBlockAt(
                    railBlock.getX() + MathUtil.floor(this._position.posX),
                    railBlock.getY() + MathUtil.floor(this._position.posY),
                    railBlock.getZ() + MathUtil.floor(this._position.posZ));
        } else {
            World railWorld = this.railWorld();
            if (railWorld == null) {
                throw new IllegalStateException("Rails Block or World must be set before positionBlock can be obtained");
            }
            return railWorld.getBlockAt(
                    MathUtil.floor(this._position.posX),
                    MathUtil.floor(this._position.posY),
                    MathUtil.floor(this._position.posZ));
        }
    }

    /**
     * Turns the current {@link #position()} into a Bukkit Location object, with yaw and pitch set to the
     * direction that is being moved.
     * 
     * @return position location
     */
    public Location positionLocation() {
        Block railBlock = this.railBlock();
        if (railBlock == null) {
            throw new IllegalStateException("Rails Block must be set before positionLocation can be obtained");
        }
        if (this._position.relative) {
            return new Location(
                    railBlock.getWorld(),
                    railBlock.getX() + this._position.posX,
                    railBlock.getY() + this._position.posY,
                    railBlock.getZ() + this._position.posZ,
                    MathUtil.getLookAtYaw(this._position.motX, this._position.motZ),
                    MathUtil.getLookAtPitch(this._position.motX, this._position.motY, this._position.motZ));
        } else {
            return new Location(
                    railBlock.getWorld(),
                    this._position.posX,
                    this._position.posY,
                    this._position.posZ,
                    MathUtil.getLookAtYaw(this._position.motX, this._position.motZ),
                    MathUtil.getLookAtPitch(this._position.motX, this._position.motY, this._position.motZ));
        }
    }

    /**
     * Retrieves the motion vector from {@link #position()}.
     * 
     * @return motion vector
     */
    public Vector motionVector() {
        return this._position.getMotion();
    }

    /**
     * Sets the motion vector of {@link #position()}.
     * 
     * @param motionVector to set to
     */
    public void setMotionVector(Vector motionVector) {
        this._position.setMotion(motionVector);
    }

    /**
     * Gets the rail piece information
     * 
     * @return rail piece
     */
    public RailPiece railPiece() {
        return this._railPiece;
    }

    /**
     * Sets the rail piece information
     * 
     * @param railPiece to set to
     */
    public void setRailPiece(RailPiece railPiece) {
        this._railPiece = railPiece;
    }

    /**
     * Gets the rails world which is currently used to control the movement of the path.
     * This is equivalent to {@link #railPiece()}.{@link RailPiece#world() world()}
     * 
     * @return rails world
     */
    public final World railWorld() {
        return this._railPiece.world();
    }

    /**
     * Gets the rails block which is currently used to control the movement of the path.
     * This is equivalent to {@link #railPiece()}.{@link RailPiece#block() block()}
     * 
     * @return rails block
     */
    public final Block railBlock() {
        return this._railPiece.block();
    }

    /**
     * Gets the rails type which is currently used to control the movement of the path.
     * This is equivalent to {@link #railPiece()}.{@link RailPiece#type() type()}
     * 
     * @return rails type
     */
    public final RailType railType() {
        return this._railPiece.type();
    }

    /**
     * Sets the rails block.<br>
     * <b>Deprecated: use {@link #setRailPiece(RailPiece)} instead</b>
     * 
     * @param railsBlock
     */
    @Deprecated
    public void setRailBlock(Block railsBlock) {
        this.setRailPiece(RailPiece.create(this._railPiece.type(), railsBlock));
    }

    /**
     * Sets the rail type.<br>
     * <b>Deprecated: use {@link #setRailPiece(RailPiece)} instead</b>
     * 
     * @param type
     */
    @Deprecated
    public void setRailType(RailType type) {
        if (this._railPiece.type() == type) {
            return;
        }
        this.setRailPiece(RailPiece.create(type, this._railPiece.block()));
    }

    /**
     * Gets the relative position on the rails. This is the absolute {@link #position()} with the
     * {@link #railBlock()} coordinates subtracted.
     * 
     * @return rail relative position
     */
    public Vector railPosition() {
        if (this._position.relative) {
            return new Vector(this._position.posX,
                              this._position.posY,
                              this._position.posZ);
        } else {
            Block railBlock = this.railBlock();
            if (railBlock == null) {
                throw new IllegalStateException("Rails Block must be set before relative railPosition can be obtained");
            }
            return new Vector(this._position.posX - railBlock.getX(),
                              this._position.posY - railBlock.getY(),
                              this._position.posZ - railBlock.getZ());
        }
    }

    /**
     * Gets whether the enter direction has been initialized.
     * 
     * @return True if enter direction is initialized
     */
    public boolean hasEnterDirection() {
        return !Double.isNaN(this._enterDirection.getX());
    }

    /**
     * The movement direction of the Cart upon entering the section of rails
     * 
     * @return enter direction
     */
    public Vector enterDirection() {
        if (!hasEnterDirection()) {
            throw new IllegalStateException("Enter direction has not been initialized");
        }
        return this._enterDirection;
    }

    /**
     * Initializes the enter direction, setting it to the current motion vector direction.
     */
    public void initEnterDirection() {
        if (Double.isNaN(this._position.motX)) {
            throw new IllegalStateException("Position motion vector is NaN");
        }
        this._enterDirection.setX(this._position.motX);
        this._enterDirection.setY(this._position.motY);
        this._enterDirection.setZ(this._position.motZ);
        this._enterPosition.setX(this._position.posX);
        this._enterPosition.setY(this._position.posY);
        this._enterPosition.setZ(this._position.posZ);
    }

    /**
     * Gets the Block Face that was entered of the Block at the current position.
     * This helper function is only useful for rails blocks that cover an exact block,
     * like standard Vanilla rails.
     * 
     * @return entered Block Face
     */
    public BlockFace enterFace() {
        Vector d = this.enterDirection();

        // Optimize the cases when the direction vector is perfectly along an axis
        // This situation occurs very frequently on straight rails
        double ls = d.lengthSquared();
        if (ls == (d.getX()*d.getX())) {
            return (d.getX() >= 0.0) ? BlockFace.EAST : BlockFace.WEST;
        }
        if (ls == (d.getZ()*d.getZ())) {
            return (d.getZ() >= 0.0) ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        if (ls == (d.getY()*d.getY())) {
            return (d.getY() >= 0.0) ? BlockFace.UP : BlockFace.DOWN;
        }

        // Create position relative to block coordinates and ask AABB about it
        Vector p = this._enterPosition;
        Vector pos = new Vector(p.getX() - p.getBlockX(),
                                p.getY() - p.getBlockY(),
                                p.getZ() - p.getBlockZ());
        return RailAABB.BLOCK.calculateEnterFace(pos, d);
    }

    /**
     * When applicable and available, returns the minecart that is using these
     * rails right now.
     * 
     * @return member using these rails
     */
    public MinecartMember<?> member() {
        return this._member;
    }

    /**
     * Sets the minecart member that is using these rails right now.
     * 
     * @param member
     */
    public void setMember(MinecartMember<?> member) {
        this._member = member;
    }

    /**
     * Checks whether this rail state has the exact same rails as another rail state
     * 
     * @param other state
     * @return True if the same rails
     */
    public boolean isSameRails(RailState other) {
        return this.railType() == other.railType() && BlockUtil.equals(this.railBlock(), other.railBlock());
    }

    /**
     * Queries the rail type for the rail logic to use with this rail state
     * 
     * @param member hint for the logic, null to ignore
     * @return Rail Logic
     */
    public RailLogic loadRailLogic() {
        RailLogic logic = this.railType().getLogic(this);
        logic.onPathAdjust(this);
        return logic;
    }

    @Override
    public RailState clone() {
        RailState state = new RailState();
        this.position().copyTo(state.position());
        state.setRailPiece(this.railPiece());
        state.setMember(this.member());
        state._enterDirection.setX(this._enterDirection.getX());
        state._enterDirection.setY(this._enterDirection.getY());
        state._enterDirection.setZ(this._enterDirection.getZ());
        state._enterPosition.setX(this._enterPosition.getX());
        state._enterPosition.setY(this._enterPosition.getY());
        state._enterPosition.setZ(this._enterPosition.getZ());
        return state;
    }

    @Override
    public String toString() {
        return "{rail=" + this._railPiece.toString() +
                ", pos=" + this._position + "}";
    }

    /**
     * Gets the Rail State when spawned on a rails block<br>
     * <b>Deprecated: {@link #getSpawnState(RailPiece)} is more efficient</b>
     * 
     * @param railType
     * @param railBlock
     * @return spawn state
     */
    @Deprecated
    public static RailState getSpawnState(RailType railType, Block railBlock) {
        return getSpawnState(RailPiece.create(railType, railBlock));
    }

    /**
     * Gets the Rail State when spawned on a rails block
     * 
     * @param railPiece
     * @return spawn state
     */
    public static RailState getSpawnState(RailPiece railPiece) {
        RailState state = new RailState();
        state.setRailPiece(railPiece);
        state.position().setLocation(railPiece.type().getSpawnLocation(railPiece.block(), BlockFace.NORTH));
        RailType.loadRailInformation(state);
        state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
        return state;
    }
}
