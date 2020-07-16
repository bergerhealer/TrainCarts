package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentItem;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

public class PositionMenu extends MapWidgetMenu {
    private boolean isLoadingWidgets;

    public PositionMenu() {
        this.setBounds(5, 15, 118, 108);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        isLoadingWidgets = true;

        int slider_width = 86;
        int y_offset = 5;
        int y_step = 12;

        this.addWidget(new MapWidgetSelectionBox() { // anchor
            @Override
            public void onAttached() {
                super.onAttached();

                // To test compatibility: load the attachment first
                AttachmentType attachmentType = attachment.getType();
                for (AttachmentAnchor type : AttachmentAnchor.values()) {
                    if (type.supports(MinecartMemberNetwork.class, attachmentType)) {
                        this.addItem(type.getName());
                    }
                }
                this.setSelectedItem(getConfigValue("anchor", AttachmentAnchor.DEFAULT.getName()));
            }

            @Override
            public void onSelectedItemChanged() {
                if (!getConfigValue("anchor", AttachmentAnchor.DEFAULT.getName()).equals(getSelectedItem())) {
                    updateConfigValue("anchor", getSelectedItem());
                }
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Anchor");
        y_offset += y_step;

        if (this.attachment.getType() == CartAttachmentItem.TYPE) {
            this.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    for (ItemTransformType type : ItemTransformType.values()) {
                        this.addItem(type.toString());
                    }
                    this.setSelectedItem(getConfigValue("transform", ItemTransformType.HEAD).toString());
                }

                @Override
                public void onSelectedItemChanged() {
                    updateConfigValue("transform", ItemTransformType.get(getSelectedItem()).name());
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
                this.setValue(getConfigValue("posX", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position X-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("posX", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.X");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfigValue("posY", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Y-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("posY", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Y");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfigValue("posZ", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Z-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("posZ", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation X (pitch)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfigValue("rotX", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Pitch";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("rotX", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pitch");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation Y (yaw)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfigValue("rotY", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Yaw";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("rotY", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Yaw");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation Z (roll)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getConfigValue("rotZ", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Roll";
            }

            @Override
            public void onValueChanged() {
                updateConfigValue("rotZ", getValue());
            }
        }).setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;

        isLoadingWidgets = false;
    }

    public <T> T getConfigValue(String key, T def) {
        ConfigurationNode config = getConfig();
        if (config.contains(key)) {
            return config.get(key, def);
        } else {
            return def;
        }
    }
    
    public void updateConfigValue(String key, Object value) {
        if (isLoadingWidgets) {
            return;
        }

        ConfigurationNode config = getConfig();
        boolean wasDefaultPosition = ObjectPosition.isDefaultSeatParent(config);

        config.set(key, value);

        // Reload the entire model when changing 'seat default' rules
        if (wasDefaultPosition != ObjectPosition.isDefaultSeatParent(config)) {
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        } else {
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
        }
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("position");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}
