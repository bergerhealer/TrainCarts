package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.TCConfig;

/**
 * Shows a large preview of an item's 3D model
 */
public class MapWidgetItemPreview extends MapWidget {
    private ItemStack item = null;
    private float yaw = 0.0f;
    private float pitch = 0.0f;

    public void setItem(ItemStack item) {
        this.item = item;
        this.invalidate();
    }

    @Override
    public void onTick() {
        List<Player> viewers = display.getViewers();
        if (!viewers.isEmpty()) {
            Location loc = viewers.get(0).getEyeLocation();
            float newYaw = loc.getYaw();
            float newPitch = loc.getPitch() - 90.0f;
            if (newYaw != yaw || newPitch != pitch) {
                yaw = newYaw;
                pitch = newPitch;
                this.invalidate();
            }
        }
    }

    @Override
    public void onDraw() {
        if (this.item == null) {
            return;
        }

        double scale = getWidth() / 64.0;
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(getWidth() / 2.0, 0.0, getHeight() / 2.0);
        transform.scale(scale);

        transform.rotateX(pitch);
        transform.rotateY(yaw);
        transform.translate(-8.0 / scale, -8.0 / scale, -8.0 / scale);
        Model itemModel = TCConfig.resourcePack.getItemModel(this.item);
        this.view.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
        this.view.drawModel(itemModel, transform);
    }
}
