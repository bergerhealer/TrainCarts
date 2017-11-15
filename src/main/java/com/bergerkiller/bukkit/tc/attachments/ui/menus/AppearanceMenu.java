package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

public class AppearanceMenu extends MapWidgetWindow {
    private final MapWidgetAttachmentNode attachment;

    public AppearanceMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 20, 118, 95);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
    }

    @Override
    public void onAttached() {
        this.activate();
        
        //this.addWidget(new MapWidgetText().setText("Uweh~").setBounds(5, 5, 100, 50));

        this.addWidget(new MapWidgetItemSelector() {
            @Override
            public void onSelectedItemChanged() {
                getAttachment().getConfig().set("item", getSelectedItem());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setSelectedItem(new ItemStack(Material.DIAMOND_SWORD)).setPosition(5, 5);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.BACK && this.isActivated()) {
            this.remove();
            return;
        }
        super.onKeyPressed(event);
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

    @Override
    public void onStatusChanged(MapStatusEvent event) {
        if (event.isName("changed")) {
            this.attachment.update();
        } else {
            System.out.println("NNN " + event);
        }
    }
}
