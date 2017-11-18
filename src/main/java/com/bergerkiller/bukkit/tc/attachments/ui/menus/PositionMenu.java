package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public class PositionMenu extends MapWidgetWindow {
    private final MapWidgetAttachmentNode attachment;

    public PositionMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 15, 118, 104);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
    }

    @Override
    public void onAttached() {
        this.activate();

        this.addWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("posX", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("posX", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 5, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("posY", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("posY", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 20, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("posZ", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("posZ", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 35, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("rotX", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotX", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 50, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("rotY", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotY", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 65, 100, 11);
        
        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("rotZ", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotZ", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(10, 80, 100, 11);
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
        return this.attachment.getConfig().getNode("position");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}
