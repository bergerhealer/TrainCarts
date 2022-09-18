package com.bergerkiller.bukkit.tc.utils;

import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.google.common.collect.Lists;

/**
 * Vanilla Minecraft's Minecart track logic, ported to Bukkit.
 * This is used for upside-down rails that have normal physics cancelled (to prevent breaking)
 */
public class MinecartTrackLogic {
    private final World world;
    private final IntVector3 pos;
    private BlockData data;
    private final boolean hasNoCurves;
    private final List<IntVector3> neigh = Lists.newArrayList();

    public MinecartTrackLogic(Block block) {
        this(block.getWorld(), new IntVector3(block), WorldUtil.getBlockData(block));
    }

    public MinecartTrackLogic(World world, IntVector3 blockposition, BlockData iblockdata) {
        this.world = world;
        this.pos = blockposition;
        this.data = iblockdata;
        this.hasNoCurves = !RailType.REGULAR.isRail(iblockdata);
        this.getNeighbours((Rails) iblockdata.newMaterialData());
    }

    public List<IntVector3> getNeighbours() {
        return this.neigh;
    }

    private void getNeighbours(Rails rails) {
        this.neigh.clear();

        if (rails.isOnSlope()) {
            this.neigh.add(this.pos.add(rails.getDirection()).add(BlockFace.UP));
            this.neigh.add(this.pos.add(rails.getDirection().getOppositeFace()));
        } else if (rails.isCurve()) {
            for (BlockFace face : FaceUtil.getFaces(rails.getDirection())) {
                this.neigh.add(this.pos.add(face));
            }
        } else {
            this.neigh.add(this.pos.add(rails.getDirection()));
            this.neigh.add(this.pos.add(rails.getDirection().getOppositeFace()));
        }
    }

    private void refreshNeighbours() {
        for (int i = 0; i < this.neigh.size(); ++i) {
            MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic = this.createTrackLogic(this.neigh.get(i));

            if (blockminecarttrackabstract_minecarttracklogic != null && blockminecarttrackabstract_minecarttracklogic.isNeighbour(this)) {
                this.neigh.set(i, blockminecarttrackabstract_minecarttracklogic.pos);
            } else {
                this.neigh.remove(i--);
            }
        }
    }

    private MinecartTrackLogic createTrackLogic(IntVector3 blockposition) {
        BlockData iblockdata = WorldUtil.getBlockData(this.world, blockposition);

        if (MaterialUtil.ISRAILS.get(iblockdata)) {
            return new MinecartTrackLogic(this.world, blockposition, iblockdata);
        } else {
            IntVector3 blockposition1 = blockposition.add(BlockFace.UP);

            iblockdata = WorldUtil.getBlockData(this.world, blockposition1);
            if (MaterialUtil.ISRAILS.get(iblockdata)) {
                return new MinecartTrackLogic(this.world, blockposition1, iblockdata);
            } else {
                blockposition1 = blockposition.add(BlockFace.DOWN);
                iblockdata = WorldUtil.getBlockData(this.world, blockposition1);
                if (MaterialUtil.ISRAILS.get(iblockdata)) {
                    return new MinecartTrackLogic(this.world, blockposition1, iblockdata);
                } else {
                    return null;
                }
            }
        }
    }

    private boolean isNeighbour(MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic) {
        return this.isNeighbourAt(blockminecarttrackabstract_minecarttracklogic.pos);
    }

    private boolean isNeighbourAt(IntVector3 blockposition) {
        for (int i = 0; i < this.neigh.size(); ++i) {
            IntVector3 blockposition1 = this.neigh.get(i);
            if (blockposition1.x == blockposition.x && blockposition1.z == blockposition.z) {
                return true;
            }
        }
        return false;
    }

    public int getNeighbourCount() {
        int i = 0;
        for (BlockFace face : FaceUtil.AXIS) {
            if (this.isRailsAtUpDown(this.pos.add(face))) {
                ++i;
            }
        }
        return i;
    }

