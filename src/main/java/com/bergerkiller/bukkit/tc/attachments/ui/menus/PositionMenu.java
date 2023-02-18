package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PositionMenu extends MapWidgetMenu {
    private boolean isLoadingWidgets;
    private final MapWidgetScroller scroller = new MapWidgetScroller();

    public PositionMenu() {
        this.setBounds(5, 15, 118, 108);
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

        isLoadingWidgets = true;

        final Builder builder = new Builder();
        builder.addRow(menu -> new MapWidgetSelectionBox() { // anchor
            @Override
            public void onAttached() {
                super.onAttached();

                // To test compatibility: load the attachment first
                AttachmentType attachmentType = menu.getAttachment().getType();
                for (AttachmentAnchor type : AttachmentAnchor.values()) {
                    if (type.supports(AttachmentControllerMember.class, attachmentType)) {
                        this.addItem(type.getName());
                    }
                }
                this.setSelectedItem(menu.getPositionConfigValue("anchor", AttachmentAnchor.DEFAULT.getName()));
            }

            @Override
            public void onSelectedItemChanged() {
                if (!menu.getPositionConfigValue("anchor", AttachmentAnchor.DEFAULT.getName()).equals(getSelectedItem())) {
                    menu.updatePositionConfigValue("anchor", getSelectedItem());
                }
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Anchor");

        builder.addRow(menu -> new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(menu.getPositionConfigValue("posX", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position X-Coordinate";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("posX", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Pos.X")
                .setSpacingAbove(3);

        builder.addRow(menu -> new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(menu.getPositionConfigValue("posY", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Y-Coordinate";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("posY", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Pos.Y");

        builder.addRow(menu -> new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(menu.getPositionConfigValue("posZ", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Z-Coordinate";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("posZ", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Pos.Z");

        builder.addRow(menu -> new MapWidgetNumberBox() { // Rotation X (pitch)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(menu.getPositionConfigValue("rotX", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Pitch";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("rotX", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Pitch");

        builder.addRow(menu -> new MapWidgetNumberBox() { // Rotation Y (yaw)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(menu.getPositionConfigValue("rotY", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Yaw";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("rotY", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Yaw");

        builder.addRow(menu -> new MapWidgetNumberBox() { // Rotation Z (roll)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(menu.getPositionConfigValue("rotZ", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Roll";
            }

            @Override
            public void onValueChanged() {
                menu.updatePositionConfigValue("rotZ", getValue());
            }
        }.setBounds(25, 0, menu.getSliderWidth(), 11))
                .addLabel(0, 3, "Roll");

        // Let the attachment type insert/add additional rows
        this.getAttachment().getType().createPositionMenu(builder);

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

        isLoadingWidgets = false;
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
            return config.get(key, def);
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
        if (isLoadingWidgets) {
            return;
        }

        ConfigurationNode config = getPositionConfig();
        boolean wasDefaultPosition = ObjectPosition.isDefaultSeatParent(config);

        manipulator.accept(config);

        // Reload the entire model when changing 'seat default' rules
        if (wasDefaultPosition != ObjectPosition.isDefaultSeatParent(config)) {
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        } else {
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
        }
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
        if (isLoadingWidgets) {
            return;
        }

        ConfigurationNode config = this.getAttachment().getConfig();
        manipulator.accept(config);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
    }

    /**
     * Gets the position configuration of the selected attachment
     *
     * @return position configuration
     * @see #getAttachment()
     * @see MapWidgetAttachmentNode#getConfig()
     */
    public ConfigurationNode getPositionConfig() {
        return this.attachment.getConfig().getNode("position");
    }

    /**
     * Gets the selected attachment node information
     *
     * @return selected attachment
     */
    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
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
    }
}
