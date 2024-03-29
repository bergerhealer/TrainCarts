package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PositionMenu extends MapWidgetMenu {
    private final MapWidgetScroller scroller = new MapWidgetScroller();

    public PositionMenu() {
        this.setBounds(5, 15, 118, 108);
        this.setPositionAbsolute(true);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        this.scroller.setBounds(5, 5, getWidth() - 7, getHeight() - 10);
        this.scroller.setScrollPadding(20);
        this.addWidget(this.scroller);
    }

    /**
     * Gets the widget of a number slider widget in this menu
     *
     * @return slider width
     */
    public int getSliderWidth() {
        return 86;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        final Builder builder = new Builder();
        builder.addRow(menu -> new MapWidgetSelectionBox() { // anchor
            @Override
            public void onAttached() {
                super.onAttached();

                // Currently selected
                AttachmentAnchor current = getCurrentAnchor();

                // To test compatibility: load the attachment first
                AttachmentType attachmentType = menu.getMenuAttachmentType();
                boolean foundCurrent = false;
                for (AttachmentAnchor type : AttachmentAnchor.values()) {
                    if (type.supports(AttachmentControllerMember.class, attachmentType)) {
                        this.addItem(type.getName());
                        if (type.equals(current)) {
                            foundCurrent = true;
                        }
                    }
                }
                if (!foundCurrent) {
                    this.addItem(current.getName()); // Some unsupported type, but show it anyway
                }
                this.setSelectedItem(current.getName());
            }

            @Override
            public void onSelectedItemChanged() {
                AttachmentAnchor newAnchor = AttachmentAnchor.find(
                        AttachmentControllerMember.class, menu.getMenuAttachmentType(), getSelectedItem());
                if (!getCurrentAnchor().equals(newAnchor)) {
                    menu.updatePositionConfigValue("anchor", newAnchor.getName());
                }
            }

            private AttachmentAnchor getCurrentAnchor() {
                String name = menu.getPositionConfigValue("anchor", AttachmentAnchor.DEFAULT.getName());
                return AttachmentAnchor.find(AttachmentControllerMember.class, menu.getMenuAttachmentType(), name);
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Anchor");

        builder.addPositionSlider("posX", "Pos.X", "Position X-Coordinate")
                        .setSpacingAbove(3);
        builder.addPositionSlider("posY", "Pos.Y", "Position Y-Coordinate");
        builder.addPositionSlider("posZ", "Pos.Z", "Position Z-Coordinate");

        builder.addRotationSlider("rotX", "Pitch", "Rotation Pitch");
        builder.addRotationSlider("rotY", "Yaw", "Rotation Yaw");
        builder.addRotationSlider("rotZ", "Roll", "Rotation Roll");

        // Let the attachment type insert/add additional rows
        this.getMenuAttachmentType().createPositionMenu(builder);

        // Actually create and initialize all the rows
        Row prevRow = null;
        int yPos = 0;
        for (Row row : builder.getRows()) {
            // Track spacing between rows
            if (prevRow != null) {
                yPos += Math.max(prevRow.spacingBelow, row.spacingAbove);
            }
            prevRow = row;

            // Create, position and add the widget and its labels to the scroller container
            MapWidget widget = row.creator.apply(this);
            int rowHeight = widget.getY() + widget.getHeight();
            widget.setPosition(widget.getX(), widget.getY() + yPos);
            scroller.addContainerWidget(widget);
            for (Row.Label label : row.labels) {
                MapWidgetText textWidget = new MapWidgetText();
                textWidget.setFont(MapFont.TINY);
                textWidget.setText(label.text);
                textWidget.setPosition(label.x, label.y + yPos);
                textWidget.setColor(MapColorPalette.getSpecular(this.labelColor, 0.5f));
                scroller.addContainerWidget(textWidget);
            }

            yPos += rowHeight;
        }
    }

    protected AttachmentType getMenuAttachmentType() {
        return this.getAttachment().getType();
    }

    /**
     * Reads a single value at a key of a position configuration
     *
     * @param key Key
     * @param def Default value to return if not stored
     * @return value
     * @param <T> Value type
     */
    public <T> T getPositionConfigValue(String key, T def) {
        ConfigurationNode config = getPositionConfig();
        if (config.contains(key)) {
            return config.getOrDefault(key, def);
        } else {
            return def;
        }
    }

    /**
     * Updates a single key-value pair in the position configuration of the
     * selected attachment.
     *
     * @param key Key
     * @param value Value to assign
     */
    public void updatePositionConfigValue(String key, Object value) {
        updatePositionConfig(config -> config.set(key, value));
    }

    /**
     * Updates the position configuration of the selected attachment using
     * a manipulator function. Modify the configuration inside the callback.
     *
     * @param manipulator
     */
    public void updatePositionConfig(Consumer<ConfigurationNode> manipulator) {
        ConfigurationNode config = getPositionConfig();
        manipulator.accept(config);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
    }

    /**
     * Updates a single key-value pair in the configuration of the
     * selected attachment.
     *
     * @param key Key
     * @param value Value to assign
     */
    public void updateConfigValue(String key, Object value) {
        updateConfig(config -> config.set(key, value));
    }

    /**
     * Updates the configuration of the selected attachment using
     * a manipulator function. Modify the configuration inside the callback.
     *
     * @param manipulator
     */
    public void updateConfig(Consumer<ConfigurationNode> manipulator) {
        ConfigurationNode config = this.getConfig();
        manipulator.accept(config);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
    }

    /**
     * Gets the configuration of the selected attachment
     *
     * @return configuration
     * @see MapWidgetAttachmentNode#getConfig()
     */
    public ConfigurationNode getConfig() {
        return this.attachment.getConfig();
    }

    /**
     * Gets the position configuration of the selected attachment
     *
     * @return position configuration
     * @see MapWidgetAttachmentNode#getConfig()
     */
    public final ConfigurationNode getPositionConfig() {
        return this.getConfig().getNode("position");
    }

    /**
     * Gets the selected attachment node information
     *
     * @return selected attachment
     */
    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

    private static class RotationNumberBox extends MapWidgetNumberBox {

        public RotationNumberBox() {
            this.setIncrement(0.1);
        }

        @Override
        public void onResetSpecial(MapPlayerInput.Key key) {
            // LEFT/RIGHT increments it by 90 degrees steps
            // UP/DOWN flips the value 180 degrees
            if (key == MapPlayerInput.Key.RIGHT) {
                setValue(MathUtil.wrapAngle(getValue() + 90.0));
            } else if (key == MapPlayerInput.Key.LEFT) {
                setValue(MathUtil.wrapAngle(getValue() - 90.0));
            } else {
                setValue(MathUtil.wrapAngle(getValue() + 180.0));
            }
        }
    }

    /**
     * A single row displayed in the position menu
     */
    public static class Row {
        public final Function<PositionMenu, MapWidget> creator;
        public final List<Label> labels = new ArrayList<>();
        public int spacingAbove = 1;
        public int spacingBelow = 1;

        public Row(Function<PositionMenu, MapWidget> creator) {
            this.creator = creator;
        }

        /**
         * Adds a label to be displayed for this row
         *
         * @param x Label X-coordinate
         * @param y Label Y-coordinate. Relative to row.
         * @param text Text to be displayed
         * @return this row
         */
        public Row addLabel(int x, int y, String text) {
            labels.add(new Label(x, y, text));
            return this;
        }

        /**
         * Sets the pixel spacing between this row and the next row.
         * By default this is 1 pixel. The maximum spacing of either
         * this row's below or the belows row above is used.
         *
         * @param spacing Pixel spacing
         * @return this row
         */
        public Row setSpacingBelow(int spacing) {
            this.spacingBelow = spacing;
            return this;
        }

        /**
         * Sets the pixel spacing between this row and the next row.
         * By default this is 1 pixel. The maximum spacing of either
         * this row's below or the belows row above is used.
         *
         * @param spacing Pixel spacing
         * @return this row
         */
        public Row setSpacingAbove(int spacing) {
            this.spacingAbove = spacing;
            return this;
        }

        /**
         * A single label text displayed relative to a row
         */
        public static class Label {
            public final int x, y;
            public final String text;

            public Label(int x, int y, String text) {
                this.x = x;
                this.y = y;
                this.text = text;
            }
        }
    }

    /**
     * Builder that configures the contents of a position menu. Callers can
     * modify the rows displayed in the menu.
     */
    public static class Builder {
        private final ArrayList<Row> rows = new ArrayList<>();

        /**
         * Gets a modifiable list of rows of widgets to be displayed in the
         * position menu.
         *
         * @return List of rows
         */
        public List<Row> getRows() {
            return rows;
        }

        /**
         * Adds a new widget row at the end. Labels can be added to the returned row.
         *
         * @param creator Function that creates the widget. The supplied PositionMenu can be
         *                used by the widget to load/update configuration.
         * @return added row
         */
        public Row addRow(Function<PositionMenu, MapWidget> creator) {
            Row row = new Row(creator);
            rows.add(row);
            return row;
        }

        /**
         * Adds a new widget row at a certain index position.
         * Labels can be added to the returned row.
         *
         * @param index Row index. 0 is front.
         * @param creator Function that creates the widget. The supplied PositionMenu can be
         *                used by the widget to load/update configuration.
         * @return added row
         */
        public Row addRow(int index, Function<PositionMenu, MapWidget> creator) {
            Row row = new Row(creator);
            rows.add(index, row);
            return row;
        }

        /**
         * Adds a set of sliders for configuring the configured size of the attachment
         *
         * @return added row
         */
        public Row addSizeBox() {
            return this.addRow(menu -> (new MapWidgetSizeBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    setInitialSize(menu.getPositionConfigValue("sizeX", 1.0),
                                   menu.getPositionConfigValue("sizeY", 1.0),
                                   menu.getPositionConfigValue("sizeZ", 1.0));
                }

                @Override
                public void onSizeChanged() {
                    menu.updatePositionConfig(config -> {
                        if (x.getValue() == 1.0 && y.getValue() == 1.0 && z.getValue() == 1.0) {
                            config.remove("sizeX");
                            config.remove("sizeY");
                            config.remove("sizeZ");
                        } else {
                            config.set("sizeX", x.getValue());
                            config.set("sizeY", y.getValue());
                            config.set("sizeZ", z.getValue());
                        }
                    });
                }
            }).setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }

        /**
         * Adds a position number slider, the same that is used for position x/y/z
         *
         * @param settingName Setting name, e.g. "posX"
         * @param shortName Label next to slider, e.g. "Pos.X"
         * @param propertyName Property name for the menu set command, e.g. "Position X Coordinate"
         * @return added row
         */
        public Row addPositionSlider(String settingName, String shortName, String propertyName) {
            return addPositionSlider(settingName, shortName, propertyName, Double.NaN);
        }

        /**
         * Adds a position number slider, the same that is used for position x/y/z
         *
         * @param settingName Setting name, e.g. "posX"
         * @param shortName Label next to slider, e.g. "Pos.X"
         * @param propertyName Property name for the menu set command, e.g. "Position X Coordinate"
         * @param defaultValue Default value that, if slider has this value, the property is removed.
         *                     Use NaN to make it so this never happens.
         * @return added row
         */
        public Row addPositionSlider(String settingName, String shortName, String propertyName, double defaultValue) {
            return this.addRow(menu -> new MapWidgetNumberBox() {
                        @Override
                        public void onAttached() {
                            super.onAttached();
                            this.setInitialValue(menu.getPositionConfigValue(settingName, 0.0));
                        }

                        @Override
                        public String getAcceptedPropertyName() {
                            return propertyName;
                        }

                        @Override
                        public void onValueChanged() {
                            if (getValue() == defaultValue) {
                                menu.updatePositionConfig(cfg -> cfg.remove(settingName));
                            } else {
                                menu.updatePositionConfigValue(settingName, getValue());
                            }
                        }
            }.setBounds(25, 0, menu.getSliderWidth(), 11))
             .addLabel(0, 3, shortName);
        }

        /**
         * Adds a rotation number slider, the same that is used for rotation x/y/z
         *
         * @param settingName Setting name, e.g. "rotX"
         * @param shortName Label next to slider, e.g. "Rot.X"
         * @param propertyName Property name for the menu set command, e.g. "Rotation X Coordinate"
         * @return added row
         */
        public Row addRotationSlider(String settingName, String shortName, String propertyName) {
            return this.addRow(menu -> new RotationNumberBox() {
                        @Override
                        public void onAttached() {
                            super.onAttached();
                            this.setInitialValue(menu.getPositionConfigValue(settingName, 0.0));
                        }

                        @Override
                        public String getAcceptedPropertyName() {
                            return propertyName;
                        }

                        @Override
                        public void onValueChanged() {
                            menu.updatePositionConfigValue(settingName, getValue());
                        }
            }.setBounds(25, 0, menu.getSliderWidth(), 11))
             .addLabel(0, 3, shortName);
        }
    }
}
