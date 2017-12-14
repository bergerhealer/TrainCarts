package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

/**
 * Menu for setting up the cart length, wheel distance, wheel centering,
 * banking and movement drag parameters.
 */
public class PhysicalMenu extends MapWidgetWindow {
    private final MapWidgetAttachmentNode attachment;

    public PhysicalMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 15, 118, 104);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_ORANGE);
    }

    @Override
    public void onAttached() {
        this.activate();

        int y_offset = 5;
        int y_step = 12;

        //this.transformType
        this.addWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("cartLength", 1.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("cartLength", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, y_offset, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("wheelDistance", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("wheelDistance", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, y_offset + y_step, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("wheelCenter", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("wheelCenter", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, y_offset + 2 * y_step, 100, 11);
        
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.BACK && this.isActivated()) {
            this.removeWidget();
            return;
        }
        super.onKeyPressed(event);
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("physical");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}
