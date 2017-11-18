package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
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
        
        // This widget is always visible: type selector
        MapWidgetSelectionBox typeSelectionBox = this.addWidget(new MapWidgetSelectionBox() {
            @Override
            public void onSelectedItemChanged() {
                setType(ParseUtil.parseEnum(this.getSelectedItem(), CartAttachmentType.EMPTY));
            }
        });
        for (CartAttachmentType type : CartAttachmentType.values()) {
            typeSelectionBox.addItem(type.toString());
        }
        typeSelectionBox.setSelectedItem(getAttachment().getConfig().get("type", String.class));
        typeSelectionBox.setBounds(7, 3, 100, 9);

        // Set to display currently selected type
        setType(getAttachment().getConfig().get("type", CartAttachmentType.EMPTY));
    }

    public void setType(CartAttachmentType type) {
        if (getAttachment().getConfig().get("type", CartAttachmentType.EMPTY) != type) {
            getAttachment().getConfig().set("type", type);
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        }

        // Reset widgets
        while (this.getWidgetCount() > 2) {
            this.removeWidget(this.getWidget(this.getWidgetCount() - 1));
        }

        // Add correct widgets based on type
        if (type == CartAttachmentType.ITEM) {
            // ItemStack selector
            this.addWidget(new MapWidgetItemSelector() {
                @Override
                public void onSelectedItemChanged() {
                    getAttachment().getConfig().set("item", this.getSelectedItem());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setSelectedItem(new ItemStack(Material.DIAMOND_SWORD)).setPosition(5, 12);
        } else if (type == CartAttachmentType.ENTITY) {
            // Entity type selector
            
        }
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
