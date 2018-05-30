package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.config.PositionAnchorType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class PositionMenu extends MapWidgetWindow {
    private final MapWidgetAttachmentNode attachment;

    public PositionMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 15, 118, 108);
        this.setDepthOffset(4);
        this.setFocusable(true);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
    }

    @Override
    public void onAttached() {
        this.activate();

        int slider_width = 86;
        int y_offset = 5;
        int y_step = 12;

        this.addWidget(new MapWidgetSelectionBox() { // anchor
            @Override
            public void onAttached() {
                super.onAttached();

                for (PositionAnchorType type : PositionAnchorType.values()) {
                    this.addItem(type.toString());
                }
                this.setSelectedItem(getConfig().get("anchor", PositionAnchorType.DEFAULT).toString());
            }

            @Override
            public void onSelectedItemChanged() {
                getConfig().set("anchor", PositionAnchorType.get(getSelectedItem()).name());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Anchor");
        y_offset += y_step;

        if (this.attachment.getType() == CartAttachmentType.ITEM) {
            this.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    for (ItemTransformType type : ItemTransformType.values()) {
                        this.addItem(type.toString());
                    }
                    this.setSelectedItem(getConfig().get("transform", ItemTransformType.HEAD).toString());
                }

                @Override
                public void onSelectedItemChanged() {
                    getConfig().set("transform", ItemTransformType.get(getSelectedItem()).name());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(30, y_offset, slider_width, 11);
            addLabel(5, y_offset + 3, "Mode");
            y_offset += y_step;
        }

        // Spacing
        y_offset += 3;

        //this.transformType
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
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.X");
        y_offset += y_step;

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
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Y");
        y_offset += y_step;

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
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfig().get("rotX", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotX", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pitch");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfig().get("rotY", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotY", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Yaw");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfig().get("rotZ", 0.0));
            }

            @Override
            public void onValueChanged() {
                getConfig().set("rotZ", getValue());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;
    }

    private void addLabel(int x, int y, String text) {
        MapWidgetText label = new MapWidgetText();
        label.setFont(MapFont.TINY);
        label.setText(text);
        label.setPosition(x, y);
        label.setColor(MapColorPalette.getSpecular(MapColorPalette.COLOR_GREEN, 0.5f));
        this.addWidget(label);
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
