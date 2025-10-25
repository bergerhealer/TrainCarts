package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A 2D plane (can tilt) above which players are kept positioned. The
 * size of this plane can be adjusted. When players are detected falling
 * through this plane, they are teleported upwards.
 */
public class CartAttachmentPlatformPlane extends CartAttachmentPlatform {
    private final OrientedBoundingBox bbox = new OrientedBoundingBox();

    @Override
    public void onLoad(ConfigurationNode config) {
        Vector3 size = LogicUtil.fixNull(this.getConfiguredPosition().size, DEFAULT_SIZE);
        bbox.setSize(new Vector(size.x, size.y, size.z));
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        // Switches between attachment implementation class
        if (isShulkerModeInConfig(config)) {
            return false;
        }

        return true;
    }

    @Override
    public void onMove(boolean absolute) {
    }

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }
}