    private boolean isSpecialNeighbour(MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic) {
        return this.isNeighbour(blockminecarttrackabstract_minecarttracklogic) || this.neigh.size() != 2;
    }

    private boolean isRailsAtUpDown(IntVector3 blockposition) {
        return isRailsAt(blockposition) || isRailsAt(blockposition.add(BlockFace.UP)) || isRailsAt(blockposition.add(BlockFace.DOWN));
    }

    private boolean isRailsAt(IntVector3 pos) {
        return MaterialUtil.ISRAILS.get(this.world, pos.x, pos.y, pos.z);
    }

    private void c(MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic) {
        this.neigh.add(blockminecarttrackabstract_minecarttracklogic.pos);
        IntVector3 pos_north = this.pos.add(BlockFace.NORTH);
        IntVector3 pos_south = this.pos.add(BlockFace.SOUTH);
        IntVector3 pos_west = this.pos.add(BlockFace.WEST);
        IntVector3 pos_east = this.pos.add(BlockFace.EAST);
        boolean rails_at_north = this.isNeighbourAt(pos_north);
        boolean rails_at_south = this.isNeighbourAt(pos_south);
        boolean rails_at_west = this.isNeighbourAt(pos_west);
        boolean rails_at_east = this.isNeighbourAt(pos_east);

        boolean railsSet = false;
        Rails newRails = new Rails(this.data.getLegacyType());

        if (rails_at_north || rails_at_south) {
            railsSet = true;
            newRails.setDirection(BlockFace.SOUTH, false);
        }

        if (rails_at_west || rails_at_east) {
            railsSet = true;
            newRails.setDirection(BlockFace.EAST, false);
        }

        if (!this.hasNoCurves) {
            if (rails_at_south && rails_at_east && !rails_at_north && !rails_at_west) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH_WEST, false);
            }

