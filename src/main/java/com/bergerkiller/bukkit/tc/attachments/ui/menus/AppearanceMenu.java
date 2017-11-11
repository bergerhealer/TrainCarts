package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

public class AppearanceMenu extends MapWidgetWindow {

    public AppearanceMenu() {
        this.setBounds(5, 20, 118, 95);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
    }

    public void close() {
        this.getParent().removeWidget(this);
    }
    
    @Override
    public void onAttached() {
        this.activate();
        
        //this.addWidget(new MapWidgetText().setText("Uweh~").setBounds(5, 5, 100, 50));

        this.addWidget(new MapWidgetItemSelector()).setSelectedItem(new ItemStack(Material.DIAMOND_SWORD)).setPosition(5, 5);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.BACK && this.isActivated()) {
            this.close();
            return;
        }
        super.onKeyPressed(event);
    }
}
