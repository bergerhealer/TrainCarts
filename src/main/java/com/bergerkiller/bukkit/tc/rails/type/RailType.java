package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailAABB;
import com.bergerkiller.bukkit.tc.controller.components.RailLogicState;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;
import com.bergerkiller.bukkit.tc.rails.util.RailTypeCache;
import com.bergerkiller.bukkit.tc.utils.RailInfo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    private final boolean _isComplexRailBlock;

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
        // If not a registered rail type, ignore the error. We had already disabled this rail.
        if (!values.contains(railType)) {
            return;
        }

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
        if (values.remove(type)) {
            RailTypeCache.reset();
        }
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
        RailTypeCache.reset();
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
            BlockData railsBlockData = WorldUtil.getBlockData(railsBlock);
            for (RailType type : values()) {
                try {
                    if (type.isComplexRailBlock() ? type.isRail(railsBlock) : type.isRail(railsBlockData)) {
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
     * Checks all registered rail types and attempts to load it into a {@link RailState} object. This provides
     * information such as rails block and rail type used. Some performance enhancements are used to make this
     * lookup faster for repeated calls for positions inside the same block. Note that the position does not
     * have to be the same position as the rails block itself. For example, rails that have trains hover above
     * or below it will have entirely different rails blocks.
     * 
     * @param state to load with rail information
     * @param member hint when resolving multiple rails in one block, null to ignore
     * @return True if rails were found (railtype != NONE), False otherwise
     */
    public static boolean loadRailInformation(RailState state, MinecartMember<?> member) {
        Block positionBlock = state.positionBlock();
        RailInfo[] cachedInfo = RailTypeCache.getInfo(positionBlock);
        if (cachedInfo.length == 0) {
            // Standard lookup. Cache the result if we succeed.
            try (Timings tim = TCTimings.RAILTYPE_FINDRAILINFO.start()) {
                for (RailType type : values()) {
                    try {
                        List<Block> rails = type.findRails(positionBlock);
                        if (!rails.isEmpty()) {
                            for (Block railsBlock : rails) {
                                cachedInfo = Arrays.copyOf(cachedInfo, cachedInfo.length + 1);
                                cachedInfo[cachedInfo.length - 1] = new RailInfo(positionBlock, railsBlock, type);
                            }
                            break;
                        }
                    } catch (Throwable t) {
                        handleCriticalError(type, t);
                    }
                }
            }

            // Store in cache if we have results
            if (cachedInfo.length > 0) {
                RailTypeCache.storeInfo(positionBlock, cachedInfo);
            } else {
                state.setRailBlock(positionBlock);
                state.setRailType(RailType.NONE);
                return false;
            }
        }

        // If more than one rails exists here, pick the most appropriate one for this position
        // This is a little bit slower, but required for rare instances of multiple rails per block
        RailInfo result;
        if (cachedInfo.length >= 2) {
            result = cachedInfo[0];
            double minDistSq = Double.MAX_VALUE;
            for (RailInfo info : cachedInfo) {
                state.setRailBlock(info.railBlock);
                state.setRailType(info.railType);
                Util.calculateEnterFace(state);
                RailLogic logic = state.loadRailLogic(member);
                RailPath path = logic.getPath();
                double distSq = path.distanceSquared(state.railPosition());
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    result = info;
                }
            }
        } else {
            result = cachedInfo[0];
        }

        state.setRailBlock(result.railBlock);
        state.setRailType(result.railType);
        Util.calculateEnterFace(state);
        return true;
    }

    /**
     * <b>Deprecated: use {@link #findRailInfo(Location)} instead</b>
     * 
     * @param posBlock block position where the Minecart is at
     * @return rail info at this block, null if no rails are found
     */
    @Deprecated
    public static RailInfo findRailInfo(Block posBlock) {
        return findRailInfo(new Location(posBlock.getWorld(), posBlock.getX() + 0.5, posBlock.getY() + 0.5, posBlock.getZ() + 0.5));
    }

    /**
     * Checks all registered rail types and attempts to find it for the Minecart position specified.
     * Some performance enhancements are used to make this lookup faster for repeated calls for positions inside
     * the same block. Note that the position does not have to be the same position as the rails block
     * itself. For example, rails that have trains hover above or below it will have entirely different
     * rails blocks.
     * 
     * @param position in world coordinates where to look for rails
     * @return rail info at this block, null if no rails are found
     */
    public static RailInfo findRailInfo(Location position) {
        Block positionBlock = position.getBlock();
        RailState state = new RailState();
        state.position().setLocation(position);
        state.setRailBlock(positionBlock);
        state.setRailType(RailType.NONE);
        if (loadRailInformation(state, null)) {
            //public RailInfo(Block posBlock, Block railBlock, RailType railType) {
            return new RailInfo(positionBlock, state.railBlock(), state.railType());
        } else {
            return null;
        }
    }

    public RailType() {
        // Detect whether isRail(world, x, y, z) is overrided
        // If it is not, we can optimize lookup for this rail type
        this._isComplexRailBlock = CommonUtil.isMethodOverrided(RailType.class, getClass(),
                "isRail", World.class, int.class, int.class, int.class);
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
     * Gets the bounding box of a rails block. This bounding box is used
     * to calculate the face direction when a minecart enters the rails.
     * It should surround the entire rails block for optimal results.
     * 
     * @param railsBlock
     * @return bounding box
     */
    public RailAABB getBoundingBox(Block railsBlock) {
        return RailAABB.BLOCK;
    }

    /**
     * Gets whether {@link #isRail(World, x, y, z)} is overrided, indicating this rail
     * type is more complex than a single block
     * 
     * @return True if this is a complex rail block
     */
    public final boolean isComplexRailBlock() {
        return this._isComplexRailBlock;
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
     * <b>Deprecated: use {@link #findRails(Block)} instead.</b>
     *
     * @param pos Block a Minecart is 'at'
     * @return the rail of this type, or null if not found
     */
    @Deprecated
    public Block findRail(Block pos) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Tries to find all the Rails blocks of this Rail Type for a Minecart whose position is inside
     * a particular Block. Multiple different rails can have logic for a single block area. If no rails
     * are found, it is recommended to return {@link Collections#emptyList()}.
     * 
     * @param positionBlock to find rails at
     * @return railsBlocks list of rails blocks of this rail type (do NOT return null!)
     */
    public List<Block> findRails(Block positionBlock) {
        Block rail = this.findRail(positionBlock);
        return (rail == null) ? Collections.emptyList() : Collections.singletonList(rail);
    }

    /**
     * <b>Deprecated: this is no longer being used</b>
     *
     * @param trackBlock where this Rail Type is at
     * @return Minecart position
     */
    @Deprecated
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
    public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
        RailLogicState state = new RailLogicState(null, currentTrack, currentDirection);
        RailLogic logic = this.getLogic(state);
        if (logic == null) {
            return null;
        }
        RailPath path = logic.getPath();
        if (path == null) {
            return null;
        }
        return null;
    }

    /**
     * Obtains the direction of this type of Rails.
     * This is the direction along minecarts move.
     *
     * @param railsBlock to get it for
     * @return rails Direction
     */
    public abstract BlockFace getDirection(Block railsBlock);

    /**
     * Gets the track-relative direction to look for signs related to this Rails
     * 
     * @param railsBlock to find the sign column direction for
     * @return direction to look for signs relating to this rails block
     */
    public abstract BlockFace getSignColumnDirection(Block railsBlock);

    /**
     * Gets the first block of the sign column where signs for this rail are located.
     * 
     * @param railsBlock
     * @return sign column start
     */
    public Block getSignColumnStart(Block railsBlock) {
        return railsBlock;
    }

    /**
     * Obtains the Rail Logic to use for the Minecart at the (previously calculated) rail position in a World.
     * <br>
     * <b>Deprecated: use {@link #getLogic(RailLogicState)} instead.</b>
     *
     * @param member to get the logic for (can be null when used by track walkers for e.g. spawning)
     * @param railsBlock the Minecart is driving on
     * @param direction in which the Minecart is moving. Only block directions (north/east/south/west/up/down) are used.
     * @return Rail Logic
     */
    @Deprecated
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock, BlockFace direction) {
        return RailLogicAir.INSTANCE;
    }

    /**
     * Obtains the Rail Logic to use for the rail logic state situation specified
     * 
     * @param state input
     * @return desired rail logic
     */
    public RailLogic getLogic(RailLogicState state) {
        return getLogic(state.getMember(), state.getRailsBlock(), state.getEnterDirection());
    }

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
        at.setYaw(FaceUtil.faceToYaw(orientation));
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
