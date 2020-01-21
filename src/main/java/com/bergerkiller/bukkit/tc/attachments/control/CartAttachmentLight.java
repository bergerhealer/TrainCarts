package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;

import ru.beykerykt.lightapi.LightAPI;
import ru.beykerykt.lightapi.LightType;
import ru.beykerykt.lightapi.chunks.ChunkInfo;

public class CartAttachmentLight extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "LIGHT";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/light.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentLight();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("lightType", LightType.BLOCK);
            config.set("lightLevel", 15);
        }
    };

    private IntVector3 prev_block = null;
    private LightType light_type = LightType.BLOCK;
    private int light_level = 15;

    @Override
    public void onAttached() {
        // Don't have position information yet
    }

    @Override
    public void onDetached() {
        // Clean up
        if (prev_block != null) {
            World world = getManager().getWorld();
            LightAPI.deleteLight(world, prev_block.x, prev_block.y, prev_block.z, light_type, true);
            List<ChunkInfo> prev_chunks = LightAPI.collectChunks(world, prev_block.x, prev_block.y, prev_block.z, light_type, light_level);
            for (ChunkInfo chunk : prev_chunks) {
                LightAPI.updateChunk(chunk, light_type);
            }
            prev_block = null;
        }
    }

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
        Vector pos_d = this.getTransform().toVector();
        IntVector3 pos = new IntVector3(pos_d.getX(), pos_d.getY(), pos_d.getZ());
        if (!pos.equals(prev_block)) {
            World world = getManager().getWorld();
            List<ChunkInfo> prev_chunks = Collections.emptyList();
            if (prev_block != null) {
                LightAPI.deleteLight(world, prev_block.x, prev_block.y, prev_block.z, light_type, true);
                prev_chunks = LightAPI.collectChunks(world, prev_block.x, prev_block.y, prev_block.z, light_type, light_level);
            }
            LightAPI.createLight(world, pos.x, pos.y, pos.z, light_type, light_level, true);
            List<ChunkInfo> new_chunks = LightAPI.collectChunks(world, pos.x, pos.y, pos.z, light_type, light_level);
            prev_block = pos;

            for (ChunkInfo new_chunk : new_chunks) {
                LightAPI.updateChunk(new_chunk, this.light_type);
            }
            for (ChunkInfo prev_chunk : prev_chunks) {
                if (!new_chunks.contains(prev_chunk)) {
                    LightAPI.updateChunk(prev_chunk, this.light_type);
                }
            }
        }
    }
}
