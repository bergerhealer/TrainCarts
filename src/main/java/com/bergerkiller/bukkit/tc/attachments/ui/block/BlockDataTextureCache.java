package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders and caches the texture icons of blocks in any
 * requested resolution.
 */
public class BlockDataTextureCache {
    private static final Map<IntVector2, BlockDataTextureCache> textureCaches = new HashMap<IntVector2, BlockDataTextureCache>();
    private final Map<BlockData, MapTexture> blockTextures = new HashMap<BlockData, MapTexture>();
    private final int width, height;
    private final float scale;
    private final int off_x, off_y;

    private BlockDataTextureCache(IntVector2 key) {
        this.width = key.x;
        this.height = key.z;
        this.scale = (float) Math.max(this.width, this.height) / 25.7f;
        this.off_x = (int) (this.scale * 24.0f);
        this.off_y = (int) (this.scale * 20.0f);
    }

    public MapTexture get(BlockData data) {
        return blockTextures.computeIfAbsent(data, d -> {
            MapTexture texture = MapTexture.createEmpty(this.width, this.height);
            Model model = TCConfig.resourcePack.getBlockModel(d);
            texture.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
            texture.drawModel(model, this.scale, this.off_x, this.off_y, 225.0f, -60.0f);
            return texture;
        });
    }

    public static BlockDataTextureCache get(int width, int height) {
        return textureCaches.computeIfAbsent(new IntVector2(width, height), BlockDataTextureCache::new);
    }
}