            if (rails_at_south && rails_at_west && !rails_at_north && !rails_at_east) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH_EAST, false);
            }

            if (rails_at_north && rails_at_west && !rails_at_south && !rails_at_east) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH_EAST, false);
            }

            if (rails_at_north && rails_at_east && !rails_at_south && !rails_at_west) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH_WEST, false);
            }
        }

        if (railsSet && newRails.getDirection() == BlockFace.SOUTH) {
            if (isRailsAt(pos_north.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH, true);
            }

            if (isRailsAt(pos_south.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH, true);
            }
        }

        if (railsSet && newRails.getDirection() == BlockFace.EAST) {
            if (isRailsAt(pos_east.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.EAST, true);
            }

            if (isRailsAt(pos_west.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.WEST, true);
            }
        }

        if (!railsSet) {
            railsSet = true;
            newRails.setDirection(BlockFace.SOUTH, false);
        }

        this.data = BlockData.fromMaterialData(newRails);
        TrainCarts.plugin.setBlockDataWithoutBreaking(this.pos.toBlock(this.world), this.data);
    }

    private boolean isSpecialNeighbour(IntVector3 blockposition) {
        MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic = this.createTrackLogic(blockposition);

        if (blockminecarttrackabstract_minecarttracklogic == null) {
            return false;
        } else {
            blockminecarttrackabstract_minecarttracklogic.refreshNeighbours();
            return blockminecarttrackabstract_minecarttracklogic.isSpecialNeighbour(this);
        }
    }

    public MinecartTrackLogic update(boolean isPowered, boolean forcePhysics) {
        IntVector3 pos_north = this.pos.add(BlockFace.NORTH);
        IntVector3 pos_south = this.pos.add(BlockFace.SOUTH);
        IntVector3 pos_west = this.pos.add(BlockFace.WEST);
        IntVector3 pos_east = this.pos.add(BlockFace.EAST);
        boolean rails_at_north = this.isSpecialNeighbour(pos_north);
        boolean rails_at_south = this.isSpecialNeighbour(pos_south);
        boolean rails_at_west = this.isSpecialNeighbour(pos_west);
        boolean rails_at_east = this.isSpecialNeighbour(pos_east);

        boolean railsSet = false;
        Rails newRails = new Rails(this.data.getLegacyType());

        if ((rails_at_north || rails_at_south) && !rails_at_west && !rails_at_east) {
            railsSet = true;
            newRails.setDirection(BlockFace.SOUTH, false);
        }

        if ((rails_at_west || rails_at_east) && !rails_at_north && !rails_at_south) {
            railsSet = true;
            newRails.setDirection(BlockFace.EAST, false);
        }

        if (!this.hasNoCurves) {
            if (rails_at_south && rails_at_east && !rails_at_north && !rails_at_west) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH_WEST, false);
            }

            if (rails_at_south && rails_at_west && !rails_at_north && !rails_at_east) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH_EAST, false);
            }

            if (rails_at_north && rails_at_west && !rails_at_south && !rails_at_east) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH_EAST, false);
            }

            if (rails_at_north && rails_at_east && !rails_at_south && !rails_at_west) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH_WEST, false);
            }
        }

        if (!railsSet) {
            if (rails_at_north || rails_at_south) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH, false);
            }

            if (rails_at_west || rails_at_east) {
                railsSet = true;
                newRails.setDirection(BlockFace.EAST, false);
            }

            if (!this.hasNoCurves) {
                if (isPowered) {
                    if (rails_at_south && rails_at_east) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.NORTH_WEST, false);
                    }

                    if (rails_at_west && rails_at_south) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.NORTH_EAST, false);
                    }

                    if (rails_at_east && rails_at_north) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.SOUTH_WEST, false);
                    }

                    if (rails_at_north && rails_at_west) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.SOUTH_EAST, false);
                    }
                } else {
                    if (rails_at_north && rails_at_west) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.SOUTH_EAST, false);
                    }

                    if (rails_at_east && rails_at_north) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.SOUTH_WEST, false);
                    }

                    if (rails_at_west && rails_at_south) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.NORTH_EAST, false);
                    }

                    if (rails_at_south && rails_at_east) {
                        railsSet = true;
                        newRails.setDirection(BlockFace.NORTH_WEST, false);
                    }
                }
            }
        }

        if (railsSet && newRails.getDirection() == BlockFace.SOUTH) {
            if (isRailsAt(pos_north.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.NORTH, true);
            }

            if (isRailsAt(pos_south.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.SOUTH, true);
            }
        }

        if (railsSet && newRails.getDirection() == BlockFace.EAST) {
            if (isRailsAt(pos_east.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.EAST, true);
            }

            if (isRailsAt(pos_west.add(BlockFace.UP))) {
                railsSet = true;
                newRails.setDirection(BlockFace.WEST, true);
            }
        }

        if (!railsSet) {
            railsSet = true;
            newRails.setDirection(BlockFace.SOUTH, false);
        }

        this.getNeighbours(newRails);
        this.data = BlockData.fromMaterialData(newRails);
        if (forcePhysics || !WorldUtil.getBlockData(this.world, this.pos).equals(this.data)) {
            TrainCarts.plugin.setBlockDataWithoutBreaking(this.pos.toBlock(this.world), this.data);

            for (int i = 0; i < this.neigh.size(); ++i) {
                MinecartTrackLogic blockminecarttrackabstract_minecarttracklogic = this.createTrackLogic(this.neigh.get(i));

                if (blockminecarttrackabstract_minecarttracklogic != null) {
                    blockminecarttrackabstract_minecarttracklogic.refreshNeighbours();
                    if (blockminecarttrackabstract_minecarttracklogic.isSpecialNeighbour(this)) {
                        blockminecarttrackabstract_minecarttracklogic.c(this);
                    }
                }
            }
        }

        return this;
    }

    public BlockData getData() {
        return this.data;
    }

}
