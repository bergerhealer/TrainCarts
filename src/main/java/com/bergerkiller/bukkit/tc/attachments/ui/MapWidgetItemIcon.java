package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TCConfig;

/**
 * Displays a 16x16 icon of an ItemStack
 */
public abstract class MapWidgetItemIcon extends MapWidget {
    private ItemStack item = new ItemStack(Material.AIR);

    public MapWidgetItemIcon() {
        this.setFocusable(true);
        this.setSize(16, 16);
    }

    public ItemStack getItemStack() {
        return this.item;
    }

    public MapWidgetItemIcon setItemStack(ItemStack item) {
        this.item = item;
        this.invalidate();
        return this;
    }

    @Override
    public void onDraw() {
        this.view.fillItem(TCConfig.resourcePack, this.item);
        if (this.isFocused()) {
            this.view.drawRectangle(0, 0, this.getWidth(), this.getHeight(), MapColorPalette.COLOR_RED);
        }
    }

    @Override
    public void onActivate() {
        this.onClick();
    }

    public abstract void onClick();
}
