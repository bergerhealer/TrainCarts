package com.bergerkiller.bukkit.tc.editor;

import java.util.Arrays;

import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Stores the 6 sides of a block to display rails at 6 different angles in the map editor
 */
public class RailsTexture {
    private static MapTexture default_texture = null;
    private final MapTexture[] textures = new MapTexture[6];
    private final JavaPlugin owner;
    private final String root;

    public RailsTexture() {
        this(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/rails/");
    }

    public RailsTexture(JavaPlugin owner, String textureRoot) {
        this.owner = owner;
        this.root = textureRoot;
        if (default_texture == null) {
            default_texture = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/rails/unknown.png");
        }
        Arrays.fill(textures, default_texture);
    }

    /**
     * Sets two opposite sides of the block at the same time.
     * This is done by texture-transforming the texture for the opposite side,
     * mirroring it.
     * 
     * @param face
     * @param filename
     * @return this
     */
    public RailsTexture setOpposites(BlockFace face, String filename) {
        return setOpposites(face, load(filename));
    }

    /**
     * Sets two opposite sides of the block at the same time.
     * This is done by texture-transforming the texture for the opposite side,
     * mirroring it.
     * 
     * @param face
     * @param texture
     * @return this
     */
    public RailsTexture setOpposites(BlockFace face, MapTexture texture) {
        return set(face, texture).set(face.getOppositeFace(), FaceUtil.isVertical(face) ? flipV(texture) : flipH(texture));
    }

    public RailsTexture set(BlockFace face, String filename) {
        return this.set(face, load(filename));
    }

    public RailsTexture set(BlockFace face, MapTexture texture) {
        textures[faceToIdx(face)] = texture;
        return this;
    }

    public MapTexture get(BlockFace face) {
        return textures[faceToIdx(face)];
    }

    private final MapTexture load(String filename) {
        return MapTexture.loadPluginResource(this.owner, this.root + filename);
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

    /**
     * Flips the map texture horizontally
     * TODO: Move this to BKCommonLib
     * 
     * @param input texture
     * @return flipped texture
     */
    public static MapTexture flipH(MapTexture input) {
        MapTexture result = MapTexture.createEmpty(input.getWidth(), input.getHeight());
        for (int x = 0; x < result.getWidth(); x++) {
            for (int y = 0; y < result.getHeight(); y++) {
                result.writePixel(x, y, input.readPixel(result.getWidth() - x - 1, y));
            }
        }
        return result;
    }

    /**
     * Flips the map texture vertically
     * TODO: Move this to BKCommonLib
     * 
     * @param input texture
     * @return flipped texture
     */
    public static MapTexture flipV(MapTexture input) {
        MapTexture result = MapTexture.createEmpty(input.getWidth(), input.getHeight());
        for (int x = 0; x < result.getWidth(); x++) {
            for (int y = 0; y < result.getHeight(); y++) {
                result.writePixel(x, y, input.readPixel(x, result.getHeight() - y - 1));
            }
        }
        return result;
    }

    /**
     * Rotates a texture 0, 90, 180, or 270 degrees.
     * TODO: Move this to BKCommonLib
     * 
     * @param input texture
     * @param angle to rotate
     * @return rotated texture
     */
    public static MapTexture rotate(MapTexture input, int angle) {
        MapTexture result;
        if (MathUtil.getAngleDifference(angle, 90) <= 45) {
            result = MapTexture.createEmpty(input.getHeight(), input.getWidth());
            for (int x = 0; x < result.getWidth(); x++) {
                for (int y = 0; y < result.getHeight(); y++) {
                    result.writePixel(x, result.getHeight() - y - 1, input.readPixel(y, x));
                }
            }
        } else if (MathUtil.getAngleDifference(angle, 180) <= 45) {
            result = MapTexture.createEmpty(input.getWidth(), input.getHeight());
            for (int x = 0; x < result.getWidth(); x++) {
                for (int y = 0; y < result.getHeight(); y++) {
                    result.writePixel(x, y, input.readPixel(result.getWidth() - x - 1, result.getHeight() - y - 1));
                }
            }
        } else if (MathUtil.getAngleDifference(angle, 270) <= 45) {
            result = MapTexture.createEmpty(input.getHeight(), input.getWidth());
            for (int x = 0; x < result.getWidth(); x++) {
                for (int y = 0; y < result.getHeight(); y++) {
                    result.writePixel(result.getWidth() - x - 1, y, input.readPixel(y, x));
                }
            }
        } else {
            result = input.clone(); // no rotation
        }
        return result;
    }

}
