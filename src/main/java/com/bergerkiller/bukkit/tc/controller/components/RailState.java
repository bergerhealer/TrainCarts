package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
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
    private Block _railBlock;
    private RailType _railType;
    private BlockFace _enterFace;
    private final RailPath.Position _position;

    public RailState() {
        this._railBlock = null;
        this._railType = RailType.NONE;
        this._enterFace = null;
        this._position = new RailPath.Position();
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
     * Gets the Block at the current {@link #position()}.
     * 
     * @return position block
     */
    public Block positionBlock() {
        if (this._railBlock == null) {
            throw new IllegalStateException("Rails Block must be set before positionBlock can be obtained");
        }
        return this._railBlock.getWorld().getBlockAt(
                MathUtil.floor(this._position.posX),
                MathUtil.floor(this._position.posY),
                MathUtil.floor(this._position.posZ));
    }

    /**
     * Turns the current {@link #position()} into a Bukkit Location object, with yaw and pitch set to the
     * direction that is being moved.
     * 
     * @return position location
     */
    public Location positionLocation() {
        if (this._railBlock == null) {
            throw new IllegalStateException("Rails Block must be set before positionLocation can be obtained");
        }
        return new Location(
                this._railBlock.getWorld(),
                this._position.posX,
                this._position.posY,
                this._position.posZ,
                MathUtil.getLookAtYaw(this._position.motX, this._position.motZ),
                MathUtil.getLookAtPitch(this._position.motX, this._position.motY, this._position.motZ));
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
     * Gets the rails block which is currently used to control the movement of the path.
     * 
     * @return rails block
     */
    public Block railBlock() {
        return this._railBlock;
    }

    /**
     * Sets the rails block. See {@link #railBlock()}.
     * 
     * @param railsBlock
     */
    public void setRailBlock(Block railsBlock) {
        this._railBlock = railsBlock;
    }

    /**
     * Gets the rails type which is currently used to control the movement of the path.
     * 
     * @return rails type
     */
    public RailType railType() {
        return this._railType;
    }

    /**
     * Sets the rail type. See {@link #railType()}
     * @param type
     */
    public void setRailType(RailType type) {
        this._railType = type;
    }

    /**
     * Gets the relative position on the rails. This is the absolute {@link #position()} with the
     * {@link #railBlock()} coordinates subtracted.
     * 
     * @return rail relative position
     */
    public Vector railPosition() {
        if (this._railBlock == null) {
            throw new IllegalStateException("Rails Block must be set before railPosition can be obtained");
        }
        return new Vector(this._position.posX - this._railBlock.getX(),
                          this._position.posY - this._railBlock.getY(),
                          this._position.posZ - this._railBlock.getZ());
    }

    /**
     * Sets the Block Face that was entered of the Block at the current position.
     * 
     * @return entered Block Face
     */
    public BlockFace enterFace() {
        if (this._enterFace == null) {
            throw new IllegalStateException("Enter face has not been initialized");
        }
        return this._enterFace;
    }

    /**
     * Sets the Block Face that was entered of the rails Block at the current position.
     * 
     * @param enterFace to set to
     */
    public void setEnterFace(BlockFace enterFace) {
        this._enterFace = enterFace;
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
    public RailLogic loadRailLogic(MinecartMember<?> member) {
        RailLogicState state = new RailLogicState(member, this.railPosition(), this.railBlock(), this.enterFace());
        RailLogic logic = this.railType().getLogic(state);
        logic.onPathAdjust(this);
        return logic;
    }

    @Override
    public RailState clone() {
        RailState state = new RailState();
        this.position().copyTo(state.position());
        state.setRailBlock(this.railBlock());
        state.setRailType(this.railType());
        state.setEnterFace(this.enterFace());
        return state;
    }

    @Override
    public String toString() {
        return "{rail={" + this._railBlock.getX() + "/" +
                this._railBlock.getY() + "/" +
                this._railBlock.getZ() + "/" +
                this._railType + "}" +
                ", pos=" + this._position + "}";
    }
}
