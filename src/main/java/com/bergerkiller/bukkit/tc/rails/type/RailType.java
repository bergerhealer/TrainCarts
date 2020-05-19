package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.cache.RailPieceCache;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailAABB;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;

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
            RailPieceCache.reset();
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
        RailPieceCache.reset();
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
     * @return True if rails were found (railtype != NONE), False otherwise
     */
    public static boolean loadRailInformation(RailState state) {
        state.initEnterDirection();
        state.position().assertAbsolute();
        RailPiece[] cachedPieces = RailPieceCache.find(state);
        if (cachedPieces.length == 0) {
            // Standard lookup. Cache the result if we succeed.
            Block positionBlock = state.positionBlock();
            try (Timings tim = TCTimings.RAILTYPE_FINDRAILINFO.start()) {
                for (RailType type : values()) {
                    try {
                        List<Block> rails = type.findRails(positionBlock);
                        if (!rails.isEmpty()) {
                            int index = cachedPieces.length;
                            cachedPieces = Arrays.copyOf(cachedPieces, cachedPieces.length + rails.size());
                            for (Block railsBlock : rails) {
                                cachedPieces[index++] = RailPiece.create(type, railsBlock);
                            }
                            break;
                        }
                    } catch (Throwable t) {
                        handleCriticalError(type, t);
                    }
                }
            }

            // Store in cache if we have results
            if (cachedPieces.length > 0) {
                RailPieceCache.storeInfo(positionBlock, cachedPieces);
            } else {
                state.setRailPiece(RailPiece.create(RailType.NONE, positionBlock));
                return false;
            }
        }

        // If more than one rail piece exists here, pick the most appropriate one for this position
        // This is a little bit slower, but required for rare instances of multiple rails per block
        RailPiece resultPiece = cachedPieces[0];
        if (cachedPieces.length >= 2) {
            RailPath.ProximityInfo nearest = null;
            for (RailPiece piece : cachedPieces) {
                state.setRailPiece(piece);
                RailLogic logic = state.loadRailLogic();
                RailPath path = logic.getPath();
                RailPath.ProximityInfo near = path.getProximityInfo(state.railPosition(), state.motionVector());
                if (nearest == null || near.compareTo(nearest) < 0) {
                    nearest = near;
                    resultPiece = piece;
                }
            }
        }

        state.setRailPiece(resultPiece);
        return true;
    }

    /**
     * <b>Deprecated: use {@link #findRailPiece(Location)} instead</b>
     * 
     * @param blockPosition block position where the Minecart is at
     * @return rail piece at this block, null if no rails are found
     */
    @Deprecated
    public static RailPiece findRailPiece(Block blockPosition) {
        RailState state = new RailState();
        state.position().setLocationMidOf(blockPosition);
        state.setRailPiece(RailPiece.createWorldPlaceholder(blockPosition.getWorld()));
        if (loadRailInformation(state)) {
            return state.railPiece();
        } else {
            return null;
        }
    }

    /**
     * Checks all registered rail types and attempts to find the rail piece used for the Minecart position specified.
     * Some performance enhancements are used to make this lookup faster for repeated calls for positions inside
     * the same block. Note that the position does not have to be the same position as the rails block
     * itself. For example, rails that have trains hover above or below it will have entirely different
     * rails blocks.
     * 
     * @param position in world coordinates where to look for rails
     * @return rail piece at this position, null if no rails are found
     */
    public static RailPiece findRailPiece(Location position) {
        RailState state = new RailState();
        state.position().setLocation(position);
        state.setRailPiece(RailPiece.createWorldPlaceholder(position.getWorld()));
        if (loadRailInformation(state)) {
            return state.railPiece();
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
     * It should surround the entire rails section for optimal results.
     * By default returns a standard 1x1x1 bounding box.
     * 
     * @param state of the rails
     * @return bounding box
     */
    public RailAABB getBoundingBox(RailState state) {
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
     * Gets an array containing all possible directions a Minecart can move on the trackBlock.<br>
     * <b>Deprecated: implement {@link #getJunctions()} instead (if needed)</b>
     *
     * @param trackBlock to use
     * @return all possible directions the Minecart can move
     */
    @Deprecated
    public abstract BlockFace[] getPossibleDirections(Block trackBlock);

    /**
     * Gets an array containing all possible junctions that can be taken for a particular rail block.
     * There does not have to be a valid rail at the end for a junction to exist. By default the two end
     * points of the path returned by the logic for a 'down' direction are returned.
     * 
     * @param railBlock where this Rail Type is at
     * @return list of junctions supported by this rail type, empty if no junctions are available
     */
    public List<RailJunction> getJunctions(Block railBlock) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railBlock));
        state.position().setLocation(this.getSpawnLocation(railBlock, BlockFace.DOWN));
        state.position().setMotion(BlockFace.DOWN);
        state.initEnterDirection();

        RailPath path = this.getLogic(state).getPath();
        if (path.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(new RailJunction("1", path.getStartPosition()),
                                 new RailJunction("2", path.getEndPosition()));
        }
    }

    /**
     * Prepares a {@link RailState} when taking a junction returned by {@link #getJunctions(Block)}.
     * Feeding this state into a walking point will enable further discovery past the junction.
     * 
     * @param railBlock where this Rail Type is at
     * @param junction to check
     * @return RailState after taking the junction, null if there is no rails here
     */
    public RailState takeJunction(Block railBlock, RailJunction junction) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railBlock));
        junction.position().copyTo(state.position());
        state.position().makeAbsolute(railBlock);
        state.position().smallAdvance();
        if (!loadRailInformation(state)) {
            return null; // No rail here
        }
        if (state.railType() == this && state.railBlock().equals(railBlock)) {
            return null; // Same rail - avoid cyclical loop error
        }
        return state;
    }

    /**
     * Switches the rails from one junction to another. Junctions are used from {@link #getJunctions(railBlock)}.
     * 
     * @param railBlock where this Rail Type is at
     * @param from junction
     * @param to junction
     */
    public void switchJunction(Block railBlock, RailJunction from, RailJunction to) {
    }

    /**
     * Obtains the direction of this type of Rails.
     * This is the direction along minecarts move.<br>
     * <br>
     * <b>Deprecated: BlockFace offers too little information, use RailState for computing this instead</b>
     *
     * @param railsBlock to get it for
     * @return rails Direction
     */
    @Deprecated
    public BlockFace getDirection(Block railsBlock) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railsBlock));
        state.setPosition(Position.fromLocation(this.getSpawnLocation(railsBlock, BlockFace.SELF)));
        state.initEnterDirection();
        return state.enterFace();
    }

    /**
     * Gets the track-relative direction to look for signs related to this Rails
     * 
     * @param railsBlock to find the sign column direction for
     * @return direction to look for signs relating to this rails block
     */
    public abstract BlockFace getSignColumnDirection(Block railsBlock);

    /**
     * Gets the default trigger (movement) directions of trains on the rails
     * that can activate signs. These directions can be overrided on the sign, so these
     * are the default if none are specified.<br>
     * <br>
     * By default returns BLOCK_SIDES (up/down/north/east/south/west) to indicate all
     * possible directions activate the sign.
     * 
     * @param railBlock The rail block that has this RailType
     * @param signBlock The sign block of the sign being activated
     * @param signFacing The facing of the sign being activated
     * @return sign trigger directions
     */
    public BlockFace[] getSignTriggerDirections(Block railBlock, Block signBlock, BlockFace signFacing) {
        return FaceUtil.BLOCK_SIDES;
    }

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
     * Obtains the Rail Logic to use for the rail state situation specified
     * 
     * @param state input
     * @return desired rail logic
     */
    public RailLogic getLogic(RailState state) {
        return getLogic(state.member(), state.railBlock(), state.enterFace());
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
     * Gets whether this Rail Type uses block activation as part of basic physics.
     * Examples are detector rails and pressure plates.
     * This property is used to optimize movement physics by not checking when not needed.
     * Is ignored completely when this optimization is turned off in the configuration.
     * 
     * @param railBlock
     * @return True if block activation is used
     */
    public boolean hasBlockActivation(Block railBlock) {
        return false;
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
    public abstract Location getSpawnLocation(Block railsBlock, BlockFace orientation);

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
