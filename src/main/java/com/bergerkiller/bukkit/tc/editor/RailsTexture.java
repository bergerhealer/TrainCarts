package com.bergerkiller.bukkit.tc.editor;

import java.util.Arrays;

import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
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
        return set(face, texture).set(face.getOppositeFace(), FaceUtil.isVertical(face)
                ? MapTexture.flipV(texture) : MapTexture.flipH(texture));
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
}
