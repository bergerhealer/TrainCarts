package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.DebugUtil;

/**
 * Shows a large preview of an item's 3D model
 */
public class MapWidgetItemPreview extends MapWidget {
    private ItemStack item = null;

    public void setItem(ItemStack item) {
        this.item = item;
        this.invalidate();
    }

    @Override
    public void onTick() {
        this.invalidate();
    }

    @Override
    public void onDraw() {
        if (this.item == null) {
            return;
        }

        List<Player> viewers = display.getViewers();
        if (viewers.isEmpty()) {
            this.view.fillItem(MapResourcePack.SERVER, this.item);
        } else {
            double scale = getWidth() / 64.0;
            Location loc = viewers.get(0).getEyeLocation();
            Matrix4x4 transform = new Matrix4x4();
            transform.translate(getWidth() / 2.0, 0.0, getHeight() / 2.0);
            transform.scale(scale);

            float f = loc.getPitch() - 90.0f;
            transform.rotateX(f);
            transform.rotateY(loc.getYaw());
            transform.translate(-8.0 / scale, -8.0 / scale, -8.0 / scale);
            Model itemModel = MapResourcePack.SERVER.getItemModel(this.item);
            this.view.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
            this.view.drawModel(itemModel, transform);
        }
    }
}
