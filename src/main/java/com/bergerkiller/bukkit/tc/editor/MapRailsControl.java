package com.bergerkiller.bukkit.tc.editor;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Displays a rails block from a certain viewpoint
 */
public class MapRailsControl extends MapControl {
    private static final byte[] MARKER_COLORS = {18, 30, 50, 122, 66, 42};
    private final boolean[] _directions = new boolean[6];
    private RailsTexture _texture = new RailsTexture();
    private BlockFace face = BlockFace.NORTH;
    private int rotation = 0;
    private int index = -1;
    private int blinkCtr = 0;
    private boolean blinkOn = false;

    /**
     * Sets the Rails type and block this rails control is displaying
     * 
     * @param type
     * @param railsBlock
     */
    public void setRails(RailType type, Block railsBlock) {
        this._texture = type.getRailsTexture(railsBlock);
    }

    /**
     * Turns a direction marker on or off
     * 
     * @param direction
     * @param enabled
     */
    public void setDirection(BlockFace direction, boolean enabled) {
        int idx = faceToIdx(direction);
        if (this._directions[idx] != enabled) {
            this._directions[idx] = enabled;
            this.draw();
        }
    }

    /**
     * Reads whether a direction marker is on or off
     * 
     * @param direction to get
     * @return marker state
     */
    public boolean getDirection(BlockFace direction) {
        return this._directions[faceToIdx(direction)];
    }

    /**
     * Gets all enabled marker directions
     * 
     * @return marker directions
     */
    public BlockFace[] getDirections() {
        ArrayList<BlockFace> faces = new ArrayList<BlockFace>(2);
        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            if (getDirection(face)) {
                faces.add(face);
            }
        }
        return LogicUtil.toArray(faces, BlockFace.class);
    }

    @Override
    public void onInit() {
        Arrays.fill(this._directions, true);
        updateView();
    }

    @Override
    public void onTick() {
        if (updateView()) {
            draw();
        }
        if (this.isSelected()) {
            if (index == -1) {
                nextIndex(1);
            }
            if (++blinkCtr >= 6) {
                blinkCtr = 0;
                blinkOn = !blinkOn;
                draw();
            }
        } else if (index != -1) {
            index = -1;
            draw();
        }
    }

    @Override
    public void onDraw() {
        MapTexture texture = RailsTexture.rotate(this._texture.get(this.face), this.rotation);
        display.getLayer(2).draw(texture, x, y);

        display.getLayer(3).clearRectangle(x, y, texture.getWidth(), texture.getHeight());

        MapTexture arrow = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/arrow.png");
        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            BlockFace markerFace = getMarkerFace(face);
            if (markerFace == null) {
                continue;
            }

            MapTexture tex = null;
            int i = faceToIdx(face);
            if (i == this.index && this.blinkOn) {
                // Show solid white selection arrow
                tex = arrow;
            } else if (this._directions[i]) {
                // Show colored arrow
                tex = arrow.clone();
                tex.setBlendMode(MapBlendMode.MULTIPLY);
                tex.fill(MARKER_COLORS[i]);
            }

            if (tex != null) {
                tex = RailsTexture.rotate(tex, 270 - FaceUtil.faceToYaw(markerFace));
                int arrow_dx = (texture.getWidth() - tex.getWidth()) / 2;
                int arrow_dy = (texture.getHeight() - tex.getHeight()) / 2;
                
                if (markerFace == BlockFace.NORTH) {
                    arrow_dy = 0;
                } else if (markerFace == BlockFace.EAST) {
                    arrow_dx = texture.getWidth() - tex.getWidth();
                } else if (markerFace == BlockFace.SOUTH) {
                    arrow_dy = texture.getHeight() - tex.getHeight();
                } else if (markerFace == BlockFace.WEST) {
                    arrow_dx = 0;
                }
                display.getLayer(3).draw(tex, x + arrow_dx, y + arrow_dy);
            }
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.UP) {
            this.nextIndex(1);
        } else if (event.getKey() == Key.DOWN) {
            this.nextIndex(-1);
        } else if (event.getKey() == Key.ENTER && index >= 0 && index < this._directions.length) {
            this._directions[this.index] = !this._directions[index];
            this.draw();
        }
    }
    
    private void nextIndex(int n) {
        do {
            index += n;
            if (index < 0) {
                index = this._directions.length - 1;
            } else if (index >= this._directions.length) {
                index = 0;
            }
        } while (getMarkerFace(idxToFace(index)) == null);
        this.draw();
    }

    // gets north/east/south/west where marker is drawn. Null if not visible.
    private BlockFace getMarkerFace(BlockFace face) {
        // Do not shower vertical directions when looking from above/below
        if (FaceUtil.isVertical(this.face) && FaceUtil.isVertical(face)) {
            return null;
        }

        if (this.face == BlockFace.UP) {
            return FaceUtil.yawToFace(FaceUtil.faceToYaw(face) - this.rotation);
        } else if (this.face == BlockFace.DOWN) {
            BlockFace result = FaceUtil.yawToFace(FaceUtil.faceToYaw(face) + this.rotation);
            if (FaceUtil.isAlongZ(result)) {
                result = result.getOppositeFace();
            }
            return result;
        } else {
            if (face == BlockFace.UP) {
                return BlockFace.NORTH;
            } else if (face == BlockFace.DOWN) {
                return BlockFace.SOUTH;
            } else {
                BlockFace combined = FaceUtil.yawToFace(FaceUtil.faceToYaw(face) - FaceUtil.faceToYaw(this.face));
                if (FaceUtil.isAlongX(combined)) {
                    return combined;
                }
                return null;
            }
            /*
            } else if (FaceUtil.getFaceYawDifference(this.face, face) == 180) {
                return BlockFace.EAST;
            } else {
                return BlockFace.WEST;
            }
            */
        }
    }

    private boolean updateView() {
        Location loc = ((TCMapEditor) display).getOwner().getLocation();
        BlockFace face_new = FaceUtil.yawToFace(loc.getYaw() + 90.0f, false);
        int rotation_new = 0;
        if (loc.getPitch() > 70.0f) {
            // Looking down at the top face
            rotation_new = FaceUtil.faceToNotch(face_new) * 45;
            face_new = BlockFace.UP;
        } else if (loc.getPitch() < -70.0f) {
            // Looking up at the bottom face
            rotation_new = -FaceUtil.faceToNotch(face_new) * 45;
            face_new = BlockFace.DOWN;
        } else {
            // Looking from the side
            rotation_new = 0;
        }
        if (face_new != face || rotation_new != rotation) {
            face = face_new;
            rotation = rotation_new;
            return true;
        } else {
            return false;
        }
    }

    private static final int faceToIdx(BlockFace face) {
        switch (face) {
        case NORTH: return 0;
        case EAST: return 1;
        case SOUTH: return 2;
        case WEST: return 3;
        case UP: return 4;
        case DOWN: return 5;
        default: return 0;
        }
    }

    private static final BlockFace idxToFace(int idx) {
        switch (idx) {
        case 0: return BlockFace.NORTH;
        case 1: return BlockFace.EAST;
        case 2: return BlockFace.SOUTH;
        case 3: return BlockFace.WEST;
        case 4: return BlockFace.UP;
        case 5: return BlockFace.DOWN;
        default: return BlockFace.NORTH;
        }
    }
}
