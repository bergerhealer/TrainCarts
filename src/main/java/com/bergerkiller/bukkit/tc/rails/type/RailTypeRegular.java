package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.logic.*;
import com.bergerkiller.bukkit.tc.utils.MinecartTrackLogic;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getFirst;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.material.Rails;

/**
 * All regular types of rails - the ones provided by Minecraft.
 * All these rail types can slope and some of them can make curves.
 * None of them can be used vertically, hence, it is a horizontal rail.<br><br>
 * <p/>
 * Warning: all 'regular' type of rails REQUIRE a valid Rails Material type.
 * Do not write an isType check that results in non-Rails material types.
 */
public class RailTypeRegular extends RailTypeHorizontal {
    private static final Material REGULAR_RAIL_TYPE = getFirst("RAIL", "LEGACY_RAILS");

    /**
     * Gets all possible directions a Minecart can go when on regular rails.
     * There is no 'UP' direction for vertical rails - it assumes the horizontal direction
     * towards the wall this vertical rail is attached to instead.
     *
     * @param railDirection of the track
     * @return all possible directions
     */
    public static BlockFace[] getPossibleDirections(BlockFace railDirection) {
        return FaceUtil.getFaces(railDirection.getOppositeFace());
    }

    @Override
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (this.isUpsideDown(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlaced(Block railsBlock) {
        // Perform Vanilla minecart track logic which includes some fixes for our custom rail types
        // Note that the builtin onPlace is not called when placing a custom rail!
        if (this.isUpsideDown(railsBlock)) {
            MinecartTrackLogic logic = new MinecartTrackLogic(railsBlock);
            logic.update(railsBlock.isBlockIndirectlyPowered(), true);
        }

        // Find and validate rails - only regular 'initial' types are allowed
        Rails rails = BlockUtil.getRails(railsBlock);
        if (rails == null || rails.isCurve() || rails.isOnSlope()) {
            return;
        }

        // Check for a vertical rail right above this rail
        // If one is found, turn this rail into a slope to it
        Block above = railsBlock.getRelative(BlockFace.UP);
        if (Util.ISVERTRAIL.get(above)) {

            BlockFace railDir = rails.getDirection();
            BlockFace dir = Util.getVerticalRailDirection(above);
            // No other directions going on for this rail?
            if (railDir != dir && railDir != dir.getOppositeFace()) {
                if (Util.getRailsBlock(railsBlock.getRelative(railDir)) != null) {
                    return;
                }
                if (Util.getRailsBlock(railsBlock.getRelative(railDir.getOppositeFace())) != null) {
                    return;
                }
            }

            // Direction we are about to connect is supported?
            if (BlockUtil.isSuffocating(railsBlock.getRelative(dir))) {
                rails.setDirection(dir, true);
                BlockUtil.setData(railsBlock, rails);
            }
        }

        // Check for a vertical rail right above this rail on an outer-facing wall
        // This logic only applies for upside-down rails
        if (isUpsideDown(railsBlock, rails)) {
            for (BlockFace face : FaceUtil.AXIS) {
                Block aboveAt = above.getRelative(face);
                if (Util.ISVERTRAIL.get(aboveAt)) {
                    rails.setDirection(face, true);
                    BlockUtil.setData(railsBlock, rails);
                    break;
                }
            }
        }
    }

    /**
     * Checks whether a rails block is a sloped rail leading upwards into
     * a certain direction. This is used by vertical rails.
     * 
     * @param railsBlock
     * @param direction
     * @return True if the rails is an upside-down slope leading upwards the direction
     */
    public boolean isSlopeUpwardsTo(Block railsBlock, BlockFace direction) {
        if (!TCConfig.allowUpsideDownRails) {
            return false;
        }

        Rails rails = Util.getRailsRO(railsBlock);
        return rails != null && rails.isOnSlope() && rails.getDirection() == direction;
    }

    @Override
    public boolean isUpsideDown(Block railsBlock) {
        return isUpsideDown(railsBlock, null);
    }

    private boolean isUpsideDown(Block railsBlock, Rails rails) {
        if (!TCConfig.allowUpsideDownRails) {
            return false;
        }

        // Check block directly above - should be a valid solid
        // Shortcut for BlockData 'AIR', as this is the most common type you're going to get
        // isSuffocating() should be a fast call, but it goes through some layers, so why not.
        Block blockAbove = railsBlock.getRelative(BlockFace.UP);
        if (!Util.isUpsideDownRailSupport(blockAbove)) {
            return false;
        }

        // Check block directly below supports the rails, or not
        if (BlockUtil.canSupportTop(railsBlock.getRelative(BlockFace.DOWN))) {
            return false;
        }

        if (rails == null) {
            rails = Util.getRailsRO(railsBlock);
        }
        if (rails == null) {
            return false;
        }

        if (rails.isOnSlope()) {
            // Check blocks above-forward - should be valid solid or another upside-down rails
            Block nextBlock = railsBlock.getRelative(rails.getDirection().getOppositeFace());
            if (!BlockUtil.isSuffocating(nextBlock)) {
                RailType railType = RailType.getType(nextBlock);
                if (railType == RailType.NONE) {
                    return false;
                }
            }

            // Valid!
            return true;
        } else {
            // Valid!
            return true;
        }
    }
    
    /**
     * Gets the next position to go to, without requesting information from the rail itself.
     * This allows it to be used by other rail types.
     *
     * @param currentTrack     the 'Minecart' is on
     * @param currentDirection the 'Minecart' is moving
     * @param railDirection    of the currentTrack
     * @param sloped           state of the rail - whether it is sloped
     * @return the next position the 'Minecart' goes to
     */
    public static Block getNextPos(Block currentTrack, BlockFace currentDirection, BlockFace railDirection, boolean sloped) {
        return getNextPos(currentTrack, currentDirection, railDirection, sloped, false);
    }

    /**
     * Gets the next position to go to, without requesting information from the rail itself.
     * This allows it to be used by other rail types.
     *
     * @param currentTrack     the 'Minecart' is on
     * @param currentDirection the 'Minecart' is moving
     * @param railDirection    of the currentTrack
     * @param sloped           state of the rail - whether it is sloped
     * @param upsideDown       whether the rails is upside-down
     * @return the next position the 'Minecart' goes to
     */
    public static Block getNextPos(Block currentTrack, BlockFace currentDirection, BlockFace railDirection, boolean sloped, boolean upsideDown) {
        Block result;
        if (FaceUtil.isSubCardinal(railDirection)) {
            // Get a set of possible directions to go to
            BlockFace[] possible = FaceUtil.getFaces(railDirection.getOppositeFace());

            // Simple forward - always true
            boolean isSimpleForward = false;
            for (BlockFace newdir : possible) {
                if (newdir == currentDirection) {
                    isSimpleForward = true;
                    break;
                }
            }
            if (isSimpleForward) {
                result = currentTrack.getRelative(currentDirection);
            } else if (FaceUtil.isVertical(currentDirection)) {
                // Down or Up goes to a fixed direction at all times
                BlockFace dir = possible[1];
                result = currentTrack.getRelative(dir);
            } else {
                // Get connected faces
                BlockFace dir = currentDirection.getOppositeFace();
                BlockFace nextDir;
                if (possible[0].equals(dir)) {
                    nextDir = possible[1];
                } else if (possible[1].equals(dir)) {
                    nextDir = possible[0];
                    // south-east rule
                } else if (possible[0] == BlockFace.SOUTH || possible[0] == BlockFace.EAST) {
                    nextDir = possible[0];
                } else {
                    nextDir = possible[1];
                }
                result = currentTrack.getRelative(nextDir);
            }
        } else if (sloped) {
            if (railDirection == currentDirection || currentDirection == BlockFace.UP) {
                // Moving up the slope
                Block above = currentTrack.getRelative(BlockFace.UP);
                if (RailType.VERTICAL.isRail(above) && (currentDirection == BlockFace.UP || Util.getVerticalRailDirection(above) == currentDirection)) {
                    // Go to vertical rails above
                    result = above;
                } else {
                    // Go up one and then forward
                    result = above.getRelative(railDirection);
                }
            } else {
                // Moving down an upside-down slope onto a vertical rail below
                Block below = currentTrack.getRelative(BlockFace.DOWN);
                if (upsideDown && Util.ISVERTRAIL.get(below)) {
                    // Go to vertical rails below
                    result = currentTrack; // is done -1 at the end of the function
                } else {
                    // Moving down the slope, follow slope end-direction
                    result = currentTrack.getRelative(railDirection.getOppositeFace());
                }
            }
        } else if (railDirection == currentDirection || railDirection.getOppositeFace() == currentDirection) {
            // Move along horizontal tracks
            result = currentTrack.getRelative(currentDirection);
        } else {
            // South-West rule
            result = currentTrack.getRelative(railDirection);
        }
        if (upsideDown) {
            result = result.getRelative(BlockFace.DOWN);
        }
        return result;
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.isType(REGULAR_RAIL_TYPE);
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        Rails rails = Util.getRailsRO(trackBlock);
        if (rails == null) {
            return new BlockFace[0];
        } else if (rails.isOnSlope() && Util.isVerticalAbove(trackBlock, rails.getDirection())) {
            return new BlockFace[] { rails.getDirection().getOppositeFace(), BlockFace.UP };
        } else {
            return getPossibleDirections(rails.getDirection());
        }
    }

    @Override
    public List<RailJunction> getJunctions(Block railBlock) {
        Rails rails = Util.getRailsRO(railBlock);
        if (rails == null || rails.isOnSlope()) {
            return super.getJunctions(railBlock);
        }

        return Arrays.asList(
                new RailJunction("n", RailLogicHorizontal.get(BlockFace.NORTH).getPath().getStartPosition()),
                new RailJunction("e", RailLogicHorizontal.get(BlockFace.EAST).getPath().getEndPosition()),
                new RailJunction("s", RailLogicHorizontal.get(BlockFace.SOUTH).getPath().getEndPosition()),
                new RailJunction("w", RailLogicHorizontal.get(BlockFace.WEST).getPath().getStartPosition())
                );
    }

    @Override
    public void switchJunction(Block railBlock, RailJunction from, RailJunction to) {
        BlockUtil.setRails(railBlock, juncToFace(from), juncToFace(to));
    }

    private static final BlockFace juncToFace(RailJunction junction) {
        switch (junction.name()) {
        case "n":
            return BlockFace.NORTH;
        case "e":
            return BlockFace.EAST;
        case "s":
            return BlockFace.SOUTH;
        case "w":
            return BlockFace.WEST;
        default:
            return BlockFace.NORTH;
        }
    }

    public RailLogicHorizontal getLogicForRails(Block railsBlock, Rails rails, BlockFace enterFace) {
        BlockFace direction = rails.getDirection();
        boolean upsideDown = isUpsideDown(railsBlock, rails);

        // Sloped logic
        if (rails.isOnSlope()) {
            // To vertical
            if (Util.isVerticalAbove(railsBlock, direction)) {
                return RailLogicVerticalSlopeNormalA.get(direction);
            }

            // From vertical to upside-down
            if (upsideDown && Util.isVerticalBelow(railsBlock, direction.getOppositeFace())) {
                return RailLogicVerticalSlopeUpsideDownA.get(direction);
            }

            // Default
            return RailLogicSloped.get(direction, upsideDown);
        }

        if (rails.isCurve()) {
            // Curved rails: is straight when entered a certain way
            BlockFace[] faces = FaceUtil.getFaces(direction);
            if (enterFace == faces[0].getOppositeFace() || enterFace == faces[1].getOppositeFace()) {
                return RailLogicHorizontal.get(enterFace);
            }
        } else {
            // Straight rails: is curve when entered from the side
            // It is here that the south-east rule is applied
            BlockFace sideFace = FaceUtil.rotate(direction, 2);
            if (enterFace == sideFace || enterFace == sideFace.getOppositeFace()) {
                BlockFace curvedDir = FaceUtil.combine(enterFace, direction.getOppositeFace());
                return RailLogicHorizontal.get(curvedDir);
            }
        }

        // Default Horizontal logic
        return RailLogicHorizontal.get(direction, upsideDown);
    }

    @Override
    public RailLogic getLogic(RailState state) {
        Rails rails = Util.getRailsRO(state.railBlock());
        if (rails == null) {
            return RailLogicGround.INSTANCE;
        }
        return getLogicForRails(state.railBlock(), rails, state.enterFace());
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        Rails rails = Util.getRailsRO(railsBlock);
        return rails == null ? BlockFace.SELF : rails.getDirection();
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        Rails rails = Util.getRailsRO(railsBlock);
        if (rails == null) {
            return super.getSpawnLocation(railsBlock, orientation);
        }

        BlockFace dir = FaceUtil.getRailsCartDirection(rails.getDirection());
        if (FaceUtil.getFaceYawDifference(dir.getOppositeFace(), orientation) < 90) {
            dir = dir.getOppositeFace();
        }
        Location result = super.getSpawnLocation(railsBlock, dir);
        if (rails.isOnSlope()) {
            // At a 45-degree angle
            result.setPitch(result.getPitch() - 45.0F);
            // Slope height offset
            result.setY(result.getY() + 0.5);
        }
        return result;
    }

    @Override
    public RailsTexture getRailsTexture(Block railsBlock) {
        Rails rails = Util.getRailsRO(railsBlock);
        if (rails == null) {
            return super.getRailsTexture(railsBlock);
        }
        BlockFace direction = rails.getDirection();
        if (FaceUtil.isSubCardinal(direction)) {
            int yaw = 45 - FaceUtil.faceToYaw(direction);
            MapTexture top = RailsTexture.rotate(getResource(rails, "top"), yaw);
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            back = RailsTexture.flipV(back);
            RailsTexture result = new RailsTexture()
                    .set(BlockFace.UP, top)
                    .set(BlockFace.DOWN, back);
            for (BlockFace face : FaceUtil.AXIS) {
                result.set(face, RailsTexture.rotate(top, FaceUtil.faceToYaw(face)));
            }
            return result;
        } else if (rails.isOnSlope()) {
            MapTexture side = getResource(rails, "side");
            MapTexture top = getResource(rails, "top");
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            return new RailsTexture()
                    .set(direction.getOppositeFace(), top)
                    .set(direction, RailsTexture.flipV(back))
                    .set(BlockFace.UP, RailsTexture.rotate(top, -FaceUtil.faceToYaw(direction)))
                    .set(BlockFace.DOWN, RailsTexture.rotate(back, FaceUtil.faceToYaw(direction)))
                    .setOpposites(FaceUtil.rotate(direction, 2), side);
            
        } else {
            MapTexture front = getResource(rails, "front");
            MapTexture side = getResource(rails, "side");
            MapTexture top = RailsTexture.rotate(getResource(rails, "top"), FaceUtil.faceToYaw(direction));
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            return new RailsTexture()
                    .set(BlockFace.UP, top)
                    .set(BlockFace.DOWN, back)
                    .setOpposites(direction, front)
                    .setOpposites(FaceUtil.rotate(direction, 2), side);
        }
    }

    protected String getRailsTexturePath(Rails rails, String name) {
        if (rails.isCurve()) {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_curved_" + name + ".png";
        } else if (rails.isOnSlope()) {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_sloped_" + name + ".png";
        } else {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_straight_" + name + ".png";
        }
    }

    private MapTexture getResource(Rails rails, String name) {
        return MapTexture.loadPluginResource(TrainCarts.plugin, getRailsTexturePath(rails, name));
    }
}
