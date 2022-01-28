package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

public class SeatDisplayedItemDialog extends MapWidgetMenu {
    MapWidget setItemButton, positionButton, showFPVButton, disableButton;

    public SeatDisplayedItemDialog() {
        this.setBounds(17, 16, 84, 79);
        this.setBackgroundColor(MapColorPalette.getColor(16, 16, 128));
    }

    @Override
    public void onAttached() {
        setItemButton = this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                // Show dialog to set a new item
                this.getParent().addWidget(new SelectItemDialog() {
                    @Override
                    public void onDetached() {
                        super.onDetached();

                        if (attachment.getConfig().get("displayItem.enabled", false)) {
                            // Enable disable/position button
                            positionButton.setEnabled(true);
                            showFPVButton.setEnabled(true);
                            disableButton.setEnabled(true);
                        }
                    }
                }).setAttachment(attachment);
            }
        }).setText("Set Item")
          .setBounds(5, 5, 74, 15);

        positionButton = this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                this.getParent().addWidget(new PositionItemDialog()).setAttachment(attachment);
            }
        }).setText("Position")
          .setBounds(5, 23, 74, 15);

        showFPVButton = this.addWidget(new MapWidgetButton() {

            @Override
            public void onAttached() {
                updateText();
            }

            @Override
            public void onActivate() {
                attachment.getConfig().set("displayItem.showFirstPerson",
                        !attachment.getConfig().get("displayItem.showFirstPerson", false));
                updateText();
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
            }

            private void updateText() {
                this.setText(attachment.getConfig().get("displayItem.showFirstPerson", false)
                        ? "FPV: Visible" : "FPV: Hidden");
            }
        }).setBounds(5, 41, 74, 15);

        disableButton = this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                attachment.getConfig().set("displayItem.enabled", false);
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");

                // Disable all buttons except 'set item'
                positionButton.setEnabled(false);
                showFPVButton.setEnabled(false);
                disableButton.setEnabled(false);
                setItemButton.focus();

                display.playSound(SoundEffect.EXTINGUISH);
            }
        }).setText("Disable")
          .setBounds(5, 59, 74, 15);

        // Enable position/disable button if actually enabled
        // It can only be enabled by setting an item
        boolean isEnabled = attachment.getConfig().get("displayItem.enabled", false);
        positionButton.setEnabled(isEnabled);
        showFPVButton.setEnabled(isEnabled);
        disableButton.setEnabled(isEnabled);

        super.onAttached();
    }

    private static class PositionItemDialog extends MapWidgetMenu {
        private boolean isLoadingWidgets;

        public PositionItemDialog() {
            this.setBounds(-13, -16, 108, 98);
            this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        }

        @Override
        public void onAttached() {
            super.onAttached();

            isLoadingWidgets = true;

            int slider_width = 74;
            int y_offset = 5;
            int y_step = 12;

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

            display.playSound(SoundEffect.PISTON_EXTEND);
        }

        @Override
        public void onDetached() {
            super.onDetached();
            display.playSound(SoundEffect.PISTON_CONTRACT);
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

            config.set(key, value);

            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
        }

        public ConfigurationNode getConfig() {
            return this.attachment.getConfig().getNode("displayItem.position");
        }
    }

    private static class SelectItemDialog extends MapWidgetMenu implements ItemDropTarget {
        private MapWidgetItemSelector selector;

        public SelectItemDialog() {
            this.setBounds(-13, -12, 111, 97);
            this.setBackgroundColor(MapColorPalette.getColor(0, 128, 200));
            this.setDepthOffset(1);
        }

        @Override
        public void onAttached() {
            selector = addWidget(new MapWidgetItemSelector() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setSelectedItem(attachment.getConfig().get("displayItem.item", new ItemStack(Material.PUMPKIN)));
                }

                @Override
                public void onSelectedItemChanged() {
                    boolean wasEnabled = attachment.getConfig().get("displayItem.enabled", false);
                    attachment.getConfig().set("displayItem.item", this.getSelectedItem());
                    attachment.getConfig().set("displayItem.enabled", true);
                    if (wasEnabled) {
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                    } else {
                        // Enabled false -> true -> reload entire seat attachment
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }
            });
            selector.setPosition(5, 5);

            display.playSound(SoundEffect.PISTON_EXTEND);

            super.onAttached();
        }

        @Override
        public void onDetached() {
            super.onDetached();
            display.playSound(SoundEffect.PISTON_CONTRACT);
        }

        @Override
        public boolean acceptItem(ItemStack item) {
            selector.setSelectedItem(item);
            display.playSound(SoundEffect.CLICK_WOOD);
            return true;
        }
    }
}
