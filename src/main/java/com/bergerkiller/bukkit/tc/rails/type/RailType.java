package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RailType {
    public static final RailTypeVertical VERTICAL = new RailTypeVertical();
    public static final RailTypeActivator ACTIVATOR_ON = new RailTypeActivator(true);
    public static final RailTypeActivator ACTIVATOR_OFF = new RailTypeActivator(false);
    public static final RailTypeCrossing CROSSING = new RailTypeCrossing();
    public static final RailTypeRegular REGULAR = new RailTypeRegular();
    public static final RailTypeDetector DETECTOR = new RailTypeDetector();
    public static final RailTypePowered BRAKE = new RailTypePowered(false);
    public static final RailTypePowered BOOST = new RailTypePowered(true);
    public static final RailTypeNone NONE = new RailTypeNone();
    private static final List<RailType> values = new ArrayList<RailType>();

    static {
        for (RailType type : CommonUtil.getClassConstants(RailType.class)) {
            if (type != NONE) {
                values.add(type);
            }
        }
    }

    /**
     * Handles a critical error that occurred while using a certain RailType.
     * If the RailType was externally registered by a plugin, it is unregistered to prevent
     * further failing of TrainCarts itself.
     * 
     * @param railType to unregister
     * @param reason for unregistering
     */
    public static void handleCriticalError(RailType railType, Throwable reason) {
        Plugin plugin = CommonUtil.getPluginByClass(railType.getClass());
        Logger logger = TrainCarts.plugin.getLogger();
        if (plugin == TrainCarts.plugin) {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "'", reason);
        } else if (plugin != null) {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "' " +
                                     "from plugin " + plugin.getName() + ". The rail type has been disabled.", reason);
            unregister(railType);
        } else {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "' " + 
                                     "from an unknown plugin. The rail type has been disabled.", reason);
            unregister(railType);
        }
    }

    /**
     * Unregisters a Rail Type so it can no longer be used
     *
     * @param type to unregister
     */
    public static void unregister(RailType type) {
        values.remove(type);
    }

    /**
     * Registers a Rail Type for use
     *
     * @param type         to register
     * @param withPriority - True to make it activate prior to other types, False after
     */
    public static void register(RailType type, boolean withPriority) {
        if (withPriority) {
            values.add(0, type);
        } else {
            values.add(type);
        }
    }

    /**
     * Gets a Collection of all available Rail Types.
     * The NONE constant is not included.
     *
     * @return Rail Types
     */
    public static Collection<RailType> values() {
        return values;
    }

    /**
     * Tries to find the Rail Type a specific rails block represents.
     * If none is identified, NONE is returned.
     *
     * @param railsBlock to get the RailType of
     * @return the RailType, or NONE if not found
     */
    public static RailType getType(Block railsBlock) {
        if (railsBlock != null) {
            for (RailType type : values()) {
                try {
                    if (type.isRail(railsBlock)) {
                        return type;
                    }
                } catch (Throwable t) {
                    handleCriticalError(type, t);
                    break;
                }
            }
        }
        return NONE;
    }

    /**
     * Checks whether the block data given denote this type of Rail.
     * This function is called from {@link #isRail(world, x, y, z)} exclusively.
     * If the rails is more complex than one type of Block, override that method
     * and ignore this one.
     *
     * @param blockData of the Block
     * @return True if it is this type of Rail, False if not
     */
    public abstract boolean isRail(BlockData blockData);

    /**
     * Checks whether the Block specified denote this type of Rail.
     * To check for complex structures, this method should be overrided to check for that.
     *
     * @param world the Block is in
     * @param x     - coordinate of the Block
     * @param y     - coordinate of the Block
     * @param z     - coordinate of the Block
     * @return True if it is this Rail, False if not
     */
    public boolean isRail(World world, int x, int y, int z) {
        return isRail(WorldUtil.getBlockData(world, x, y, z));
    }

    /**
     * Checks whether the Block face specified denote this type of Rail
     *
     * @param block  to check
     * @param offset face from the block to check
     * @return True if it is this Rail, False if not
     */
    public final boolean isRail(Block block, BlockFace offset) {
        return isRail(block.getWorld(), block.getX() + offset.getModX(), block.getY() + offset.getModY(),
                block.getZ() + offset.getModZ());
    }

    /**
     * Checks whether the Block specified denote this type of Rail
     *
     * @param block to check
     * @return True if it is this Rail, False if not
     */
    public final boolean isRail(Block block) {
        return isRail(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Gets whether blocks surrounding the rails block indicate the rails is used upside-down.
     * It is only upside-down when the block 'below' the rails is air, and a solid block exists above.
     * This strict rule avoids regular tracks turning into upside-down tracks.
     * Rail types that don't support upside-down Minecarts should always return false here.
     * 
     * @param railsBlock
     * @return True if the rails are upside-down
     */
    public boolean isUpsideDown(Block railsBlock) {
        return false;
    }

    /**
     * <b>Deprecated: this function is never used anymore, only {@link #findRail(Block)} is</b>
     */
    @Deprecated
    public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
        Block rail = this.findRail(pos.toBlock(world));
        return (rail == null) ? null : new IntVector3(rail);
    }

    /**
     * Tries to find this Rail Type near a 'next' Block returned by {@link #getNextPos(Block, BlockFace)}
     *
     * @param pos Block a Minecart is 'at'
     * @return the rail of this type, or null if not found
     */
    public abstract Block findRail(Block pos);

    /**
     * Gets the Block where a Minecart would be if it was using this rail.
     * This is the inverse of {@link #findRail(Block)}.
     *
     * @param trackBlock where this Rail Type is at
     * @return Minecart position
     */
    public abstract Block findMinecartPos(Block trackBlock);

    /**
     * Gets an array containing all possible directions a Minecart can move on the trackBlock.
     *
     * @param trackBlock to use
     * @return all possible directions the Minecart can move
     */
    public abstract BlockFace[] getPossibleDirections(Block trackBlock);

    /**
     * Gets the next Minecart Position Block while moving on this type of Rail.
     * The goal of this method is to find out where Minecarts that enter this rail
     * end up at when moving forward.<br><br>
     * <p/>
     * If the result is null, then this Rail Type forcibly disallows that direction
     * from being used, and no movement was possible.
     *
     * @param currentTrack     of this rail type the 'Minecart' is using to drive on
     * @param currentDirection the 'Minecart' is moving
     * @return next Block the minecart is at after moving over this rail
     */
    public abstract Block getNextPos(Block currentTrack, BlockFace currentDirection);

    /**
     * Obtains the direction of this type of Rails.
     * This is the direction along minecarts move.
     *
     * @param railsBlock to get it for
     * @return rails Direction
     */
    public abstract BlockFace getDirection(Block railsBlock);

    /**
     * Gets the track-relative position to look for signs related to this Rails
     * 
     * @param railsBlock to find the sign column direction for
     * @return direction to look for signs relating to this rails block
     */
    public abstract BlockFace getSignColumnDirection(Block railsBlock);

    /**
     * Obtains the Rail Logic to use for the Minecart at the (previously calculated) rail position in a World.
     *
     * @param member     to get the logic for
     * @param railsBlock the Minecart is driving on
     * @return Rail Logic
     */
    public abstract RailLogic getLogic(MinecartMember<?> member, Block railsBlock);

    /**
     * Called one tick after a block of this Rail Type was placed down in the world
     * 
     * @param railsBlock that was placed
     */
    public void onBlockPlaced(Block railsBlock) {
    }

    /**
     * Called when block physics are being performed for a Block matching this Rail Type.
     * 
     * @param event block physics event
     */
    public void onBlockPhysics(BlockPhysicsEvent event) {
    }

    /**
     * Gets whether this Rails Type is supported by a block it is attached to.
     * If this returns False, the rails block is automatically broken and an item is dropped.
     * 
     * @param railsBlock to check
     * @return True if the rails block is supported
     */
    public boolean isRailsSupported(Block railsBlock) {
        return true;
    }

    /**
     * Called right before a Minecart is moved from one point to the other.
     * This is called after the pre-movement updates performed by rail logic.
     *
     * @param member that is about to be moved
     */
    public void onPreMove(MinecartMember<?> member) {
    }

    /**
     * Called right after a Minecart was moved from one point to the other.
     * This is called after the post-movement updates performed by rail logic.
     *
     * @param member that just moved
     */
    public void onPostMove(MinecartMember<?> member) {
    }

    /**
     * Handles collision with this Rail Type
     *
     * @param with    Minecart that his this Rail
     * @param block   of this Rail
     * @param hitFace of this Rail
     * @return True if collision is allowed, False if not
     */
    public boolean onCollide(MinecartMember<?> with, Block block, BlockFace hitFace) {
        return true;
    }

    /**
     * Handles a Minecart colliding with a Block while using this Rail Type.
     *
     * @param member     that collided
     * @param railsBlock the member is driving on
     * @param hitBlock   the Minecart hit
     * @param hitFace    the Minecart hit
     * @return True if collision is allowed, False if not
     */
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock, BlockFace hitFace) {
        return true;
    }

    /**
     * Gets whether a minecart hit a block 'head-on', meaning it should stop the train
     * 
     * @param member that hit a block
     * @param railsBlock the minecart is on
     * @param hitBlock that was hit
     * @return True if head-on, False if not
     */
    public boolean isHeadOnCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock) {
        return false;
    }

    /**
     * Gets the initial location of a minecart when placed on this Rail Type.
     * By default spawns the Minecart on top of the block, facing the orientation
     * 
     * @param railsBlock to spawn on
     * @param orientation horizontal orientation of the one that placed the minecart
     * @return spawn location
     */
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        Location at = this.findMinecartPos(railsBlock).getLocation();
        if (this.isUpsideDown(railsBlock)) {
            at.add(0.5, RailLogicHorizontal.Y_POS_OFFSET_UPSIDEDOWN, 0.5);
            at.setPitch(-180.0F);
        } else {
            at.add(0.5, RailLogicHorizontal.Y_POS_OFFSET, 0.5);
            at.setPitch(0.0F);
        }
        BlockFace dir = this.getDirection(railsBlock);
        if (FaceUtil.isSubCardinal(dir)) {
            at.setYaw(FaceUtil.faceToYaw(dir) - 90.0f);
        } else {
            at.setYaw(FaceUtil.faceToYaw(dir));
        }
        return at;
    }

    /**
     * Gets rails texture information about this Rail Type for a particular Block.
     * This texture is displayed in the editor.
     * 
     * @param railsBlock
     * @return rails texture
     */
    public RailsTexture getRailsTexture(Block railsBlock) {
        return new RailsTexture();
    }
}
