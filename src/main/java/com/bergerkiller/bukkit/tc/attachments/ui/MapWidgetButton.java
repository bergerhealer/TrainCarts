package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

public class MapWidgetButton extends MapWidget {

    public MapWidgetButton() {
        this.setFocusable(true);
    }

    @Override
    public void onDraw() {
        if (this.isActivated()) {
            this.view.fillItem(MapResourcePack.SERVER, new ItemStack(Material.DIAMOND_BLOCK));
        } else if (this.isFocused()) {
            this.view.fillItem(MapResourcePack.SERVER, new ItemStack(Material.DIAMOND));
        } else {
            this.view.fillItem(MapResourcePack.SERVER, new ItemStack(Material.SUGAR));
        }
    }
    
    
    
}
